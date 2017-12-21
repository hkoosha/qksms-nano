package com.moez.QKSMS.ui.conversationlist;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.moez.QKSMS.Blocker;
import com.moez.QKSMS.QKPreference;
import com.moez.QKSMS.QKSMSApp;
import com.moez.QKSMS.R;
import com.moez.QKSMS.common.Util;
import com.moez.QKSMS.data.Contact;
import com.moez.QKSMS.data.Conversation;
import com.moez.QKSMS.data.ConversationLegacy;
import com.moez.QKSMS.sms.SmsHelper;
import com.moez.QKSMS.ui.MainActivity;
import com.moez.QKSMS.ui.QKActivity;
import com.moez.QKSMS.ui.RecyclerCursorAdapter;
import com.moez.QKSMS.ui.ThemeManager;
import com.moez.QKSMS.ui.UiUtil;
import com.moez.QKSMS.ui.compose.ComposeActivity;
import com.moez.QKSMS.ui.messagelist.MessageListActivity;

import java.util.Observer;


public class ConversationListFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        RecyclerCursorAdapter.ItemClickListener<Conversation>,
        RecyclerCursorAdapter.MultiSelectListener {

    public static final String TAG = "ConversationListFragment";

    private static final int LOADER_CONVERSATIONS = 0;

    private View mEmptyState;
    private RecyclerView mRecyclerView;

    private ConversationListAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private SharedPreferences mPrefs;
    private MenuItem mBlockedItem;
    private boolean mShowBlocked = false;

    private QKActivity mContext;

    // This does not hold the current position of the list, rather the position
    // the list is pending being set to
    private int mPosition;

    private final Observer observer = (observable, o) -> this.initLoaderManager();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        setHasOptionsMenu(true);

        mAdapter = new ConversationListAdapter(mContext);
        mAdapter.setItemClickListener(this);
        mAdapter.setMultiSelectListener(this);
        mLayoutManager = new LinearLayoutManager(mContext);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversations, null);

        mEmptyState = view.findViewById(R.id.empty_state);

        mRecyclerView = view.findViewById(R.id.conversations_list);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);

        ImageView mEmptyStateIcon = view.findViewById(R.id.empty_state_icon);
        mEmptyStateIcon.setColorFilter(ThemeManager.getTextOnBackgroundPrimary());
        mEmptyStateIcon.setColorFilter(ThemeManager.getTextOnBackgroundPrimary());

        FloatingActionButton mFab = view.findViewById(R.id.fab);
        mFab.setBackgroundColor(ThemeManager.getColor());
        mFab.setBackgroundTintList(ColorStateList.valueOf(ThemeManager.getColor()));
        mFab.setImageResource(R.drawable.ic_add);
        mFab.getDrawable()
            .setColorFilter(ThemeManager.getTextOnColorPrimary(), PorterDuff.Mode.SRC_ATOP);
        mFab.setRippleColor(Util.lighten(ThemeManager.getColor()));
        mFab.setColorFilter(ThemeManager.getTextOnColorPrimary());
        mFab.setOnClickListener(v -> {
            mAdapter.disableMultiSelectMode();
            mContext.startActivity(ComposeActivity.class);
        });

        initLoaderManager();

        Blocker.FutureBlockedConversationObservable.getInstance().addObserver(this.observer);

        return view;
    }

    /**
     * Returns the weighting for unread vs. read conversations that are selected, to decide
     * which options we should show in the multi selction toolbar
     */
    private int getUnreadWeight() {
        int unreadWeight = 0;
        for (Conversation conversation : mAdapter.getSelectedItems().values()) {
            unreadWeight += conversation.hasUnreadMessages() ? 1 : -1;
        }
        return unreadWeight;
    }

    /**
     * Returns the weighting for blocked vs. unblocked conversations that are selected
     */
    private int getBlockedWeight() {
        int blockedWeight = 0;
        for (Conversation conversation : mAdapter.getSelectedItems().values()) {
            blockedWeight += Blocker.isConversationBlocked(
                    mPrefs, conversation.getThreadId()) ? 1 : -1;
        }
        return blockedWeight;
    }

    public void inflateToolbar(Menu menu, MenuInflater inflater) {
        if (mAdapter.isInMultiSelectMode()) {
            inflater.inflate(R.menu.conversations_selection, menu);
            mContext.setTitle(getString(R.string.title_conversations_selected,
                                        "" + mAdapter.getSelectedItems().size()));

            menu.findItem(R.id.menu_block)
                .setVisible(mPrefs.getBoolean(QKPreference.K_BLOCKED_ENABLED, false));

            menu.findItem(R.id.menu_mark_read)
                .setIcon(getUnreadWeight() >= 0
                         ? R.drawable.ic_mark_read
                         : R.drawable.ic_mark_unread);
            menu.findItem(R.id.menu_mark_read)
                .setTitle(getUnreadWeight() >= 0
                          ? R.string.menu_mark_read
                          : R.string.menu_mark_unread);
            menu.findItem(R.id.menu_block)
                .setTitle(getBlockedWeight() > 0
                          ? R.string.menu_unblock_conversations
                          : R.string.menu_block_conversations);

            boolean doSomeHaveErrors = false;
            for (Conversation conversation : mAdapter.getSelectedItems().values()) {
                if (conversation.hasError()) {
                    doSomeHaveErrors = true;
                    break;
                }
            }
            menu.findItem(R.id.menu_delete_failed).setVisible(doSomeHaveErrors);
        }
        else {
            inflater.inflate(R.menu.conversations, menu);
            mContext.setTitle(mShowBlocked
                              ? R.string.title_blocked
                              : R.string.title_conversation_list);

            mBlockedItem = menu.findItem(R.id.menu_blocked);
            Blocker.bindBlockedMenuItem(mContext,
                                        mPrefs,
                                        mBlockedItem,
                                        mShowBlocked);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_blocked:
                setShowingBlocked(!mShowBlocked);
                return true;

            case R.id.menu_delete:
                UiUtil.showDeleteConversationsDialog(mContext,
                                                     mAdapter.getSelectedItems().keySet());
                mAdapter.disableMultiSelectMode();
                return true;

            case R.id.menu_mark_read:
                for (long threadId : mAdapter.getSelectedItems().keySet()) {
                    if (getUnreadWeight() >= 0) {
                        new ConversationLegacy(threadId).markRead(this.getView(), mContext);
                    }
                    else {
                        new ConversationLegacy(threadId).markUnread(getView(), mContext);
                    }
                }
                mAdapter.disableMultiSelectMode();
                return true;

            case R.id.menu_block:
                for (long threadId : mAdapter.getSelectedItems().keySet()) {
                    if (getBlockedWeight() > 0) {
                        Blocker.unblockConversation(mPrefs, threadId);
                    }
                    else {
                        Blocker.blockConversation(mPrefs, threadId);
                    }
                }
                mAdapter.disableMultiSelectMode();
                initLoaderManager();
                return true;

            case R.id.menu_delete_failed:
                UiUtil.showDeleteFailedMessagesDialog((MainActivity) mContext,
                                                      mAdapter.getSelectedItems().keySet());
                mAdapter.disableMultiSelectMode();
                return true;

            case R.id.menu_done:
                mAdapter.disableMultiSelectMode();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean isShowingBlocked() {
        return mShowBlocked;
    }

    public void setShowingBlocked(boolean showBlocked) {
        mShowBlocked = showBlocked;
        mContext.setTitle(mShowBlocked ? R.string.title_blocked : R.string.title_conversation_list);
        Blocker.bindBlockedMenuItem(mContext, mPrefs, mBlockedItem, mShowBlocked);
        initLoaderManager();
    }

    @Override
    public void onItemClick(Conversation conversation) {
        if (mAdapter.isInMultiSelectMode()) {
            mAdapter.toggleSelection(conversation.getThreadId(), conversation);
        }
        else {
            MessageListActivity.launch(mContext, conversation.getThreadId(), -1, null);
        }
    }

    @Override
    public void onItemLongClick(final Conversation conversation) {
        mAdapter.toggleSelection(conversation.getThreadId(), conversation);
    }

    private void initLoaderManager() {
        getLoaderManager().restartLoader(LOADER_CONVERSATIONS,
                                         null,
                                         this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        QKSMSApp.getRefWatcher(getActivity()).watch(this);
        Blocker.FutureBlockedConversationObservable
                .getInstance().deleteObserver(this.observer);

        if (null == mRecyclerView) {
            return;
        }

        try {
            for (int i = 0; i < mRecyclerView.getChildCount(); i++) {
                View child = mRecyclerView.getChildAt(i);
                RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(child);
                if (holder instanceof ConversationListViewHolder) {
                    Contact.removeListener((ConversationListViewHolder) holder);
                }
            }
        }
        catch (Exception ignored) {
            //
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
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id != LOADER_CONVERSATIONS) {
            return null;
        }

        return new CursorLoader(mContext,
                                SmsHelper.CONVERSATIONS_CONTENT_PROVIDER,
                                Conversation.ALL_THREADS_PROJECTION,
                                Blocker.getCursorSelection(mPrefs, mShowBlocked),
                                Blocker.getBlockedConversationArray(mPrefs),
                                "date DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == LOADER_CONVERSATIONS) {
            if (mAdapter != null) {
                // Swap the new cursor in.  (The framework will take care of closing the
                // old cursor once we return.)
                mAdapter.changeCursor(data);
                if (mPosition != 0) {
                    mRecyclerView.scrollToPosition(Math.min(mPosition, data.getCount() - 1));
                    mPosition = 0;
                }
            }

            mEmptyState.setVisibility(data != null && data.getCount() > 0
                                      ? View.GONE
                                      : View.VISIBLE);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (mAdapter != null && loader.getId() == LOADER_CONVERSATIONS) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    public void onMultiSelectStateChanged() {
        mContext.invalidateOptionsMenu();
    }

    @Override
    public void onItemAdded() {
        mContext.invalidateOptionsMenu();
    }

    @Override
    public void onItemRemoved() {
        mContext.invalidateOptionsMenu();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = (QKActivity) activity;
    }

}
