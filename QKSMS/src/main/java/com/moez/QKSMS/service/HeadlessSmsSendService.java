package com.moez.QKSMS.service;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.moez.QKSMS.sms.Message;
import com.moez.QKSMS.sms.Transaction;
import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.sms.SmsHelper;

import static com.moez.QKSMS.data.Conversation.getRecipients;

public class HeadlessSmsSendService extends IntentService {
    private static final String TAG = "HeadlessSmsSendService";

    public HeadlessSmsSendService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(action)) {
            return;
        }

        Bundle extras = intent.getExtras();
        if (extras != null) {
            String body = extras.getString(Intent.EXTRA_TEXT);
            Uri intentUri = intent.getData();
            String recipients = getRecipients(intentUri);

            if (!TextUtils.isEmpty(recipients) && !TextUtils.isEmpty(body)) {
                String[] destinations = TextUtils.split(recipients, ";");

                Transaction sendTransaction = new Transaction(this);

                Message message = new Message(body, destinations);

                sendTransaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
                NotificationMgr.update(this);
            }
        }
    }
}
