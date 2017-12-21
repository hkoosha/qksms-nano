package com.moez.QKSMS.settings;

import android.app.FragmentManager;
import android.os.Bundle;

import com.moez.QKSMS.R;
import com.moez.QKSMS.ui.QKSwipeBackActivity;

public class SettingsActivity extends QKSwipeBackActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getFragmentManager();
        SettingsFragment mSettingsFragment =
                (SettingsFragment) fm.findFragmentByTag(SettingsFragment.TAG);

        if (mSettingsFragment == null) {
            mSettingsFragment = SettingsFragment.newInstance(R.xml.settings_main);
            fm.beginTransaction()
              .replace(R.id.content_frame, mSettingsFragment, SettingsFragment.TAG)
              .commit();
        }
        else {
            fm.beginTransaction()
              .show(mSettingsFragment)
              .commit();
        }
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
        }
        else {
            super.onBackPressed();
        }
    }

}
