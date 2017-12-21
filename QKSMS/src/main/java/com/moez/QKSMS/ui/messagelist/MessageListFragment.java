package com.moez.QKSMS.ui.messagelist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.QKSMSApp;
import com.moez.QKSMS.R;
import com.moez.QKSMS.SqliteWrapper;
import com.moez.QKSMS.common.CIELChEvaluator;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.data.ContactList;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.data.ConversationLegacy;
import com.moez.QKSMS.data.Message;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.ui.MainActivity;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.RecyclerCursorAdapter;
import com.moez.QKSMS.ui.SwipeBackLayout;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.UiUtil;
import com.moez.QKSMS.ui.dialog.ConversationDetailsDialog;
import com.moez.QKSMS.ui.dialog.QKDialog;
import com.moez.QKSMS.ui.view.ComposeView;
import com.moez.QKSMS.ui.view.MessageListRecyclerView;
import com.moez.QKSMS.ui.view.SmoothLinearLayoutManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.moez.QKSMS.common.Util.setOf;

public class MessageListFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        RecyclerCursorAdapter.MultiSelectListener,
        SwipeBackLayout.ScrollChangedListener,
        RecyclerCursorAdapter.ItemClickListener<MessageItem> {

    public static final String TAG = "MessageListFragment";

    private static final int LOADER_MESSAGES = 1;

    private static final int MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN = 9528;
    private static final int DELETE_MESSAGE_TOKEN = 9700;

    private static final int MENU_EDIT_MESSAGE = 14;
    private static final int MENU_VIEW_MESSAGE_DETAILS = 17;
    private static final int MENU_DELETE_MESSAGE = 18;
    private static final int MENU_DELIVERY_REPORT = 20;
    private static final int MENU_FORWARD_MESSAGE = 21;
    private static final int MENU_COPY_MESSAGE_TEXT = 24;
    private static final int MENU_COPY_TO_SDCARD = 25;
    private static final int MENU_ADD_ADDRESS_TO_CONTACTS = 27;
    private static final int MENU_LOCK_MESSAGE = 28;
    private static final int MENU_UNLOCK_MESSAGE = 29;
    private static final int MENU_SAVE_RINGTONE = 30;
    private static final String[] SMS_REPORT_STATUS_PROJECTION = new String[]{
            Telephony.Sms.ADDRESS,            //0
            Telephony.Sms.STATUS,             //1
            Telephony.Sms.DATE_SENT,          //2
            Telephony.Sms.TYPE                //3
    };
    // These indices must sync up with the projections above.
    private static final int COLUMN_RECIPIENT = 0;
    private static final int COLUMN_DELIVERY_STATUS = 1;
    private static final int COLUMN_DATE_SENT = 2;
    private static final int COLUMN_MESSAGE_TYPE = 3;

    private boolean mIsSmsEnabled;

    private Cursor mCursor;
    private CIELChEvaluator mCIELChEvaluator;
    private MessageListAdapter mAdapter;
    private MessageListRecyclerView mRecyclerView;
    private Conversation mConversation;
    private ConversationLegacy mConversationLegacy;

    private ComposeView mComposeView;
    private ConversationDetailsDialog mConversationDetailsDialog;

    // so we can remember it after re-entering the activity.
    // If the value >= 0, then we jump to that line. If the
    // value is maxInt, then we jump to the end.

    private BackgroundQueryHandler mBackgroundQueryHandler;

    private long mThreadId;
    private long mRowId;
    private String mHighlight;
    private boolean mShowImmediate;

    private QKActivity mContext;

    protected static MessageListFragment getInstance(long threadId, long rowId, String highlight, boolean showImmediate) {

        Bundle args = new Bundle();
        args.putLong(MessageListActivity.ARG_THREAD_ID, threadId);
        args.putLong(MessageListActivity.ARG_ROW_ID, rowId);
        args.putString(MessageListActivity.ARG_HIGHLIGHT, highlight);
        args.putBoolean(MessageListActivity.ARG_SHOW_IMMEDIATE, showImmediate);

        MessageListFragment fragment = new MessageListFragment();
        fragment.setArguments(args);

        return fragment;
    }

    public MessageListFragment() {

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = (QKActivity) activity;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        QKSMSApp.getRefWatcher(getActivity()).watch(this);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getView() != null) {
            getView().setBackgroundColor(ThemeManager.getBackgroundColor());
        }
    }

    // ------------------------

    private static void lockMessage(Context context, MessageItem msgItem, boolean locked) {
        Uri uri = Telephony.Sms.CONTENT_URI;
        final Uri lockUri = ContentUris.withAppendedId(uri, msgItem.mMsgId);

        final ContentValues values = new ContentValues(1);
        values.put("locked", locked ? 1 : 0);

        new Thread(() -> context.getContentResolver().update(
                lockUri, values, null, null),
                   "MainActivity.lockMessage").start();
    }

    private static Uri getContactUriForEmail(Context context, String emailAddress) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI,
                                       Uri.encode(emailAddress));
        String[] projection = {ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.Contacts.DISPLAY_NAME};

        try (Cursor cursor = SqliteWrapper.query(context,
                                                 cr,
                                                 uri,
                                                 projection,
                                                 null,
                                                 null,
                                                 null)) {
            if (cursor != null)
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    if (!TextUtils.isEmpty(name))
                        return ContentUris.withAppendedId(
                                ContactsContract.Contacts.CONTENT_URI, cursor.getLong(0));
                }
        }

        return null;
    }

    private static Uri getContactUriForPhoneNumber(String phoneNumber) {
        Contact contact = Contact.get(phoneNumber, false);
        return contact.existsInDatabase() ? contact.getUri() : null;
    }

    private static String getMessageDetails(Context context, Cursor cursor) {
        if (cursor == null) {
            return null;
        }

        Log.d("Mms", "getTextMessageDetails");

        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.text_message));

        // Address: ***
        details.append("\n\n");
        int smsType = cursor.getInt(MessageColumns.COLUMN_SMS_TYPE);
        if (SmsHelper.isOutgoingFolder(smsType)) {
            details.append(res.getString(R.string.to_address_label));
        }
        else {
            details.append(res.getString(R.string.from_label));
        }
        details.append(cursor.getString(MessageColumns.COLUMN_SMS_ADDRESS));

        // Sent: ***
        if (smsType == Telephony.Sms.MESSAGE_TYPE_INBOX) {
            long date_sent = cursor.getLong(MessageColumns.COLUMN_SMS_DATE_SENT);
            if (date_sent > 0) {
                details.append("\n\n");
                details.append(res.getString(R.string.sent_label));
                details.append(Util.formatTimeStampString(context, date_sent));
            }
        }

        // Received: ***
        details.append("\n\n");
        if (smsType == Telephony.Sms.MESSAGE_TYPE_DRAFT) {
            details.append(res.getString(R.string.saved_label));
        }
        else if (smsType == Telephony.Sms.MESSAGE_TYPE_INBOX) {
            details.append(res.getString(R.string.received_label));
        }
        else {
            details.append(res.getString(R.string.sent_label));
        }

        long date = cursor.getLong(MessageColumns.COLUMN_SMS_DATE);
        details.append(Util.formatTimeStampString(context, date));

        // Delivered: ***
        if (smsType == Telephony.Sms.MESSAGE_TYPE_SENT) {
            // For sent messages with delivery reports, we stick the delivery time in the
            // date_sent column (see MessageStatusReceiver).
            long dateDelivered = cursor.getLong(MessageColumns.COLUMN_SMS_DATE_SENT);
            if (dateDelivered > 0) {
                details.append("\n\n");
                details.append(res.getString(R.string.delivered_label));
                details.append(Util.formatTimeStampString(context, dateDelivered));
            }
        }

        // Error code: ***
        int errorCode = cursor.getInt(MessageColumns.COLUMN_SMS_ERROR_CODE);
        if (errorCode != 0) {
            details.append("\n\n")
                   .append(res.getString(R.string.error_code_label))
                   .append(errorCode);
        }

        return details.toString();
    }

    public static List<DeliveryReportItem> getListItems(Context context, long messageId) {
        List<DeliveryReportItem> items = new ArrayList<>();

        try (Cursor c = SqliteWrapper.query(context,
                                            context.getContentResolver(),
                                            Telephony.Sms.CONTENT_URI,
                                            SMS_REPORT_STATUS_PROJECTION,
                                            "_id = " + messageId,
                                            null,
                                            null)) {
            while (c != null && c.getCount() > 0 && c.moveToNext()) {
                // For sent messages with delivery reports, we stick the delivery time in the
                // date_sent column (see MessageStatusReceiver).
                String deliveryDateString = null;
                long deliveryDate = c.getLong(COLUMN_DATE_SENT);
                if (c.getInt(COLUMN_MESSAGE_TYPE) == Telephony.Sms.MESSAGE_TYPE_SENT && deliveryDate > 0) {
                    deliveryDateString = context.getString(R.string.delivered_label) +
                            Util.formatTimeStampString(context, deliveryDate);
                }

                int status = c.getInt(COLUMN_DELIVERY_STATUS);
                String result;
                if (status == Telephony.Sms.STATUS_NONE)
                    result = context.getString(R.string.status_none);
                else if (status >= Telephony.Sms.STATUS_FAILED)
                    result = context.getString(R.string.status_failed);
                else if (status >= Telephony.Sms.STATUS_PENDING)
                    result = context.getString(R.string.status_pending);
                else
                    result = context.getString(R.string.status_received);
                items.add(new DeliveryReportItem(
                        context.getString(R.string.recipient_label)
                                + c.getString(COLUMN_RECIPIENT),
                        context.getString(R.string.status_label) +
                                result,
                        deliveryDateString
                ));
            }
        }
        catch (Exception e) {
            Log.e(TAG, "getDeliveryReportItems", e);
        }

        if (items.isEmpty())
            items.add(new DeliveryReportItem("", context.getString(R.string.status_none), null));

        return items;
    }

    // ------------------------

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            Bundle args = getArguments();
            mThreadId = args.getLong(MessageListActivity.ARG_THREAD_ID, -1);
            mRowId = args.getLong(MessageListActivity.ARG_ROW_ID, -1);
            mHighlight = args.getString(MessageListActivity.ARG_HIGHLIGHT, null);
            mShowImmediate = args.getBoolean(MessageListActivity.ARG_SHOW_IMMEDIATE, false);
        }
        else if (savedInstanceState != null) {
            mThreadId = savedInstanceState.getLong(MessageListActivity.ARG_THREAD_ID, -1);
            mRowId = savedInstanceState.getLong(MessageListActivity.ARG_ROW_ID, -1);
            mHighlight = savedInstanceState.getString(MessageListActivity.ARG_HIGHLIGHT, null);
            mShowImmediate = savedInstanceState.getBoolean(MessageListActivity.ARG_SHOW_IMMEDIATE,
                                                           false);
        }

        mIsSmsEnabled = QKSMSApp.APP_PACKAGE.equals(Telephony.Sms.getDefaultSmsPackage(mContext));
        mConversationDetailsDialog = new ConversationDetailsDialog(mContext);
        // open conversation
        new LoadConversationTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        setHasOptionsMenu(true);

        mCIELChEvaluator = new CIELChEvaluator(ThemeManager.getThemeColor(),
                                               ThemeManager.getThemeColor());

        mBackgroundQueryHandler = new BackgroundQueryHandler(mContext);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        mRecyclerView = view.findViewById(R.id.conversation);

        mAdapter = new MessageListAdapter(mContext);
        mAdapter.setItemClickListener(this);
        mAdapter.setMultiSelectListener(this);
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            private long mLastMessageId = -1;

            @Override
            public void onChanged() {
                LinearLayoutManager manager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                int position;

                if (mRowId != -1 && mCursor != null) {
                    // Scroll to the position in the conversation for that message.
                    position = SmsHelper.getPositionForMessageId(mCursor,
                                                                 mRowId,
                                                                 mAdapter.getColumnsMap());

                    // Be sure to reset the row ID here---we only want to scroll to the message
                    // the first time the cursor is loaded after the row ID is set.
                    mRowId = -1;

                }
                else {
                    position = mAdapter.getItemCount() - 1;
                }

                if (mAdapter.getCount() > 0) {
                    MessageItem lastMessage = mAdapter.getItem(mAdapter.getCount() - 1);
                    if (mLastMessageId >= 0 && mLastMessageId != lastMessage.getMessageId()) {
                        // Scroll to bottom only if a new message was inserted in this conversation
                        if (position != -1) {
                            manager.smoothScrollToPosition(mRecyclerView, null, position);
                        }
                    }
                    mLastMessageId = lastMessage.getMessageId();
                }
            }
        });

        mRecyclerView.setAdapter(mAdapter);

        SmoothLinearLayoutManager mLayoutManager = new SmoothLinearLayoutManager(mContext);
        mLayoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mComposeView = view.findViewById(R.id.compose_view);

        mRecyclerView.setComposeView(mComposeView);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeManager.setActiveColor(ThemeManager.getThemeColor());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putLong(MessageListActivity.ARG_THREAD_ID, mThreadId);
        outState.putLong(MessageListActivity.ARG_ROW_ID, mRowId);
        outState.putString(MessageListActivity.ARG_HIGHLIGHT, mHighlight);
        outState.putBoolean(MessageListActivity.ARG_SHOW_IMMEDIATE, mShowImmediate);
    }

    private void setTitle() {
        if (mContext != null && mConversation != null) {
            mContext.setTitle(mConversation.getRecipients().formatNames());
        }
    }

    @Override
    public void onItemClick(final MessageItem messageItem) {
        if (mAdapter.isInMultiSelectMode()) {
            mAdapter.toggleSelection(messageItem.getMessageId(), messageItem);
        }
        else {
            if (messageItem != null && messageItem.isOutgoingMessage() && messageItem.isFailedMessage()) {
                showMessageResendOptions(messageItem);
            }
            else {
                showMessageDetails(messageItem);
            }
        }
    }

    @Override
    public void onItemLongClick(MessageItem messageItem) {

        QKDialog dialog = new QKDialog();
        dialog.setContext(mContext);
        dialog.setTitle(R.string.message_options);

        MsgListMenuClickListener l = new MsgListMenuClickListener(messageItem);

        // It is unclear what would make most sense for copying an MMS message
        // to the clipboard, so we currently do SMS only.
        // Message type is sms. Only allow "edit" if the message has a single recipient
        if (getRecipients().size() == 1 && (messageItem.mBoxId == Telephony.Sms.MESSAGE_TYPE_OUTBOX || messageItem.mBoxId == Telephony.Sms.MESSAGE_TYPE_FAILED)) {
            dialog.addMenuItem(R.string.menu_edit, MENU_EDIT_MESSAGE);

        }

        dialog.addMenuItem(R.string.copy_message_text, MENU_COPY_MESSAGE_TEXT);

        addCallAndContactMenuItems(dialog, messageItem);

        if (mIsSmsEnabled) {
            dialog.addMenuItem(R.string.menu_forward, MENU_FORWARD_MESSAGE);
        }

        if (messageItem.mLocked && mIsSmsEnabled) {
            dialog.addMenuItem(R.string.menu_unlock, MENU_UNLOCK_MESSAGE);
        }
        else if (mIsSmsEnabled) {
            dialog.addMenuItem(R.string.menu_lock, MENU_LOCK_MESSAGE);
        }

        dialog.addMenuItem(R.string.view_message_details, MENU_VIEW_MESSAGE_DETAILS);

        if (messageItem.mDeliveryStatus != MessageItem.DeliveryStatus.NONE || messageItem.mReadReport) {
            dialog.addMenuItem(R.string.view_delivery_report, MENU_DELIVERY_REPORT);
        }

        if (mIsSmsEnabled) {
            dialog.addMenuItem(R.string.delete_message, MENU_DELETE_MESSAGE);
        }

        dialog.buildMenu(l);
        dialog.show();
    }

    private void addCallAndContactMenuItems(QKDialog dialog, MessageItem msgItem) {
        if (TextUtils.isEmpty(msgItem.mBody)) {
            return;
        }
        SpannableString msg = new SpannableString(msgItem.mBody);
        Linkify.addLinks(msg, Linkify.ALL);

        ArrayList<String> uris = new ArrayList<>();
        for (URLSpan span : msg.getSpans(0, msg.length(), URLSpan.class)) {
            uris.add(span.getURL());
        }

        // Remove any dupes so they don't get added to the menu multiple times
        HashSet<String> collapsedUris = new HashSet<>();
        for (String uri : uris) {
            collapsedUris.add(uri.toLowerCase());
        }
        for (String uriString : collapsedUris) {
            String prefix = null;
            int sep = uriString.indexOf(":");
            if (sep >= 0) {
                prefix = uriString.substring(0, sep);
                uriString = uriString.substring(sep + 1);
            }
            Uri contactUri = null;
            boolean knownPrefix = true;
            if ("mailto".equalsIgnoreCase(prefix)) {
                contactUri = getContactUriForEmail(mContext, uriString);
            }
            else if ("tel".equalsIgnoreCase(prefix)) {
                contactUri = getContactUriForPhoneNumber(uriString);
            }
            else {
                knownPrefix = false;
            }
            if (knownPrefix && contactUri == null) {

                String addContactString = getString(R.string.menu_add_address_to_contacts,
                                                    uriString);
                dialog.addMenuItem(addContactString, MENU_ADD_ADDRESS_TO_CONTACTS);
            }
        }
    }

    private ContactList getRecipients() {
        return mConversation.getRecipients();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_call:
                makeCall();
                return true;

            case R.id.menu_details:
                mConversationDetailsDialog.showDetails(mConversation);
                return true;

            case R.id.menu_delete_conversation:
                UiUtil.showDeleteConversationsDialog(mContext, setOf(mThreadId));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void makeCall() {
        Intent openDialerIntent = new Intent(Intent.ACTION_CALL);
        openDialerIntent.setData(Uri.parse("tel:" + mConversationLegacy.getAddress(getActivity())));
        startActivity(openDialerIntent);
    }

    /**
     * Photo Selection result
     */
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
    }

    /**
     * Should only be called for failed messages. Deletes the message, placing the text from the
     * message back in the edit box to be updated and then sent.
     * <p>
     * Assumes that cursor points to the correct MessageItem.
     */
    private void editMessageItem(MessageItem msgItem) {
        String body = msgItem.mBody;

        // Delete the message and put the text back into the edit text.
        deleteMessageItem(msgItem);

        // Set the text and open the keyboard
        Util.showKeyboard(mContext, null);

        mComposeView.setText(body);
    }

    /**
     * Should only be called for failed messages. Deletes the message and re-sends it.
     */
    public void resendMessageItem(final MessageItem msgItem) {
        String body = msgItem.mBody;
        deleteMessageItem(msgItem);

        mComposeView.setText(body);
        mComposeView.sendSms();
    }

    /**
     * Deletes the message from the conversation list and the conversation history.
     */
    @SuppressLint("StaticFieldLeak")
    public void deleteMessageItem(final MessageItem msgItem) {
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... none) {
                // Determine if we're deleting the last item in the cursor.
                Boolean deletingLastItem = false;
                if (mAdapter != null && mAdapter.getCursor() != null) {
                    mCursor = mAdapter.getCursor();
                    mCursor.moveToLast();
                    long msgId = mCursor.getLong(MessageColumns.COLUMN_ID);
                    deletingLastItem = msgId == msgItem.mMsgId;
                }

                mBackgroundQueryHandler.startDelete(DELETE_MESSAGE_TOKEN,
                                                    deletingLastItem,
                                                    msgItem.mMessageUri,
                                                    msgItem.mLocked ? null : "locked=0",
                                                    null);
                return null;
            }
        }.execute();
    }

    private void initLoaderManager() {
        getLoaderManager().initLoader(LOADER_MESSAGES, null, this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mComposeView.saveDraft();

        if (mConversationLegacy != null) {
            mConversationLegacy.markRead(getView(), getActivity());
        }

        if (mConversation != null) {
            mConversation.blockMarkAsRead();
            mConversation.markAsRead();
            mComposeView.saveDraft();
        }

        ThemeManager.setActiveColor(ThemeManager.getThemeColor());
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == LOADER_MESSAGES) {
            return new CursorLoader(mContext,
                                    Uri.withAppendedPath(Message.MMS_SMS_CONTENT_PROVIDER,
                                                         String.valueOf(mThreadId)),
                                    MessageColumns.PROJECTION, null, null, "normalized_date ASC");
        }
        else {
            return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (mAdapter != null && loader.getId() == LOADER_MESSAGES) {
            // Swap the new cursor in.  (The framework will take care of closing the, old cursor once we return.)
            mAdapter.changeCursor(data);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (mAdapter != null && loader.getId() == LOADER_MESSAGES) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    public void onMultiSelectStateChanged() {

    }

    @Override
    public void onItemAdded() {

    }

    @Override
    public void onItemRemoved() {

    }

    @Override
    public void onScrollChanged(float scrollPercent) {
        ThemeManager.setActiveColor(mCIELChEvaluator.evaluate(scrollPercent, 0, 0));
    }

    private class DeleteMessageListener implements DialogInterface.OnClickListener {
        private final MessageItem mMessageItem;

        DeleteMessageListener(MessageItem messageItem) {
            mMessageItem = messageItem;
        }

        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            dialog.dismiss();
            deleteMessageItem(mMessageItem);
        }
    }

    /**
     * Context menu handlers for the message list view.
     */
    private final class MsgListMenuClickListener implements AdapterView.OnItemClickListener {
        private MessageItem mMsgItem;

        MsgListMenuClickListener(MessageItem msgItem) {
            mMsgItem = msgItem;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mMsgItem == null) {
                return;
            }

            switch ((int) id) {
                case MENU_EDIT_MESSAGE:
                    editMessageItem(mMsgItem);
                    break;

                case MENU_COPY_MESSAGE_TEXT:
                    Util.copyToClipboard(mContext, mMsgItem.mBody);
                    break;

                case MENU_VIEW_MESSAGE_DETAILS:
                    showMessageDetails(mMsgItem);
                    break;

                case MENU_DELETE_MESSAGE:
                    DeleteMessageListener l = new DeleteMessageListener(mMsgItem);
                    confirmDeleteDialog(l, mMsgItem.mLocked);
                    break;

                case MENU_DELIVERY_REPORT:
                    showDeliveryReport(mMsgItem.mMsgId);
                    break;

                case MENU_COPY_TO_SDCARD: {
                    break;
                }

                case MENU_SAVE_RINGTONE: {
                    break;
                }

                case MENU_ADD_ADDRESS_TO_CONTACTS:
                    Intent intent = new Intent(Intent.ACTION_INSERT);
                    intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
                    intent.putExtra(ContactsContract.Intents.Insert.PHONE, mMsgItem.mAddress);
                    mContext.startActivity(intent);
                    break;

                case MENU_LOCK_MESSAGE:
                    lockMessage(mContext, mMsgItem, true);
                    break;

                case MENU_UNLOCK_MESSAGE:
                    lockMessage(mContext, mMsgItem, false);
                    break;
            }
        }
    }

    private void showMessageResendOptions(final MessageItem msgItem) {
        final Cursor cursor = mAdapter.getCursorForItem(msgItem);
        if (cursor == null) {
            return;
        }

        Util.hideKeyboard(mContext, mComposeView);

        new QKDialog()
                .setContext(mContext)
                .setTitle(R.string.failed_message_title)
                .setItems(R.array.resend_menu, (parent, view, position, id) -> {
                    switch (position) {
                        case 0: // Resend message
                            resendMessageItem(msgItem);

                            break;
                        case 1: // Edit message
                            editMessageItem(msgItem);

                            break;
                        case 2: // Delete message
                            confirmDeleteDialog(new DeleteMessageListener(msgItem), false);
                            break;
                    }
                }).show();
    }

    private void showMessageDetails(MessageItem msgItem) {
        Cursor cursor = mAdapter.getCursorForItem(msgItem);
        if (cursor == null) {
            return;
        }
        String messageDetails = getMessageDetails(mContext, cursor);
        new QKDialog()
                .setContext(mContext)
                .setTitle(R.string.message_details_title)
                .setMessage(messageDetails)
                .show();
    }

    private void confirmDeleteDialog(DialogInterface.OnClickListener listener, boolean locked) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setCancelable(true);
        builder.setMessage(locked
                           ? R.string.confirm_delete_locked_message
                           : R.string.confirm_delete_message);
        builder.setPositiveButton(R.string.delete, listener);
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showDeliveryReport(long messageId) {
        List<DeliveryReportItem> deliveryReportItems =
                getListItems(mContext, messageId);

        String[] items = new String[deliveryReportItems.size() * 3];
        for (int i = 0; i < deliveryReportItems.size() * 3; i++) {
            switch (i % 3) {
                case 0:
                    items[i] = deliveryReportItems.get(i - (i / 3)).recipient;
                    break;
                case 1:
                    items[i] = deliveryReportItems.get(i - 1 - ((i - 1) / 3)).status;
                    break;
                case 2:
                    items[i] = deliveryReportItems.get(i - 2 - ((i - 2) / 3)).deliveryDate;
                    break;
            }
        }

        new QKDialog()
                .setContext(mContext)
                .setTitle(R.string.delivery_header_title)
                .setItems(items, null).setPositiveButton(true, R.string.okay, null)
                .show();
    }

    private void startMsgListQuery(int token) {
        /*if (mSendDiscreetMode) {
            return;
        }*/
        Uri conversationUri = mConversation.getUri();

        if (conversationUri == null) {
            Log.v(TAG, "##### startMsgListQuery: conversationUri is null, bail!");
            return;
        }

        long threadId = mConversation.getThreadId();
        Log.v(TAG, "startMsgListQuery for " + conversationUri + ", threadId=" + threadId +
                " token: " + token + " mConversation: " + mConversation);

        // Cancel any pending queries
        mBackgroundQueryHandler.cancelOperation(token);
        try {
            // Kick off the new query
            mBackgroundQueryHandler.startQuery(
                    token,
                    threadId /* cookie */,
                    conversationUri,
                    MessageColumns.PROJECTION,
                    null, null, null);
        }
        catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(mContext, e);
        }
    }

    @SuppressLint("HandlerLeak")
    private final class BackgroundQueryHandler extends Conversation.ConversationQueryHandler {

        private final QKActivity qkActivity;

        BackgroundQueryHandler(QKActivity context) {
            super(context);
            this.qkActivity = context;
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor0) {
            try (Cursor cursor = cursor0) {
                switch (token) {
                    case MainActivity.HAVE_LOCKED_MESSAGES_TOKEN:
                        if (qkActivity.isFinishing()) {
                            Log.w(TAG, "ComposeMessageActivity is finished, do nothing ");
                            return;
                        }
                        @SuppressWarnings("unchecked")
                        ArrayList<Long> threadIds = (ArrayList<Long>) cookie;
                        MainActivity.confirmDeleteThreadDialog(
                                new MainActivity.DeleteThreadListener(
                                ), threadIds,
                                cursor != null && cursor.getCount() > 0, qkActivity);
                        break;

                    case MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN:
                        // check consistency between the query result and 'mConversation'
                        long tid = (Long) cookie;

                        Log.v(TAG,
                              "##### onQueryComplete (after delete): msg history result for threadId " + tid);
                        if (tid > 0 && cursor.getCount() == 0) {
                            // We just deleted the last message and the thread will get deleted
                            // by a trigger in the database. Clear the threadId so next time we
                            // need the threadId a new thread will get created.
                            Log.v(TAG,
                                  "##### MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN clearing thread id: " + tid);
                            Conversation conv = Conversation.get(qkActivity, tid, false);
                            if (conv != null) {
                                conv.clearThreadId();
                                conv.setDraftState(false);
                            }
                            qkActivity.onBackPressed();
                        }
                }
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onDeleteComplete(token, cookie, result);
            switch (token) {
                case MainActivity.DELETE_CONVERSATION_TOKEN:
                case DELETE_MESSAGE_TOKEN:
                    // Update the notification for new messages since they may be deleted.
                    NotificationMgr.update(qkActivity);

                    // TODO Update the notification for failed messages since they may be deleted.
                    //updateSendFailedNotification();
                    break;
            }
            // If we're deleting the whole conversation, throw away our current working message and bail.
            if (token == MainActivity.DELETE_CONVERSATION_TOKEN) {
                ContactList recipients = mConversation.getRecipients();

                // Remove any recipients referenced by this single thread from the It's possible for two or more
                // threads to reference the same contact. That's ok if we remove it. We'll recreate that contact
                // when we init all Conversations below.
                if (recipients != null) {
                    for (Contact contact : recipients) {
                        contact.removeFromCache();
                    }
                }

                // Make sure the conversation cache reflects the threads in the DB.
                Conversation.init(mContext);

                // Go back to the conversation list
                mContext.onBackPressed();
            }
            else if (token == DELETE_MESSAGE_TOKEN) {
                // Check to see if we just deleted the last message
                startMsgListQuery(MESSAGE_LIST_QUERY_AFTER_DELETE_TOKEN);
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class LoadConversationTask extends AsyncTask<Void, Void, Void> {

        LoadConversationTask() {
            Log.d(TAG, "LoadConversationTask");
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "Loading conversation");
            mConversation = Conversation.get(mContext, mThreadId, true);
            mConversationLegacy = new ConversationLegacy(mThreadId);

            mConversationLegacy.markRead(getView(), getContext());
            mConversation.blockMarkAsRead();
            mConversation.markAsRead();

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(TAG, "Conversation loaded");

            mComposeView.onOpenConversation(mConversation, mConversationLegacy);
            setTitle();

            mAdapter.setIsGroupConversation(mConversation.getRecipients().size() > 1);

            if (isAdded()) {
                initLoaderManager();
            }
        }
    }

    private static final class DeliveryReportItem {
        String recipient;
        String status;
        String deliveryDate;

        DeliveryReportItem(String recipient, String status, String deliveryDate) {
            this.recipient = recipient;
            this.status = status;
            this.deliveryDate = deliveryDate;
        }

    }

}
