package com.moez.QKSMS.sms;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.R;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.data.Message;
import com.moez.QKSMS.ui.messagelist.MessageColumns;
import com.moez.QKSMS.ui.messagelist.MessageItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SmsHelper {

    public static final Uri SMS_CONTENT_PROVIDER = Uri.parse("content://sms/");
    public static final Uri MMS_SMS_CONTENT_PROVIDER = Uri.parse("content://mms-sms/conversations/");
    public static final Uri DRAFTS_CONTENT_PROVIDER = Uri.parse("content://sms/draft");
    public static final Uri RECEIVED_MESSAGE_CONTENT_PROVIDER = Uri.parse("content://sms/inbox");
    public static final Uri CONVERSATIONS_CONTENT_PROVIDER = Uri.parse(
            "content://mms-sms/conversations?simple=true");

    public static final String sortDateDesc = "date DESC";
    public static final String sortDateAsc = "date ASC";

    private static final byte UNREAD = 0;

    // Columns for SMS content providers
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_THREAD_ID = "thread_id";
    public static final String COLUMN_ADDRESS = "address";
    public static final String COLUMN_RECIPIENT = "recipient_ids";
    private static final String COLUMN_READ = "read";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_BODY = "body";
    private static final String COLUMN_SEEN = "seen";

    public static final String UNREAD_SELECTION = COLUMN_READ + " = " + UNREAD;
    private static final String UNSEEN_SELECTION = COLUMN_SEEN + " = " + UNREAD;
    public static final String FAILED_SELECTION = COLUMN_TYPE + " = " + Message.FAILED;

    private static final String TAG = "SMSHelper";

    private SmsHelper() {

    }

    /**
     * Message type: sent messages.
     */
    private static final int MESSAGE_TYPE_SENT = 2;

    /**
     * Message type: outbox.
     */
    private static final int MESSAGE_TYPE_OUTBOX = 4;

    /**
     * Message type: failed outgoing message.
     */
    private static final int MESSAGE_TYPE_FAILED = 5;

    /**
     * Message type: queued to send later.
     */
    private static final int MESSAGE_TYPE_QUEUED = 6;


    public static void markSmsSeen(Context context) {
        try (Cursor cursor = context.getContentResolver()
                                    .query(RECEIVED_MESSAGE_CONTENT_PROVIDER,
                                           new String[]{SmsHelper.COLUMN_ID},
                                           SmsHelper.UNSEEN_SELECTION + " AND " + SmsHelper.UNREAD_SELECTION,
                                           null,
                                           null)) {
            if (cursor == null) {
                Log.i(TAG, "No unseen messages");
                return;
            }

            MessageColumns.ColumnsMap map = new MessageColumns.ColumnsMap(cursor);

            if (cursor.moveToFirst()) {
                ContentValues cv = new ContentValues();
                cv.put("seen", true);

                do {
                    context.getContentResolver()
                           .update(Uri.parse("content://sms/" + cursor.getLong(map.mColumnMsgId)),
                                   cv,
                                   null,
                                   null);
                } while (cursor.moveToNext());
            }
        }
    }


    static boolean deliveryReport(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(QKPreference.K_DELIVERY_REPORTS, false);
    }

    /**
     * Add incoming SMS to inbox
     *
     * @param address Address of sender
     * @param body    Body of incoming SMS message
     * @param time    Time that incoming SMS message was sent at
     */
    public static Uri addMessageToInbox(Context context, String address, String body, long time) {

        ContentResolver contentResolver = context.getContentResolver();
        ContentValues cv = new ContentValues();

        cv.put("address", address);
        cv.put("body", body);
        cv.put("date_sent", time);

        return contentResolver.insert(RECEIVED_MESSAGE_CONTENT_PROVIDER, cv);
    }

    /**
     * Returns true iff the folder (message type) identifies an
     * outgoing message.
     */
    public static boolean isOutgoingFolder(int messageType) {
        return (messageType == MESSAGE_TYPE_FAILED)
                || (messageType == MESSAGE_TYPE_OUTBOX)
                || (messageType == MESSAGE_TYPE_SENT)
                || (messageType == MESSAGE_TYPE_QUEUED);
    }

    public static int getUnseenSMSCount(Context context, long threadId) {
        int count = 0;
        String selection = UNSEEN_SELECTION
                + " AND "
                + UNREAD_SELECTION
                + (threadId == 0 ? "" : " AND " + COLUMN_THREAD_ID + " = " + threadId);

        try (Cursor cursor = context.getContentResolver()
                                    .query(RECEIVED_MESSAGE_CONTENT_PROVIDER,
                                           new String[]{COLUMN_ID},
                                           selection,
                                           null,
                                           null)) {
            if (cursor != null) {
                cursor.moveToFirst();
                count = cursor.getCount();
            }
        }
        catch (Exception e) {
            Log.e(TAG, "getUnseenSMSCount", e);
        }

        return count;
    }

    /**
     * Returns a string containing the last 10 messages for a given conversation
     * This is used to be displayed on the notification page on a wearable, which
     * only accepts a single String to be displayed
     */
    public static String getHistoryForWearable(Context context, String name, long threadId) {
        final String me = context.getString(R.string.me);
        StringBuilder builder = new StringBuilder();
        MessageColumns.ColumnsMap map = new MessageColumns.ColumnsMap();

        try (Cursor cursor = context.getContentResolver().query(
                Uri.withAppendedPath(Message.MMS_SMS_CONTENT_PROVIDER, "" + threadId),
                MessageColumns.PROJECTION,
                null,
                null,
                "normalized_date DESC LIMIT 10")) {

            if (cursor != null) {
                cursor.moveToLast();
                do {
                    if (cursor.getString(map.mColumnMsgType).equals("sms")) {
                        int boxId = cursor.getInt(map.mColumnSmsType);
                        boolean in = boxId == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX ||
                                boxId == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_ALL;

                        builder.append(in ? name : me)
                               .append("\n")
                               .append(cursor.getString(map.mColumnSmsBody))
                               .append("\n\n");
                    }
                } while (cursor.moveToPrevious());
            }
        }
        catch (Exception e) {
            Log.e(TAG, "getHistoryForWearable", e);
        }

        return builder.toString();
    }

    /**
     * List of messages grouped by thread id, used for showing notifications
     */
    public static HashMap<Long, ArrayList<MessageItem>> getUnreadUnseenConversations(Context context) {
        @SuppressLint("UseSparseArrays")
        HashMap<Long, ArrayList<MessageItem>> result = new HashMap<>();

        String selection = SmsHelper.UNSEEN_SELECTION + " AND " + SmsHelper.UNREAD_SELECTION;

        // Create a cursor for the conversation list
        try (Cursor conversationCursor = context.getContentResolver().query(
                SmsHelper.CONVERSATIONS_CONTENT_PROVIDER, Conversation.ALL_THREADS_PROJECTION,
                SmsHelper.UNREAD_SELECTION, null, SmsHelper.sortDateAsc)) {

            if (conversationCursor == null || !conversationCursor.moveToFirst()) {
                return result;
            }

            do {
                ArrayList<MessageItem> messages = new ArrayList<>();
                long threadId = conversationCursor.getLong(Conversation.ID);
                Uri threadUri = Uri.withAppendedPath(Message.MMS_SMS_CONTENT_PROVIDER,
                                                     Long.toString(threadId));
                Cursor messageCursor = context.getContentResolver()
                                              .query(threadUri,
                                                     MessageColumns.PROJECTION,
                                                     selection,
                                                     null,
                                                     SmsHelper.sortDateAsc);

                if (messageCursor != null && messageCursor.moveToFirst()) {
                    do {
                        MessageColumns.ColumnsMap columnsMap = new MessageColumns.ColumnsMap(
                                messageCursor);
                        MessageItem message;
                        message = new MessageItem(context,
                                                  messageCursor.getString(columnsMap.mColumnMsgType),
                                                  messageCursor,
                                                  columnsMap,
                                                  null,
                                                  true);
                        messages.add(message);
                    } while (messageCursor.moveToNext());
                    messageCursor.close();
                    result.put(threadId, messages);
                }

            } while (conversationCursor.moveToNext());
        }

        return result;
    }

    /**
     * @return A list of unread messages to be deleted by QKReply
     */
    public static ArrayList<Message> getUnreadMessagesLegacy(Context context, Uri threadUri) {
        ArrayList<Message> result = new ArrayList<>();
        if (threadUri != null) {
            try (Cursor cursor = context.getContentResolver()
                                        .query(threadUri,
                                               MessageColumns.PROJECTION,
                                               UNREAD_SELECTION,
                                               null,
                                               SmsHelper.sortDateAsc)) {
                if (cursor != null && cursor.moveToFirst()) {
                    MessageColumns.ColumnsMap columnsMap
                            = new MessageColumns.ColumnsMap(cursor);
                    do {
                        try {
                            Message message =
                                    new Message(context, cursor.getLong(columnsMap.mColumnMsgId));
                            result.add(message);
                        }
                        catch (Exception e) {
                            Log.e(TAG, "getUnreadMessagesLegacy", e);
                        }
                    } while (cursor.moveToNext());
                }
            }
        }

        return result;
    }

    public static String getUnreadMessageText(Context context, Uri threadUri) {
        StringBuilder builder = new StringBuilder();

        ArrayList<Message> messages = SmsHelper.getUnreadMessagesLegacy(context, threadUri);
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            builder.append(message.getBody());
            if (i < messages.size() - 1) {
                builder.append("\n\n");
            }
        }

        return builder.toString();
    }

    public static int getUnreadMessageCount(Context context) {
        int result = 0;

        // Create a cursor for the conversation list
        try (Cursor conversationCursor = context.getContentResolver().query(
                SmsHelper.CONVERSATIONS_CONTENT_PROVIDER, Conversation.ALL_THREADS_PROJECTION,
                SmsHelper.UNREAD_SELECTION, null, SmsHelper.sortDateAsc)) {

            if (conversationCursor != null && conversationCursor.moveToFirst()) {
                do {
                    Uri threadUri = Uri.withAppendedPath(Message.MMS_SMS_CONTENT_PROVIDER,
                                                         conversationCursor.getString(Conversation.ID));
                    try (Cursor messageCursor = context.getContentResolver()
                                                       .query(threadUri,
                                                              MessageColumns.PROJECTION,
                                                              SmsHelper.UNREAD_SELECTION,
                                                              null,
                                                              SmsHelper.sortDateDesc)) {
                        if (messageCursor != null)
                            result += messageCursor.getCount();
                    }
                } while (conversationCursor.moveToNext());
            }
        }

        return result;
    }

    public static List<Message> getFailedMessages(Context context) {
        List<Message> messages = new ArrayList<>();

        try (Cursor cursor = context.getContentResolver()
                                    .query(SMS_CONTENT_PROVIDER,
                                           new String[]{COLUMN_ID},
                                           FAILED_SELECTION,
                                           null,
                                           sortDateDesc)) {
            if (cursor != null) {
                cursor.moveToFirst();
                for (int i = 0; i < cursor.getCount(); i++) {
                    messages.add(new Message(context,
                                             cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))));
                    cursor.moveToNext();
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "getFailedMessages", e);
        }

        return messages;
    }

    public static void deleteFailedMessages(Context context, long threadId) {
        Log.d(TAG, "Deleting failed messages");
        List<Message> messages = new ArrayList<>();

        try (Cursor cursor = context.getContentResolver()
                                    .query(SMS_CONTENT_PROVIDER,
                                           new String[]{COLUMN_ID},
                                           FAILED_SELECTION,
                                           null,
                                           sortDateDesc)) {
            if (cursor != null) {
                cursor.moveToFirst();
                for (int i = 0; i < cursor.getCount(); i++) {
                    messages.add(new Message(context,
                                             cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))));
                    cursor.moveToNext();
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "deleteFailedMessages", e);
        }

        for (Message m : messages)
            if (m.getThreadId() == threadId) {
                Log.d(TAG, "Deleting failed message to " + m.getName() + "\n Body: " + m.getBody());
                m.delete();
            }
    }

    /**
     * Is the specified address an email address?
     *
     * @param address the input address to test
     * @return true if address is an email address; false otherwise.
     */
    public static boolean isEmailAddress(String address) {
        if (TextUtils.isEmpty(address))
            return false;

        Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);
        String s = match.matches() ? match.group(2) : address;
        return Patterns.EMAIL_ADDRESS.matcher(s).matches();
    }

    /**
     * Regex pattern for names and email addresses.
     * <ul>
     * <li><em>mailbox</em> = {@code name-addr}</li>
     * <li><em>name-addr</em> = {@code [display-name] angle-addr}</li>
     * <li><em>angle-addr</em> = {@code [CFWS] "<" addr-spec ">" [CFWS]}</li>
     * </ul>
     */
    private static final Pattern NAME_ADDR_EMAIL_PATTERN =
            Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");

    /**
     * Returns the position of the message in the cursor.
     */
    public static int getPositionForMessageId(Cursor cursor, long messageId, MessageColumns.ColumnsMap map) {

        // Modified binary search on the cursor to find the position of the message in the cursor.
        // It's modified because, although the SMS and MMS are generally ordered in terms of their
        // ID, they have different IDs. So, we might have a list of IDs like:
        //
        // [ 4444, 4447, 4449, 4448, 312, 315, 4451 ]
        //
        // where the 44xx IDs are for SMS messages, and the 31x IDs are for MMS messages. The
        // solution is to do a linear scan if we reach a point in the list where the ID doesn't
        // match what we're looking for.

        // Lower and upper bounds for doing the search
        int min = 0;
        int max = cursor.getCount() - 1;

        while (min <= max) {
            int mid = min / 2 + max / 2 + (min & max & 1);
            cursor.moveToPosition(mid);
            long candidateId = cursor.getLong(map.mColumnMsgId);

            if (messageId < candidateId) {
                max = mid - 1;
            }
            else if (messageId > candidateId) {
                min = mid + 1;
            }
            else {
                return mid;
            }
        }

        // This is the case where we've minimized our bounds until
        // they're the same, and we haven't found anything yet---
        // this means that the item doesn't exist, so return -1.
        return -1;
    }

}
