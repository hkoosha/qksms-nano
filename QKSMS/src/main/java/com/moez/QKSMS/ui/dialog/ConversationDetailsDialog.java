package com.moez.QKSMS.ui.dialog;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.view.QKTextView;

public class ConversationDetailsDialog {

    private QKActivity mContext;

    public ConversationDetailsDialog(QKActivity context) {
        mContext = context;
    }

    public void showDetails(Conversation conversation) {
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                   ViewGroup.LayoutParams.WRAP_CONTENT);
        AbsListView.LayoutParams listParams = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                           ViewGroup.LayoutParams.WRAP_CONTENT);

        View view = View.inflate(mContext, R.layout.dialog_conversation_details, null);
        view.setLayoutParams(listParams);
        ((QKTextView) view.findViewById(R.id.date)).setText(Util.getDate(mContext,
                                                                         conversation.getDate()));
        ((QKTextView) view.findViewById(R.id.recipients)).setText(mContext.getString(
                R.string.dialog_conversation_details_recipients,
                Integer.toString(conversation.getRecipients().size())));

        ListView listView = new ListView(mContext);
        listView.setLayoutParams(params);
        listView.addHeaderView(view);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setAdapter(new ConversationDetailsContactListAdapter(mContext,
                                                                      conversation.getRecipients()));

        new QKDialog()
                .setContext(mContext)
                .setTitle(R.string.dialog_conversation_details_title)
                .setCustomView(listView).setPositiveButton(true, R.string.okay, null)
                .show();
    }

}
