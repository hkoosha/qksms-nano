package com.moez.QKSMS;

import android.content.Context;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDexApplication;
import android.util.Log;

import com.moez.QKSMS.common.DraftCache;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.ui.ThemeManager;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

public class QKSMSApp extends MultiDexApplication {

    public static final String APP_PACKAGE = "com.moez.QKSMS";

    private static QKSMSApp sQKSMSApp = null;

    public static RefWatcher getRefWatcher(Context context) {
        QKSMSApp application = (QKSMSApp) context.getApplicationContext();
        return application.refWatcher;
    }

    public static QKSMSApp getApplication() {
        return sQKSMSApp;
    }

    private RefWatcher refWatcher;

    @Override
    public void onCreate() {
        super.onCreate();

        // Figure out the country *before* loading contacts and formatting numbers
        Util.countryIso();

        if (Log.isLoggable("Mms:strictmode", Log.DEBUG)) {
            // Log tag for enabling/disabling StrictMode violation log. This will dump a stack
            // in the log that shows the StrictMode violator.
            // To enable: adb shell setprop log.tag.Mms:strictmode DEBUG
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
        }

        QKSMSApp.sQKSMSApp = this;

        PreferenceManager.setDefaultValues(this, R.xml.settings, false);

        refWatcher = LeakCanary.install(this);

        registerActivityLifecycleCallbacks(new LifecycleHandler());

        ThemeManager.init(this);
        Contact.init(this);
        DraftCache.init(this);
        Conversation.init(this);
        NotificationMgr.init(this);

        // For Sms: retry to send sms in outbox and queued box
        //sendBroadcast(new Intent(SmsReceiverService.ACTION_SEND_INACTIVE_MESSAGE, null, this,
        // MessagingReciever.class));
    }

}
