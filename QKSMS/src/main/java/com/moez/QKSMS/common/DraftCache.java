/*
* Copyright (C) 2009 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.moez.QKSMS.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms.Conversations;
import android.util.Log;

import com.moez.QKSMS.SqliteWrapper;

import java.util.HashSet;


public final class DraftCache {

    private static final String TAG = "Mms/draft";

    private static final String[] DRAFT_PROJECTION = new String[]{
            Conversations.THREAD_ID           // 0
    };

    private static final int COLUMN_DRAFT_THREAD_ID = 0;

    private static DraftCache sInstance;

    private HashSet<Long> mDraftSet = new HashSet<>(4);
    private final Object mDraftSetLock = new Object();

    private DraftCache(Context context) {
        if (Log.isLoggable("Mms:app", Log.DEBUG)) {
            log("DraftCache.constructor");
        }

        refresh(context);
    }

    /**
     * To be called whenever the draft state might have changed.
     * Dispatches work to a thread and returns immediately.
     *
     */
    private void refresh(Context context) {
        if (Log.isLoggable("Mms:app", Log.DEBUG)) {
            log("refresh");
        }

        Thread thread = new Thread(() -> rebuildCache(context), "DraftCache.refresh");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /**
     * Does the actual work of rebuilding the draft cache.
     */
    @SuppressLint("NewApi")
    private void rebuildCache(Context context) {
        if (Log.isLoggable("Mms:app", Log.DEBUG)) {
            log("rebuildCache");
        }

        HashSet<Long> newDraftSet = new HashSet<>();

        Cursor cursor = SqliteWrapper.query(
                context,
                context.getContentResolver(),
                MmsSms.CONTENT_DRAFT_URI,
                DRAFT_PROJECTION, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    for (; !cursor.isAfterLast(); cursor.moveToNext()) {
                        long threadId = cursor.getLong(COLUMN_DRAFT_THREAD_ID);
                        newDraftSet.add(threadId);
                        if (Log.isLoggable("Mms:app", Log.DEBUG)) {
                            log("rebuildCache: add tid=" + threadId);
                        }
                    }
                }
            }
            finally {
                cursor.close();
            }
        }

        synchronized (mDraftSetLock) {
            mDraftSet = newDraftSet;
        }
    }

    /**
     * Updates the has-draft status of a particular thread on
     * a piecemeal basis, to be called when a draft has appeared
     * or disappeared.
     */
    public void setDraftState(long threadId, boolean hasDraft) {
        if (threadId <= 0) {
            return;
        }

        boolean changed;
        synchronized (mDraftSetLock) {
            if (hasDraft) {
                changed = mDraftSet.add(threadId);
            }
            else {
                changed = mDraftSet.remove(threadId);
            }
        }

        if (Log.isLoggable("Mms:app", Log.DEBUG)) {
            log("setDraftState: tid=" + threadId + ", value=" + hasDraft + ", changed=" + changed);
        }
    }

    /**
     * Returns true if the given thread ID has a draft associated
     * with it, false if not.
     */
    public boolean hasDraft(long threadId) {
        synchronized (mDraftSetLock) {
            return mDraftSet.contains(threadId);
        }
    }

    /**
     * Initialize the global instance. Should call only once.
     */
    public static void init(Context context) {
        sInstance = new DraftCache(context);
    }

    /**
     * Get the global instance.
     */
    public static DraftCache getInstance() {
        return sInstance;
    }

    private void log(String format, Object... args) {
        String s = String.format(format, args);
        Log.d(TAG, "[DraftCache/" + Thread.currentThread().getId() + "] " + s);
    }

}
