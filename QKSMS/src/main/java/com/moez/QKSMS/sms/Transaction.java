/*
 * Copyright (C) 2015 QK Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.moez.QKSMS.sms;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import com.moez.QKSMS.common.Util;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Class to process transaction requests for sending
 */
public class Transaction {

    private static final String TAG = "Transaction";

    private final boolean mDeliveryReport;
    private Context context;

    private boolean saveMessage = true;

    private String SMS_SENT = ".SMS_SENT";
    private String SMS_DELIVERED = ".SMS_DELIVERED";

    static String NOTIFY_SMS_FAILURE = ".NOTIFY_SMS_FAILURE";
    static final String NOTIFY_OF_DELIVERY = "com.moez.QKSMS.send_message.NOTIFY_DELIVERY";

    public static final long NO_THREAD_ID = 0;

    /**
     * Sets context and settings
     *
     * @param context  is the context of the activity or service
     */
    public Transaction(Context context) {
        this.context = context;
        SMS_SENT = context.getPackageName() + SMS_SENT;
        SMS_DELIVERED = context.getPackageName() + SMS_DELIVERED;
        if (NOTIFY_SMS_FAILURE.equals(".NOTIFY_SMS_FAILURE"))
            NOTIFY_SMS_FAILURE = context.getPackageName() + NOTIFY_SMS_FAILURE;
        mDeliveryReport = SmsHelper.deliveryReport(context);
    }

    /**
     * Called to send a new message depending on settings and provided Message object
     * If you want to send message as mms, call this from the UI thread
     *
     * @param message  is the message that you want to send
     * @param threadId is the thread id of who to send the message to (can also be set to Transaction.NO_THREAD_ID)
     */
    public void sendNewMessage(Message message, long threadId) {
        this.saveMessage = message.getSave();
        Log.v(TAG, "sending sms");
        sendSmsMessage(message.getText(),
                       message.getAddresses(),
                       threadId,
                       message.getDelay());

    }

    private void sendSmsMessage(final String text,
                                final String[] addresses,
                                long threadId,
                                final int delay) {
        Log.v(TAG, "message text: " + text);
        Uri messageUri;
        int messageId = 0;
        if (!saveMessage)
            return;

        Log.v(TAG, "saving message");

        ContentResolver cr = context.getContentResolver();

        // save the message for each of the addresses
        for (String address : addresses) {
            Calendar cal = Calendar.getInstance();
            ContentValues values = new ContentValues();
            values.put("address", address);
            values.put("body", text);
            values.put("date", cal.getTimeInMillis() + "");
            values.put("read", 1);
            values.put("type", 4);

            // attempt to create correct thread id if one is not supplied
            if (threadId == NO_THREAD_ID || addresses.length > 1)
                threadId = Util.getOrCreateThreadId(context, address);

            Log.v(TAG, "saving message with thread id: " + threadId);

            values.put("thread_id", threadId);
            messageUri = cr.insert(Uri.parse("content://sms/"), values);

            Log.v(TAG, "inserted to uri: " + messageUri);

            try (Cursor query = cr.query(messageUri,
                                         new String[]{"_id"},
                                         null,
                                         null,
                                         null)) {
                if (query != null && query.moveToFirst())
                    messageId = query.getInt(0);

                Log.v(TAG, "message id: " + messageId);

                // set up sent and delivered pending intents to be used with message request
                Intent sendPIIntent = new Intent(SMS_SENT).putExtra("message_uri",
                                                                    messageUri.toString());
                PendingIntent sentPI = PendingIntent.getBroadcast(context,
                                                                  messageId,
                                                                  sendPIIntent,
                                                                  PendingIntent.FLAG_UPDATE_CURRENT);

                Intent deliveredPIIntent = new Intent(SMS_DELIVERED).putExtra("message_uri",
                                                                              messageUri.toString());
                PendingIntent deliveredPI = PendingIntent.getBroadcast(context,
                                                                       messageId,
                                                                       deliveredPIIntent,
                                                                       PendingIntent.FLAG_UPDATE_CURRENT);

                ArrayList<PendingIntent> sPI = new ArrayList<>();
                ArrayList<PendingIntent> dPI = new ArrayList<>();

                Log.v(TAG, "found sms manager");

                SmsManager smsManager = SmsManager.getDefault();
                ArrayList<String> parts = smsManager.divideMessage(text);

                for (int j = 0; j < parts.size(); j++) {
                    sPI.add(saveMessage ? sentPI : null);
                    dPI.add(mDeliveryReport && saveMessage ? deliveredPI : null);
                }

                try {
                    Log.v(TAG, "sent message");
                    sendDelayedSms(smsManager,
                                   address,
                                   parts,
                                   sPI,
                                   dPI,
                                   delay,
                                   messageUri);
                }
                catch (Exception e) {
                    // whoops...
                    Log.v(TAG, "error sending message");
                    Log.e(TAG, "exception thrown", e);

                    try {
                        ((Activity) context).getWindow()
                                            .getDecorView()
                                            .findViewById(android.R.id.content)
                                            .post(() -> Toast.makeText(context,
                                                                       "Message could not be sent",
                                                                       Toast.LENGTH_LONG)
                                                             .show());
                    }
                    catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void sendDelayedSms(final SmsManager smsManager,
                                final String address,
                                final ArrayList<String> parts,
                                final ArrayList<PendingIntent> sPI,
                                final ArrayList<PendingIntent> dPI,
                                final int delay,
                                final Uri messageUri) {
        new Thread(() -> {
            try {
                Thread.sleep(delay);
            }
            catch (Exception ignored) {
            }

            ContentResolver cr = context.getContentResolver();
            boolean exists = true;
            try (Cursor query = cr.query(messageUri, new String[]{"_id"}, null, null, null)) {
                exists = query != null && query.moveToFirst();
            }
            catch (Exception ignored) {
            }

            if (exists) {
                Log.v(TAG, "message sent after delay");
                try {
                    smsManager.sendMultipartTextMessage(address, null, parts, sPI, dPI);
                }
                catch (Exception e) {
                    Log.e(TAG, "exception thrown", e);
                }
            }
            else {
                Log.v(TAG, "message not sent after delay, no longer exists");
            }
        }).start();
    }

}
