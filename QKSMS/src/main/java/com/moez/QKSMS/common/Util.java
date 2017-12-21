package com.moez.QKSMS.common;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.moez.QKSMS.R;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.content.Context.POWER_SERVICE;


public final class Util {

    private static final String TAG = "QKSMS";
    private static final Pattern EMAIL_ADDRESS_PATTERN
            = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-]{1,256}" +
                    "\\@" +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                    ")+"
    );
    private static final Pattern NAME_ADDR_EMAIL_PATTERN =
            Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");

    private Util() {
    }

    private static final String countryIso = Locale.getDefault().getCountry().toUpperCase();

    public static String countryIso() {
        return countryIso;
    }

    // ---------------------------------------------------------------

    public static boolean isValid(Cursor cursor) {
        return cursor != null && !cursor.isClosed();
    }

    public static void close(Cursor c) {
        if (c != null) {
            try {
                c.close();
            }
            catch (Exception ignored) {

            }
        }
    }

    // ---------------------------------------------------------------

    @SuppressWarnings("SameParameterValue")
    public static int dpToPx(Context context, int dp) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public static int lighten(int color) {
        double r = Color.red(color);
        double g = Color.green(color);
        double b = Color.blue(color);

        r *= 1.1;
        g *= 1.1;
        b *= 1.1;

        double threshold = 255.999;
        double max = Math.max(r, Math.max(g, b));

        if (max > threshold) {
            double total = r + g + b;
            if (total >= 3 * threshold)
                return Color.WHITE;

            double x = (3 * threshold - total) / (3 * max - total);
            double gray = threshold - x * max;

            r = gray + x * r;
            g = gray + x * g;
            b = gray + x * b;
        }

        return Color.argb(255, (int) r, (int) g, (int) b);
    }

    public static int darken(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.85f;
        color = Color.HSVToColor(hsv);
        return color;
    }

    public static void hideKeyboard(Context context, View view) {
        if (context == null || view == null) {
            Log.w("QKSMS", "hide called with null parameter: " + context + " " + view);
            return;
        }

        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void showKeyboard(Context context, View viewToFocus) {
        if (context != null) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) {
                return;
            }
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
        }
        else {
            Log.w("QKSMS", "show called with null context");
        }

        if (viewToFocus != null) {
            viewToFocus.requestFocus();
        }
    }

    private static void snack(View v,
                              @StringRes int msg,
                              @StringRes int text,
                              View.OnClickListener onClick,
                              Context ctx) {

        CharSequence msg1 = ctx.getResources().getString(msg);

        try {
            Snackbar.make(v,
                          msg1,
                          Snackbar.LENGTH_SHORT)
                    .setAction(text, onClick)
                    .show();
        }
        catch (Exception ex) {
            Toast.makeText(ctx, msg1, Toast.LENGTH_SHORT).show();
        }
    }

    public static void toast(Context context,
                             @StringRes int msg) {

        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }


    // ---------------------------------------------------------------

    public static Boolean bool(final Object value, final boolean def) {

        return value == null ? def : (Boolean) value;
    }

    // ---------------------------------------------------------------

    @SafeVarargs
    public static <T> Set<T> setOf(T... ts) {
        Set<T> s;
        if (ts.length == 1) {
            s = new HashSet<>(1);
            s.add(ts[0]);
        }
        else {
            s = new HashSet<>();
            s.addAll(Arrays.asList(ts));
        }
        return s;
    }

    public static <T> Set<T> set(Collection<T> c) {
        return new HashSet<>(c);
    }

    public static <T> List<T> list(Collection<T> col) {
        return new ArrayList<>(col);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> sort(Collection<T> col) {
        List<T> l = list(col);
        Collections.sort((List) l);
        return l;
    }

    // ---------------------------------------------------------------

    @SuppressWarnings("SameParameterValue")
    public static void wake(Context context, long period, String tag) {

        PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);
        if (pm == null)
            return;

        final int flag = PowerManager.SCREEN_DIM_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP;
        PowerManager.WakeLock wakeLock = pm.newWakeLock(flag, tag);
        wakeLock.acquire(period);
        wakeLock.release();
    }

    public static boolean isScreenOn(Context context) {

        PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        if (powerManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            return powerManager.isInteractive();
        }
        else {
            return powerManager.isScreenOn();
        }
    }

    public static void copyToClipboard(Context context, String str) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, str));
        }
    }

    // ---------------------------------------------------------------

    public static String formatTimeStampString(Context context, long when) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                DateUtils.FORMAT_ABBREV_ALL |
                DateUtils.FORMAT_CAP_AMPM;

        // If the message is from a different year, show the date and year.
        if (then.year != now.year) {
            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        }
        else if (then.yearDay != now.yearDay) {
            // If it is from a different day than today, show only the date.
            format_flags |= DateUtils.FORMAT_SHOW_DATE;
        }
        else {
            // Otherwise, if the message is from today, show the time.
            format_flags |= DateUtils.FORMAT_SHOW_TIME;
        }

        format_flags |= (DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        return DateUtils.formatDateTime(context, when, format_flags);
    }

    public static String getConversationTimestamp(Context context, long date) {
        if (isSameDay(date)) {
            return accountFor24HourTime(context, sdf("h:mm a")).format(date);
        }

        int flag;
        if (isSameWeek(date)) {
            flag = DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY;
        }
        else if (isSameYear(date)) {
            flag = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH;
        }
        else {
            flag = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
        }
        return DateUtils.formatDateTime(context, date, flag);
    }

    public static String getMessageTimestamp(Context context, long date) {

        if (isSameDay(date)) {
            return accountFor24HourTime(context, sdf("h:mm a")).format(date);
        }

        final String time = ", " +
                accountFor24HourTime(context, sdf("h:mm a")).format(date);
        if (isYesterday(date)) {
            return context.getString(R.string.date_yesterday) + time;
        }

        int flag;
        if (isSameWeek(date)) {
            flag = DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY;
        }
        else if (isSameYear(date)) {
            flag = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH;
        }
        else {
            flag = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
        }
        return DateUtils.formatDateTime(context, date, flag) + time;
    }

    public static String getDate(Context context, long date) {
        return DateUtils.formatDateTime(context, date, DateUtils.FORMAT_SHOW_DATE)
                + accountFor24HourTime(context, sdf(", h:mm:ss a")).format(date);
    }

    private static SimpleDateFormat sdf(final String pattern) {

        return new SimpleDateFormat(pattern, Locale.getDefault());
    }

    private static boolean isSameDay(long date) {
        SimpleDateFormat formatter = sdf("D, y");
        return formatter.format(date).equals(formatter.format(System.currentTimeMillis()));
    }

    private static boolean isSameWeek(long date) {
        SimpleDateFormat formatter = sdf("w, y");
        return formatter.format(date).equals(formatter.format(System.currentTimeMillis()));
    }

    private static boolean isSameYear(long date) {
        SimpleDateFormat formatter = sdf("y");
        return formatter.format(date).equals(formatter.format(System.currentTimeMillis()));
    }

    private static boolean isYesterday(long date) {
        SimpleDateFormat formatter = sdf("yD");
        return Integer.parseInt(formatter.format(date)) + 1 ==
                Integer.parseInt(formatter.format(System.currentTimeMillis()));
    }

    private static SimpleDateFormat accountFor24HourTime(Context context, SimpleDateFormat input) {
        // pass in 12 hour time. If needed, change to 24 hr.
        boolean isUsing24HourTime = DateFormat.is24HourFormat(context);

        return isUsing24HourTime
               ? sdf(input.toPattern().replace('h', 'H').replaceAll(" a", ""))
               : input;
    }

    // ---------------------------------------------------------------

    public static String getContactName(Context context, String address) {
        if (address == null || address.isEmpty())
            return address;

        Pattern pattern;
        Matcher matcher;
        String EMAIL_PATTERN = "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b";
        pattern = Pattern.compile(EMAIL_PATTERN);
        matcher = pattern.matcher(address);
        if (matcher.matches()) {
            return address;
        }

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                       Uri.encode(address));
        ContentResolver contentResolver = context.getContentResolver();

        String name = address;

        try (Cursor cursor = contentResolver.query(uri,
                                                   new String[]{BaseColumns._ID,
                                                           ContactsContract.PhoneLookup.DISPLAY_NAME},
                                                   null,
                                                   null,
                                                   null)) {
            if (cursor != null && cursor.moveToNext())
                name = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
            close(cursor);
        }
        catch (Exception e) {
            Log.d(TAG, "Failed to find name for address " + address, e);
        }

        return name;
    }

    // ---------------------------------------------------------------

    public static boolean isDefaultSmsApp(Context context) {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ||
                context.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(context));
    }

    public static void showDefaultSMSDialog(View v, @StringRes int msgRes) {
        if (v == null || isDefaultSmsApp(v.getContext())) {
            return;
        }

        Util.snack(v, msgRes, R.string.upgrade_now, view -> {
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                            view.getContext().getPackageName());
            view.getContext().startActivity(intent);
        }, v.getContext());
    }

    // ---------------------------------------------------------------

    /**
     * Gets the current thread_id or creates a new one for the given recipient
     *
     * @param context   is the context of the activity or service
     * @param recipient is the person message is being sent to
     * @return the thread_id to use in the database
     */
    public static long getOrCreateThreadId(Context context, String recipient) {
        Set<String> recipients = new HashSet<>();
        recipients.add(recipient);
        return getOrCreateThreadId(context, recipients);
    }

    /**
     * Gets the current thread_id or creates a new one for the given recipient
     *
     * @param context    is the context of the activity or service
     * @param recipients is the set of people message is being sent to
     * @return the thread_id to use in the database
     */
    private static long getOrCreateThreadId(Context context, Set<String> recipients) {
        long threadId = getThreadId(context, recipients);
        Random random = new Random();
        return threadId == -1 ? random.nextLong() : threadId;
    }

    /**
     * Gets the current thread_id or -1 if none found
     *
     * @param context    is the context of the activity or service
     * @param recipients is the set of people message is being sent to
     * @return the thread_id to use in the database, -1 if none found
     */
    public static long getThreadId(Context context, Set<String> recipients) {
        Uri.Builder uriBuilder = Uri.parse("content://mms-sms/threadID").buildUpon();

        for (String recipient : recipients) {
            if (isEmailAddress(recipient)) {
                recipient = extractAddrSpec(recipient);
            }
            uriBuilder.appendQueryParameter("recipient", recipient);
        }

        Uri uri = uriBuilder.build();

        ContentResolver resolver = context.getContentResolver();

        Cursor cursor;

        try {
            cursor = resolver.query(uri, new String[]{"_id"}, null, null, null);
        }
        catch (SQLiteException e) {
            Log.e("FUU", "Catch a SQLiteException when query: ", e);
            throw e;
        }

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            }
            finally {
                cursor.close();
            }
        }

        return -1;
    }

    private static boolean isEmailAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return false;
        }

        String s = extractAddrSpec(address);
        Matcher match = EMAIL_ADDRESS_PATTERN.matcher(s);
        return match.matches();
    }

    private static String extractAddrSpec(String address) {
        Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);

        if (match.matches()) {
            return match.group(2);
        }
        return address;
    }


}
