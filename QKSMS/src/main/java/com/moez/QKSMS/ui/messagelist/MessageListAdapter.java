package com.moez.QKSMS.ui.messagelist;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.provider.Telephony.TextBasedSmsColumns;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.LinkifyUtils;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.RecyclerCursorAdapter;
import com.moez.QKSMS.ui.ThemeManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageListAdapter extends RecyclerCursorAdapter<MessageListViewHolder, MessageItem> {

    private static final int INCOMING_ITEM = 0;
    private static final int OUTGOING_ITEM = 1;

    private MessageItemCache mMessageItemCache;
    private MessageColumns.ColumnsMap mColumnsMap;

    private final SharedPreferences mPrefs;

    private Pattern mSearchHighlighter = null;
    private boolean mIsGroupConversation = false;

    public MessageListAdapter(QKActivity context) {
        super(context);
        mPrefs = mContext.getPrefs();
    }

    protected MessageItem getItem(int position) {
        mCursor.moveToPosition(position);

        String type = mCursor.getString(mColumnsMap.mColumnMsgType);
        long msgId = mCursor.getLong(mColumnsMap.mColumnMsgId);

        return mMessageItemCache.get(type, msgId, mCursor);
    }

    Cursor getCursorForItem(MessageItem item) {
        if (Util.isValid(mCursor) && mCursor.moveToFirst()) {
            do {
                long id = mCursor.getLong(mColumnsMap.mColumnMsgId);
                String type = mCursor.getString(mColumnsMap.mColumnMsgType);
                if (id == item.mMsgId && type != null && type.equals(item.mType))
                    return mCursor;
            } while (mCursor.moveToNext());
        }
        return null;
    }

    MessageColumns.ColumnsMap getColumnsMap() {
        return mColumnsMap;
    }

    void setIsGroupConversation(boolean b) {
        mIsGroupConversation = b;
    }

    @Override
    public MessageListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        int resource;
        boolean sent;

        if (viewType == INCOMING_ITEM) {
            resource = R.layout.list_item_message_in;
            sent = false;
        }
        else {
            resource = R.layout.list_item_message_out;
            sent = true;
        }

        View view = inflater.inflate(resource, parent, false);
        return setupViewHolder(view, sent);
    }

    private MessageListViewHolder setupViewHolder(View view, boolean sent) {
        MessageListViewHolder holder = new MessageListViewHolder(mContext, view);

        if (sent) {
            // set up colors
            holder.mBodyTextView.setOnColorBackground(ThemeManager.getSentBubbleColor() != ThemeManager
                    .getNeutralBubbleColor());
            holder.mDateView.setOnColorBackground(false);
            holder.mDeliveredIndicator.setColorFilter(ThemeManager.getTextOnBackgroundSecondary(),
                                                      PorterDuff.Mode.SRC_ATOP);
            holder.mLockedIndicator.setColorFilter(ThemeManager.getTextOnBackgroundSecondary(),
                                                   PorterDuff.Mode.SRC_ATOP);
        }
        else {
            // set up colors
            holder.mBodyTextView.setOnColorBackground(ThemeManager.getReceivedBubbleColor() != ThemeManager
                    .getNeutralBubbleColor());
            holder.mDateView.setOnColorBackground(false);
            holder.mDeliveredIndicator.setColorFilter(ThemeManager.getTextOnBackgroundSecondary(),
                                                      PorterDuff.Mode.SRC_ATOP);
            holder.mLockedIndicator.setColorFilter(ThemeManager.getTextOnBackgroundSecondary(),
                                                   PorterDuff.Mode.SRC_ATOP);
        }

        holder.mRoot.setBackgroundDrawable(ThemeManager.getRippleBackground());

        return holder;
    }

    @Override
    public void onBindViewHolder(MessageListViewHolder holder, int position) {
        MessageItem messageItem = getItem(position);

        holder.mData = messageItem;
        holder.mContext = mContext;
        holder.mClickListener = mItemClickListener;
        holder.mRoot.setOnClickListener(holder);
        holder.mRoot.setOnLongClickListener(holder);

        bindGrouping(holder, messageItem);
        bindTimestamp(holder, messageItem);

        bindBody(holder, messageItem);
        bindIndicators(holder, messageItem);

        if (messageItem.isMe()) {
            holder.mBodyTextView.getBackground()
                                .setColorFilter(ThemeManager.getSentBubbleColor(),
                                                PorterDuff.Mode.SRC_ATOP);
        }
        else {
            holder.mBodyTextView.getBackground()
                                .setColorFilter(ThemeManager.getReceivedBubbleColor(),
                                                PorterDuff.Mode.SRC_ATOP);
        }
    }

    private boolean shouldShowTimestamp(MessageItem messageItem, int position) {
        if (position == mCursor.getCount() - 1) {
            return true;
        }

        MessageItem messageItem2 = getItem(position + 1);

        if (mPrefs.getBoolean(QKPreference.K_FORCE_TIMESTAMPS, false)) {
            return true;
        }
        else if (messageItem.mDeliveryStatus != MessageItem.DeliveryStatus.NONE) {
            return true;
        }
        else if (messageItem.isFailedMessage()) {
            return true;
        }
        else if (messageItem.isSending()) {
            return true;
        }
        else if (messagesFromDifferentPeople(messageItem, messageItem2)) {
            return true;
        }
        else {
            int MAX_DURATION = Integer.parseInt(mPrefs.getString(QKPreference.K_SHOW_NEW_TIMESTAMP_DELAY,
                                                                 "5")) * 60 * 1000;
            return (messageItem2.mDate - messageItem.mDate >= MAX_DURATION);
        }
    }

    private boolean messagesFromDifferentPeople(MessageItem a, MessageItem b) {
        return (a.mAddress != null && b.mAddress != null &&
                !a.mAddress.equals(b.mAddress) &&
                !a.isOutgoingMessage(

                ) && !b.isOutgoingMessage());
    }

    private int getBubbleBackgroundResource(boolean isMine) {
        if (isMine)
            return ThemeManager.getSentBubbleAltRes();
        else
            return ThemeManager.getReceivedBubbleAltRes();
    }

    private void bindGrouping(MessageListViewHolder holder, MessageItem messageItem) {
        int position = mCursor.getPosition();

        boolean showTimestamp = shouldShowTimestamp(messageItem, position);

        holder.mDateView.setVisibility(showTimestamp ? View.VISIBLE : View.GONE);
        holder.mBodyTextView.setBackgroundResource(getBubbleBackgroundResource(
                messageItem.isMe()));
    }

    private void bindBody(MessageListViewHolder holder, MessageItem messageItem) {
        holder.mBodyTextView.setAutoLinkMask(0);
        SpannableStringBuilder buf = new SpannableStringBuilder();

        String body = messageItem.mBody;

        if (!TextUtils.isEmpty(body)) {
            buf.append(body);
        }

        if (messageItem.mHighlight != null) {
            Matcher m = messageItem.mHighlight.matcher(buf.toString());
            while (m.find()) {
                buf.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), 0);
            }
        }

        if (!TextUtils.isEmpty(buf)) {
            holder.mBodyTextView.setText(buf);
            LinkifyUtils.addLinks(holder.mBodyTextView);
        }
        holder.mBodyTextView.setVisibility(TextUtils.isEmpty(buf) ? View.GONE : View.VISIBLE);
        holder.mBodyTextView.setOnClickListener(v -> holder.mRoot.callOnClick());
        holder.mBodyTextView.setOnLongClickListener(v -> holder.mRoot.performLongClick());
    }

    private void bindTimestamp(MessageListViewHolder holder, MessageItem messageItem) {
        String timestamp;


        if (messageItem.isSending()) {
            timestamp = mContext.getString(R.string.status_sending);
        }
        else if (messageItem.mTimestamp != null && !messageItem.mTimestamp.equals("")) {
            timestamp = messageItem.mTimestamp;
        }
        else if (messageItem.isOutgoingMessage() && messageItem.isFailedMessage()) {
            timestamp = mContext.getResources().getString(R.string.status_failed);
        }
        else {
            timestamp = "";
        }

        if (!mIsGroupConversation || messageItem.isMe() || TextUtils.isEmpty(messageItem.mContact)) {
            holder.mDateView.setText(timestamp);
        }
        else {
            holder.mDateView.setText(mContext.getString(R.string.message_timestamp_format,
                                                        timestamp,
                                                        messageItem.mContact));
        }

    }

    private void bindIndicators(MessageListViewHolder holder, MessageItem messageItem) {
        // Locked icon
        if (messageItem.mLocked) {
            holder.mLockedIndicator.setVisibility(View.VISIBLE);
        }
        else {
            holder.mLockedIndicator.setVisibility(View.GONE);
        }

        // Delivery icon - we can show a failed icon for both sms and mms, but for an actual
        // delivery, we only show the icon for sms. We don't have the information here in mms to
        // know whether the message has been delivered. For mms, msgItem.mDeliveryStatus set
        // to MessageItem.DeliveryStatus.RECEIVED simply means the setting requesting a
        // delivery report was turned on when the message was sent. Yes, it's confusing!
        if ((messageItem.isOutgoingMessage() && messageItem.isFailedMessage()) ||
                messageItem.mDeliveryStatus == MessageItem.DeliveryStatus.FAILED) {
            holder.mDeliveredIndicator.setVisibility(View.VISIBLE);
        }
        else if (messageItem.mDeliveryStatus == MessageItem.DeliveryStatus.RECEIVED) {
            holder.mDeliveredIndicator.setVisibility(View.VISIBLE);
        }
        else {
            holder.mDeliveredIndicator.setVisibility(View.GONE);
        }

        // Message details icon - this icon is shown both for sms and mms messages. For mms,
        // we show the icon if the read report or delivery report setting was set when the
        // message was sent. Showing the icon tells the user there's more information
        // by selecting the "View report" menu.
        if (messageItem.mDeliveryStatus == MessageItem.DeliveryStatus.INFO || messageItem.mReadReport) {
            holder.mDetailsIndicator.setVisibility(View.VISIBLE);
        }
        else {
            holder.mDetailsIndicator.setVisibility(View.GONE);
        }
    }

    @Override
    public void changeCursor(Cursor cursor) {
        if (Util.isValid(cursor)) {
            mColumnsMap = new MessageColumns.ColumnsMap(cursor);
            mMessageItemCache = new MessageItemCache(mContext,
                                                     mColumnsMap,
                                                     mSearchHighlighter,
                                                     MessageColumns.CACHE_SIZE);
        }

        super.changeCursor(cursor);
    }

    @Override
    public int getItemViewType(int position) {
        // This method shouldn't be called if our cursor is null, since the framework should know
        // that there aren't any items to look at in that case
        MessageItem item = getItem(position);
        int boxId = item.getBoxId();

        if (boxId == TextBasedSmsColumns.MESSAGE_TYPE_INBOX || boxId == TextBasedSmsColumns.MESSAGE_TYPE_ALL) {
            return INCOMING_ITEM;
        }
        else {
            return OUTGOING_ITEM;
        }
    }

}
