package com.moez.QKSMS.ui.compose;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.android.ex.chips.recipientchip.DrawableRecipientChip;
import com.moez.QKSMS.QKSMSApp;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.PhoneNumberUtils;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.RecipientProvider;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.messagelist.MessageListActivity;
import com.moez.QKSMS.ui.view.AutoCompleteContactView;
import com.moez.QKSMS.ui.view.ComposeView;

public class ComposeFragment extends Fragment implements
        RecipientProvider,
        ComposeView.OnSendListener,
        AdapterView.OnItemClickListener {

    public static final String TAG = "ComposeFragment";

    private AutoCompleteContactView mRecipients;
    private ComposeView mComposeView;

    private QKActivity mContext;

    public ComposeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_compose, container, false);

        mRecipients = view.findViewById(R.id.compose_recipients);
        mRecipients.setOnItemClickListener(this);

        mComposeView = view.findViewById(R.id.compose_view);
        mComposeView.onOpenConversation(null, null);
        mComposeView.setRecipientProvider(this);
        mComposeView.setOnSendListener(this);

        new Handler().postDelayed(() -> Util.showKeyboard(mContext, mRecipients), 100);

        return view;
    }

    @Override
    public void onSend(String[] recipients) {
        long threadId = Util.getOrCreateThreadId(mContext, recipients[0]);
        if (threadId != 0) {
            mContext.finish();
            MessageListActivity.launch(mContext, threadId, -1, null);
        }
        else {
            mContext.onBackPressed();
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getView() != null) {
            getView().setBackgroundColor(ThemeManager.getBackgroundColor());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        QKSMSApp.getRefWatcher(getActivity()).watch(this);
        if (mComposeView != null) {
            mComposeView.saveDraft();
        }
    }

    /**
     * @return the addresses of all the contacts in the AutoCompleteContactsView.
     */
    @Override
    public String[] getRecipientAddresses() {
        DrawableRecipientChip[] chips = mRecipients.getRecipients();
        String[] addresses = new String[chips.length];

        for (int i = 0; i < chips.length; i++) {
            addresses[i] = PhoneNumberUtils.stripSeparators(chips[i].getEntry().getDestination());
        }

        return addresses;
    }

    /**
     * Photo Selection result
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mRecipients.onItemClick(parent, view, position, id);
        mComposeView.requestReplyTextFocus();
    }

    public boolean isReplyTextEmpty() {
        return mComposeView == null || mComposeView.isReplyTextEmpty();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = (QKActivity) activity;
    }
}
