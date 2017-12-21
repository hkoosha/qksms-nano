package com.moez.QKSMS.data;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Presence;
import android.provider.ContactsContract.Profile;
import android.text.TextUtils;
import android.util.Log;

import com.moez.QKSMS.R;
import com.moez.QKSMS.SqliteWrapper;
import com.moez.QKSMS.common.PhoneNumberUtils;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.sms.SmsHelper;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;


@SuppressLint({"DefaultLocale", "LogTagMismatch"})
public class Contact {

    private static final String TAG = "Contact";

    private static final int CONTACT_METHOD_TYPE_PHONE = 1;
    private static final int CONTACT_METHOD_TYPE_EMAIL = 2;
    private static final int CONTACT_METHOD_TYPE_SELF = 3;       // the "Me" or profile contact
    private static final int CONTACT_METHOD_ID_UNKNOWN = -1;
    private static ContactsCache sContactCache;
    private static final String SELF_ITEM_KEY = "Self_Item_Key";

    @SuppressWarnings("unused")
    private static final ContentObserver sPresenceObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfUpdate) {
            if (Log.isLoggable("Mms:app", Log.VERBOSE)) {
                Log.d(TAG, "presence changed, invalidate cache");
            }
            if (Log.isLoggable("Mms:app", Log.VERBOSE)) {
                Log.d(TAG, "invalidateCache");
            }

            // While invalidating our local Cache doesn't remove the contacts, it will mark them
            // stale so the next time we're asked for a particular contact, we'll return that
            // stale contact and at the same time, fire off an asyncUpdateContact to update
            // that contact's info in the background. UI elements using the contact typically
            // call addListener() so they immediately get notified when the contact has been
            // updated with the latest info. They redraw themselves when we call the
            // listener's onUpdate().
            sContactCache.invalidate();
        }
    };

    private final static HashSet<UpdateListener> mListeners = new HashSet<>();

    private long mContactMethodId;   // Id in phone or email Uri returned by provider of current
    // Contact, -1 is invalid. e.g. contact method id is 20 when
    // current contact has phone content://.../phones/20.
    private int mContactMethodType;
    private String mNumber;
    private String mNumberE164;
    private String mName;
    private String mNameAndNumber;   // for display, e.g. Fred Flintstone <670-782-1123>

    private String mLabel;
    private long mPersonId;
    private int mPresenceResId;      // TODO: make this a state instead of a res ID
    private String mPresenceText;
    private boolean mIsStale;
    private boolean mQueryPending;
    private boolean mIsMe;          // true if this contact is me!
    private boolean mSendToVoicemail;   // true if this contact should not put up notification

    public interface UpdateListener {
        void onUpdate(Contact updated);
    }

    /**
     * Make a basic contact object with a phone number.
     */
    private Contact(String number) {
        init(number);
    }

    private Contact(boolean isMe) {
        init(SELF_ITEM_KEY);
        mIsMe = isMe;
    }

    private void init(String number) {
        mContactMethodId = CONTACT_METHOD_ID_UNKNOWN;
        mName = "";
        setNumber(number);
        mLabel = "";
        mPersonId = 0;
        mPresenceResId = 0;
        mIsStale = true;
        mSendToVoicemail = false;
    }

    @Override
    public String toString() {
        return String.format(
                "{ number=%s, name=%s, nameAndNumber=%s, label=%s, person_id=%d, hash=%d method_id=%d }",
                (mNumber != null ? mNumber : "null"),
                (mName != null ? mName : "null"),
                (mNameAndNumber != null ? mNameAndNumber : "null"),
                (mLabel != null ? mLabel : "null"),
                mPersonId,
                hashCode(),
                mContactMethodId);
    }

    public static Contact get(String number, boolean canBlock) {
        return sContactCache.get(number, canBlock);
    }

    public void removeFromCache() {
        sContactCache.remove(this);
    }

    public synchronized String getNumber() {
        return mNumber;
    }

    public synchronized void setNumber(String number) {
        if (!SmsHelper.isEmailAddress(number)) {
            mNumber = PhoneNumberUtils.formatNumber(number, mNumberE164, Util.countryIso());
        }
        else {
            mNumber = number;
        }
        notSynchronizedUpdateNameAndNumber();
    }

    public synchronized String getName() {
        return TextUtils.isEmpty(mName) ? mNumber : mName;
    }

    public synchronized boolean isNamed() {
        return !TextUtils.isEmpty(mName);
    }

    public synchronized String getNameAndNumber() {
        return mNameAndNumber;
    }

    private void notSynchronizedUpdateNameAndNumber() {
        String result;
        // Format like this: Mike Cleron <(650) 555-1234>
        //                   Erick Tseng <(650) 555-1212>
        //                   Tutankhamun <tutank1341@gmail.com>
        //                   (408) 555-1289
        String formattedNumber = mNumber;
        if (!SmsHelper.isEmailAddress(mNumber)) {
            formattedNumber = PhoneNumberUtils.formatNumber(mNumber,
                                                            mNumberE164,
                                                            Util.countryIso());
        }

        if (!TextUtils.isEmpty(mName) && !mName.equals(mNumber)) {
            result = mName + " <" + formattedNumber + ">";
        }
        else {
            result = formattedNumber;
        }
        mNameAndNumber = result;
    }

    public synchronized Uri getUri() {
        return ContentUris.withAppendedId(Contacts.CONTENT_URI, mPersonId);
    }

    public synchronized boolean existsInDatabase() {
        return (mPersonId > 0);
    }

    public static void addListener(UpdateListener l) {
        synchronized (mListeners) {
            mListeners.add(l);
        }
    }

    public static void removeListener(UpdateListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
        }
    }

    public static void init(final Context context) {
        if (sContactCache != null) { // Stop previous Runnable
            sContactCache.mTaskQueue.mWorkerThread.interrupt();
        }
        sContactCache = new ContactsCache(context);

        RecipientIdCache.init(context);
    }

    private static final class ContactsCache {
        private final TaskStack mTaskQueue = new TaskStack();

        /**
         * For a specified phone number, 2 rows were inserted into phone_lookup
         * table. One is the phone number's E164 representation, and another is
         * one's normalized format. If the phone number's normalized format in
         * the lookup table is the suffix of the given number's one, it is
         * treated as matched CallerId. E164 format number must fully equal.
         * <p>
         * For example: Both 650-123-4567 and +1 (650) 123-4567 will match the
         * normalized number 6501234567 in the phone lookup.
         * <p>
         * The min_match is used to narrow down the candidates for the final
         * comparison.
         */
        // query params for caller id lookup
        private static final String CALLER_ID_SELECTION = " Data._ID IN "
                + " (SELECT DISTINCT lookup.data_id "
                + " FROM "
                + " (SELECT data_id, normalized_number, length(normalized_number) as len "
                + " FROM phone_lookup "
                + " WHERE min_match = ?) AS lookup "
                + " WHERE lookup.normalized_number = ? OR"
                + " (lookup.len <= ? AND "
                + " substr(?, ? - lookup.len + 1) = lookup.normalized_number))";

        // query params for caller id lookup without E164 number as param
        private static final String CALLER_ID_SELECTION_WITHOUT_E164 = " Data._ID IN "
                + " (SELECT DISTINCT lookup.data_id "
                + " FROM "
                + " (SELECT data_id, normalized_number, length(normalized_number) as len "
                + " FROM phone_lookup "
                + " WHERE min_match = ?) AS lookup "
                + " WHERE "
                + " (lookup.len <= ? AND "
                + " substr(?, ? - lookup.len + 1) = lookup.normalized_number))";

        // Utilizing private API
        private static final Uri PHONES_WITH_PRESENCE_URI = Data.CONTENT_URI;

        private static final String[] CALLER_ID_PROJECTION = new String[]{
                Phone._ID,                      // 0
                Phone.NUMBER,                   // 1
                Phone.LABEL,                    // 2
                Phone.DISPLAY_NAME,             // 3
                Phone.CONTACT_ID,               // 4
                Phone.CONTACT_PRESENCE,         // 5
                Phone.CONTACT_STATUS,           // 6
                Phone.NORMALIZED_NUMBER,        // 7
                Contacts.SEND_TO_VOICEMAIL      // 8
        };

        private static final int PHONE_ID_COLUMN = 0;
        private static final int PHONE_LABEL_COLUMN = 2;
        private static final int CONTACT_NAME_COLUMN = 3;
        private static final int CONTACT_ID_COLUMN = 4;
        private static final int CONTACT_PRESENCE_COLUMN = 5;
        private static final int CONTACT_STATUS_COLUMN = 6;
        private static final int PHONE_NORMALIZED_NUMBER = 7;
        private static final int SEND_TO_VOICEMAIL = 8;

        private static final String[] SELF_PROJECTION = new String[]{
                Phone._ID,                      // 0
                Phone.DISPLAY_NAME,             // 1
        };

        private static final int SELF_NAME_COLUMN = 1;

        // query params for contact lookup by email
        private static final Uri EMAIL_WITH_PRESENCE_URI = Data.CONTENT_URI;

        private static final String EMAIL_SELECTION = "UPPER(" + Email.DATA + ")=UPPER(?) AND "
                + Data.MIMETYPE + "='" + Email.CONTENT_ITEM_TYPE + "'";

        private static final String[] EMAIL_PROJECTION = new String[]{
                Email._ID,                    // 0
                Email.DISPLAY_NAME,           // 1
                Email.CONTACT_PRESENCE,       // 2
                Email.CONTACT_ID,             // 3
                Phone.DISPLAY_NAME,           // 4
                Contacts.SEND_TO_VOICEMAIL    // 5
        };
        private static final int EMAIL_ID_COLUMN = 0;
        private static final int EMAIL_NAME_COLUMN = 1;
        private static final int EMAIL_STATUS_COLUMN = 2;
        private static final int EMAIL_CONTACT_ID_COLUMN = 3;
        private static final int EMAIL_CONTACT_NAME_COLUMN = 4;
        private static final int EMAIL_SEND_TO_VOICEMAIL_COLUMN = 5;

        private final Context mContext;

        private final HashMap<String, ArrayList<Contact>> mContactsHash = new HashMap<>();

        private ContactsCache(Context context) {
            mContext = context;
        }

        private static class TaskStack {
            Thread mWorkerThread;
            private final ArrayList<Runnable> mThingsToLoad;

            TaskStack() {
                mThingsToLoad = new ArrayList<>();
                mWorkerThread = new Thread(() -> {
                    while (true) {
                        Runnable r = null;
                        synchronized (mThingsToLoad) {
                            if (mThingsToLoad.isEmpty()) {
                                try {
                                    mThingsToLoad.wait();
                                }
                                catch (InterruptedException ex) {
                                    break;  // Exception sent by Contact.init() to stop Runnable
                                }
                            }
                            if (!mThingsToLoad.isEmpty()) {
                                r = mThingsToLoad.remove(0);
                            }
                        }
                        if (r != null) {
                            r.run();
                        }
                    }
                }, "Contact.ContactsCache.TaskStack worker thread");
                mWorkerThread.setPriority(Thread.MIN_PRIORITY);
                mWorkerThread.start();
            }

            void push(Runnable r) {
                synchronized (mThingsToLoad) {
                    mThingsToLoad.add(r);
                    mThingsToLoad.notify();
                }
            }
        }

        void pushTask(Runnable r) {
            mTaskQueue.push(r);
        }

        private Contact get(String number, boolean canBlock) {
            if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                Thread current = Thread.currentThread();
                StackTraceElement[] stack = current.getStackTrace();

                StringBuilder sb = new StringBuilder();
                sb.append("[");
                sb.append(current.getId());
                sb.append("] ");
                sb.append(String.format("get(%s, %s, %s)", number, false, canBlock));

                sb.append(" <- ");
                int stop = stack.length > 7 ? 7 : stack.length;
                for (int i = 3; i < stop; i++) {
                    String methodName = stack[i].getMethodName();
                    sb.append(methodName);
                    if ((i + 1) != stop) {
                        sb.append(" <- ");
                    }
                }

                Log.d(TAG, sb.toString());
            }

            if (TextUtils.isEmpty(number)) {
                number = "";        // In some places (such as Korea), it's possible to receive
                // a message without the sender's address. In this case,
                // all such anonymous messages will get added to the same
                // thread.
            }

            // Always return a Contact object, if if we don't have an actual contact
            // in the contacts db.
            Contact contact = internalGet(number, false);
            Runnable r = null;

            synchronized (contact) {
                // If there's a query pending and we're willing to block then
                // wait here until the query completes.
                while (canBlock && contact.mQueryPending) {
                    try {
                        contact.wait();
                    }
                    catch (InterruptedException ex) {
                        // try again by virtue of the loop unless mQueryPending is false
                    }
                }

                // If we're stale and we haven't already kicked off a query then kick
                // it off here.
                if (contact.mIsStale && !contact.mQueryPending) {
                    contact.mIsStale = false;

                    if (Log.isLoggable("Mms:app", Log.VERBOSE)) {
                        Log.d(TAG,
                              "async update for " + contact.toString() + " canBlock: " + canBlock +
                                      " isStale: " + contact.mIsStale);
                    }

                    final Contact c = contact;
                    r = () -> updateContact(c);

                    // set this to true while we have the lock on contact since we will
                    // either run the query directly (canBlock case) or push the query
                    // onto the queue.  In either case the mQueryPending will get set
                    // to false via updateContact.
                    contact.mQueryPending = true;
                }
            }
            // do this outside of the synchronized so we don't hold up any
            // subsequent calls to "get" on other threads
            if (r != null) {
                if (canBlock) {
                    r.run();
                }
                else {
                    pushTask(r);
                }
            }
            return contact;
        }

        private boolean contactChanged(Contact orig, Contact newContactData) {
            // The phone number should never change, so don't bother checking.
            // TODO: Maybe update it if it has gotten longer, i.e. 650-234-5678 -> +16502345678?

            // Do the quick check first.
            if (orig.mContactMethodType != newContactData.mContactMethodType) {
                return true;
            }

            if (orig.mContactMethodId != newContactData.mContactMethodId) {
                return true;
            }

            if (orig.mPersonId != newContactData.mPersonId) {
                if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                    Log.d(TAG, "person id changed");
                }
                return true;
            }

            if (orig.mPresenceResId != newContactData.mPresenceResId) {
                if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                    Log.d(TAG, "presence changed");
                }
                return true;
            }

            if (orig.mSendToVoicemail != newContactData.mSendToVoicemail) {
                return true;
            }

            String oldName = (orig.mName != null ? orig.mName : "");
            String newName = (newContactData.mName != null ? newContactData.mName : "");
            if (!oldName.equals(newName)) {
                if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                    Log.d(TAG, String.format("name changed: %s -> %s", oldName, newName));
                }
                return true;
            }

            String oldLabel = (orig.mLabel != null ? orig.mLabel : "");
            String newLabel = (newContactData.mLabel != null ? newContactData.mLabel : "");
            if (!oldLabel.equals(newLabel)) {
                if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                    Log.d(TAG, String.format("label changed: %s -> %s", oldLabel, newLabel));
                }
                return true;
            }

            return false;
        }

        private void updateContact(final Contact c) {
            if (c == null) {
                return;
            }

            Contact entry = getContactInfo(c);
            synchronized (c) {
                if (contactChanged(c, entry)) {
                    if (Log.isLoggable("Mms:app", Log.VERBOSE)) {
                        Log.d(TAG, "updateContact: contact changed for " + entry.mName);
                    }

                    c.mNumber = entry.mNumber;
                    c.mLabel = entry.mLabel;
                    c.mPersonId = entry.mPersonId;
                    c.mPresenceResId = entry.mPresenceResId;
                    c.mPresenceText = entry.mPresenceText;
                    c.mContactMethodId = entry.mContactMethodId;
                    c.mContactMethodType = entry.mContactMethodType;
                    c.mNumberE164 = entry.mNumberE164;
                    c.mName = entry.mName;
                    c.mSendToVoicemail = entry.mSendToVoicemail;

                    c.notSynchronizedUpdateNameAndNumber();

                    // We saw a bug where we were updating an empty contact. That would trigger
                    // l.onUpdate() below, which would call ComposeMessageActivity.onUpdate,
                    // which would call the adapter's notifyDataSetChanged, which would throw
                    // away the message items and rebuild, eventually calling updateContact()
                    // again -- all in a vicious and unending loop. Break the cycle and don't
                    // notify if the number (the most important piece of information) is empty.
                    if (!TextUtils.isEmpty(c.mNumber)) {
                        // clone the list of listeners in case the onUpdate call turns around and
                        // modifies the list of listeners
                        // access to mListeners is synchronized on ContactsCache
                        HashSet<UpdateListener> iterator;
                        synchronized (mListeners) {
                            //noinspection unchecked
                            iterator = (HashSet<UpdateListener>) Contact.mListeners.clone();
                        }
                        for (UpdateListener l : iterator) {
                            if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                                Log.d(TAG, "updating " + l);
                            }
                            l.onUpdate(c);
                        }
                    }
                }
                synchronized (c) {
                    c.mQueryPending = false;
                    c.notifyAll();
                }
            }
        }

        /**
         * Returns the caller info in Contact.
         */
        private Contact getContactInfo(Contact c) {
            if (c.mIsMe) {
                return getContactInfoForSelf();
            }
            else if (SmsHelper.isEmailAddress(c.mNumber)) {
                return getContactInfoForEmailAddress(c.mNumber);
            }
            else if (isAlphaNumber(c.mNumber)) {
                // first try to look it up in the email field
                Contact contact = getContactInfoForEmailAddress(c.mNumber);
                if (contact.existsInDatabase()) {
                    return contact;
                }
                // then look it up in the phone field
                return getContactInfoForPhoneNumber(c.mNumber);
            }
            else {
                // it's a real phone number, so strip out non-digits and look it up
                final String strippedNumber = android.telephony.PhoneNumberUtils.stripSeparators(c.mNumber);
                return getContactInfoForPhoneNumber(strippedNumber);
            }
        }

        // Some received sms's have addresses such as "OakfieldCPS" or "T-Mobile". This
        // function will attempt to identify these and return true. If the number contains
        // 3 or more digits, such as "jello123", this function will return false.
        // Some countries have 3 digits shortcodes and we have to identify them as numbers.
        //    http://en.wikipedia.org/wiki/Short_code
        // Examples of input/output for this function:
        //    "Jello123" -> false  [3 digits, it is considered to be the phone number "123"]
        //    "T-Mobile" -> true   [it is considered to be the address "T-Mobile"]
        //    "Mobile1"  -> true   [1 digit, it is considered to be the address "Mobile1"]
        //    "Dogs77"   -> true   [2 digits, it is considered to be the address "Dogs77"]
        //    "****1"    -> true   [1 digits, it is considered to be the address "****1"]
        //    "#4#5#6#"  -> true   [it is considered to be the address "#4#5#6#"]
        //    "AB12"     -> true   [2 digits, it is considered to be the address "AB12"]
        //    "12"       -> true   [2 digits, it is considered to be the address "12"]
        private boolean isAlphaNumber(String number) {
            // TODO: PhoneNumberUtils.isWellFormedSmsAddress() only check if the number is a valid
            // GSM SMS address. If the address contains a dialable char, it considers it a well
            // formed SMS addr. CDMA doesn't work that way and has a different parser for SMS
            // address (see CdmaSmsAddress.parse(String address)). We should definitely fix this!!!
            if (!android.telephony.PhoneNumberUtils.isWellFormedSmsAddress(number)) {
                // The example "T-Mobile" will exit here because there are no numbers.
                return true;        // we're not an sms address, consider it an alpha number
            }

            number = android.telephony.PhoneNumberUtils.extractNetworkPortion(number);
            if (TextUtils.isEmpty(number)) {
                return true;    // there are no digits whatsoever in the number
            }
            // At this point, anything like "Mobile1" or "Dogs77" will be stripped down to
            // "1" and "77". "#4#5#6#" remains as "#4#5#6#" at this point.
            return number.length() < 3;
        }

        /**
         * Queries the caller id info with the phone number.
         *
         * @return a Contact containing the caller id info corresponding to the number.
         */
        private Contact getContactInfoForPhoneNumber(String number) {
            Contact entry = new Contact(number);
            entry.mContactMethodType = CONTACT_METHOD_TYPE_PHONE;

            if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                Log.d(TAG, "queryContactInfoByNumber: number=" + number);
            }

            String normalizedNumber = PhoneNumberUtils.normalizeNumber(number);
            String minMatch = android.telephony.PhoneNumberUtils.toCallerIDMinMatch(normalizedNumber);
            if (!TextUtils.isEmpty(normalizedNumber) && !TextUtils.isEmpty(minMatch)) {
                String numberLen = String.valueOf(normalizedNumber.length());
                String numberE164 = PhoneNumberUtils.formatNumberToE164(
                        number, Util.countryIso());
                String selection;
                String[] args;
                if (TextUtils.isEmpty(numberE164)) {
                    selection = CALLER_ID_SELECTION_WITHOUT_E164;
                    args = new String[]{minMatch, numberLen, normalizedNumber, numberLen};
                }
                else {
                    selection = CALLER_ID_SELECTION;
                    args = new String[]{
                            minMatch, numberE164, numberLen, normalizedNumber, numberLen};
                }

                Cursor cursor = mContext.getContentResolver().query(
                        PHONES_WITH_PRESENCE_URI, CALLER_ID_PROJECTION, selection, args, null);
                if (cursor == null) {
                    Log.w(TAG, "queryContactInfoByNumber(" + number + ") returned NULL cursor!"
                            + " contact uri used " + PHONES_WITH_PRESENCE_URI);
                    return entry;
                }

                try {
                    if (cursor.moveToFirst()) {
                        fillPhoneTypeContact(entry, cursor);
                    }
                }
                finally {
                    cursor.close();
                }
            }
            return entry;
        }

        /**
         * @return a Contact containing the info for the profile.
         */
        private Contact getContactInfoForSelf() {
            Contact entry = new Contact(true);
            entry.mContactMethodType = CONTACT_METHOD_TYPE_SELF;

            if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                Log.d(TAG, "getContactInfoForSelf");
            }
            Cursor cursor = mContext.getContentResolver().query(
                    Profile.CONTENT_URI, SELF_PROJECTION, null, null, null);
            if (cursor == null) {
                Log.w(TAG, "getContactInfoForSelf() returned NULL cursor!"
                        + " contact uri used " + Profile.CONTENT_URI);
                return entry;
            }

            try {
                if (cursor.moveToFirst()) {
                    fillSelfContact(entry, cursor);
                }
            }
            finally {
                cursor.close();
            }
            return entry;
        }

        private void fillPhoneTypeContact(final Contact contact, final Cursor cursor) {
            synchronized (contact) {
                contact.mContactMethodType = CONTACT_METHOD_TYPE_PHONE;
                contact.mContactMethodId = cursor.getLong(PHONE_ID_COLUMN);
                contact.mLabel = cursor.getString(PHONE_LABEL_COLUMN);
                contact.mName = cursor.getString(CONTACT_NAME_COLUMN);
                contact.mPersonId = cursor.getLong(CONTACT_ID_COLUMN);
                contact.mPresenceResId = getPresenceIconResourceId(
                        cursor.getInt(CONTACT_PRESENCE_COLUMN));
                contact.mPresenceText = cursor.getString(CONTACT_STATUS_COLUMN);
                contact.mNumberE164 = cursor.getString(PHONE_NORMALIZED_NUMBER);
                contact.mSendToVoicemail = cursor.getInt(SEND_TO_VOICEMAIL) == 1;
                if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                    Log.d(TAG, "fillPhoneTypeContact: name=" + contact.mName + ", number="
                            + contact.mNumber + ", presence=" + contact.mPresenceResId
                            + " SendToVoicemail: " + contact.mSendToVoicemail);
                }
            }
        }

        private void fillSelfContact(final Contact contact, final Cursor cursor) {
            synchronized (contact) {
                contact.mName = cursor.getString(SELF_NAME_COLUMN);
                if (TextUtils.isEmpty(contact.mName)) {
                    contact.mName = mContext.getString(R.string.messagelist_sender_self);
                }
                if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                    Log.d(TAG, "fillSelfContact: name=" + contact.mName + ", number="
                            + contact.mNumber);
                }
            }
        }

        private int getPresenceIconResourceId(int presence) {
            // TODO: must fix for SDK
            if (presence != Presence.OFFLINE) {
                return Presence.getPresenceIconResourceId(presence);
            }

            return 0;
        }

        /**
         * Query the contact email table to get the name of an email address.
         */
        private Contact getContactInfoForEmailAddress(String email) {
            Contact entry = new Contact(email);
            entry.mContactMethodType = CONTACT_METHOD_TYPE_EMAIL;

            Cursor cursor = SqliteWrapper.query(mContext, mContext.getContentResolver(),
                                                EMAIL_WITH_PRESENCE_URI,
                                                EMAIL_PROJECTION,
                                                EMAIL_SELECTION,
                                                new String[]{email},
                                                null);

            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        synchronized (entry) {
                            entry.mContactMethodId = cursor.getLong(EMAIL_ID_COLUMN);
                            entry.mPresenceResId = getPresenceIconResourceId(
                                    cursor.getInt(EMAIL_STATUS_COLUMN));
                            entry.mPersonId = cursor.getLong(EMAIL_CONTACT_ID_COLUMN);
                            entry.mSendToVoicemail =
                                    cursor.getInt(EMAIL_SEND_TO_VOICEMAIL_COLUMN) == 1;

                            String name = cursor.getString(EMAIL_NAME_COLUMN);
                            if (TextUtils.isEmpty(name)) {
                                name = cursor.getString(EMAIL_CONTACT_NAME_COLUMN);
                            }
                            if (!TextUtils.isEmpty(name)) {
                                entry.mName = name;
                                if (Log.isLoggable("Mms:contact", Log.DEBUG)) {
                                    Log.d(TAG,
                                          "getContactInfoForEmailAddress: name=" + entry.mName +
                                                  ", email=" + email + ", presence=" +
                                                  entry.mPresenceResId);
                                }
                                break;
                            }
                        }
                    }
                }
                finally {
                    cursor.close();
                }
            }
            return entry;
        }

        // Invert and truncate to five characters the phoneNumber so that we
        // can use it as the key in a hashtable.  We keep a mapping of this
        // key to a list of all contacts which have the same key.
        private String key(String phoneNumber, CharBuffer keyBuffer) {
            keyBuffer.clear();
            keyBuffer.mark();

            int position = phoneNumber.length();
            int resultCount = 0;
            while (--position >= 0) {
                char c = phoneNumber.charAt(position);
                if (Character.isDigit(c)) {
                    keyBuffer.put(c);
                    if (++resultCount == STATIC_KEY_BUFFER_MAXIMUM_LENGTH) {
                        break;
                    }
                }
            }
            keyBuffer.reset();
            if (resultCount > 0) {
                return keyBuffer.toString();
            }
            else {
                // there were no usable digits in the input phoneNumber
                return phoneNumber;
            }
        }

        // Reuse this so we don't have to allocate each time we go through this
        // "get" function.
        static final int STATIC_KEY_BUFFER_MAXIMUM_LENGTH = 5;
        static CharBuffer sStaticKeyBuffer = CharBuffer.allocate(STATIC_KEY_BUFFER_MAXIMUM_LENGTH);

        private Contact internalGet(String numberOrEmail, boolean isMe) {
            synchronized (ContactsCache.this) {
                // See if we can find "number" in the hashtable.
                // If so, just return the result.

                final boolean isNotRegularPhoneNumber =
                        isMe || SmsHelper.isEmailAddress(numberOrEmail);
                final String key = isNotRegularPhoneNumber ?
                                   numberOrEmail : key(numberOrEmail, sStaticKeyBuffer);

                ArrayList<Contact> candidates = mContactsHash.get(key);
                if (candidates != null) {
                    int length = candidates.size();
                    for (int i = 0; i < length; i++) {
                        Contact c = candidates.get(i);
                        if (isNotRegularPhoneNumber) {
                            if (numberOrEmail.equals(c.mNumber)) {
                                return c;
                            }
                        }
                        else {
                            if (android.telephony.PhoneNumberUtils.compare(numberOrEmail,
                                                                           c.mNumber)) {
                                return c;
                            }
                        }
                    }
                }
                else {
                    candidates = new ArrayList<>();
                    // call toString() since it may be the static CharBuffer
                    mContactsHash.put(key, candidates);
                }
                Contact c = isMe ?
                            new Contact(true) :
                            new Contact(numberOrEmail);
                candidates.add(c);
                return c;
            }
        }

        void invalidate() {
            // Don't remove the contacts. Just mark them stale so we'll update their
            // info, particularly their presence.
            synchronized (ContactsCache.this) {
                for (ArrayList<Contact> alc : mContactsHash.values()) {
                    for (Contact c : alc) {
                        synchronized (c) {
                            c.mIsStale = true;
                        }
                    }
                }
            }
        }

        // Remove a contact from the ContactsCache based on the number or email address
        private void remove(Contact contact) {
            synchronized (ContactsCache.this) {
                String number = contact.getNumber();

                final boolean isNotRegularPhoneNumber =
                        contact.mIsMe || SmsHelper.isEmailAddress(number);
                final String key = isNotRegularPhoneNumber ?
                                   number : key(number, sStaticKeyBuffer);
                ArrayList<Contact> candidates = mContactsHash.get(key);
                if (candidates != null) {
                    int length = candidates.size();
                    for (int i = 0; i < length; i++) {
                        Contact c = candidates.get(i);
                        if (isNotRegularPhoneNumber) {
                            if (number.equals(c.mNumber)) {
                                candidates.remove(i);
                                break;
                            }
                        }
                        else {
                            if (android.telephony.PhoneNumberUtils.compare(number, c.mNumber)) {
                                candidates.remove(i);
                                break;
                            }
                        }
                    }
                    if (candidates.isEmpty()) {
                        mContactsHash.remove(key);
                    }
                }
            }
        }
    }

}
