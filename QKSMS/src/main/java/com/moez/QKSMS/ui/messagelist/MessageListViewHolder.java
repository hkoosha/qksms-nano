package com.moez.QKSMS.ui.messagelist;

import android.view.View;
import android.widget.ImageView;

import com.moez.QKSMS.R;
import com.moez.QKSMS.ui.ClickyViewHolder;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.view.QKTextView;

public class MessageListViewHolder extends ClickyViewHolder<MessageItem> {

    // Views
    View mRoot;
    QKTextView mBodyTextView;
    QKTextView mDateView;
    ImageView mLockedIndicator;
    ImageView mDeliveredIndicator;
    ImageView mDetailsIndicator;

    MessageListViewHolder(QKActivity context, View view) {
        super(context, view);

        mRoot = view;
        mBodyTextView = view.findViewById(R.id.text_view);
        mDateView = view.findViewById(R.id.date_view);
        mLockedIndicator = view.findViewById(R.id.locked_indicator);
        mDeliveredIndicator = view.findViewById(R.id.delivered_indicator);
        mDetailsIndicator = view.findViewById(R.id.details_indicator);
    }

}
