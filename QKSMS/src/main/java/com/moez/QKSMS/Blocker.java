package com.moez.QKSMS;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.util.Log;
import android.view.MenuItem;

import com.moez.QKSMS.common.PhoneNumberUtils;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.data.Message;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.ui.messagelist.MessageColumns;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Set;
import java.util.regex.Pattern;

import static com.moez.QKSMS.common.Util.close;

/**
 * A set of helper methods to group the logic related to blocked conversation
 */
public class Blocker {

    public static boolean isConversationBlocked(SharedPreferences prefs, long threadId) {
        Set<String> idStrings = prefs.getStringSet(
                QKPreference.K_BLOCKED_SENDERS, new HashSet<>());
        return idStrings.contains(String.valueOf(threadId));
    }

    public static void blockConversation(SharedPreferences prefs, long threadId) {
        Set<String> idStrings = prefs.getStringSet(
                QKPreference.K_BLOCKED_SENDERS, new HashSet<>());
        idStrings.add(String.valueOf(threadId));
        prefs.edit().putStringSet(QKPreference.K_BLOCKED_SENDERS, idStrings).apply();
    }

    public static void unblockConversation(SharedPreferences prefs, long threadId) {
        Set<String> idStrings = prefs.getStringSet(
                QKPreference.K_BLOCKED_SENDERS, new HashSet<>());
        idStrings.remove(String.valueOf(threadId));
        prefs.edit().putStringSet(QKPreference.K_BLOCKED_SENDERS, idStrings).apply();
    }

    public static Set<Long> getBlockedConversationIds(SharedPreferences prefs) {
        Set<String> conversations = getBlockedConversations(prefs);
        Set<Long> ids = new HashSet<>();
        for (String id : conversations) {
            ids.add(Long.parseLong(id));
        }
        return ids;
    }

    private static Set<String> getBlockedConversations(SharedPreferences prefs) {
        return prefs.getStringSet(QKPreference.K_BLOCKED_SENDERS, new HashSet<>());
    }

    public static boolean blockFutureConversation(SharedPreferences prefs, String address) {
        Set<String> idStrings = prefs.getStringSet(
                QKPreference.K_BLOCKED_FUTURE, new HashSet<>());
        final boolean modified = idStrings.add(address);
        prefs.edit().putStringSet(QKPreference.K_BLOCKED_FUTURE, idStrings).apply();
        return modified;
    }

    public static boolean unblockFutureConversation(SharedPreferences prefs, String address) {
        Set<String> idStrings2 = prefs.getStringSet(
                QKPreference.K_BLOCKED_FUTURE, new HashSet<>());
        final boolean modified = idStrings2.remove(address);
        prefs.edit().putStringSet(QKPreference.K_BLOCKED_FUTURE, idStrings2).apply();
        return modified;
    }

    public static Set<String> getFutureBlockedConversations(SharedPreferences prefs) {
        return prefs.getStringSet(QKPreference.K_BLOCKED_FUTURE, new HashSet<>());
    }

    private static boolean isFutureBlocked(SharedPreferences prefs, String address) {
        for (String s : getFutureBlockedConversations(prefs)) {
            if (PhoneNumberUtils.compareLoosely(s, address)) {
                return true;
            }
        }

        return false;
    }

    public static String[] getBlockedConversationArray(SharedPreferences prefs) {
        Set<String> idStrings = getBlockedConversations(prefs);
        return idStrings.toArray(new String[idStrings.size()]);
    }

    public static String getCursorSelection(SharedPreferences prefs, boolean blocked) {
        StringBuilder selection = new StringBuilder();
        selection.append(Telephony.Threads.MESSAGE_COUNT);
        selection.append(" != 0");
        selection.append(" AND ");
        selection.append(Telephony.Threads._ID);
        if (!blocked)
            selection.append(" NOT");
        selection.append(" IN (");

        Set<String> idStrings = getBlockedConversations(prefs);
        for (int i = 0; i < idStrings.size(); i++) {
            selection.append("?");
            if (i < idStrings.size() - 1) {
                selection.append(",");
            }
        }
        selection.append(")");

        return selection.toString();
    }

