package com.moez.QKSMS.receiver;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.moez.QKSMS.R;

public class DeliveredReceiver extends com.moez.QKSMS.sms.DeliveredReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        switch (getResultCode()) {
            case Activity.RESULT_OK:
                break;

            case Activity.RESULT_CANCELED:
                Toast.makeText(context, R.string.message_not_delivered, Toast.LENGTH_LONG).show();
                break;
        }
    }
}
