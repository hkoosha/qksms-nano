package com.moez.QKSMS.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import com.moez.QKSMS.data.ConversationLegacy;

public class MarkReadService extends IntentService {

    public MarkReadService() {
        super("MarkReadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null)
            return;
        long threadId = extras.getLong("thread_id");
        ConversationLegacy conversation = new ConversationLegacy(threadId);
        conversation.markRead(null, getBaseContext());
    }
}
