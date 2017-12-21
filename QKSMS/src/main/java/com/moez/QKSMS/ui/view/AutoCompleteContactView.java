package com.moez.QKSMS.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.MultiAutoCompleteTextView;

import com.android.ex.chips.BaseRecipientAdapter;
import com.android.ex.chips.RecipientEditTextView;
import com.moez.QKSMS.ui.ThemeManager;

public class AutoCompleteContactView extends RecipientEditTextView {

    public AutoCompleteContactView(Context context) {
        this(context, null);
        if (!isInEditMode()) {
            init();
        }
    }

    public AutoCompleteContactView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            init();
        }
    }

    private void init() {

        BaseRecipientAdapter mAdapter = new BaseRecipientAdapter(
                BaseRecipientAdapter.QUERY_TYPE_PHONE,
                getContext());

        setThreshold(1);
        setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        setAdapter(mAdapter);
        setOnItemClickListener(this);

        setTextColor(ThemeManager.getTextOnBackgroundPrimary());
        setHintTextColor(ThemeManager.getTextOnBackgroundSecondary());
    }

}
