/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.moez.QKSMS.ui.messagelist;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.telephony.TelephonyManager;

import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.moez.QKSMS.QKSMSApp;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.sms.SmsHelper;

import java.util.regex.Pattern;


/**
 * Mostly immutable model for an SMS/MMS message.
 * <p>
 * <p>The only mutable field is the cached formatted message member,
 * the formatting of which is done outside this model in MessageListItem.
 */
public class MessageItem {

    public enum DeliveryStatus {
        NONE,
        INFO,
        FAILED,
        PENDING,
        RECEIVED
    }

    public final long mMsgId;
    final String mType;
    final int mBoxId;

    DeliveryStatus mDeliveryStatus;
    boolean mReadReport;
    boolean mLocked;            // locked to prevent auto-deletion

    public Uri mMessageUri;

    long mDate;
    String mTimestamp;
    public String mAddress;
    public String mContact;
    public String mBody; // Body of SMS, first text of MMS.
    Pattern mHighlight; // portion of message to highlight (from search)

    @SuppressLint("NewApi")
    public MessageItem(Context context, String type, final Cursor cursor,
                       final MessageColumns.ColumnsMap columnsMap, Pattern highlight,
                       boolean canBlock) {
        mMsgId = cursor.getLong(columnsMap.mColumnMsgId);
        mHighlight = highlight;
        mType = type;

        mReadReport = false; // No read reports in sms

        long status = cursor.getLong(columnsMap.mColumnSmsStatus);
        if (status == Sms.STATUS_NONE) {
            // No delivery report requested
            mDeliveryStatus = DeliveryStatus.NONE;
        }
        else if (status >= Sms.STATUS_FAILED) {
            // Failure
            mDeliveryStatus = DeliveryStatus.FAILED;
        }
        else if (status >= Sms.STATUS_PENDING) {
            // Pending
            mDeliveryStatus = DeliveryStatus.PENDING;
        }
        else {
            // Success
            mDeliveryStatus = DeliveryStatus.RECEIVED;
        }

        mMessageUri = ContentUris.withAppendedId(Sms.CONTENT_URI, mMsgId);

        // Set contact and message body
        mBoxId = cursor.getInt(columnsMap.mColumnSmsType);
        mAddress = cursor.getString(columnsMap.mColumnSmsAddress);
        if (SmsHelper.isOutgoingFolder(mBoxId)) {

            mContact = context.getString(
                    R.string.messagelist_sender_self);
        }
        else {
            // For incoming messages, the ADDRESS field contains the sender.
            mContact = Contact.get(mAddress, canBlock).getName();
        }
        mBody = cursor.getString(columnsMap.mColumnSmsBody);
        mBody = formatNumberToContact(mBody);

        // Unless the message is currently in the progress of being sent, it gets a time stamp.
        if (!isOutgoingMessage()) {
            // Set "received" or "sent" time stamp
            mDate = cursor.getLong(columnsMap.mColumnSmsDate);
            mTimestamp = Util.getMessageTimestamp(context, mDate);
        }

        mLocked = cursor.getInt(columnsMap.mColumnSmsLocked) != 0;
    }

    public boolean isMe() {
        // Logic matches MessageListAdapter.getItemViewType which is used to decide which
        // type of MessageListItem to create: a left or right justified item depending on whether
        // the message is incoming or outgoing.
        return !(mBoxId == Sms.MESSAGE_TYPE_INBOX || mBoxId == Sms.MESSAGE_TYPE_ALL);
    }

    boolean isOutgoingMessage() {
        return mBoxId == Sms.MESSAGE_TYPE_FAILED
                || mBoxId == Sms.MESSAGE_TYPE_OUTBOX
                || mBoxId == Sms.MESSAGE_TYPE_QUEUED;
    }

    boolean isSending() {
        return !isFailedMessage() && isOutgoingMessage();
    }

    boolean isFailedMessage() {
        return mBoxId == Sms.MESSAGE_TYPE_FAILED;
    }

    int getBoxId() {
        return mBoxId;
    }

    long getMessageId() {
        return mMsgId;
    }

    @Override
    public String toString() {
        return "type: " + mType +
                " box: " + mBoxId +
                " address: " + mAddress +
                " contact: " + mContact +
                " read: " + mReadReport +
                " delivery status: " + mDeliveryStatus;
    }

    private static String mCountryIso;
    private static final Object LOCK = new Object();

    private static String getCurrentCountryIso() {
        synchronized (LOCK) {
            if (mCountryIso == null) {
                TelephonyManager tm = (TelephonyManager) QKSMSApp
                        .getApplication()
                        .getSystemService(Context.TELEPHONY_SERVICE);

                if (tm == null) {
                    mCountryIso = "US";
                }
                else {
                    mCountryIso = tm.getNetworkCountryIso();
                    if (mCountryIso == null)
                        mCountryIso = "US";
                    mCountryIso = mCountryIso.toUpperCase();
                }
            }
            return mCountryIso;
        }
    }

    private static String formatNumberToContact(String text) {
        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        Iterable<PhoneNumberMatch> matches = phoneNumberUtil.findNumbers(
                text, getCurrentCountryIso());
        for (PhoneNumberMatch match : matches) {
            Contact contact = Contact.get(match.rawString(), true);
            if (contact.isNamed()) {
                String nameAndNumber = phoneNumberUtil.format(
                        match.number(), PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
                        + " (" + contact.getName() + ")";
                text = text.replace(match.rawString(), nameAndNumber);
            } // If the contact doesn't exist yet, leave the number as-is
        }
        return text;
    }


}
