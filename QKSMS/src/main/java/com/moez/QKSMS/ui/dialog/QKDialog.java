package com.moez.QKSMS.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;

import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.view.QKTextView;

import java.util.ArrayList;
import java.util.List;


public class QKDialog extends DialogFragment {

    private static final String TAG = "QKDialog";

    protected Activity mContext;
    protected Resources mResources;

    private boolean mTitleEnabled;
    private String mTitleText;

    private boolean mMessageEnabled;
    private String mMessageText;

    private ArrayAdapter mAdapter;

    private boolean mCustomViewEnabled;
    private View mCustomView;

    private boolean mPositiveButtonEnabled;
    private String mPositiveButtonText;
    private OnClickListener mPositiveButtonClickListener;

    private boolean mNeutralButtonEnabled;
    private String mNeutralButtonText;
    private OnClickListener mNeutralButtonClickListener;

    private boolean mNegativeButtonEnabled = false;
    private String mNegativeButtonText;
    private OnClickListener mNegativeButtonClickListener;

    private ArrayList<String> mMenuItems = new ArrayList<>();
    private ArrayList<Long> mMenuItemIds = new ArrayList<>();

    public QKDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Dialog dialog = new Dialog(mContext);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_material, null);

        if (mTitleEnabled || mMessageEnabled) {
            LinearLayout mContentPanel = view.findViewById(R.id.contentPanel);
            mContentPanel.setVisibility(View.VISIBLE);
        }

        if (mTitleEnabled) {
            QKTextView mTitleView = view.findViewById(R.id.alertTitle);
            mTitleView.setVisibility(View.VISIBLE);
            mTitleView.setText(mTitleText);
            Log.d(TAG, "title enabled");
        }

        if (mMessageEnabled) {
            QKTextView mMessageView = view.findViewById(R.id.message);
            mMessageView.setVisibility(View.VISIBLE);
            mMessageView.setText(mMessageText);
        }

        if (mCustomViewEnabled) {
            LinearLayout mCustomPanel = view.findViewById(R.id.customPanel);
            mCustomPanel.setVisibility(View.VISIBLE);

            if (mCustomView instanceof ListView || mCustomView instanceof RecyclerView) {
                mCustomPanel.addView(mCustomView);
            }
            else {
                ScrollView scrollView = new ScrollView(mContext);
                scrollView.addView(mCustomView);
                mCustomPanel.addView(scrollView);
            }
        }

        if (mPositiveButtonEnabled || mNegativeButtonEnabled) {
            LinearLayout mButtonBar = view.findViewById(R.id.buttonPanel);
            mButtonBar.setVisibility(View.VISIBLE);
            mButtonBar.setOrientation(LinearLayout.HORIZONTAL);
        }

        QKTextView mPositiveButtonView = view.findViewById(R.id.buttonPositive);
        if (mPositiveButtonEnabled) {
            mPositiveButtonView.setVisibility(View.VISIBLE);
            mPositiveButtonView.setText(mPositiveButtonText);
            mPositiveButtonView.setOnClickListener(mPositiveButtonClickListener);
            mPositiveButtonView.setTextColor(ThemeManager.getColor());
        }
        else {
            mPositiveButtonView.setVisibility(View.GONE);
        }

        QKTextView mNeutralButtonView = view.findViewById(R.id.buttonNeutral);
        if (mNeutralButtonEnabled) {
            mNeutralButtonView.setVisibility(View.VISIBLE);
            mNeutralButtonView.setText(mNeutralButtonText);
            mNeutralButtonView.setOnClickListener(mNeutralButtonClickListener);
        }
        else {
            mNeutralButtonView.setVisibility(View.GONE);
        }

        QKTextView mNegativeButtonView = view.findViewById(R.id.buttonNegative);
        if (mNegativeButtonEnabled) {
            mNegativeButtonView.setVisibility(View.VISIBLE);
            mNegativeButtonView.setText(mNegativeButtonText);
            mNegativeButtonView.setOnClickListener(mNegativeButtonClickListener);
        }
        else {
            mNegativeButtonView.setVisibility(View.GONE);
        }

        dialog.setContentView(view);

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();

        Window window = getDialog().getWindow();
        if (window != null) {
            WindowManager.LayoutParams windowParams = window.getAttributes();
            windowParams.dimAmount = 0.33f;
            windowParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            window.setAttributes(windowParams);

            DisplayMetrics metrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int width = (int) (metrics.widthPixels * 0.9);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public QKDialog setContext(Activity context) {
        mContext = context;
        mResources = context.getResources();
        return this;
    }

    public QKDialog setTitle(int resource) {
        mTitleEnabled = true;
        mTitleText = mResources.getString(resource);
        return this;
    }

    public QKDialog setMessage(int resource) {
        return setMessage(mResources.getString(resource));
    }

    public QKDialog setMessage(String message) {
        mMessageEnabled = true;
        mMessageText = message;
        return this;
    }

    public QKDialog setCustomView(View view) {
        mCustomViewEnabled = true;
        mCustomView = view;
        return this;
    }

    public void addMenuItem(@StringRes int titleId, long id) {
        addMenuItem(mContext.getString(titleId), id);
    }

    /**
     * Adds a menu style item, allowing for dynamic ids for different items. This is useful when the item order
     * is set dynamically, like in the MessageListItem
     * <p>
     * If you use this method, always make sure to use #buildMenu(OnItemClickListener) to compile the items and add the
     * click listener
     */
    public void addMenuItem(String title, long id) {
        mMenuItems.add(title);
        mMenuItemIds.add(id);
    }

    public void buildMenu(final OnItemClickListener onItemClickListener) {
        ArrayAdapter adapter = new ArrayAdapter<>(mContext, R.layout.list_item_simple, mMenuItems);
        ListView listView = new ListView(mContext);
        listView.setAdapter(adapter);
        listView.setDivider(null);
        listView.setPadding(0, Util.dpToPx(mContext, 8), 0, Util.dpToPx(mContext, 8));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            // Ignore the given ID and use the one that we set in #addMenuItem()
            onItemClickListener.onItemClick(parent, view, position, mMenuItemIds.get(position));
            dismiss();
        });
        setCustomView(listView);
    }


    public QKDialog setItems(int resource, final OnItemClickListener onClickListener) {
        return setItems(mResources.getStringArray(resource), onClickListener);
    }

    public QKDialog setItems(String[] items, final OnItemClickListener onClickListener) {
        mAdapter = new ArrayAdapter<>(mContext, R.layout.list_item_simple, items);
        ListView listView = new ListView(mContext);
        listView.setAdapter(mAdapter);
        listView.setDivider(null);
        listView.setPadding(0, Util.dpToPx(mContext, 8), 0, Util.dpToPx(mContext, 8));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (onClickListener != null) {
                onClickListener.onItemClick(parent, view, position, id);
                dismiss();
            }
        });
        return setCustomView(listView);
    }

    public QKDialog setItems(List<String> items,
                             final OnItemClickListener onClickListener) {
        mAdapter = new ArrayAdapter<>(mContext, R.layout.list_item_simple, items);
        ListView listView = new ListView(mContext);
        listView.setAdapter(mAdapter);
        listView.setDivider(null);
        listView.setPadding(0, Util.dpToPx(mContext, 8), 0, Util.dpToPx(mContext, 8));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (onClickListener != null) {
                onClickListener.onItemClick(parent, view, position, id);
            }
        });
        return setCustomView(listView);
    }

    public ArrayAdapter getListAdapter() {
        return mAdapter;
    }


    public QKDialog setPositiveButton(boolean dismiss, int title, final OnClickListener onClickListener) {
        mPositiveButtonEnabled = true;
        mPositiveButtonText = mResources.getString(title);
        mPositiveButtonClickListener = v -> {
            if (onClickListener != null) {
                onClickListener.onClick(v);
                if (dismiss)
                    dismiss();
            }
        };
        return this;
    }

    @SuppressWarnings("unused")
    public QKDialog setNeutralButton(int resource, OnClickListener onClickListener) {
        mNeutralButtonEnabled = true;
        mNeutralButtonText = mResources.getString(resource);
        mNeutralButtonClickListener = v -> {
            if (onClickListener != null) {
                onClickListener.onClick(v);
            }
            dismiss();
        };
        return this;
    }

    public QKDialog setNegativeButton(int resource, OnClickListener onClickListener) {
        String text = mResources.getString(resource);
        if (text.isEmpty()) {
            mNegativeButtonEnabled = false;
            mNegativeButtonClickListener = null;
            mNegativeButtonText = "";
        }

        mNegativeButtonEnabled = true;
        mNegativeButtonText = text;
        mNegativeButtonClickListener = v -> {
            if (onClickListener != null) {
                onClickListener.onClick(v);
            }
            dismiss();
        };
        return this;
    }


    @Override
    public void onPause() {
        super.onPause();
        dismiss();
    }

    public void show() {
        setCancelable(true);
        try {
            super.show(mContext.getFragmentManager(), null);
        }
        catch (IllegalStateException ignored) {
            // Sometimes the context is destroyed, but the check for that is API 17+
        }
    }

}

