package com.moez.QKSMS.receiver;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.moez.QKSMS.sms.SmsHelper;

import me.leolin.shortcutbadger.ShortcutBadger;

public class UnreadBadgeService extends IntentService {

    public static final String UNREAD_COUNT_UPDATED = "com.moez.QKSMS.intent.action.UNREAD_COUNT_UPDATED";

    public UnreadBadgeService() {
        super("UnreadBadgeService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!UNREAD_COUNT_UPDATED.equals(intent.getAction())) {
            return;
        }

        ShortcutBadger.applyCount(getApplicationContext(),
                                  SmsHelper.getUnreadMessageCount(this));
    }

    public static void update(Context context) {
        Intent intent = new Intent(context, UnreadBadgeService.class);
        intent.setAction(UNREAD_COUNT_UPDATED);
        context.startService(intent);
    }

}
