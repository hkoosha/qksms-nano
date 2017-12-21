package com.moez.QKSMS.receiver;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;
import android.view.Gravity;

import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.R;
import com.moez.QKSMS.service.MarkReadService;
import com.moez.QKSMS.sms.Message;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.sms.Transaction;
import com.moez.QKSMS.ui.ThemeManager;

import java.util.Collections;
import java.util.List;

import static android.support.v4.app.NotificationCompat.BigTextStyle;
import static android.support.v4.app.NotificationCompat.WearableExtender;

public class RemoteMessagingReceiver extends BroadcastReceiver {

    public static final String ACTION_REPLY = "com.moez.QKSMS.receiver.WearableIntentReceiver.REPLY";

    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_THREAD_ID = "thread_id";
    public static final String EXTRA_VOICE_REPLY = "voice_reply";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        Bundle bundle = intent.getExtras();
        if (remoteInput != null && bundle != null) {
            if (ACTION_REPLY.equals(intent.getAction())) {

                CharSequence charSequence = remoteInput.getCharSequence(EXTRA_VOICE_REPLY);
                Message message = new Message(
                        charSequence == null ? "null" : charSequence.toString(),
                        new String[]{bundle.getString(EXTRA_ADDRESS)}
                );

                Transaction sendTransaction = new Transaction(context);
                sendTransaction.sendNewMessage(message, bundle.getLong(EXTRA_THREAD_ID));

                Intent i = new Intent(context, MarkReadService.class);
                i.putExtra(EXTRA_THREAD_ID, bundle.getLong(EXTRA_THREAD_ID));
                context.startService(i);
            }
        }
    }

    public static WearableExtender getConversationExtender(Context context, String name, String address, long threadId, String channelId) {
        WearableExtender wearableExtender = new WearableExtender();
        wearableExtender.setGravity(Gravity.BOTTOM);
        wearableExtender.setStartScrollBottom(true);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        Bitmap background = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888);

        // We can't use ThemeManager here because it might not be initialized
        background.eraseColor(Integer.parseInt(prefs.getString(
                QKPreference.K_THEME, "" + ThemeManager.DEFAULT_COLOR)));

        wearableExtender.setBackground(background);


        BigTextStyle chatPageStyle = new BigTextStyle();
        chatPageStyle
                .setBigContentTitle(name)
                .setSummaryText(address)
                .bigText(SmsHelper.getHistoryForWearable(context, name, threadId));

        Notification chatPage = new NotificationCompat.Builder(context, channelId)
                .setStyle(chatPageStyle)
                .extend(new WearableExtender()
                                .setStartScrollBottom(true))
                .build();

        wearableExtender.addPage(chatPage);

        wearableExtender.addAction(getReplyAction(context, address, threadId));

        Intent readIntent = new Intent(NotificationMgr.ACTION_MARK_READ);
        readIntent.putExtra(EXTRA_THREAD_ID, threadId);
        PendingIntent readPI = PendingIntent.getBroadcast(context,
                                                          2,
                                                          readIntent,
                                                          PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action readAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_accept,
                context.getString(R.string.mark_read), readPI)
                .build();

        wearableExtender.addAction(readAction);


        return wearableExtender;
    }

    public static NotificationCompat.Action getReplyAction(Context context, String address, long threadId) {

        Intent replyIntent = new Intent(ACTION_REPLY).setClass(context,
                                                               RemoteMessagingReceiver.class);
        replyIntent.putExtra(EXTRA_ADDRESS, address);
        replyIntent.putExtra(EXTRA_THREAD_ID, threadId);

        List<String> responses = Collections.emptyList();

        PendingIntent replyPI = PendingIntent.getBroadcast(context,
                                                           0,
                                                           replyIntent,
                                                           PendingIntent.FLAG_UPDATE_CURRENT);
        RemoteInput remoteInput = new RemoteInput.Builder(EXTRA_VOICE_REPLY)
                .setLabel(context.getString(R.string.reply))
                .setChoices(responses.toArray(new String[responses.size()]))
                .build();

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_reply,
                context.getString(R.string.reply), replyPI)
                .addRemoteInput(remoteInput)
                .build();
    }
}
