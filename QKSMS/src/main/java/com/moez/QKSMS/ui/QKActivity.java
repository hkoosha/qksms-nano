package com.moez.QKSMS.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.settings.SettingsActivity;
import com.moez.QKSMS.ui.view.QKTextView;

import java.util.ArrayList;


public abstract class QKActivity extends AppCompatActivity {

    private final String TAG = "QKActivity";

    private Toolbar mToolbar;
    private QKTextView mTitle;
    private ImageView mOverflowButton;
    private Menu mMenu;

    protected SharedPreferences mPrefs;

    protected View mParentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPrefs();
    }

    /**
     * Reloads the toolbar and it's view references.
     * <p>
     * This is called every time the content view of the activity is set, since the
     * toolbar is now a part of the activity layout.
     * <p>
     * TODO: If someone ever wants to manage the Toolbar dynamically instead of keeping it in their
     * TODO  layout file, we can add an alternate way of setting the toolbar programmatically.
     */
    private void reloadToolbar() {
        mToolbar = findViewById(R.id.toolbar);

        if (mToolbar == null) {
            throw new RuntimeException("Toolbar not found in BaseActivity layout.");
        }
        else {
            mToolbar.setPopupTheme(R.style.PopupTheme);
            mTitle = mToolbar.findViewById(R.id.toolbar_title);
            setSupportActionBar(mToolbar);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Util.darken(ThemeManager.getColor()));
            getWindow().setNavigationBarColor(Util.darken(ThemeManager.getColor()));
        }

        int themeRes;
        switch (ThemeManager.getTheme()) {
            case DARK:
                themeRes = R.style.AppThemeDark;
                break;
            case BLACK:
                themeRes = R.style.AppThemeDarkAmoled;
                break;
            default:
                themeRes = R.style.AppThemeLight;
        }
        setTheme(themeRes);
        switch (ThemeManager.getTheme()) {
            case LIGHT:
                mToolbar.setPopupTheme(R.style.PopupThemeLight);
                break;
            case DARK:
            case BLACK:
                mToolbar.setPopupTheme(R.style.PopupTheme);
                break;
        }
        ((QKTextView) findViewById(R.id.toolbar_title))
                .setTextColor(ThemeManager.getTextOnColorPrimary());
    }

    protected void showBackButton(boolean show) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(show);
    }

    public SharedPreferences getPrefs() {
        if (mPrefs == null) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        }
        return mPrefs;
    }

    public void colorMenuIcons(Menu menu, int color) {
        // Toolbar navigation icon
        Drawable navigationIcon = mToolbar.getNavigationIcon();
        if (navigationIcon != null) {
            navigationIcon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            mToolbar.setNavigationIcon(navigationIcon);
        }

        // Overflow icon
        colorOverflowButtonWhenReady(color);

        // Other icons
        for (int i = 0; i < menu.size(); i++) {
            MenuItem menuItem = menu.getItem(i);
            Drawable newIcon = menuItem.getIcon();
            if (newIcon != null) {
                newIcon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                menuItem.setIcon(newIcon);
            }
        }
    }

    private void colorOverflowButtonWhenReady(final int color) {
        if (mOverflowButton != null) {
            // We already have the overflow button, so just color it.
            Drawable icon = mOverflowButton.getDrawable();
            icon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            // Have to clear the image drawable first or else it won't take effect
            mOverflowButton.setImageDrawable(null);
            mOverflowButton.setImageDrawable(icon);

        }
        else {
            // Otherwise, find the overflow button by searching for the content description.
            final ViewGroup decor = (ViewGroup) getWindow().getDecorView();
            decor.getViewTreeObserver()
                 .addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                     @Override
                     public boolean onPreDraw() {
                         decor.getViewTreeObserver().removeOnPreDrawListener(this);

                         final ArrayList<View> views = new ArrayList<>();
                         decor.findViewsWithText(views, getString(R.string.more_options),
                                                 View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);

                         if (views.isEmpty()) {
                             Log.w(TAG, "no views");
                         }
                         else {
                             if (views.get(0) instanceof ImageView) {
                                 mOverflowButton = (ImageView) views.get(0);
                                 colorOverflowButtonWhenReady(color);
                             }
                             else {
                                 Log.w(TAG, "overflow button isn't an imageview");
                             }
                         }
                         return false;
                     }
                 });
        }
    }

    public Menu getMenu() {
        return mMenu;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Save a reference to the menu so that we can quickly access menu icons later.
        mMenu = menu;
        colorMenuIcons(mMenu, ThemeManager.getTextOnColorPrimary());
        return true;
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        reloadToolbar();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        reloadToolbar();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        reloadToolbar();
    }

    /**
     * Sets the title of the activity, displayed on the toolbar
     * <p>
     * Make sure this is only called AFTER setContentView, or else the Toolbar
     * is likely not initialized yet and this method will do nothing
     *
     * @param title title of activity
     */
    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);

        if (mTitle != null) {
            mTitle.setText(title);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(SettingsActivity.class);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void startActivity(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        startActivity(intent);
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        this.mParentView = parent;
        return super.onCreateView(parent, name, context, attrs);
    }

}