    /**
     * If the user has message blocking enabled, then in the menu of the conversation list, there's an item that says
     * Blocked (#). This method will find the number of blocked unread messages to show in that menu item and bind it
     */
    public static void bindBlockedMenuItem(final Context context, final SharedPreferences prefs, final MenuItem item, boolean showBlocked) {
        if (item == null) {
            return;
        }

        new BindMenuItemTask(context, prefs, item, showBlocked).execute((Void[]) null);
    }

    private final static class BindMenuItemTask extends AsyncTask<Void, Void, Integer> {

        private final SharedPreferences mPrefs;
        private final MenuItem mMenuItem;
        private final boolean mShowBlocked;

        private final String mMessagesString;
        private final String mBlockedString;
        private final ContentResolver mContentResolver;


        private BindMenuItemTask(Context context, SharedPreferences prefs, MenuItem item, boolean showBlocked) {
            mPrefs = prefs;
            mMenuItem = item;
            mShowBlocked = showBlocked;
            mMessagesString = context.getString(R.string.menu_messages);
            mBlockedString = context.getString(R.string.menu_blocked);
            mContentResolver = context.getContentResolver();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mMenuItem.setVisible(mPrefs.getBoolean(QKPreference.K_BLOCKED_ENABLED, false));
            mMenuItem.setTitle(mShowBlocked ? mMessagesString : mBlockedString);
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int unreadCount = 0;

            // Create a cursor for the conversation list
            Cursor conversationCursor = mContentResolver.query(
                    SmsHelper.CONVERSATIONS_CONTENT_PROVIDER,
                    Conversation.ALL_THREADS_PROJECTION,
                    getCursorSelection(mPrefs, !mShowBlocked),
                    getBlockedConversationArray(mPrefs),
                    SmsHelper.sortDateDesc);

            if (conversationCursor != null && conversationCursor.moveToFirst()) {
                do {
                    Uri threadUri = Uri.withAppendedPath(Message.MMS_SMS_CONTENT_PROVIDER,
                                                         conversationCursor.getString(Conversation.ID));
                    Cursor messageCursor = mContentResolver
                            .query(threadUri,
                                   MessageColumns.PROJECTION,
                                   SmsHelper.UNREAD_SELECTION,
                                   null,
                                   SmsHelper.sortDateDesc);
                    unreadCount += messageCursor == null ? 0 : messageCursor.getCount();
                    close(messageCursor);
                } while (conversationCursor.moveToNext());
            }

            close(conversationCursor);
            return unreadCount;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);
            Log.d("BindMenuItemTask", "onPostExecute: " + integer);
            mMenuItem.setTitle(mShowBlocked
                               ? (mMessagesString + " (" + integer + ")")
                               : (mBlockedString + " (" + integer + ")")
            );
        }
    }

    public final static class FutureBlockedConversationObservable extends Observable {

        private final Object LOCK = new Object();

        private static FutureBlockedConversationObservable sInstance
                = new FutureBlockedConversationObservable();

        public static FutureBlockedConversationObservable getInstance() {
            return sInstance;
        }

        public void futureBlockedConversationReceived() {
            synchronized (LOCK) {
                setChanged();
                notifyObservers();
            }
        }

    }


    /**
     * Return true if a message from a sender should be blocked.
     */
    public static boolean isBlocked(final SharedPreferences prefs,
                                    final Message message,
                                    final String address,
                                    String body) {

        if (!prefs.getBoolean(QKPreference.K_BLOCK_ENABLED, false))
            return false;

        if (prefs.getBoolean(QKPreference.K_BLOCK_SKIP_CONTACTS, true) &&
                Contact.get(message.getAddress(), true).existsInDatabase()) {
            Log.d("QKSMS::Blocker", "skipping spam check from contact: " + address);
            return false;
        }

        if (address != null && !address.isEmpty()) {
            if (isFutureBlocked(prefs, address)) {
                Log.i("QKSMS::Blocker",
                      "blocked from <" + address + "> because of address blacklist");
                return true;
            }

            final String numberPrefix0 = getBlockedNumberPrefixOf(prefs, address);
            if (numberPrefix0 != null) {
                Log.i("QKSMS::Blocker",
                      "blocked from <" + address + "> because of number prefix: " + numberPrefix0);
                return true;
            }

            StringBuilder rippedAddr = new StringBuilder();
            for (int i = 0; i < address.length(); i++) {
                final Character c = address.charAt(i);
                if ('0' <= c && c <= '9')
                    rippedAddr.append(c);
            }
            final String numberPrefix1 = getBlockedNumberPrefixOf(prefs, rippedAddr.toString());
            if (numberPrefix1 != null) {
                Log.i("QKSMS::Blocker",
                      "blocked from <" + address + "> because of number prefix: " + numberPrefix1);
                return true;
            }
        }

        if (body != null && !body.isEmpty()) {
            body = body.trim().toLowerCase();

            final String word = getBlockedWordOf(prefs, body);
            if (word != null) {
                Log.i("QKSMS::Blocker",
                      "blocked from <" + address + "> because of spam words: " + word);
                return true;
            }

            final String pattern = getBlockedPatternOf(prefs, body);
            if (pattern != null) {
                Log.i("QKSMS::Blocker",
                      "blocked from <" + address + "> because of spam pattern: " + pattern);
                return true;
            }
        }

        return false;
    }

    private static boolean modifyCollection(SharedPreferences prefs,
                                            String value,
                                            String key,
                                            boolean isAdd) {

        value = value.toLowerCase().trim();
        final Set<String> values = prefs.getStringSet(key, new HashSet<>(0));
        final boolean modified = isAdd ? values.add(value) : values.remove(value);

        if (modified)
            prefs.edit().putStringSet(key, values).apply();

        return modified;
    }

    // ------------------- BLOCK BY WORD BLACKLIST

    /**
     * Add a word to spam list. If a message contains that word, it's *probably* spam.
     *
     * @param prefs app shared pref.
     * @return true if the value did not exist in the collection before, and hence the collection was modified.
     */
    public static boolean blockWord(SharedPreferences prefs, String value, boolean extreme) {

        return modifyCollection(prefs, value,
                                extreme
                                ? QKPreference.K_BLOCKED_WORD_EXTREME
                                : QKPreference.K_BLOCKED_WORD,
                                true);
    }

    /**
     * Remove a word from spam list.
     *
     * @param prefs app shared pref.
     * @param value the value to remove.
     * @return true if the value actually existed in the collection, and hence the collection was modified.
     */
    public static boolean unblockWord(SharedPreferences prefs, String value, boolean extreme) {

        return modifyCollection(prefs, value,
                                extreme
                                ? QKPreference.K_BLOCKED_WORD_EXTREME
                                : QKPreference.K_BLOCKED_WORD,
                                false);
    }

    /**
     * List of all blacklisted words (spam words). If a message contains any of
     * these words, it's probably spam and should be blocked.
     *
     * @param prefs app shared pref.
     * @return collection of all the words considered to be spam if seen in a message.
     */
    public static Collection<String> getBlockedWords(SharedPreferences prefs, boolean extreme) {

        final String key = extreme ? QKPreference.K_BLOCKED_WORD_EXTREME
                                   : QKPreference.K_BLOCKED_WORD;

        return prefs.getStringSet(key, Collections.emptySet());
    }

    /**
     * Returns the first seen spam word in the {@code text}, null if none was found.
     *
     * @param prefs app shared pref.
     * @param value body of the the message to check.
     * @return the first seen spam word in text or null of none was found.
     */
    private static String getBlockedWordOf(SharedPreferences prefs, String value) {

        value = preProcessFa(value.replaceAll("\\s", " "));

        // NO regex used, utf support for \w, \s, and \b character classes is very clunky.
        final List<String> split = Arrays.asList(value.split(" "));
        for (final String each : getBlockedWords(prefs, false))
            if (split.contains(each))
                return each;

        for (final String each : getBlockedWords(prefs, true))
            if (value.contains(each))
                return each;

        return null;
    }

    /**
     * PreProcess for persian language.
     */
    private static String preProcessFa(String value) {

        final String[] from = {
                "ي", "ك", "‍", "دِ", "بِ", "زِ", "ذِ", "ِشِ", "ِسِ", "‌", "ى",
                "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠", // arabic numbers
                "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰", // persian numbers
        };
        final String[] to = {
                "ی", "ک", "", "د", "ب", "ز", "ذ", "ش", "س", "", "ی",
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        };

        for (int i = 0; i < from.length; i++)
            value = value.replace(from[i], to[i]);

        return value;
    }

    // -------------------------- BLOCK BY PATTERN

    /**
     * Add a pattern to list of spam patterns.
     *
     * @param prefs app shared pref.
     * @return true if the value did not exist in the collection before, and hence the collection was modified.
     * @see #getBlockedPatterns(SharedPreferences)
     */
    public static boolean blockPattern(SharedPreferences prefs, final String value) {

        return compilePattern(value) != null &&
                modifyCollection(prefs, value, QKPreference.K_BLOCKED_PATTERN, true);

    }

    /**
     * Remove a value from the list of spam patterns.
     *
     * @param prefs app shared pref.
     * @param value the value to remove.
     * @return true if the value actually existed in the collection, and hence the collection was modified.
     * @see #getBlockedPatterns(SharedPreferences)
     */
    public static boolean unblockPattern(SharedPreferences prefs, final String value) {

        return modifyCollection(prefs, value,
                                QKPreference.K_BLOCKED_PATTERN,
                                false);
    }

    /**
     * Collection of all patterns considered to be spam. If a message matches
     * against any of these patterns, then the message is spam and should be
     * blocked.
     *
     * @param prefs app shared pref
     * @return Collection of all patterns considered to be spam.
     */
    public static Collection<String> getBlockedPatterns(SharedPreferences prefs) {

        final String key = QKPreference.K_BLOCKED_PATTERN;
        return prefs.getStringSet(key, Collections.emptySet());
    }

    /**
     * Returns the first spam pattern the {@code value} matches against, null if none
     * was found.
     *
     * @param prefs app shared pref.
     * @param value body of the the message to check.
     * @see #getBlockedPatterns(SharedPreferences)
     */
    private static String getBlockedPatternOf(SharedPreferences prefs, final String value) {

        Pattern regex;
        for (final String each : getBlockedPatterns(prefs)) {
            regex = compilePattern(each);
            if (regex != null) {
                if (regex.matcher(value).matches())
                    return each;
            }
        }

        return null;
    }

    /**
     * Try to compile a regex pattern but do not raise any error for invalid patterns,
     * return null instead.
     */
    public static Pattern compilePattern(final String pattern) {

        if (pattern == null || pattern.isEmpty()) {
            Log.e("QKSMS::Blocker", "empty pattern");
            return null;
        }

        try {
            return Pattern.compile(pattern.trim());
        }
        catch (final Exception e) {
            Log.e("QKSMS::Blocker", "invalid pattern: " + pattern);
            return null;
        }
    }

    // -------------------- BLOCK BY NUMBER PREFIX

    public static boolean blockNumberPrefix(SharedPreferences prefs, final String value) {

        return modifyCollection(prefs, value,
                                QKPreference.K_BLOCKED_NUMBER_PREFIX,
                                true);
    }

    public static boolean unblockNumberPrefix(SharedPreferences prefs, final String value) {

        return modifyCollection(prefs, value,
                                QKPreference.K_BLOCKED_NUMBER_PREFIX,
                                false);
    }

    /**
     * Returns collection of all the number prefixes considered to be spam. If a
     * messages arrives from a number, and that numbers prefix is in this list,
     * that message is considered to be spam and should be blocked.
     *
     * @param prefs app shared pref.
     * @return collection of all the number prefixes considered to be spam.
     */
    public static Collection<String> getBlockedNumberPrefixes(SharedPreferences prefs) {

        final String key = QKPreference.K_BLOCKED_NUMBER_PREFIX;
        return prefs.getStringSet(key, Collections.emptySet());
    }

    /**
     * Returns the first number prefix found, null if none was found.
     *
     * @param prefs app shared pref.
     * @param value body of the the message to check.
     * @see #getBlockedPatterns(SharedPreferences)
     */
    private static String getBlockedNumberPrefixOf(SharedPreferences prefs, final String value) {

        for (final String each : getBlockedNumberPrefixes(prefs))
            if (value.startsWith(each))
                return each;

        return null;
    }

}
