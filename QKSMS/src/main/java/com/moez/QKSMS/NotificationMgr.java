package com.moez.QKSMS;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;

import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Message;
import com.moez.QKSMS.receiver.RemoteMessagingReceiver;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.ui.MainActivity;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.messagelist.MessageItem;
import com.moez.QKSMS.ui.messagelist.MessageListActivity;
import com.moez.QKSMS.ui.QKReplyActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static com.moez.QKSMS.common.Util.close;

public class NotificationMgr {

    private static final String TAG = "NotificationMgr";

    private static final int NOTIFICATION_ID_FAILED = 4295;

    public static final String ACTION_MARK_READ = "com.moez.QKSMS.MARK_READ";
    private static final String ACTION_MARK_SEEN = "com.moez.QKSMS.MARK_SEEN";

    private static final String DEFAULT_RINGTONE = "content://settings/system/notification_sound";

    private static final String PREV_NOTIFICATIONS = "key_prev_notifications";

    private static final long[] VIBRATION = {0, 200, 200, 200};
    private static final long[] VIBRATION_SILENT = {0, 0};

    private static final String CHANNEL_ID = "com.moez.QKSMS.notificationDefaultChannel";
    private static final String CHANNEL_NAME = "com.moez.QKSMS.notificationDefaultChannelName";

    private static Handler sHandler;

    private static SharedPreferences sPrefs;
    private static Resources sRes;

    static {
        // Start a new thread for showing notifications on with minimum priority
        HandlerThread sThread = new HandlerThread("NotificationMgr");
        sThread.start();
        sThread.setPriority(HandlerThread.MIN_PRIORITY);

        Looper sLooper = sThread.getLooper();
        sHandler = new Handler(sLooper);
    }

