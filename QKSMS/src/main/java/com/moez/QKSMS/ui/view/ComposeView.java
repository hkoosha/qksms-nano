package com.moez.QKSMS.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.github.lzyzsd.circleprogress.DonutProgress;
import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.PhoneNumberUtils;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.data.ConversationLegacy;
import com.moez.QKSMS.sms.Message;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.sms.Transaction;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.RecipientProvider;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.dialog.QKDialog;

public class ComposeView extends LinearLayout implements View.OnClickListener {
    public final static String TAG = "ComposeView";

    private final String KEY_DELAYED_INFO_DIALOG_SHOWN = "delayed_info_dialog_shown";

    public interface OnSendListener {
        void onSend(String[] addresses);
    }

    enum SendButtonState {
        // send a message
        SEND,
        // cancel a message while it's sending
        CANCEL
    }

    private QKActivity mContext;
    private SharedPreferences mPrefs;
    private Resources mRes;

    private Conversation mConversation;
    private ConversationLegacy mConversationLegacy;

    private OnSendListener mOnSendListener;
    private RecipientProvider mRecipientProvider;

    // Views
    private QKEditText mReplyText;
    private DonutProgress mProgress;
    private ImageView mComposeIcon;
    private ImageButton mDelay;
    private QKTextView mLetterCount;

    // State
    private boolean mDelayedMessagingEnabled;
    private boolean mSendingCancelled;

    private ValueAnimator mProgressAnimator;
    private int mDelayDuration = 3000;

    private SendButtonState mButtonState = SendButtonState.SEND;

    public ComposeView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ComposeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = (QKActivity) context;
        mPrefs = mContext.getPrefs();
        mRes = mContext.getResources();

