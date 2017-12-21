package com.moez.QKSMS.settings;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.moez.QKSMS.Blocker;
import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Fun;
import com.moez.QKSMS.common.PhoneNumberUtils;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.dialog.QKDialog;
import com.moez.QKSMS.ui.view.QKEditText;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import static com.moez.QKSMS.common.Util.bool;

public class SettingsFragment extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    public static final String TAG = "SettingsFragment";

    static SettingsFragment newInstance(int category) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putInt("category", category);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        getActivity().setTitle(R.string.title_settings);

        int mResource = getArguments().getInt("category", R.xml.settings);
        addPreferencesFromResource(mResource);

        // Set `this` to be the preferences click/change listener for all the preferences.
        PreferenceScreen screen = getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference pref = screen.getPreference(i);
            pref.setOnPreferenceClickListener(this);
            pref.setOnPreferenceChangeListener(this);
            // If this is a preference category, make sure to go through
            // all the sub-preferences as well.
            if (pref instanceof PreferenceCategory) {
                Stack<PreferenceCategory> stack = new Stack<>();
                stack.push((PreferenceCategory) pref);
                do {
                    PreferenceCategory category = stack.pop();
                    for (int j = 0; j < category.getPreferenceCount(); j++) {
                        Preference subPref = category.getPreference(j);
                        subPref.setOnPreferenceClickListener(this);
                        subPref.setOnPreferenceChangeListener(this);
                        if (subPref instanceof PreferenceCategory) {
                            stack.push((PreferenceCategory) subPref);
                        }
                    }
                } while (!stack.isEmpty());
            }
        }

        Preference version = findPreference(QKPreference.K_VERSION);
        if (version != null) {
            String v = "unknown";
            try {
                PackageInfo info = getActivity().getPackageManager()
                                                .getPackageInfo(getActivity().getPackageName(), 0);
                v = info.versionName;
            }
            catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "version err", e);
            }
            version.setSummary(v);
        }

        // Status and nav bar tinting are only supported on kit kat or above.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // remove pref
            Preference pref = findPreference(QKPreference.K_CATEGORY_APPEARANCE_SYSTEM_BARS);
            PreferenceScreen screen1 = getPreferenceScreen();
            if (pref != null && screen1 != null) {
                screen1.removePreference(pref);
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Util.hideKeyboard(getActivity(), view);
        ListView mListView = view.findViewById(android.R.id.list);
        applyCustomScrollbar(getActivity(), mListView);
        View view1 = getView();
        if (view1 != null) {
            view1.setBackgroundColor(ThemeManager.getBackgroundColor());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        final String key = preference.getKey();

        String valueString = newValue == null ? "null" : newValue.toString();

        Log.d(TAG, "onPreferenceChange key:" + key + " newValue: " + valueString);

        switch (key) {
            case QKPreference.K_BACKGROUND:
                ThemeManager.setTheme(ThemeManager.Theme.fromString((String) newValue));
                break;
            case QKPreference.K_STATUS_TINT:
                ThemeManager.setStatusBarTintEnabled(getActivity().getWindow(),
                                                     bool(newValue, true));
                break;
            case QKPreference.K_NAVIGATION_TINT:
                ThemeManager.setNavigationBarTintEnabled(getActivity().getWindow(),
                                                         bool(newValue, true));
                break;
            case QKPreference.K_DELAY_DURATION:
                int duration = Integer.parseInt((String) newValue);
                if (duration < 1 || duration > 30)
                    Toast.makeText(getActivity(),
                                   R.string.delayed_duration_bounds_error,
                                   Toast.LENGTH_SHORT).show();
                break;
        }

        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {

        final String key = preference.getKey() != null ? preference.getKey() : "";

        // Categories
        int resId = 0;
        switch (key) {
            case QKPreference.K_CATEGORY_APPEARANCE:
                resId = R.xml.settings_appearance;
                break;
            case QKPreference.K_CATEGORY_GENERAL:
                resId = R.xml.settings_general;
                break;
            case QKPreference.K_CATEGORY_NOTIFICATIONS:
                resId = R.xml.settings_notifications;
                break;
            case QKPreference.K_CATEGORY_BLOCKING:
                resId = R.xml.settings_blocking;
                break;
            case QKPreference.K_CATEGORY_ABOUT:
                resId = R.xml.settings_about;
                break;
        }
        if (resId != 0) {
            Fragment fragment = SettingsFragment.newInstance(resId);
            getFragmentManager()
                    .beginTransaction()
                    .addToBackStack(null)
                    .replace(R.id.content_frame, fragment, QKPreference.K_CATEGORY_TAG)
                    .commit();
        }

        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getActivity());

        switch (key) {
            case QKPreference.K_THEME:
                ThemeManager.showColorPickerDialog((QKActivity) getActivity());
                break;

            case QKPreference.K_BLOCKED_FUTURE:
                makeDialog(getActivity(),
                           R.string.title_block_address,
                           R.string.pref_block_future,
                           Blocker.getFutureBlockedConversations(prefs),
                           value -> Blocker.blockFutureConversation(prefs, value),
                           value -> Blocker.unblockFutureConversation(prefs, value),
                           value -> true
                );
                break;

            case QKPreference.K_BLOCKED_NUMBER_PREFIX:
                makeDialog(getActivity(),
                           R.string.title_block_number_prefix,
                           R.string.pref_block_number_prefix,
                           Blocker.getBlockedNumberPrefixes(prefs),
                           value1 -> Blocker.blockNumberPrefix(prefs, value1),
                           value11 -> Blocker.unblockNumberPrefix(prefs, value11),
                           value12 -> {
                               if (!PhoneNumberUtils.isWellFormedSmsAddress(value12))
                                   Util.toast(getActivity(), R.string.invalid_number_prefix);
                               return true;
                           }
                );
                break;

            case QKPreference.K_BLOCKED_PATTERN:
                makeDialog(getActivity(),
                           R.string.title_block_pattern,
                           R.string.pref_block_pattern,
                           Blocker.getBlockedPatterns(prefs),
                           value -> Blocker.blockPattern(prefs, value),
                           value -> Blocker.unblockPattern(prefs, value),
                           value -> {
                               if (Blocker.compilePattern(value) != null)
                                   return true;
                               Util.toast(getActivity(), R.string.invalid_pattern);
                               return false;
                           }
                );
                break;

            case QKPreference.K_BLOCKED_WORD:
                makeDialog(getActivity(),
                           R.string.title_block_word,
                           R.string.pref_block_word,
                           Blocker.getBlockedWords(prefs, false),
                           value -> Blocker.blockWord(prefs, value, false),
                           value -> Blocker.unblockWord(prefs, value, false),
                           value -> true
                );
                break;

            case QKPreference.K_BLOCKED_WORD_EXTREME:
                makeDialog(getActivity(),
                           R.string.title_block_word_extreme,
                           R.string.pref_block_word_extreme,
                           Blocker.getBlockedWords(prefs, true),
                           value -> Blocker.blockWord(prefs, value, true),
                           value -> Blocker.unblockWord(prefs, value, true),
                           value -> true
                );
                break;
        }

        return false;
    }

    private static void applyCustomScrollbar(Context context, ListView listView) {
        if (context != null && listView != null) {
            try {
                Drawable drawable = ContextCompat.getDrawable(context, R.drawable.scrollbar);
                if (drawable == null)
                    return;
                drawable.setColorFilter(Color.argb(64,
                                                   Color.red(ThemeManager.getTextOnBackgroundSecondary()),
                                                   Color.green(ThemeManager.getTextOnBackgroundSecondary()),
                                                   Color.blue(ThemeManager.getTextOnBackgroundSecondary())),
                                        PorterDuff.Mode.SRC_ATOP);

                Field mScrollCacheField = View.class.getDeclaredField("mScrollCache");
                mScrollCacheField.setAccessible(true);
                Object mScrollCache = mScrollCacheField.get(listView);
                Field scrollBarField = mScrollCache.getClass().getDeclaredField("scrollBar");
                scrollBarField.setAccessible(true);
                Object scrollBar = scrollBarField.get(mScrollCache);
                Method method = scrollBar.getClass()
                                         .getDeclaredMethod("setVerticalThumbDrawable",
                                                            Drawable.class);
                method.setAccessible(true);
                method.invoke(scrollBar, drawable);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void makeDialog(Activity activity,
                                   @StringRes int addDialogTitle,
                                   @StringRes int parentDialogTitle,
                                   Collection<String> values,
                                   Fun<String, Boolean> adder,
                                   Fun<String, Boolean> remover,
                                   Fun<String, Boolean> validator) {

        List<String> values_ = Util.sort(values);

        QKDialog parentDialog = new QKDialog();
        QKDialog addDialog = new QKDialog();

        final AdapterView.OnItemClickListener onItemClick = (parent, view, position, id) -> {
            CharSequence userInput = ((TextView) view).getText();
            String value = userInput == null ? "" : userInput.toString();
            remover.apply(value);
            values_.remove(value);
            parentDialog.getListAdapter().notifyDataSetChanged();
        };

        View.OnClickListener onAdd = view -> {
            QKEditText text = new QKEditText(activity);
            View.OnClickListener onClickListener = v -> {
                CharSequence userInput = ((TextView) text).getText();
                String value = userInput == null ? "" : userInput.toString();
                if (value.isEmpty() || !validator.apply(value))
                    return;
                boolean changed = adder.apply(value);
                if (changed) {
                    values_.add(value);
                    parentDialog.getListAdapter().notifyDataSetChanged();
                }
            };
            addDialog.setContext(activity)
                     .setTitle(addDialogTitle)
                     .setCustomView(text).setPositiveButton(true, R.string.add, onClickListener)
                     .setNegativeButton(R.string.cancel, null)
                     .show();
        };

        parentDialog.setContext(activity)
                    .setTitle(parentDialogTitle)
                    .setItems(values_, onItemClick)
                    .setPositiveButton(false, R.string.add, onAdd)
                    .setNegativeButton(R.string.EMPTY_STRING, null)
                    .show();
    }


}
