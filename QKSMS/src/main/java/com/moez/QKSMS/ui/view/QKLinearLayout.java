package com.moez.QKSMS.ui.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.moez.QKSMS.R;

public class QKLinearLayout extends LinearLayout {

    private int mBackgroundTint = 0xFFFFFFFF;

    public QKLinearLayout(Context context) {
        super(context);
        init(context, null);
    }

    public QKLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.QKLinearLayout);

            for (int i = 0; i < a.length(); i++) {
                int attr = a.getIndex(i);

                switch (attr) {
                    case R.styleable.QKLinearLayout_backgroundTint:
                        mBackgroundTint = a.getInt(i, 0xFFFFFFFF);
                        break;
                }
            }

            setBackground(getBackground());

            a.recycle();
        }
    }

    public void setBackgroundTint(int backgroundTint) {
        if (mBackgroundTint != backgroundTint) {
            mBackgroundTint = backgroundTint;
            setBackground(getBackground());
        }
    }

    @TargetApi(16)
    @Override
    public void setBackground(Drawable background) {
        background.mutate().setColorFilter(mBackgroundTint, PorterDuff.Mode.MULTIPLY);
        super.setBackground(background);
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        background.mutate().setColorFilter(mBackgroundTint, PorterDuff.Mode.MULTIPLY);
        super.setBackgroundDrawable(background);
    }
}
