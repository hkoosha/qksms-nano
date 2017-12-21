package com.moez.QKSMS.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.moez.QKSMS.Blocker;
import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Message;
import com.moez.QKSMS.service.NotificationService;
import com.moez.QKSMS.sms.SmsHelper;


public class MessagingReceiver extends BroadcastReceiver {

    private static final String TAG = "MessagingReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive");
        abortBroadcast();

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (intent.getExtras() == null)
            return;

        final Object[] pdus = (Object[]) intent.getExtras().get("pdus");
        if (pdus == null)
            return;

        SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < messages.length; i++) {
            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
        }

        SmsMessage sms = messages[0];
        String mBody;
        if (messages.length == 1 || sms.isReplace()) {
            mBody = sms.getDisplayMessageBody();
        }
        else {
            StringBuilder bodyText = new StringBuilder();
            for (SmsMessage message : messages) {
                bodyText.append(message.getMessageBody());
            }
            mBody = bodyText.toString();
        }

        String mAddress = sms.getDisplayOriginatingAddress();
        long mDate = sms.getTimestampMillis();

        Uri mUri = SmsHelper.addMessageToInbox(context, mAddress, mBody, mDate);

        Message message = new Message(context, mUri);

        // The user has set messages from this address to be blocked, but we at the time there weren't any
        // messages from them already in the database, so we couldn't block any thread URI. Now that we have one,
        // we can block it, so that the conversation list adapter knows to ignore this thread in the main list
        final boolean isBlocked =
                Blocker.isBlocked(mPrefs, message, mAddress, mBody) ||
                        Blocker.getBlockedConversationIds(mPrefs).contains(message.getThreadId());

        if (isBlocked) {
            Blocker.unblockFutureConversation(mPrefs, mAddress);
            message.markSeen();
            Blocker.blockConversation(mPrefs, message.getThreadId());
            Blocker.FutureBlockedConversationObservable
                    .getInstance().futureBlockedConversationReceived();
        }
        else {
            Intent messageHandlerIntent = new Intent(context, NotificationService.class);
            messageHandlerIntent.putExtra(NotificationService.EXTRA_POPUP, true);
            messageHandlerIntent.putExtra(NotificationService.EXTRA_URI, mUri.toString());
            context.startService(messageHandlerIntent);
            UnreadBadgeService.update(context);
            NotificationMgr.create(context);
            if (mPrefs.getBoolean(QKPreference.K_WAKE, false))
                Util.wake(context, 60 * 1000, "MessagingReceiver");
        }
    }

}
