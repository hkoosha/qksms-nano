package com.moez.QKSMS.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import com.moez.QKSMS.R;
import com.moez.QKSMS.ui.ThemeManager;

public class QKTextView extends AppCompatTextView {

    private int mType = ThemeManager.TEXT_TYPE_PRIMARY;
    private boolean mOnColorBackground = false;

    public QKTextView(Context context) {
        super(context);

        if (!isInEditMode()) {
            init(context, null);
        }
    }

    public QKTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (!isInEditMode()) {
            init(context, attrs);
        }
    }

    public QKTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (!isInEditMode()) {
            init(context, attrs);
        }
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            final TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.QKTextView);
            mType = array.getInt(R.styleable.QKTextView_type, ThemeManager.TEXT_TYPE_PRIMARY);
            array.recycle();
        }

        setTextColor(ThemeManager.getTextColor(context, mType));
        setText(getText());
        setType(mType);
    }

    public void setType(int type) {
        mType = type;
        setText(getText(), BufferType.NORMAL);
    }

    public void setOnColorBackground(boolean onColorBackground) {
        if (onColorBackground != mOnColorBackground) {
            mOnColorBackground = onColorBackground;

            if (onColorBackground) {
                if (mType == ThemeManager.TEXT_TYPE_PRIMARY) {
                    setTextColor(ThemeManager.getTextOnColorPrimary());
                    setLinkTextColor(ThemeManager.getTextOnColorPrimary());
                }
                else if (mType == ThemeManager.TEXT_TYPE_SECONDARY ||
                        mType == ThemeManager.TEXT_TYPE_TERTIARY) {
                    setTextColor(ThemeManager.getTextOnColorSecondary());
                }
            }
            else {
                if (mType == ThemeManager.TEXT_TYPE_PRIMARY) {
                    setTextColor(ThemeManager.getTextOnBackgroundPrimary());
                    setLinkTextColor(ThemeManager.getColor());
                }
                else if (mType == ThemeManager.TEXT_TYPE_SECONDARY ||
                        mType == ThemeManager.TEXT_TYPE_TERTIARY) {
                    setTextColor(ThemeManager.getTextOnBackgroundSecondary());
                }
            }
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (mType == ThemeManager.TEXT_TYPE_DIALOG_BUTTON) {
            text = text.toString().toUpperCase();
        }
        super.setText(text, BufferType.NORMAL);
    }
}
