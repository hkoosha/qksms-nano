package com.moez.QKSMS.ui.messagelist;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.Telephony;

public class MessageColumns {

    static final int COLUMN_MSG_TYPE = 0;
    static final int COLUMN_ID = 1;
    static final int COLUMN_SMS_ADDRESS = 3;
    static final int COLUMN_SMS_BODY = 4;
    static final int COLUMN_SMS_DATE = 5;
    static final int COLUMN_SMS_DATE_SENT = 6;
    static final int COLUMN_SMS_TYPE = 8;
    static final int COLUMN_SMS_STATUS = 9;
    static final int COLUMN_SMS_LOCKED = 10;
    static final int COLUMN_SMS_ERROR_CODE = 11;

    static final int CACHE_SIZE = 50;

    @SuppressLint("InlinedApi")
    public static final String[] PROJECTION = new String[]{
            Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN,
            BaseColumns._ID,
            Telephony.Sms.Conversations.THREAD_ID,
            // For SMS
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS,
            Telephony.Sms.LOCKED,
            Telephony.Sms.ERROR_CODE,
    };

    public static class ColumnsMap {

        public int mColumnMsgType;
        public int mColumnMsgId;
        int mColumnSmsAddress;
        public int mColumnSmsBody;
        int mColumnSmsDate;
        int mColumnSmsDateSent;
        public int mColumnSmsType;
        int mColumnSmsStatus;
        int mColumnSmsLocked;

        public ColumnsMap() {
            mColumnMsgType = COLUMN_MSG_TYPE;
            mColumnMsgId = COLUMN_ID;
            mColumnSmsAddress = COLUMN_SMS_ADDRESS;
            mColumnSmsBody = COLUMN_SMS_BODY;
            mColumnSmsDate = COLUMN_SMS_DATE;
            mColumnSmsDateSent = COLUMN_SMS_DATE_SENT;
            mColumnSmsType = COLUMN_SMS_TYPE;
            mColumnSmsStatus = COLUMN_SMS_STATUS;
            mColumnSmsLocked = COLUMN_SMS_LOCKED;
        }

        @SuppressLint("InlinedApi")
        public ColumnsMap(Cursor cursor) {
            // Ignore all 'not found' exceptions since the custom columns
            // may be just a subset of the default columns.
            try {
                mColumnMsgType = cursor.getColumnIndexOrThrow(Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN);
            }
            catch (IllegalArgumentException ignored) {
            }

            try {
                mColumnMsgId = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            }
            catch (IllegalArgumentException ignored) {
            }

            try {
                mColumnSmsAddress = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
            }
            catch (IllegalArgumentException ignored) {
            }

            try {
                mColumnSmsBody = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
            }
            catch (IllegalArgumentException ignored) {
            }

            try {
                mColumnSmsDate = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE);
            }
            catch (IllegalArgumentException ignored) {
            }

            try {
                mColumnSmsDateSent = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT);
            }
            catch (IllegalArgumentException ignored) {
            }

            try {
                mColumnSmsType = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE);
            }
            catch (IllegalArgumentException ignored) {
            }

            try {
                mColumnSmsStatus = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS);
            }
            catch (IllegalArgumentException ignored) {
            }

            try {
                mColumnSmsLocked = cursor.getColumnIndexOrThrow(Telephony.Sms.LOCKED);
            }
            catch (IllegalArgumentException ignored) {
            }
        }
    }
}