    public static void init(final Context context) {

        // Initialize the static shared prefs and resources.
        sPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        sRes = context.getResources();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel androidChannel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            androidChannel.enableLights(true);
            androidChannel.enableVibration(true);
            androidChannel.setLightColor(Color.DKGRAY);
            androidChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            NotificationManager m = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (m != null) {
                m.createNotificationChannel(androidChannel);
            }
        }

    }

    /**
     * Creates a new notification, called when a new message is received. This
     * notification will have sound and vibration
     */
    public static void create(final Context context) {
        sHandler.post(() -> {
            HashMap<Long, ArrayList<MessageItem>> conversations =
                    SmsHelper.getUnreadUnseenConversations(context);

            // Let's find the list of current notifications. If we're showing multiple notifications, now we know
            // which ones don't need to be touched
            Set<Long> oldThreads = new HashSet<>();
            for (String s : sPrefs.getStringSet(PREV_NOTIFICATIONS,
                                                new HashSet<>())) {
                long l = Long.parseLong(s);
                if (!oldThreads.contains(l)) {
                    oldThreads.add(l);
                }
            }

            dismissOld(context, conversations);

            // If there are no messages, don't try to create a notification
            if (conversations.size() == 0) {
                return;
            }
            long k = (long) conversations.keySet().toArray()[0];
            ArrayList<MessageItem> lastConversation = conversations.get(k);
            MessageItem lastMessage = lastConversation.get(0);

            // If this message is in the foreground, mark it as read
            Message message = new Message(context, lastMessage.mMsgId);
            if (MessageListActivity.isInForeground && message.getThreadId() == MessageListActivity
                    .getThreadId()) {
                message.markRead();
                return;
            }

            // Otherwise, reset the state and show the notification.
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setPriority(getNotificationPriority(context))
                            .setSound(Uri.parse(QKPreference.K_DEFAULT_NOTIFICATION_TONE))
                            .setVibrate(VIBRATION_SILENT)
                            .setAutoCancel(true);

            if (sPrefs.getBoolean(QKPreference.K_NOTIFICATION_VIBRATE, true)) {
                builder.setVibrate(VIBRATION);
            }

            builder.setLights(getLedColor(context), 1000, 1000);

            Integer privateNotifications =
                    Integer.parseInt(sPrefs.getString(QKPreference.K_PRIVATE_NOTIFICATION, "0"));


            if (sPrefs.getBoolean(QKPreference.K_NOTIFICATION_TICKER, true)) {
                switch (privateNotifications) {
                    case 0:
                        builder.setTicker(String.format("%s: %s",
                                                        lastMessage.mContact,
                                                        lastMessage.mBody));
                        break;
                    case 1:
                        builder.setTicker(String.format("%s: %s",
                                                        lastMessage.mContact,
                                                        sRes.getString(R.string.new_message)));
                        break;
                    case 2:
                        builder.setTicker(String.format("%s: %s",
                                                        "QKSMS",
                                                        sRes.getString(R.string.new_message)));
                        break;
                }
            }

            if (sPrefs.getBoolean(QKPreference.K_WAKE, false)) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    PowerManager.WakeLock wl = pm.newWakeLock(
                            PowerManager.FULL_WAKE_LOCK, "FlashActivity");
                    wl.acquire(60 * 1000L);
                    wl.release();
                }
            }

            if (conversations.size() == 1 && lastConversation.size() == 1) {
                singleMessage(context,
                              lastConversation,
                              k,
                              builder,
                              privateNotifications);
            }
            else if (conversations.size() == 1) {
                singleSender(context,
                             lastConversation,
                             k,
                             builder,
                             privateNotifications);
            }
            else {
                multipleSenders(context, conversations, oldThreads, builder);
            }
        });
    }

    /**
     * Updates the notifications silently. This is called when a conversation is marked read or something like that,
     * where we need to update the notifications without alerting the user
     */
    public static void update(final Context context) {
        sHandler.post(() -> {
            HashMap<Long, ArrayList<MessageItem>> conversations = SmsHelper.getUnreadUnseenConversations(
                    context);

            // Let's find the list of current notifications. If we're showing multiple notifications, now we know
            // which ones don't need to be touched
            Set<Long> oldThreads = new HashSet<>();
            for (String s : sPrefs.getStringSet(PREV_NOTIFICATIONS, new HashSet<>())) {
                long l = Long.parseLong(s);
                if (!oldThreads.contains(l)) {
                    oldThreads.add(l);
                }
            }

            dismissOld(context, conversations);

            // If there are no messages, don't try to create a notification
            // If this app is not default message app, don't try to create a notification either
            if (conversations.size() == 0 || !Util.isDefaultSmsApp(context)) {
                return;
            }
            long k = (long) conversations.keySet().toArray()[0];
            ArrayList<MessageItem> lastConversation = conversations.get(
                    k);
            MessageItem lastMessage = lastConversation.get(0);

            // If the message is visible (i.e. it is currently showing in the Main Activity),
            // don't show a notification; just mark it as read and return.
            Message message = new Message(context, lastMessage.mMsgId);
            if (MessageListActivity.isInForeground && message.getThreadId() == MessageListActivity
                    .getThreadId()) {
                message.markRead();
                return;
            }

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notification)
                            // SMS messages are high priority
                            .setPriority(getNotificationPriority(context))
                            // Silent here because this is just an update, not a new
                            // notification
                            .setSound(null)
                            .setVibrate(VIBRATION_SILENT)
                            .setAutoCancel(true);

            builder.setLights(getLedColor(context), 1000, 1000);

            Integer privateNotifications =
                    Integer.parseInt(sPrefs.getString(QKPreference.K_PRIVATE_NOTIFICATION, "0"));

            if (conversations.size() == 1 && lastConversation.size() == 1) {
                singleMessage(context,
                              lastConversation,
                              k,
                              builder,
                              privateNotifications);
            }
            else if (conversations.size() == 1) {
                singleSender(context,
                             lastConversation,
                             k,
                             builder,
                             privateNotifications);
            }
            else {
                multipleSenders(context, conversations, oldThreads, builder);
            }
        });
    }

    /**
     * Creates a notification to tell the user about failed messages. This is currently pretty shitty and needs to be
     * improved, by adding functionality such as the ability to delete all of the failed messages
     */
    public static void notifyFailed(final Context context) {
        sHandler.post(() -> {
            Cursor failedCursor = context.getContentResolver().query(
                    SmsHelper.SMS_CONTENT_PROVIDER,
                    new String[]{SmsHelper.COLUMN_THREAD_ID},
                    SmsHelper.FAILED_SELECTION,
                    null, null
            );

            // Dismiss the notification if the failed cursor doesn't have any items in it.
            if (failedCursor == null || !failedCursor.moveToFirst() || failedCursor.getCount() <= 0) {
                dismiss(context, NOTIFICATION_ID_FAILED);
                close(failedCursor);
                return;
            }

            String title;
            PendingIntent PI;
            if (failedCursor.getCount() == 1) {
                title = sRes.getString(R.string.failed_message);
                Intent intent = new Intent(context, MainActivity.class);
                intent.putExtra(MessageListActivity.ARG_THREAD_ID, failedCursor.getLong(0));
                PI = PendingIntent.getActivity(context,
                                               0,
                                               intent,
                                               PendingIntent.FLAG_UPDATE_CURRENT);
            }
            else {
                title = failedCursor.getCount() + " " + sRes.getString(R.string.failed_messages);
                Intent intent = new Intent(context, MainActivity.class);
                PI = PendingIntent.getActivity(context,
                                               0,
                                               intent,
                                               PendingIntent.FLAG_UPDATE_CURRENT);
            }


            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

            for (Message message : SmsHelper.getFailedMessages(context)) {
                switch (Integer.parseInt(sPrefs.getString(QKPreference.K_PRIVATE_NOTIFICATION,
                                                          "0"))) {
                    case 0:
                        inboxStyle.addLine(Html.fromHtml("<strong>" + message.getName() + "</strong> " + message
                                .getBody()));
                        break;
                    case 1:
                        inboxStyle.addLine(Html.fromHtml("<strong>" + message.getName() + "</strong> " + sRes
                                .getString(R.string.new_message)));
                        break;
                    case 2:
                        inboxStyle.addLine(Html.fromHtml("<strong>" + "QKSMS" + "</strong> " + sRes
                                .getString(R.string.new_message)));
                        break;
                }
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_failed)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(Uri.parse(DEFAULT_RINGTONE))
                    .setVibrate(VIBRATION_SILENT)
                    .setAutoCancel(true)
                    .setContentTitle(title)
                    .setStyle(inboxStyle)
                    .setContentText(sRes.getString(R.string.failed_messages_summary))
                    .setContentIntent(PI)
                    .setNumber(failedCursor.getCount())
                    .setColor(ThemeManager.getThemeColor());

            if (sPrefs.getBoolean(QKPreference.K_NOTIFICATION_VIBRATE, false)) {
                builder.setVibrate(VIBRATION);
            }

            builder.setLights(getLedColor(context), 1000, 1000);

            if (sPrefs.getBoolean(QKPreference.K_NOTIFICATION_TICKER, false)) {
                builder.setTicker(title);
            }

            notify(context, NOTIFICATION_ID_FAILED, builder.build());
            close(failedCursor);
        });
    }


    /**
     * Notifies the user of the given notification.
     */
    private static void notify(Context context, int id, android.app.Notification notification) {
        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null)
            mgr.notify(id, notification);
    }

    /**
     * Cancels the notification for the given ID.
     */
    private static void dismiss(Context context, int id) {
        // Cancel the notification for this ID.
        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null)
            mgr.cancel(id);
    }

    /**
     * Dismisses all old notifications. The purpose of this is to clear notifications that don't need to show up,
     * without making the remaining ones dissapear and pop up again like how NotificationMangager.cancelAll and then
     * rebuilding them would do
     * <p>
     * This should stay private, because it assumes that the preferences have already been initialized
     */
    private static void dismissOld(Context context, HashMap<Long, ArrayList<MessageItem>> newMessages) {
        // Let's find the list of current notifications
        Set<Long> oldThreads = new HashSet<>();
        for (String s : sPrefs.getStringSet(PREV_NOTIFICATIONS, new HashSet<>())) {
            long l = Long.parseLong(s);
            if (!oldThreads.contains(l)) {
                oldThreads.add(l);
            }
        }

        // Now we need a comparable list of thread ids for the new messages
        Set<Long> newThreads = newMessages.keySet();

        Log.d(TAG, "Old threads: " + Arrays.toString(oldThreads.toArray()));
        Log.d(TAG, "New threads: " + Arrays.toString(newThreads.toArray()));

        // For all of the notifications that exist and are not to be still shown, let's dismiss them
        for (long threadId : oldThreads) {
            if (!newThreads.contains(threadId)) {
                dismiss(context, (int) threadId);
            }
        }

        // Now let's convert the new list into a set of strings so we can save them to prefs
        Set<String> newThreadStrings = new HashSet<>();
        for (long threadId : newThreads) {
            newThreadStrings.add(Long.toString(threadId));
        }

        sPrefs.edit().putStringSet(PREV_NOTIFICATIONS, newThreadStrings).apply();
    }

    /**
     * Displays a notification for a single message
     */
    private static void singleMessage(final Context context, final ArrayList<MessageItem> messages, final long threadId,
                                      final NotificationCompat.Builder builder,
                                      final Integer privateNotifications) {

        buildSingleMessageNotification(context,
                                       messages,
                                       threadId,
                                       builder,
                                       privateNotifications);
    }


    /**
     * Builds the actual notification for the single message. This code can be
     * called at different points in execution depending on whether or not
     * the MMS data has been downloaded
     */
    private static void buildSingleMessageNotification(final Context context,
                                                       ArrayList<MessageItem> messages,
                                                       long threadId,
                                                       final NotificationCompat.Builder builder,
                                                       final Integer privateNotifications) {

        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);

        MessageItem message = messages.get(0);

        Intent threadIntent = new Intent(context, MainActivity.class);
        threadIntent.putExtra(MessageListActivity.ARG_THREAD_ID, threadId);
        final PendingIntent threadPI = PendingIntent.getActivity(context,
                                                                 buildRequestCode(threadId, 1),
                                                                 threadIntent,
                                                                 PendingIntent.FLAG_UPDATE_CURRENT);

        Intent readIntent = new Intent(ACTION_MARK_READ);
        readIntent.putExtra("thread_id", threadId);
        final PendingIntent readPI = PendingIntent.getBroadcast(context,
                                                                buildRequestCode(threadId, 2),
                                                                readIntent,
                                                                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent seenIntent = new Intent(ACTION_MARK_SEEN);
        final PendingIntent seenPI = PendingIntent.getBroadcast(context,
                                                                buildRequestCode(threadId, 4),
                                                                seenIntent,
                                                                PendingIntent.FLAG_UPDATE_CURRENT);

        int unreadMessageCount = SmsHelper.getUnreadMessageCount(context);
        String body;
        String title;
        NotificationCompat.Style nstyle = null;
        switch (privateNotifications) {
            case 0: //Hide nothing
                body = message.mBody;
                title = message.mContact;
                nstyle = new NotificationCompat.BigTextStyle().bigText(message.mBody);
                break;
            case 1: //Hide message
                body = sRes.getString(R.string.new_message);
                title = message.mContact;
                break;
            case 2: //Hide sender & message
                body = sRes.getString(R.string.new_message);
                title = "QKSMS";
                break;
            default:
                body = message.mBody;
                title = message.mContact;
                nstyle = null;
        }

        builder.setContentTitle(title)
               .setContentText(body)
               .setLargeIcon(null)
               .setContentIntent(threadPI)
               .setNumber(unreadMessageCount)
               .setStyle(nstyle)
               .setColor(ThemeManager.getColor())
               .addAction(R.drawable.ic_accept, sRes.getString(R.string.read), readPI)
               .extend(RemoteMessagingReceiver.getConversationExtender(context,
                                                                       message.mContact,
                                                                       message.mAddress,
                                                                       threadId,
                                                                       CHANNEL_ID))
               .setDeleteIntent(seenPI);

        if (Build.VERSION.SDK_INT < 24) {
            Intent replyIntent = new Intent(context, QKReplyActivity.class);
            replyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            replyIntent.putExtra(QKReplyActivity.EXTRA_THREAD_ID, threadId);
            replyIntent.putExtra(QKReplyActivity.EXTRA_SHOW_KEYBOARD, true);
            PendingIntent replyPI = PendingIntent.getActivity(context,
                                                              buildRequestCode(threadId, 0),
                                                              replyIntent,
                                                              PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(R.drawable.ic_reply, sRes.getString(R.string.reply), replyPI);
        }
        else {
            builder.addAction(RemoteMessagingReceiver.getReplyAction(context,
                                                                     message.mAddress,
                                                                     threadId));
        }

        if (prefs.getBoolean(QKPreference.K_DISMISSED_READ, false)) {
            builder.setDeleteIntent(readPI);
        }

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + message.mAddress));
        PendingIntent callPI = PendingIntent.getActivity(context,
                                                         buildRequestCode(threadId, 3),
                                                         callIntent,
                                                         PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(R.drawable.ic_call, sRes.getString(R.string.call), callPI);

        NotificationMgr.notify(context, (int) threadId, builder.build());
    }

    /**
     * Creates a notification that contains several messages that are all part of the same conversation
     */
    private static void singleSender(final Context context, ArrayList<MessageItem> messages, long threadId,
                                     final NotificationCompat.Builder builder,
                                     final Integer privateNotifications) {

        MessageItem message = messages.get(0);

        Intent threadIntent = new Intent(context, MainActivity.class);
        threadIntent.putExtra(MessageListActivity.ARG_THREAD_ID, threadId);
        PendingIntent threadPI = PendingIntent.getActivity(context,
                                                           buildRequestCode(threadId, 1),
                                                           threadIntent,
                                                           PendingIntent.FLAG_UPDATE_CURRENT);

        Intent readIntent = new Intent(ACTION_MARK_READ);
        readIntent.putExtra("thread_id", threadId);
        PendingIntent readPI = PendingIntent.getBroadcast(context,
                                                          buildRequestCode(threadId, 2),
                                                          readIntent,
                                                          PendingIntent.FLAG_UPDATE_CURRENT);

        Intent seenIntent = new Intent(ACTION_MARK_SEEN);
        PendingIntent seenPI = PendingIntent.getBroadcast(context,
                                                          buildRequestCode(threadId, 4),
                                                          seenIntent,
                                                          PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        for (MessageItem message1 : messages) {
            inboxStyle.addLine(message1.mBody);
        }

        String notificationTitle = message.mContact;

        if (!(privateNotifications == 0))
            inboxStyle = null;
        if (privateNotifications == 2)
            notificationTitle = "QKSMS";

        int unreadMessageCount = SmsHelper.getUnreadMessageCount(context);
        builder.setContentTitle(notificationTitle)
               .setContentText(SmsHelper.getUnseenSMSCount(context,
                                                           threadId) + " " + sRes.getString(R.string.new_messages))
               .setLargeIcon(null)
               .setContentIntent(threadPI)
               .setNumber(unreadMessageCount)
               .setStyle(inboxStyle)
               .setColor(ThemeManager.getColor())
               .addAction(R.drawable.ic_accept, sRes.getString(R.string.read), readPI)
               .extend(RemoteMessagingReceiver.getConversationExtender(context,
                                                                       message.mContact,
                                                                       message.mAddress,
                                                                       threadId,
                                                                       CHANNEL_ID))
               .setDeleteIntent(seenPI);

        if (Build.VERSION.SDK_INT < 24) {
            Intent replyIntent = new Intent(context, QKReplyActivity.class);
            replyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            replyIntent.putExtra(QKReplyActivity.EXTRA_THREAD_ID, threadId);
            replyIntent.putExtra(QKReplyActivity.EXTRA_SHOW_KEYBOARD, true);
            PendingIntent replyPI = PendingIntent.getActivity(context,
                                                              buildRequestCode(threadId, 0),
                                                              replyIntent,
                                                              PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(R.drawable.ic_reply, sRes.getString(R.string.reply), replyPI);
        }
        else {
            builder.addAction(RemoteMessagingReceiver.getReplyAction(context,
                                                                     message.mAddress,
                                                                     threadId));
        }

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + message.mAddress));
        PendingIntent callPI = PendingIntent.getActivity(context,
                                                         buildRequestCode(threadId, 3),
                                                         callIntent,
                                                         PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(R.drawable.ic_call, sRes.getString(R.string.call), callPI);

        notify(context, (int) threadId, builder.build());
    }

    /**
     * Creates a unique action ID for notification actions (Open, Mark read, Call, etc)
     */
    private static int buildRequestCode(long threadId, int action) {
        action++; // Fixes issue on some 4.3 phones | http://stackoverflow.com/questions/19031861/pendingintent-not-opening-activity-in-android-4-3
        return (int) (action * 100000 + threadId);
    }

    /**
     * Create notifications for multiple conversations
     */
    private static void multipleSenders(Context context, HashMap<Long, ArrayList<MessageItem>> conversations, Set<Long> oldThreads, NotificationCompat.Builder builder) {
        Set<Long> threadIds = conversations.keySet();
        for (long threadId : threadIds) {
            if (!oldThreads.contains(threadId)) {

                Integer privateNotification =
                        Integer.parseInt(sPrefs.getString(QKPreference.K_PRIVATE_NOTIFICATION,
                                                          "0"));

                if (conversations.get(threadId).size() == 1) {
                    singleMessage(context,
                                  conversations.get(threadId),
                                  threadId,
                                  copyBuilder(builder),
                                  privateNotification);
                }
                else {
                    singleSender(context,
                                 conversations.get(threadId),
                                 threadId,
                                 copyBuilder(builder),
                                 privateNotification);
                }
            }
        }
    }

    /**
     * Creates a clone of the NotificationCompat.Builder to be used when we're displaying multiple notifications,
     * and need multiple instances of the builder
     */
    private static NotificationCompat.Builder copyBuilder(NotificationCompat.Builder builder) {

        Notification from = builder.build();
        return new NotificationCompat.Builder(builder.mContext, CHANNEL_ID)
                .setSmallIcon(from.icon)
                .setPriority(from.priority)
                .setSound(from.sound)
                .setVibrate(from.vibrate)
                .setLights(from.ledARGB, from.ledOnMS, from.ledOffMS)
                .setTicker(from.tickerText)
                .setAutoCancel(true);
    }

    private static int getLedColor(Context c) {

        int color = ThemeManager.ledColorInt(c);

        if (color == sRes.getColor(R.color.blue_light) || color == sRes.getColor(R.color.blue_dark))
            return sRes.getColor(R.color.blue_dark);
        if (color == sRes.getColor(R.color.purple_light) || color == sRes.getColor(R.color.purple_dark))
            return sRes.getColor(R.color.purple_dark);
        if (color == sRes.getColor(R.color.green_light) || color == sRes.getColor(R.color.green_dark))
            return sRes.getColor(R.color.green_dark);
        if (color == sRes.getColor(R.color.yellow_light) || color == sRes.getColor(R.color.yellow_dark))
            return sRes.getColor(R.color.yellow_dark);
        if (color == sRes.getColor(R.color.red_light) || color == sRes.getColor(R.color.red_dark))
            return sRes.getColor(R.color.red_dark);

        return sRes.getColor(R.color.white_pure);
    }

    /**
     * Returns the notification priority we should be using based on whether or not the Heads-up notification should
     * show
     */
    private static int getNotificationPriority(Context context) {
        boolean qkReplyEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                                                  .getBoolean(QKPreference.K_QUICKREPLY,
                                                              Build.VERSION.SDK_INT < 24);
        if (qkReplyEnabled) {
            return NotificationCompat.PRIORITY_DEFAULT;
        }
        else {
            return NotificationCompat.PRIORITY_HIGH;
        }
    }

}
