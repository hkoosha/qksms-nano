package com.moez.QKSMS.data;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class ContactList extends ArrayList<Contact> {

    private static final long serialVersionUID = 1L;

    /**
     * Returns a ContactList for the corresponding recipient ids passed in. This method will
     * create the contact if it doesn't exist, and would inject the recipient id into the contact.
     */
    static ContactList getByIds(Context context, String spaceSepIds, boolean canBlock) {
        ContactList list = new ContactList();
        for (RecipientIdCache.Entry entry : RecipientIdCache.getAddresses(context, spaceSepIds)) {
            if (entry != null && !TextUtils.isEmpty(entry.number)) {
                Contact contact = Contact.get(entry.number, canBlock);
                list.add(contact);
            }
        }
        return list;
    }

    public String formatNames() {
        String[] names = new String[size()];
        int i = 0;
        for (Contact c : this) {
            names[i++] = c.getName();
        }
        return TextUtils.join(", ", names);
    }

    String serialize() {
        return TextUtils.join(";", getNumbers());
    }

    public String[] getNumbers() {
        List<String> numbers = new ArrayList<>();
        String number;
        for (Contact c : this) {
            number = c.getNumber();

            // Don't add duplicate numbers. This can happen if a contact name has a comma.
            // Since we use a comma as a delimiter between contacts, the code will consider
            // the same recipient has been added twice. The recipients UI still works correctly.
            // It's easiest to just make sure we only send to the same recipient once.
            if (!TextUtils.isEmpty(number) && !numbers.contains(number)) {
                numbers.add(number);
            }
        }
        return numbers.toArray(new String[numbers.size()]);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ContactList))
            return false;

        ContactList other = (ContactList) obj;
        // If they're different sizes, the contact
        // set is obviously different.
        if (size() != other.size()) {
            return false;
        }

        // Make sure all the individual contacts are the same.
        for (Contact c : this) {
            if (!other.contains(c)) {
                return false;
            }
        }

        return true;
    }

}
