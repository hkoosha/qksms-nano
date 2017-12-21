package com.moez.QKSMS.service;

import android.app.IntentService;
import android.content.Intent;

import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.sms.SmsHelper;

public class MarkSeenService extends IntentService {

    public MarkSeenService() {
        super("MarkSeenService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SmsHelper.markSmsSeen(this);
        NotificationMgr.update(this);
    }
}
