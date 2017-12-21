package com.moez.QKSMS.ui.view;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;

import com.moez.QKSMS.ui.ThemeManager;

public class QKEditText extends AppCompatEditText {

    public interface TextChangedListener {
        void onTextChanged(CharSequence s);
    }

    private boolean mTextChangedListenerEnabled = true;

    public QKEditText(Context context) {
        super(context);

        if (!isInEditMode()) {
            init();
        }
    }

    public QKEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (!isInEditMode()) {
            init();
        }
    }

    public QKEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (!isInEditMode()) {
            init();
        }
    }

    private void init() {
        setTextColor(ThemeManager.getTextOnBackgroundPrimary());
        setHintTextColor(ThemeManager.getTextOnBackgroundSecondary());
        setText(getText());
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (!TextUtils.isEmpty(text)) {
            text = new SpannableStringBuilder(text);
        }
        super.setText(text, type);
    }

    public void setTextChangedListener(final TextChangedListener listener) {
        if (listener != null) {
            addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (mTextChangedListenerEnabled) {
                        listener.onTextChanged(s);
                    }
                }
            });
        }
    }

}
