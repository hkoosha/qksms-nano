package com.moez.QKSMS.data;

import android.annotation.SuppressLint;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony.Threads;
import android.text.TextUtils;
import android.util.Log;

import com.moez.QKSMS.NotificationMgr;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.DraftCache;
import com.moez.QKSMS.receiver.UnreadBadgeService;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * An interface for finding information about conversations and/or creating new ones.
 */
@SuppressLint("LogTagMismatch")
public class Conversation {
    private static final String TAG = "Mms/conv";
    private static final boolean DEBUG = false;

    private static final Uri sAllThreadsUri =
            Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();

    public static final String[] ALL_THREADS_PROJECTION = {
            Threads._ID, Threads.DATE, Threads.MESSAGE_COUNT, Threads.RECIPIENT_IDS,
            Threads.SNIPPET, Threads.SNIPPET_CHARSET, Threads.READ, Threads.ERROR,
            Threads.HAS_ATTACHMENT
    };

    private static final String[] UNREAD_PROJECTION = {
            Threads._ID,
            Threads.READ
    };

    private static final String UNREAD_SELECTION = "(read=0 OR seen=0)";
    public static final String FAILED_SELECTION = "error != 0";

    public static final int ID = 0;
    private static final int DATE = 1;
    private static final int RECIPIENT_IDS = 3;
    private static final int READ = 6;
    private static final int ERROR = 7;


    private final Context mContext;

    // The thread ID of this conversation.  Can be zero in the case of a
    // new conversation where the recipient set is changing as the user
    // types and we have not hit the database yet to create a thread.
    private long mThreadId;

    private ContactList mRecipients;    // The current set of recipients.
    private long mDate;                 // The last update time.
    private String mSnippet;            // Text of the most recent message.
    private boolean mHasUnreadMessages; // True if there are unread messages.
    private boolean mHasError;          // True if any message is in an error state.

    private static ContentValues sReadContentValues;
    private static boolean sLoadingThreads;
    private static boolean sDeletingThreads;
    private static final Object sDeletingThreadsLock = new Object();
    private boolean mMarkAsReadBlocked;
    private boolean mMarkAsReadWaiting;

    private Conversation(Context context, long threadId, boolean allowQuery) {
        if (DEBUG) {
            Log.v(TAG, "Conversation constructor threadId: " + threadId);
        }
        mContext = context;
        if (!loadFromThreadId(threadId, allowQuery)) {
            mRecipients = new ContactList();
            mThreadId = 0;
        }
    }

    private Conversation(Context context, Cursor cursor, boolean allowQuery) {
        if (DEBUG) {
            Log.v(TAG, "Conversation constructor cursor, allowQuery: " + allowQuery);
        }
        mContext = context;
        fillFromCursor(context, this, cursor, allowQuery);
    }

    /**
     * Find the conversation matching the provided thread ID.
     */
    public static Conversation get(Context context, long threadId, boolean allowQuery) {
        if (DEBUG) {
            Log.v(TAG, "Conversation get by threadId: " + threadId);
        }
        Conversation conv = Cache.get(threadId);
        if (conv != null)
            return conv;

        conv = new Conversation(context, threadId, allowQuery);
        try {
            Cache.put(conv);
        }
        catch (IllegalStateException e) {
            Log.e(TAG, "Tried to add duplicate Conversation to Cache (from threadId): " + conv);
            if (!Cache.replace(conv)) {
                Log.e(TAG, "get by threadId cache.replace failed on " + conv);
            }
        }
        return conv;
    }

    /**
     * Returns a temporary Conversation (not representing one on disk) wrapping
     * the contents of the provided cursor.  The cursor should be the one
     * returned to your AsyncQueryHandler passed in to  #startQueryForAll.
     * The recipient list of this conversation can be empty if the results
     * were not in cache.
     */
    public static Conversation from(Context context, Cursor cursor) {
        // First look in the cache for the Conversation and return that one. That way, all the
        // people that are looking at the cached copy will get updated when fillFromCursor() is
        // called with this cursor.
        long threadId = cursor.getLong(ID);
        if (threadId > 0) {
            Conversation conv = Cache.get(threadId);
            if (conv != null) {
                fillFromCursor(context, conv, cursor, false);   // update the existing conv in-place
                return conv;
            }
        }
        Conversation conv = new Conversation(context, cursor, false);
        try {
            Cache.put(conv);
        }
        catch (IllegalStateException e) {
            Log.e(TAG, "Tried to add duplicate Conversation to Cache (from cursor): " + conv);
            if (!Cache.replace(conv)) {
                Log.e(TAG, "Conversations.from cache.replace failed on " + conv);
            }
        }
        return conv;
    }

