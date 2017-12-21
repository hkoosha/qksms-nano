package com.moez.QKSMS.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.DraftCache;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.receiver.UnreadBadgeService;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.ui.messagelist.MessageColumns;
import com.moez.QKSMS.ui.messagelist.MessageItem;

/**
 * Use this class (rather than Conversation) for marking conversations as read, and managing drafts.
 */
public class ConversationLegacy {

    private static final String TAG = "ConversationLegacy";

    private static final Uri CONVERSATIONS_CONTENT_PROVIDER = Uri.parse(
            "content://mms-sms/conversations?simple=true");
    private static final Uri ADDRESSES_CONTENT_PROVIDER = Uri.parse(
            "content://mms-sms/canonical-addresses");

    private static final int COLUMN_ADDRESSES_ADDRESS = 1;

    private long threadId;
    private String name;
    private String address;
    private long recipient;
    private String draft;
    private int type;

    public ConversationLegacy(long threadId) {
        this.threadId = threadId;
    }

    public long getThreadId() {
        return threadId;
    }

    public Uri getUri() {
        return Uri.parse("content://mms-sms/conversations/" + getThreadId());
    }


    public String getName(Context context, boolean findIfNull) {
        if (name == null || name.trim().isEmpty()) {
            if (findIfNull)
                name = Util.getContactName(context, getAddress(context));
            else
                return getAddress(context);
        }

        return name;
    }

    public String getAddress(Context context) {
        if (address != null) {
            return address;
        }

        if (getType(context) == 0) { //Single person
            try (Cursor cursor = context.getContentResolver()
                                        .query(ADDRESSES_CONTENT_PROVIDER,
                                               null,
                                               "_id=" + getRecipient(context),
                                               null,
                                               null)) {
                cursor.moveToFirst();
                address = cursor.getString(COLUMN_ADDRESSES_ADDRESS);

                address = PhoneNumberUtils.stripSeparators(address);

                if (address == null || address.isEmpty()) {
                    try (Cursor cursor1 = context.getContentResolver()
                                                 .query(SmsHelper.RECEIVED_MESSAGE_CONTENT_PROVIDER,
                                                        new String[]{SmsHelper.COLUMN_ID},
                                                        "thread_id=" + threadId,
                                                        null,
                                                        SmsHelper.sortDateDesc)) {
                        cursor1.moveToFirst();
                        long id = cursor1.getLong(cursor1.getColumnIndexOrThrow(SmsHelper.COLUMN_ID));
                        address = new Message(context, id).getAddress();
                    }
                }
            }
            catch (Exception e) {
                Log.e(TAG, "getAddress", e);
            }
        }

        return address;
    }