        mDelayedMessagingEnabled = mPrefs.getBoolean(QKPreference.K_DELAYED, false);
        try {
            mDelayDuration = Integer.parseInt(mPrefs.getString(QKPreference.K_DELAY_DURATION,
                                                               "3"));
            if (mDelayDuration < 1) {
                mDelayDuration = 1;
            }
            else if (mDelayDuration > 30) {
                mDelayDuration = 30;
            }
            mDelayDuration *= 1000;
        }
        catch (Exception e) {
            mDelayDuration = 3000;
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        // Get references to the views
        mReplyText = findViewById(R.id.compose_reply_text);
        FrameLayout mButton = findViewById(R.id.compose_button);
        mProgress = findViewById(R.id.progress);
        //        mButtonBackground = findViewById(R.id.compose_button_background);
        mComposeIcon = findViewById(R.id.compose_icon);
        mDelay = findViewById(R.id.delay);
        mLetterCount = findViewById(R.id.compose_letter_count);
        //        ImageButton mCancel = findViewById(R.id.cancel);

        mButton.setOnClickListener(this);
        //        mCancel.setOnClickListener(this);
        mDelay.setOnClickListener(this);


        mComposeIcon.setColorFilter(ThemeManager.getTextOnColorPrimary(),
                                    PorterDuff.Mode.SRC_ATOP);
        updateDelayButton();
        mProgress.setUnfinishedStrokeColor(ThemeManager.getTextOnColorSecondary());
        mProgress.setFinishedStrokeColor(ThemeManager.getTextOnColorPrimary());
        if (ThemeManager.getSentBubbleRes() != 0)
            mReplyText.setBackgroundResource(ThemeManager.getSentBubbleRes());
        mReplyText.getBackground()
                  .setColorFilter(ThemeManager.getNeutralBubbleColor(),
                                  PorterDuff.Mode.SRC_ATOP);
        getBackground().setColorFilter(ThemeManager.getBackgroundColor(),
                                       PorterDuff.Mode.SRC_ATOP);


        // There is an option for using the return button instead of the emoticon button in the
        // keyboard; set that up here.
        switch (Integer.parseInt(mPrefs.getString(QKPreference.K_ENTER_BUTTON, "0"))) {
            case 0: // emoji
                break;
            case 1: // new line
                mReplyText.setInputType(InputType.TYPE_CLASS_TEXT |
                                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
                                                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE);
                mReplyText.setSingleLine(false);
                break;
            case 2: // send
                mReplyText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                mReplyText.setInputType(InputType.TYPE_CLASS_TEXT |
                                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                mReplyText.setSingleLine(false);
                //Workaround because ACTION_SEND does not support multiline mode
                mReplyText.setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode == 66) {
                        sendSms();
                        return true;
                    }
                    return false;
                });
                break;
        }

        mReplyText.setTextChangedListener(s -> {
            int length = s.length();

            updateButtonState();

            // If the reply is within 10 characters of the SMS limit (160), it will start counting down
            // If the reply exceeds the SMS limit, it will count down until an extra message will have to be sent, and shows how many messages will currently be sent
            if (length < 150) {
                mLetterCount.setText("");
            }
            else if (150 <= length && length <= 160) {
                mLetterCount.setText("" + (160 - length));
            }
            else if (160 < length) {
                mLetterCount.setText((160 - length % 160) + "/" + (length / 160 + 1));
            }
        });

        mProgressAnimator = new ValueAnimator();
        mProgressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mProgressAnimator.setDuration(mDelayDuration);
        mProgressAnimator.setIntValues(0, 360);
        mProgressAnimator.addUpdateListener(animation -> mProgress.setProgress((int) animation.getAnimatedValue()));
        mProgressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mProgress.setVisibility(INVISIBLE);
                mProgress.setProgress(0);

                if (!mSendingCancelled) {
                    sendSms();
                    // In case they only enabled it for a particular message, let's set it back to the pref value
                    mDelayedMessagingEnabled = mPrefs.getBoolean(QKPreference.K_DELAYED, false);
                    updateDelayButton();
                }
                else {
                    mSendingCancelled = false;
                    updateButtonState();
                }
            }
        });
    }

    /**
     * Sets a listener to be pinged when an SMS message is sent.
     */
    public void setOnSendListener(OnSendListener l) {
        mOnSendListener = l;
    }

    /**
     * Sets a RecipientProvider. The RecipientProvider provides one method, getRecipientAddresses,
     * which returns a String[] of recipient addresses. This method will be called when we're trying
     * to send an SMS/MMS message, and onOpenConversation has NOT been called with a non-null
     * Conversation object, i.e. we cannot use the Conversation object to get recipient addresses.
     */
    public void setRecipientProvider(RecipientProvider p) {
        mRecipientProvider = p;
    }

    /**
     * Handles activity results that were started by this View. Returns true if the result was
     * handled by this view, false otherwise.
     */
    public boolean onActivityResult() {
        return false;
    }

    private void updateButtonState() {
        updateButtonState(SendButtonState.SEND);
    }

    private void updateButtonState(SendButtonState buttonState) {
        if (mButtonState != buttonState) {

            // Check if we need to switch animations
            AnimationDrawable animation = null;
            if (buttonState == SendButtonState.SEND) {
                animation = (AnimationDrawable) ContextCompat.getDrawable(mContext,
                                                                          R.drawable.plus_to_arrow);
            }
            else if (mButtonState == SendButtonState.SEND) {
                animation = (AnimationDrawable) ContextCompat.getDrawable(mContext,
                                                                          R.drawable.arrow_to_plus);
            }
            if (animation != null) {
                mComposeIcon.setImageDrawable(animation);
                animation.start();
            }

            // Handle any necessary rotation
            float rotation = mComposeIcon.getRotation();
            float target = buttonState == SendButtonState.SEND ? 0 : 45;
            int ANIMATION_DURATION = 300;
            ObjectAnimator.ofFloat(mComposeIcon, "rotation", rotation, target)
                          .setDuration(ANIMATION_DURATION)
                          .start();

            mButtonState = buttonState;
        }
    }

    /**
     * Sets the text of the Reply edit text.
     */
    public void setText(String text) {
        mReplyText.setText(text);
        mReplyText.setSelection(mReplyText.getText().length());
    }

    /**
     * Requests focus to the Reply edit text.
     */
    public void requestReplyTextFocus() {
        mReplyText.requestFocus();
    }

    public void sendDelayedSms() {
        mProgress.setVisibility(VISIBLE);
        updateButtonState(SendButtonState.CANCEL);
        mProgressAnimator.start();
    }

    public void sendSms() {
        String body = mReplyText.getText().toString();

        String[] recipients = null;
        if (mConversation != null) {
            recipients = mConversation.getRecipients().getNumbers();
            for (int i = 0; i < recipients.length; i++) {
                recipients[i] = PhoneNumberUtils.stripSeparators(recipients[i]);
            }
        }
        else if (mRecipientProvider != null) {
            recipients = mRecipientProvider.getRecipientAddresses();
        }

        // If we have some recipients, send the message!
        if (recipients != null && recipients.length > 0) {
            mReplyText.setText("");

            Transaction sendTransaction = new Transaction(mContext);

            Message message = new Message(body, recipients);

            // Notify the listener about the new text message
            if (mOnSendListener != null) {
                mOnSendListener.onSend(recipients);
            }

            long threadId = mConversation != null ? mConversation.getThreadId() : 0;
            if (!message.toString().equals("")) {
                sendTransaction.sendNewMessage(message, threadId);
            }
            NotificationMgr.update(mContext);

            if (mConversationLegacy != null) {
                mConversationLegacy.markRead(this, getContext());
            }

            // Reset the image button state
            updateButtonState();

            // Otherwise, show a toast to the user to prompt them to add recipients.
        }
        else {
            Toast.makeText(
                    mContext,
                    mRes.getString(R.string.error_no_recipients),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.compose_button:
                handleComposeButtonClick();
                break;

            case R.id.delay:
                if (!mPrefs.getBoolean(KEY_DELAYED_INFO_DIALOG_SHOWN,
                                       false) && !mDelayedMessagingEnabled) {
                    showDelayedMessagingInfo();
                }
                break;
        }
    }

    private void showDelayedMessagingInfo() {
        QKDialog qkDialog = new QKDialog()
                .setContext(mContext)
                .setTitle(R.string.pref_delayed)
                .setMessage(R.string.delayed_messaging_info)
                .setNegativeButton(R.string.just_once, v -> {
                });
        OnClickListener onClickListener = v ->
                mPrefs.edit().putBoolean(QKPreference.K_DELAYED, true).apply();
        qkDialog.setPositiveButton(true, R.string.enable, onClickListener)
                .show();
        mPrefs.edit()
              .putBoolean(KEY_DELAYED_INFO_DIALOG_SHOWN, true)
              .apply(); //This should be changed, the dialog should be shown each time when delayed messaging is disabled.
    }

    private void handleComposeButtonClick() {
        switch (mButtonState) {
            case SEND:
                if (!Util.isDefaultSmsApp(mContext)) {
                    Util.showDefaultSMSDialog(this, R.string.not_default_send);
                }
                else if (!TextUtils.isEmpty(mReplyText.getText())) {
                    if (mDelayedMessagingEnabled)
                        sendDelayedSms();
                    else
                        sendSms();
                }
                break;

            case CANCEL:
                mSendingCancelled = true;
                mProgressAnimator.end();
                //updateButtonState();
                break;
        }
    }

    /**
     * Sets the conversation for this compose view. This will setup the ComposeView with drafts.
     */
    public void onOpenConversation(Conversation conversation, ConversationLegacy conversationLegacy) {
        long threadId = mConversation != null ? mConversation.getThreadId() : -1;
        if (threadId > 0)
            sendPendingDelayedMessage();
        long newThreadId = conversation != null ? conversation.getThreadId() : -1;
        if (mConversation != null && mConversationLegacy != null && threadId != newThreadId) {
            // Save the old draft first before updating the conversation objects.
            saveDraft();
        }

        mConversation = conversation;
        mConversationLegacy = conversationLegacy;

        // If the conversation was different, set up the draft here.
        if (threadId != newThreadId || newThreadId == -1) {
            setupDraft();
        }
    }

    /**
     * If there's a pending delayed message, end the progress animation and go ahead with sending the message
     */
    private void sendPendingDelayedMessage() {
        if (mButtonState == SendButtonState.CANCEL && mProgressAnimator != null) {
            mProgressAnimator.end();
        }
    }

    /**
     * Saves a draft to the conversation.
     */
    public void saveDraft() {
        // If the conversation_reply view is null, then we won't worry about saving drafts at all. We also don't save
        // drafts if a message is about to be sent (delayed)
        if (mReplyText != null && mButtonState != SendButtonState.CANCEL) {
            String draft = mReplyText.getText().toString();

            if (mConversation != null) {
                if (mConversationLegacy.hasDraft() && TextUtils.isEmpty(draft)) {
                    mConversationLegacy.clearDrafts(getContext());

                }
                else if (!TextUtils.isEmpty(draft) &&
                        (!mConversationLegacy.hasDraft() || !draft.equals(mConversationLegacy.getDraft(
                                getContext())))) {
                    mConversationLegacy.saveDraft(getContext(), draft);
                }
            }
            else {
                // Only show the draft if we saved text, not if we just cleared some
                if (!TextUtils.isEmpty(draft)) {
                    if (mRecipientProvider != null) {
                        String[] addresses = mRecipientProvider.getRecipientAddresses();

                        if (addresses != null && addresses.length > 0) {
                            // save the message for each of the addresses
                            for (String address : addresses) {
                                ContentValues values = new ContentValues();
                                values.put("address", address);
                                values.put("date", System.currentTimeMillis());
                                values.put("read", 1);
                                values.put("type", 4);

                                // attempt to create correct thread id
                                long threadId = Util.getOrCreateThreadId(mContext, address);

                                Log.v(TAG, "saving message with thread id: " + threadId);

                                values.put("thread_id", threadId);
                                Uri messageUri = mContext.getContentResolver()
                                                         .insert(Uri.parse("content://sms/draft"),
                                                                 values);

                                Log.v(TAG, "inserted to uri: " + messageUri);

                                ConversationLegacy mConversationLegacy = new ConversationLegacy(
                                        threadId);
                                mConversationLegacy.saveDraft(getContext(), draft);
                            }
                        }
                    }
                }
            }
        }

    }

    /**
     * Displays the draft message to the user.
     */
    private void setupDraft() {
        if (mConversationLegacy != null) {
            if (mConversationLegacy.hasDraft()) {
                String text = mConversationLegacy.getDraft(getContext());

                mReplyText.setText(text);
                mReplyText.setSelection(text != null ? text.length() : 0);
                updateButtonState();
            }
            else {
                // Since this view can be reused, it's important to set the text to empty when there
                // isn't a new draft. Or else the previous conversation's draft can be carried on to
                // this new conversation.
                mReplyText.setText("");
                updateButtonState();
            }
        }
    }

    private void updateDelayButton() {
        mDelay.setColorFilter(mDelayedMessagingEnabled
                              ?
                              ThemeManager.getTextOnColorPrimary()
                              : ThemeManager.getTextOnColorSecondary(),
                              PorterDuff.Mode.SRC_ATOP);
    }

    public boolean isReplyTextEmpty() {
        return TextUtils.isEmpty(mReplyText.getText());
    }
}
