package com.moez.QKSMS.ui;

import android.util.Log;
import android.view.View;

import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.ui.dialog.QKDialog;
import com.moez.QKSMS.ui.messagelist.MessageListActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.moez.QKSMS.common.Util.set;

public final class UiUtil {

    private static final String TAG = "UiUtil";

    private UiUtil() {

    }

    public static void showDeleteConversationsDialog(final QKActivity context, final Set<Long> threadIds) {
        Util.showDefaultSMSDialog(context.mParentView, R.string.not_default_delete);

        Set<Long> threads = new HashSet<>(threadIds); // Make a copy so the list isn't reset when multi-select is disabled
        View.OnClickListener onClickListener = v -> {
            Log.d(TAG, "Deleting threads: " + Arrays.toString(threads.toArray()));
            Conversation.ConversationQueryHandler handler =
                    new Conversation.ConversationQueryHandler(context);
            Conversation.startDelete(handler, 0, threads);
            Conversation.asyncDeleteObsoleteThreads(handler, 0);
            if (context instanceof MessageListActivity) {
                context.onBackPressed();
            }
        };
        new QKDialog()
                .setContext(context)
                .setTitle(R.string.delete_conversation)
                .setMessage(context.getString(R.string.delete_confirmation, "" + threads.size()))
                .setPositiveButton(true, R.string.yes, onClickListener)
                .setNegativeButton(R.string.cancel, null)
                .show();

    }

    public static void showDeleteFailedMessagesDialog(final MainActivity context, final Set<Long> threadIds) {
        Util.showDefaultSMSDialog(context.mParentView, R.string.not_default_delete);

        Set<Long> threads = set(threadIds); // Make a copy so the list isn't reset when multi-select is disabled
        View.OnClickListener onClickListener = v -> new Thread(() -> {
            for (long threadId : threads) {
                SmsHelper.deleteFailedMessages(context, threadId);
            }
        }).start();
        new QKDialog()
                .setContext(context)
                .setTitle(R.string.delete_all_failed)
                .setMessage(context.getString(R.string.delete_all_failed_confirmation,
                                              "" + threads.size()))
                .setPositiveButton(true, R.string.yes, onClickListener)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
