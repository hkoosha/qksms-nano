package com.moez.QKSMS.ui.compose;

import android.app.FragmentManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.ui.QKSwipeBackActivity;
import com.moez.QKSMS.ui.dialog.QKDialog;

public class ComposeActivity extends QKSwipeBackActivity {

    private ComposeFragment mComposeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_compose);

        FragmentManager fm = getFragmentManager();
        mComposeFragment = (ComposeFragment) fm.findFragmentByTag(ComposeFragment.TAG);
        if (mComposeFragment == null)
            mComposeFragment = new ComposeFragment();

        fm.beginTransaction()
          .replace(R.id.content_frame, mComposeFragment, ComposeFragment.TAG)
          .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.compose, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        // Check if we're not the default SMS app
        if (!Util.isDefaultSmsApp(this)) {
            // Ask to become the default SMS app
            Util.showDefaultSMSDialog(getWindow().getDecorView().getRootView(),
                                      R.string.not_default_send);
        }
        else if (mComposeFragment != null && !mComposeFragment.isReplyTextEmpty()
                && mComposeFragment.getRecipientAddresses().length == 0) {
            // If there is Draft message and no recipients are set
            View.OnClickListener onClickListener = v -> super.onBackPressed();
            new QKDialog()
                    .setContext(this)
                    .setMessage(R.string.discard_message_reason)
                    .setPositiveButton(true, R.string.yes, onClickListener)
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
        else {
            super.onBackPressed();
        }
    }

}
