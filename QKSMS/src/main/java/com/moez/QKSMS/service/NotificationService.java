package com.moez.QKSMS.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.LifecycleHandler;
import com.moez.QKSMS.data.Message;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.ui.QKReplyActivity;

public class NotificationService extends Service {

    public static final String EXTRA_POPUP = "popup";
    public static final String EXTRA_URI = "uri";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        final Context context = this;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        Uri uri = Uri.parse(intent.getStringExtra(EXTRA_URI));

        // Try to get the message's ID, in case the given Uri is bad.
        long messageId = -1;
        Cursor cursor = context.getContentResolver().query(uri, new String[]{SmsHelper.COLUMN_ID},
                                                           null, null, null);
        if (cursor.moveToFirst()) {
            messageId = cursor.getLong(cursor.getColumnIndexOrThrow(SmsHelper.COLUMN_ID));
        }
        cursor.close();

        // Make sure we found a message before showing QuickReply and using PushBullet.
        if (messageId != -1) {

            Message message = new Message(context, messageId);

            // Only show QuickReply if we're outside of the app, and they have popups and QuickReply enabled.
            if (!LifecycleHandler.isApplicationVisible() &&
                    intent.getBooleanExtra(EXTRA_POPUP, false) && prefs.getBoolean(
                    QKPreference.K_QUICKREPLY,
                    Build.VERSION.SDK_INT < 24)) {

                Intent popupIntent = new Intent(context, QKReplyActivity.class);
                popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                popupIntent.putExtra(QKReplyActivity.EXTRA_THREAD_ID, message.getThreadId());
                startActivity(popupIntent);
            }
        }

        stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
