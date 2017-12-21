package com.moez.QKSMS.ui;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;

import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.ui.conversationlist.ConversationListFragment;
import com.moez.QKSMS.ui.messagelist.MessageListActivity;
import com.moez.QKSMS.ui.search.SearchActivity;

import java.util.Collection;


public class MainActivity extends QKActivity {

    public static final int DELETE_CONVERSATION_TOKEN = 1801;
    public static final int HAVE_LOCKED_MESSAGES_TOKEN = 1802;

    //    @Bind(R.id.root)
    View mRoot;

    private ConversationListFragment mConversationList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onNewIntent(getIntent());

        setContentView(R.layout.activity_fragment);
        setTitle(R.string.title_conversation_list);
        this.mRoot = this.findViewById(R.id.root);
        //        ButterKnife.bind(this);

        FragmentManager fm = getFragmentManager();
        mConversationList = (ConversationListFragment) fm.findFragmentByTag(ConversationListFragment.TAG);
        if (mConversationList == null) {
            mConversationList = new ConversationListFragment();
        }
        fm.beginTransaction().replace(R.id.content_frame,
                                      mConversationList,
                                      ConversationListFragment.TAG)
          .commit();

        mRoot.setBackgroundColor(ThemeManager.getBackgroundColor());
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        menu.clear();

        showBackButton(false);
        mConversationList.inflateToolbar(menu, inflater);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onKeyUp(KeyEvent.KEYCODE_BACK, null);
                return true;
            case R.id.menu_search:
                startActivity(SearchActivity.class);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Util.showDefaultSMSDialog(mParentView, R.string.not_default_first);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mConversationList.isShowingBlocked()) {
                mConversationList.setShowingBlocked(false);
            }
            else {
                finish();
            }
        }

        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Only mark screen if the screen is on. onStart() is still called if the app is in the
        // foreground and the screen is off
        // TODO this solution doesn't work if the activity is in the foreground but the lockscreen is on
        if (Util.isScreenOn(getBaseContext())) {
            SmsHelper.markSmsSeen(this);
            NotificationMgr.update(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    /**
     * MainActivity has a "singleTask" launch mode, which means that if it is currently running
     * and another intent is launched to open it, instead of creating a new MainActivity it
     * just opens the current MainActivity. We use this so that when you click on notifications,
     * only one main activity is ever used.
     * <p>
     * onNewIntent() is called every time the homescreen shortcut is tapped, even if the app
     * is already running in the background. It's also called when the app is launched via other
     * intents
     * <p>
     * Docs:
     * http://developer.android.com/guide/components/tasks-and-back-stack.html#TaskLaunchModes
     */
    @Override
    public void onNewIntent(Intent intent) {
        // onNewIntent doesn't change the result of getIntent() by default, so here we set it since
        // that makes the most sense.
        setIntent(intent);

        boolean shouldOpenConversation = intent.hasExtra(MessageListActivity.ARG_THREAD_ID);

        // The activity can also be launched by clicking on the message button from the contacts app
        // Check for {sms,mms}{,to}: schemes, in which case we know to open a conversation
        if (intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            shouldOpenConversation = shouldOpenConversation || scheme.startsWith("sms") || scheme.startsWith(
                    "mms");
        }

        if (shouldOpenConversation) {
            intent.setClass(this, MessageListActivity.class);
            startActivity(intent);
        }
    }

    /**
     * Build and show the proper delete thread dialog. The UI is slightly different
     * depending on whether there are locked messages in the thread(s) and whether we're
     * deleting single/multiple threads or all threads.
     *
     * @param listener          gets called when the delete button is pressed
     * @param threadIds         the thread IDs to be deleted (pass null for all threads)
     * @param hasLockedMessages whether the thread(s) contain locked messages
     * @param context           used to load the various UI elements
     */
    public static void confirmDeleteThreadDialog(final DeleteThreadListener listener, Collection<Long> threadIds,
                                                 boolean hasLockedMessages, Context context) {
        View contents = View.inflate(context, R.layout.dialog_delete_thread, null);
        android.widget.TextView msg = contents.findViewById(R.id.message);

        if (threadIds == null) {
            msg.setText(R.string.confirm_delete_all_conversations);
        }
        else {
            // Show the number of threads getting deleted in the confirmation dialog.
            int cnt = threadIds.size();
            msg.setText(context.getResources().getQuantityString(
                    R.plurals.confirm_delete_conversation, cnt, cnt));
        }

        final CheckBox checkbox = contents.findViewById(R.id.delete_locked);
        if (!hasLockedMessages) {
            checkbox.setVisibility(View.GONE);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.confirm_dialog_title)
               .setIconAttribute(android.R.attr.alertDialogIcon)
               .setCancelable(true)
               .setPositiveButton(R.string.delete, listener)
               .setNegativeButton(R.string.cancel, null)
               .setView(contents)
               .show();
    }

    public static class DeleteThreadListener implements DialogInterface.OnClickListener {

        public DeleteThreadListener() {
        }

        @Override
        public void onClick(DialogInterface dialog, final int whichButton) {
            dialog.dismiss();
        }
    }
}
