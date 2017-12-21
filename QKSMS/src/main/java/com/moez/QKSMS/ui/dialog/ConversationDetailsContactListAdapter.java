package com.moez.QKSMS.ui.dialog;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.moez.QKSMS.R;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.data.ContactList;
import com.moez.QKSMS.ui.view.QKTextView;

public class ConversationDetailsContactListAdapter extends ArrayAdapter {

    private ContactList mContacts;

    ConversationDetailsContactListAdapter(Context context, ContactList contacts) {
        super(context, R.layout.list_item_recipient);
        mContacts = contacts;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = View.inflate(getContext(), R.layout.list_item_recipient, null);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        Contact contact = mContacts.get(position);

        holder.name.setText(contact.getName());
        holder.address.setText(contact.getNumber());
        return convertView;
    }

    @Override
    public int getCount() {
        return mContacts.size();
    }

    static class ViewHolder {
        QKTextView name;
        QKTextView address;

        ViewHolder(View view) {
            name = view.findViewById(R.id.name);
            address = view.findViewById(R.id.address);
        }
    }
}
