package com.moez.QKSMS.ui.conversationlist;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageView;

import com.moez.QKSMS.R;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.data.ConversationLegacy;
import com.moez.QKSMS.ui.ClickyViewHolder;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.view.QKTextView;

class ConversationListViewHolder extends ClickyViewHolder<Conversation> implements Contact.UpdateListener {

    View root;
    QKTextView snippetView;
    QKTextView dateView;
    ImageView mutedView;
    ImageView unreadView;
    ImageView errorIndicator;
    ImageView mSelected;
    private QKTextView fromView;

    ConversationListViewHolder(QKActivity context, View view) {
        super(context, view);

        root = view;
        fromView = view.findViewById(R.id.conversation_list_name);
        snippetView = view.findViewById(R.id.conversation_list_snippet);
        dateView = view.findViewById(R.id.conversation_list_date);
        mutedView = view.findViewById(R.id.conversation_list_muted);
        unreadView = view.findViewById(R.id.conversation_list_unread);
        errorIndicator = view.findViewById(R.id.conversation_list_error);
        mSelected = view.findViewById(R.id.selected);
    }

    @Override
    public void onUpdate(final Contact updated) {
        if (mData.getRecipients().size() != 1 || mData.getRecipients()
                                                      .get(0)
                                                      .getNumber()
                                                      .equals(updated.getNumber()))
            mContext.runOnUiThread(() -> {
                String from = mData.getRecipients().formatNames();
                SpannableStringBuilder buf = new SpannableStringBuilder(from);
                if (new ConversationLegacy(mData.getThreadId()).hasDraft()) {
                    buf.append(mContext.getResources().getString(R.string.draft_separator));
                    int before = buf.length();
                    buf.append(mContext.getResources().getString(R.string.has_draft));
                    buf.setSpan(new ForegroundColorSpan(ThemeManager.getColor()),
                                before,
                                buf.length(),
                                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                }
                fromView.setText(buf);
            });
    }

}