    private void buildReadContentValues() {
        if (sReadContentValues == null) {
            sReadContentValues = new ContentValues(2);
            sReadContentValues.put("read", 1);
            sReadContentValues.put("seen", 1);
        }
    }


    /**
     * Marks all messages in this conversation as read and updates
     * relevant notifications.  This method returns immediately;
     * work is dispatched to a background thread. This function should
     * always be called from the UI thread.
     */
    public void markAsRead() {
        if (mMarkAsReadWaiting) {
            // We've already been asked to mark everything as read, but we're blocked.
            return;
        }
        if (mMarkAsReadBlocked) {
            // We're blocked so record the fact that we want to mark the messages as read
            // when we get unblocked.
            mMarkAsReadWaiting = true;
            return;
        }

        markAsReadTask(getUri());
    }

    @SuppressLint("StaticFieldLeak")
    private void markAsReadTask(final Uri threadUri) {
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... none) {
                if (Log.isLoggable("Mms:app", Log.VERBOSE)) {
                    Log.d(TAG, "markAsRead.doInBackground");
                }
                // If we have no Uri to mark (as in the case of a conversation that
                // has not yet made its way to disk), there's nothing to do.
                if (threadUri != null) {
                    buildReadContentValues();

                    // Check the read flag first. It's much faster to do a query than
                    // to do an update. Timing this function show it's about 10x faster to
                    // do the query compared to the update, even when there's nothing to
                    // update.
                    boolean needUpdate = true;

                    Cursor c = mContext.getContentResolver().query(threadUri,
                                                                   UNREAD_PROJECTION,
                                                                   UNREAD_SELECTION,
                                                                   null,
                                                                   null);
                    if (c != null) {
                        try {
                            needUpdate = c.getCount() > 0;
                        }
                        finally {
                            c.close();
                        }
                    }

                    if (needUpdate) {
                        Log.d(TAG, "markAsRead: update read/seen for thread uri: " +
                                threadUri);
                        mContext.getContentResolver().update(threadUri, sReadContentValues,
                                                             UNREAD_SELECTION, null);
                    }
                    setHasUnreadMessages(false);
                }
                // Always update notifications regardless of the read state, which is usually
                // canceling the notification of the thread that was just marked read.
                //MessagingNotification.blockingUpdateAllNotifications(mContext, MessagingNotification.THREAD_NONE);
                NotificationMgr.update(mContext);

                UnreadBadgeService.update(mContext);

                return null;
            }
        }.execute();
    }

    /**
     * Call this with false to prevent marking messages as read. The code calls this so
     * the DB queries in markAsRead don't slow down the main query for messages. Once we've
     * queried for all the messages (see ComposeMessageActivity.onQueryComplete), then we
     * can mark messages as read. Only call this function on the UI thread.
     */
    public void blockMarkAsRead() {
        if (Log.isLoggable("Mms:app", Log.VERBOSE)) {
            Log.d(TAG, "blockMarkAsRead: " + true);
        }

        if (!mMarkAsReadBlocked)
            mMarkAsReadBlocked = true;
    }

    /**
     * Returns a content:// URI referring to this conversation,
     * or null if it does not exist on disk yet.
     */
    public synchronized Uri getUri() {
        if (mThreadId <= 0)
            return null;

        return ContentUris.withAppendedId(Threads.CONTENT_URI, mThreadId);
    }

    /**
     * Return the Uri for all messages in the given thread ID.
     *
     * @deprecated
     */
    public static Uri getUri(long threadId) {
        // TODO: Callers using this should really just have a Conversation
        // and call getUri() on it, but this guarantees no blocking.
        return ContentUris.withAppendedId(Threads.CONTENT_URI, threadId);
    }

    /**
     * Returns the thread ID of this conversation.  Can be zero if
     * ensureThreadId has not been called yet.
     */
    public synchronized long getThreadId() {
        return mThreadId;
    }

    public synchronized void clearThreadId() {
        // remove ourself from the cache
        if (Log.isLoggable("Mms:app", Log.VERBOSE)) {
            Log.d(TAG, "clearThreadId old threadId was: " + mThreadId + " now zero");
        }
        Cache.remove(mThreadId);

        mThreadId = 0;
    }

    /**
     * Returns the recipient set of this conversation.
     */
    public synchronized ContactList getRecipients() {
        return mRecipients;
    }

    /**
     * Sets whether or not this conversation has a draft message.
     */
    public synchronized void setDraftState(boolean hasDraft) {
        if (mThreadId <= 0)
            return;

        DraftCache.getInstance().setDraftState(mThreadId, hasDraft);
    }

    /**
     * Returns the time of the last update to this conversation in milliseconds,
     * on the {@link System#currentTimeMillis} timebase.
     */
    public synchronized long getDate() {
        return mDate;
    }

    /**
     * Returns a snippet of text from the most recent message in the conversation.
     */
    public synchronized String getSnippet() {
        return mSnippet;
    }

    /**
     * Returns true if there are any unread messages in the conversation.
     */
    public boolean hasUnreadMessages() {
        synchronized (this) {
            return mHasUnreadMessages;
        }
    }

    private void setHasUnreadMessages(boolean flag) {
        synchronized (this) {
            mHasUnreadMessages = flag;
        }
    }

    /**
     * Returns true if any messages in the conversation are in an error state.
     */
    public synchronized boolean hasError() {
        return mHasError;
    }

    /*
     * The primary key of a conversation is its recipient set; override
     * equals() and hashCode() to just pass through to the internal
     * recipient sets.
     */
    @Override
    public synchronized boolean equals(Object obj) {
        try {
            Conversation other = (Conversation) obj;
            return (mRecipients.equals(other.mRecipients));
        }
        catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public synchronized int hashCode() {
        return mRecipients.hashCode();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public synchronized String toString() {
        return String.format("[%s] (tid %d)", mRecipients.serialize(), mThreadId);
    }

    /**
     * Remove any obsolete conversations sitting around on disk. Obsolete threads are threads
     * that aren't referenced by any message in the pdu or sms tables.
     */
    public static void asyncDeleteObsoleteThreads(AsyncQueryHandler handler, int token) {
        handler.startDelete(token, null, Threads.OBSOLETE_THREADS_URI, null, null);
    }

    /**
     * Start a delete of the conversation with the specified thread ID.
     *
     * @param handler   An AsyncQueryHandler that will receive onDeleteComplete
     *                  upon completion of the conversation being deleted
     * @param token     The token that will be passed to onDeleteComplete
     * @param threadIds Collection of thread IDs of the conversations to be deleted
     */
    public static void startDelete(ConversationQueryHandler handler, int token, Collection<Long> threadIds) {
        synchronized (sDeletingThreadsLock) {
            if (sDeletingThreads) {
                Log.e(TAG, "startDeleteAll already in the middle of a delete", new Exception());
            }
            //MmsApp.getApplication().getPduLoaderManager().clear();
            sDeletingThreads = true;

            for (long threadId : threadIds) {
                Uri uri = ContentUris.withAppendedId(Threads.CONTENT_URI, threadId);
                String selection = "locked=0";

                handler.setDeleteToken(token);
                handler.startDelete(token, threadId, uri, selection, null);

                DraftCache.getInstance().setDraftState(threadId, false);
            }
        }
    }

    public static class ConversationQueryHandler extends AsyncQueryHandler {
        private int mDeleteToken;
        private final Context mContext;

        public ConversationQueryHandler(Context context) {
            super(context.getContentResolver());
            mContext = context;
        }

        void setDeleteToken(int token) {
            mDeleteToken = token;
        }

        /**
         * Always call this super method from your overridden onDeleteComplete function.
         */
        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            if (token == mDeleteToken) {
                // release lock
                synchronized (sDeletingThreadsLock) {
                    sDeletingThreads = false;
                    sDeletingThreadsLock.notifyAll();
                }
                UnreadBadgeService.update(mContext);
                NotificationMgr.create(mContext);
            }
        }
    }


    /**
     * Fill the specified conversation with the values from the specified
     * cursor, possibly setting recipients to empty if value allowQuery
     * is false and the recipient IDs are not in cache.  The cursor should
     * be one made via startQueryForAll
     */
    private static void fillFromCursor(Context context,
                                       Conversation conv,
                                       Cursor c,
                                       boolean allowQuery) {
        synchronized (conv) {
            conv.mThreadId = c.getLong(ID);
            conv.mDate = c.getLong(DATE);
            // 4 is SNIPPET.
            String sn = c.getString(4);
            conv.mSnippet = sn != null && !TextUtils.isEmpty(sn)
                            ? sn
                            : context.getString(R.string.no_subject_view);

            conv.setHasUnreadMessages(c.getInt(READ) == 0);
            conv.mHasError = (c.getInt(ERROR) != 0);
        }
        // Fill in as much of the conversation as we can before doing the slow stuff of looking
        // up the contacts associated with this conversation.
        String recipientIds = c.getString(RECIPIENT_IDS);
        ContactList recipients = ContactList.getByIds(context, recipientIds, allowQuery);
        synchronized (conv) {
            conv.mRecipients = recipients;
        }

        if (Log.isLoggable("Mms:threadcache", Log.VERBOSE)) {
            Log.d(TAG, "fillFromCursor: conv=" + conv + ", recipientIds=" + recipientIds);
        }
    }

    /**
     * Private cache for the use of the various forms of Conversation.get.
     */
    private static class Cache {
        private static final Cache sInstance = new Cache();

        static Cache getInstance() {
            return sInstance;
        }

        private final HashSet<Conversation> mCache;

        private Cache() {
            mCache = new HashSet<>(10);
        }

        /**
         * Return the conversation with the specified thread ID, or
         * null if it's not in cache.
         */
        static Conversation get(long threadId) {
            synchronized (sInstance) {
                if (Log.isLoggable("Mms:threadcache", Log.VERBOSE)) {
                    Log.d(TAG, "Conversation get with threadId: " + threadId);
                }
                for (Conversation c : sInstance.mCache) {
                    if (DEBUG) {
                        Log.d(TAG, "Conversation get() threadId: " + threadId +
                                " c.getThreadId(): " + c.getThreadId());
                    }
                    if (c.getThreadId() == threadId) {
                        return c;
                    }
                }
            }
            return null;
        }

        /**
         * Put the specified conversation in the cache.  The caller
         * should not place an already-existing conversation in the
         * cache, but rather update it in place.
         */
        static void put(Conversation c) {
            synchronized (sInstance) {
                // We update cache entries in place so people with long-
                // held references get updated.
                if (Log.isLoggable("Mms:threadcache", Log.VERBOSE)) {
                    Log.d(TAG, "Conversation.Cache.put: conv= " + c + ", hash: " + c.hashCode());
                }

                if (sInstance.mCache.contains(c)) {
                    if (DEBUG) {
                        dumpCache();
                    }
                    throw new IllegalStateException("cache already contains " + c +
                                                            " threadId: " + c.mThreadId);
                }
                sInstance.mCache.add(c);
            }
        }

        /**
         * Replace the specified conversation in the cache. This is used in cases where we
         * lookup a conversation in the cache by threadId, but don't find it. The caller
         * then builds a new conversation (from the cursor) and tries to add it, but gets
         * an exception that the conversation is already in the cache, because the hash
         * is based on the recipients and it's there under a stale threadId. In this function
         * we remove the stale entry and add the new one. Returns true if the operation is
         * successful
         */
        static boolean replace(Conversation c) {
            synchronized (sInstance) {
                if (Log.isLoggable("Mms:threadcache", Log.VERBOSE)) {
                    Log.d(TAG, "Conversation.Cache.put: conv= " + c + ", hash: " + c.hashCode());
                }

                if (!sInstance.mCache.contains(c)) {
                    if (DEBUG) {
                        dumpCache();
                    }
                    return false;
                }
                // Here it looks like we're simply removing and then re-adding the same object
                // to the hashset. Because the hashkey is the conversation's recipients, and not
                // the thread id, we'll actually remove the object with the stale threadId and
                // then add the the conversation with updated threadId, both having the same
                // recipients.
                sInstance.mCache.remove(c);
                sInstance.mCache.add(c);
                return true;
            }
        }

        static void remove(long threadId) {
            synchronized (sInstance) {
                if (DEBUG) {
                    Log.d(TAG, "remove threadid: " + threadId);
                    dumpCache();
                }
                for (Conversation c : sInstance.mCache) {
                    if (c.getThreadId() == threadId) {
                        sInstance.mCache.remove(c);
                        return;
                    }
                }
            }
        }

        static void dumpCache() {
            synchronized (sInstance) {
                Log.d(TAG, "Conversation dumpCache: ");
                for (Conversation c : sInstance.mCache) {
                    Log.d(TAG, "   conv: " + c.toString() + " hash: " + c.hashCode());
                }
            }
        }

        /**
         * Remove all conversations from the cache that are not in
         * the provided set of thread IDs.
         */
        static void keepOnly(Set<Long> threads) {
            synchronized (sInstance) {
                Iterator<Conversation> iter = sInstance.mCache.iterator();
                while (iter.hasNext()) {
                    Conversation c = iter.next();
                    if (!threads.contains(c.getThreadId())) {
                        iter.remove();
                    }
                }
            }
            if (DEBUG) {
                Log.d(TAG, "after keepOnly");
                dumpCache();
            }
        }
    }

    /**
     * Set up the conversation cache.  To be called once at application
     * startup time.
     */
    public static void init(final Context context) {
        Thread thread = new Thread(() -> cacheAllThreads(context), "Conversation.init");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void cacheAllThreads(Context context) {
        if (Log.isLoggable("Mms:threadcache", Log.VERBOSE)) {
            Log.d(TAG, "[Conversation] cacheAllThreads: begin");
        }
        synchronized (Cache.getInstance()) {
            if (sLoadingThreads) {
                return;
            }
            sLoadingThreads = true;
        }

        // Keep track of what threads are now on disk so we
        // can discard anything removed from the cache.
        HashSet<Long> threadsOnDisk = new HashSet<>();

        // Query for all conversations.
        Cursor c = context.getContentResolver().query(sAllThreadsUri,
                                                      ALL_THREADS_PROJECTION, null, null, null);
        try {
            if (c != null) {
                while (c.moveToNext()) {
                    long threadId = c.getLong(ID);
                    threadsOnDisk.add(threadId);

                    // Try to find this thread ID in the cache.
                    Conversation conv;
                    synchronized (Cache.getInstance()) {
                        conv = Cache.get(threadId);
                    }

                    if (conv == null) {
                        // Make a new Conversation and put it in
                        // the cache if necessary.
                        conv = new Conversation(context, c, true);
                        try {
                            synchronized (Cache.getInstance()) {
                                Cache.put(conv);
                            }
                        }
                        catch (IllegalStateException e) {
                            Log.e(TAG,
                                  "Tried to add duplicate Conversation to Cache" + " for threadId: " + threadId + " new conv: " + conv);
                            if (!Cache.replace(conv)) {
                                Log.e(TAG, "cacheAllThreads cache.replace failed on " + conv);
                            }
                        }
                    }
                    else {
                        // Or update in place so people with references
                        // to conversations get updated too.
                        fillFromCursor(context, conv, c, true);
                    }
                }
            }
        }
        finally {
            if (c != null) {
                c.close();
            }
            synchronized (Cache.getInstance()) {
                sLoadingThreads = false;
            }
        }

        // Purge the cache of threads that no longer exist on disk.
        Cache.keepOnly(threadsOnDisk);

        if (Log.isLoggable("Mms:threadcache", Log.VERBOSE)) {
            Log.d(TAG, "[Conversation] cacheAllThreads: finished");
            Cache.dumpCache();
        }
    }

    private boolean loadFromThreadId(long threadId, boolean allowQuery) {
        try (Cursor c = mContext.getContentResolver().query(sAllThreadsUri,
                                                            ALL_THREADS_PROJECTION,
                                                            "_id=" + Long.toString(threadId),
                                                            null,
                                                            null)) {
            if (c != null && c.moveToFirst()) {
                fillFromCursor(mContext, this, c, allowQuery);

                if (threadId != mThreadId) {
                    Log.e(TAG,
                          "loadFromThreadId: fillFromCursor returned differnt thread_id!" + " threadId=" + threadId + ", mThreadId=" + mThreadId);
                }
            }
            else {
                Log.e(TAG, "loadFromThreadId: Can't find thread ID " + threadId);
                return false;
            }
        }
        return true;
    }

    public static String getRecipients(Uri uri) {
        String base = uri.getSchemeSpecificPart();
        int pos = base.indexOf('?');
        return (pos == -1) ? base : base.substring(0, pos);
    }

}
