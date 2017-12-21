package com.moez.QKSMS.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import com.moez.QKSMS.SqliteWrapper;

import java.util.ArrayList;
import java.util.List;


class RecipientIdCache {

    private static final Object LOCK = new Object();

    private static final String TAG = "Mms/cache";

    private static Uri sAllCanonical =
            Uri.parse("content://mms-sms/canonical-addresses");

    private static final RecipientIdCache sInstance = new RecipientIdCache();

    private final LongSparseArray<String> mCache = new LongSparseArray<>();

    static class Entry {
        public long id;
        public String number;

        Entry(long id, String number) {
            this.id = id;
            this.number = number;
        }
    }

    static void init(Context context) {
        new Thread(() -> fill(context), "RecipientIdCache.init").start();
    }

    private RecipientIdCache() {
        //        mContext = context;
    }

    private static void fill(Context context) {
        Log.d(TAG, "[RecipientIdCache] fill: begin");

        Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                                       sAllCanonical, null, null, null, null);
        if (c == null) {
            Log.w(TAG, "null Cursor in fill()");
            return;
        }

        try {
            synchronized (LOCK) {
                // Technically we don't have to clear this because the stupid
                // canonical_addresses table is never GC'ed.
                sInstance.mCache.clear();
                while (c.moveToNext()) {
                    // TODO: don't hardcode the column indices
                    long id = c.getLong(0);
                    String number = c.getString(1);
                    sInstance.mCache.put(id, number);
                }
            }
        }
        finally {
            c.close();
        }

        Log.d(TAG, "[RecipientIdCache] fill: finished");
    }

    static List<Entry> getAddresses(Context context, String spaceSepIds) {
        synchronized (LOCK) {
            List<Entry> numbers = new ArrayList<>();
            String[] ids = spaceSepIds.split(" ");
            for (String id : ids) {
                long longId;

                try {
                    longId = Long.parseLong(id);
                }
                catch (NumberFormatException ex) {
                    continue;
                }

                String number = sInstance.mCache.get(longId);

                if (number == null) {
                    Log.w(TAG, "RecipientId " + longId + " not in cache!");
                    fill(context);
                    number = sInstance.mCache.get(longId);
                }

                if (TextUtils.isEmpty(number)) {
                    Log.w(TAG, "RecipientId " + longId + " has empty number!");
                }
                else {
                    numbers.add(new Entry(longId, number));
                }
            }
            return numbers;
        }
    }

}
