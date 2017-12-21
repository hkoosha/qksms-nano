package com.moez.QKSMS.ui.search;

import android.view.View;

import com.moez.QKSMS.R;
import com.moez.QKSMS.ui.ClickyViewHolder;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.view.QKTextView;

public class SearchViewHolder extends ClickyViewHolder<SearchData> {

    View root;
    protected QKTextView name;
    protected QKTextView date;
    QKTextView snippet;

    SearchViewHolder(QKActivity context, View view) {
        super(context, view);

        root = view;
        name = view.findViewById(R.id.search_name);
        date = view.findViewById(R.id.search_date);
        snippet = view.findViewById(R.id.search_snippet);
    }
}
