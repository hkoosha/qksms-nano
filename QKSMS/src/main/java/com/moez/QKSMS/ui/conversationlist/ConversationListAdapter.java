package com.moez.QKSMS.ui.conversationlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.RecyclerCursorAdapter;
import com.moez.QKSMS.ui.ThemeManager;

class ConversationListAdapter extends RecyclerCursorAdapter<ConversationListViewHolder, Conversation> {

    ConversationListAdapter(QKActivity context) {
        super(context);
    }

    protected Conversation getItem(int position) {
        mCursor.moveToPosition(position);
        return Conversation.from(mContext, mCursor);
    }

    @Override
    public ConversationListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.list_item_conversation, null);

        ConversationListViewHolder holder = new ConversationListViewHolder(mContext, view);
        holder.mutedView.setImageResource(R.drawable.ic_notifications_muted);
        holder.unreadView.setImageResource(R.drawable.ic_unread_indicator);
        holder.errorIndicator.setImageResource(R.drawable.ic_error);

        holder.mutedView.setColorFilter(ThemeManager.getColor());
        holder.unreadView.setColorFilter(ThemeManager.getColor());
        holder.errorIndicator.setColorFilter(ThemeManager.getColor());

        holder.root.setBackgroundDrawable(ThemeManager.getRippleBackground());

        return holder;
    }

    @Override
    public void onBindViewHolder(ConversationListViewHolder holder, int position) {
        final Conversation conversation = getItem(position);

        holder.mData = conversation;
        holder.mContext = mContext;
        holder.mClickListener = mItemClickListener;
        holder.root.setOnClickListener(holder);
        holder.root.setOnLongClickListener(holder);

        holder.mutedView.setVisibility(View.GONE);

        holder.errorIndicator.setVisibility(conversation.hasError() ? View.VISIBLE : View.GONE);

        final boolean hasUnreadMessages = conversation.hasUnreadMessages();
        if (hasUnreadMessages) {
            holder.unreadView.setVisibility(View.VISIBLE);
            holder.snippetView.setTextColor(ThemeManager.getTextOnBackgroundPrimary());
            holder.dateView.setTextColor(ThemeManager.getColor());
            holder.snippetView.setMaxLines(5);
        }
        else {
            holder.unreadView.setVisibility(View.GONE);
            holder.snippetView.setTextColor(ThemeManager.getTextOnBackgroundSecondary());
            holder.dateView.setTextColor(ThemeManager.getTextOnBackgroundSecondary());
            holder.snippetView.setMaxLines(1);
        }

        holder.dateView.setTextColor(hasUnreadMessages
                                     ? ThemeManager.getColor()
                                     : ThemeManager.getTextOnBackgroundSecondary());

        if (isInMultiSelectMode()) {
            holder.mSelected.setVisibility(View.VISIBLE);
            if (isSelected(conversation.getThreadId())) {
                holder.mSelected.setImageResource(R.drawable.ic_selected);
                holder.mSelected.setColorFilter(ThemeManager.getColor());
                holder.mSelected.setAlpha(1f);
            }
            else {
                holder.mSelected.setImageResource(R.drawable.ic_unselected);
                holder.mSelected.setColorFilter(ThemeManager.getTextOnBackgroundSecondary());
                holder.mSelected.setAlpha(0.5f);
            }
        }
        else {
            holder.mSelected.setVisibility(View.GONE);
        }

        // Date
        holder.dateView.setText(Util.getConversationTimestamp(
                mContext, conversation.getDate()));

        holder.snippetView.setText(conversation.getSnippet());

        Contact.addListener(holder);

        holder.onUpdate(conversation.getRecipients().size() == 1 ?
                        conversation.getRecipients().get(0) : null);
    }

}
