package com.moez.QKSMS.ui.messagelist;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;

import com.moez.QKSMS.R;
import com.moez.QKSMS.common.PhoneNumberUtils;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.QKSwipeBackActivity;

import java.net.URLDecoder;

public class MessageListActivity extends QKSwipeBackActivity {
    private static final String TAG = "MessageListActivity";

    public static final String ARG_THREAD_ID = "thread_id";
    public static final String ARG_ROW_ID = "rowId";
    public static final String ARG_HIGHLIGHT = "highlight";
    public static final String ARG_SHOW_IMMEDIATE = "showImmediate";

    private static long mThreadId;

    public static boolean isInForeground;

    public static void launch(QKActivity context, long threadId, long rowId, String pattern) {
        Intent intent = new Intent(context, MessageListActivity.class);
        intent.putExtra(ARG_THREAD_ID, threadId);
        intent.putExtra(ARG_ROW_ID, rowId);
        intent.putExtra(ARG_HIGHLIGHT, pattern);
        intent.putExtra(ARG_SHOW_IMMEDIATE, true);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onNewIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mThreadId = intent.getLongExtra(ARG_THREAD_ID, -1);
        long mRowId = intent.getLongExtra(ARG_ROW_ID, -1);
        String mHighlight = intent.getStringExtra(ARG_HIGHLIGHT);
        boolean mShowImmediate = intent.getBooleanExtra(ARG_SHOW_IMMEDIATE, false);

        if (mThreadId == -1 && intent.getData() != null) {
            String data = intent.getData().toString();
            String scheme = intent.getData().getScheme();

            String address = null;
            if (scheme.startsWith("smsto") || scheme.startsWith("mmsto")) {
                address = data.replace("smsto:", "").replace("mmsto:", "");
            }
            else if (scheme.startsWith("sms") || (scheme.startsWith("mms"))) {
                address = data.replace("sms:", "").replace("mms:", "");
            }

            address = URLDecoder.decode(address);
            address = "" + Html.fromHtml(address);
            address = PhoneNumberUtils.formatNumber(address);
            mThreadId = Util.getOrCreateThreadId(this, address);
        }

        if (mThreadId != -1) {
            Log.v(TAG, "Opening thread: " + mThreadId);
            FragmentManager fm = getFragmentManager();
            MessageListFragment fragment = (MessageListFragment) fm.findFragmentByTag(
                    MessageListFragment.TAG);
            if (fragment == null) {
                fragment = MessageListFragment.getInstance(mThreadId,
                                                           mRowId, mHighlight, mShowImmediate);
            }
            mSwipeBackLayout.setScrollChangedListener(fragment);
            FragmentTransaction menuTransaction = fm.beginTransaction();
            menuTransaction.replace(R.id.content_frame, fragment, MessageListFragment.TAG);
            menuTransaction.commit();
        }
        else {
            StringBuilder msg = new StringBuilder("Couldn't open conversation: {action:");
            msg.append(intent.getAction());
            msg.append(", data:");
            msg.append(intent.getData() == null ? "null" : intent.getData().toString());
            msg.append(", scheme:");
            msg.append(intent.getData() == null ? "null" : intent.getData().getScheme());
            msg.append(", extras:{");
            Object[] keys = intent.getExtras().keySet().toArray();
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i].toString();
                msg.append(keys[i].toString());
                msg.append(":");
                msg.append(intent.getExtras().get(key));
                if (i < keys.length - 1) {
                    msg.append(", ");
                }
            }
            msg.append("}}");
            Log.d(TAG, msg.toString());
            Util.toast(this, R.string.toast_billing_not_available);
            finish();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.message_list, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public static long getThreadId() {
        return mThreadId;
    }
}
