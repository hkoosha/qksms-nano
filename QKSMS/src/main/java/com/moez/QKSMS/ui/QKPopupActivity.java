package com.moez.QKSMS.ui;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.AppCompatTextView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.R;
import com.moez.QKSMS.ui.view.QKLinearLayout;

public abstract class QKPopupActivity extends QKActivity {

    protected SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        setFinishOnTouchOutside(mPrefs.getBoolean(QKPreference.K_QUICKREPLY_TAP_DISMISS, true));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        setContentView(getLayoutResource());

        ((QKLinearLayout) findViewById(R.id.popup)).setBackgroundTint(ThemeManager.getBackgroundColor());

        View title = findViewById(R.id.title);
        if (title != null && title instanceof AppCompatTextView) {
            title.setVisibility(View.GONE);
        }
    }

    protected abstract int getLayoutResource();
}
