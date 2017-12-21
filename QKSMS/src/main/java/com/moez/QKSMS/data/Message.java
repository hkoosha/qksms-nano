package com.moez.QKSMS.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.sms.SmsHelper;

public class Message {

    private static final String TAG = "qksmsMessage";

    public static final int FAILED = 5;

    private static final Uri SMS_CONTENT_PROVIDER = Uri.parse("content://sms/");
    public static final Uri MMS_SMS_CONTENT_PROVIDER = Uri.parse("content://mms-sms/conversations/");

    private Context context;
    private long id;
    private long threadId;
    private String body;
    private String address;
    private String name;

    public Message(Context context, long id) {
        this.context = context;
        this.id = id;
    }

    public Message(Context context, Uri uri) {
        this.context = context;
        Cursor cursor = context.getContentResolver()
                               .query(uri, new String[]{SmsHelper.COLUMN_ID}, null, null, null);
        cursor.moveToFirst();
        id = cursor.getLong(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_ID));
        cursor.close();
    }

    public long getId() {
        return id;
    }

    public long getThreadId() {
        if (threadId == 0) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver()
                                .query(SMS_CONTENT_PROVIDER,
                                       new String[]{SmsHelper.COLUMN_THREAD_ID},
                                       "_id=" + id,
                                       null,
                                       null);
                cursor.moveToFirst();
                threadId = cursor.getLong(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_THREAD_ID));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return threadId;
    }

    public String getAddress() {
        Cursor cursor = null;
        if (address == null) {
            try {
                cursor = context.getContentResolver()
                                .query(SMS_CONTENT_PROVIDER,
                                       new String[]{SmsHelper.COLUMN_ADDRESS},
                                       "_id=" + id,
                                       null,
                                       null);
                cursor.moveToFirst();
                address = cursor.getString(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_ADDRESS));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return address;
    }

    public String getName() {
        if (name == null)
            name = Util.getContactName(context, getAddress());
        return name;
    }

    public String getBody() {
        if (body == null) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver()
                                .query(SMS_CONTENT_PROVIDER,
                                       new String[]{SmsHelper.COLUMN_BODY},
                                       "_id=" + id,
                                       null,
                                       null);
                cursor.moveToFirst();
                body = cursor.getString(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_BODY));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        return body;
    }

    public void markSeen() {
        ContentValues cv = new ContentValues();
        cv.put("seen", true);

        context.getContentResolver()
               .update(Uri.parse("content://sms/" + getId()), cv, null, null);
    }

    public void markRead() {
        ContentValues cv = new ContentValues();
        cv.put("read", true);
        cv.put("seen", true);

        context.getContentResolver()
               .update(Uri.parse("content://sms/" + getId()), cv, null, null);
    }

    public void delete() {
        if (!Util.isDefaultSmsApp(context))
            return;

        try {
            context.getContentResolver()
                   .delete(Uri.parse("content://sms/" + getId()), null, null);
        }
        catch (Exception e) {
            Log.e(TAG, "err delete", e);
        }
    }

}