    private long getRecipient(Context context) {
        if (recipient == 0) {
            try (Cursor cursor = context.getContentResolver()
                                        .query(CONVERSATIONS_CONTENT_PROVIDER,
                                               null,
                                               "_id=" + threadId,
                                               null,
                                               null)) {
                cursor.moveToFirst();
                recipient = cursor.getInt(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_RECIPIENT));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return recipient;
    }

    public boolean hasDraft() {
        return DraftCache.getInstance().hasDraft(threadId);
    }

    public String getDraft(Context context) {

        if (draft == null) {
            try (Cursor cursor = context.getContentResolver()
                                        .query(SmsHelper.DRAFTS_CONTENT_PROVIDER,
                                               null,
                                               SmsHelper.COLUMN_THREAD_ID + "=" + threadId,
                                               null,
                                               null)) {
                cursor.moveToFirst();
                draft = cursor.getString(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_BODY));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return draft;
    }

    public void clearDrafts(Context context) {
        if (hasDraft()) {
            try (Cursor cursor = context.getContentResolver()
                                        .query(SmsHelper.DRAFTS_CONTENT_PROVIDER,
                                               null,
                                               SmsHelper.COLUMN_THREAD_ID + "=" + threadId,
                                               null,
                                               null)) {
                DraftCache.getInstance().setDraftState(threadId, false);
                if (cursor.moveToFirst()) {
                    do {
                        context.getContentResolver()
                               .delete(Uri.parse("content://sms/" + cursor.getLong(cursor.getColumnIndexOrThrow(
                                       SmsHelper.COLUMN_ID))), null, null);
                    } while (cursor.moveToNext());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
            }
        }
    }

    public void saveDraft(Context context, final String draft) {

        clearDrafts(context);

        if (draft.length() > 0) {
            try {
                DraftCache.getInstance().setDraftState(threadId, true);
                ConversationLegacy.this.draft = draft;

                ContentResolver contentResolver = context.getContentResolver();
                ContentValues cv = new ContentValues();

                cv.put("address", getAddress(context));
                cv.put("body", draft);

                contentResolver.insert(SmsHelper.DRAFTS_CONTENT_PROVIDER, cv);
            }
            finally {
            }
        }
        else {
            ConversationLegacy.this.draft = null;
        }

        Toast.makeText(context, R.string.toast_draft, Toast.LENGTH_SHORT).show();
    }

    private int getType(Context context) {
        if (type == 0) {
            try (Cursor cursor = context.getContentResolver()
                                        .query(CONVERSATIONS_CONTENT_PROVIDER,
                                               null,
                                               "_id=" + threadId,
                                               null,
                                               null)) {
                cursor.moveToFirst();
                type = cursor.getInt(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_TYPE));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return type;
    }

    private long[] getUnreadIds(Context context) {
        long[] ids = new long[0];

        try (Cursor cursor = context.getContentResolver()
                                    .query(getUri(),
                                           new String[]{SmsHelper.COLUMN_ID},
                                           SmsHelper.UNREAD_SELECTION,
                                           null,
                                           null)) {
            ids = new long[cursor.getCount()];
            cursor.moveToFirst();

            for (int i = 0; i < ids.length; i++) {
                ids[i] = cursor.getLong(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_ID));
                cursor.moveToNext();
                Log.d(TAG, "Unread ID: " + ids[i]);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return ids;
    }

    public void markRead(View view, Context context) {
        new Thread(() -> {
            long[] ids = getUnreadIds(context);
            if (ids.length > 0) {
                Util.showDefaultSMSDialog(view, R.string.not_default_mark_read);
                ContentValues cv = new ContentValues();
                cv.put("read", true);
                cv.put("seen", true);

                for (long id : ids) {
                    context.getContentResolver()
                           .update(getUri(), cv, SmsHelper.COLUMN_ID + "=" + id, null);
                }

                NotificationMgr.update(context);
                UnreadBadgeService.update(context);
            }

        }).start();
    }

    public void markUnread(View view, Context context) {
        Util.showDefaultSMSDialog(view, R.string.not_default_mark_unread);

        try (Cursor cursor = context.getContentResolver()
                                    .query(getUri(),
                                           MessageColumns.PROJECTION,
                                           null,
                                           null,
                                           SmsHelper.sortDateDesc)) {
            cursor.moveToFirst();

            MessageColumns.ColumnsMap columnsMap = new MessageColumns.ColumnsMap(cursor);
            MessageItem message = new MessageItem(context,
                                                  cursor.getString(columnsMap.mColumnMsgType),
                                                  cursor,
                                                  columnsMap,
                                                  null,
                                                  true);

            if (message.isMe()) {
                while (cursor.moveToNext()) {
                    MessageItem message2 = new MessageItem(context,
                                                           cursor.getString(columnsMap.mColumnMsgType),
                                                           cursor,
                                                           columnsMap,
                                                           null,
                                                           true);
                    if (!message2.isMe()) {
                        message = message2;
                        break;
                    }
                }
            }

            ContentValues cv = new ContentValues();
            cv.put("read", false);
            cv.put("seen", false);

            context.getContentResolver().update(message.mMessageUri, cv, null, null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        NotificationMgr.create(context);
    }

/*    public void delete() { //TODO do this using AsyncQueryHandler
        new DefaultSmsHelper(context, R.string.not_default_delete).showIfNotDefault(null);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                SqliteWrapper.delete(context, context.getContentResolver(), getUri(), null, null);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                Toast.makeText(context, R.string.toast_conversation_deleted, Toast.LENGTH_SHORT)
                     .show();
            }
        }.execute((Void[]) null);
    }*/
}
