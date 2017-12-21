package com.moez.QKSMS.ui;

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.util.LongSparseArray;
import android.view.View;

import com.moez.QKSMS.common.Util;

import java.util.HashMap;

public abstract class RecyclerCursorAdapter<VH extends RecyclerView.ViewHolder, DataType>
        extends RecyclerView.Adapter<VH> {

    public interface ItemClickListener<DataType> {
        void onItemClick(DataType object);

        void onItemLongClick(DataType object);
    }

    public interface MultiSelectListener {
        void onMultiSelectStateChanged();

        void onItemAdded();

        void onItemRemoved();
    }

    protected QKActivity mContext;
    protected Cursor mCursor;

    public RecyclerCursorAdapter(QKActivity context) {
        mContext = context;
    }

    private HashMap<Long, DataType> mSelectedItems = new HashMap<>();

    protected ItemClickListener<DataType> mItemClickListener;
    private RecyclerCursorAdapter.MultiSelectListener mMultiSelectListener;

    public void setItemClickListener(ItemClickListener<DataType> conversationClickListener) {
        mItemClickListener = conversationClickListener;
    }

    public void setMultiSelectListener(RecyclerCursorAdapter.MultiSelectListener multiSelectListener) {
        mMultiSelectListener = multiSelectListener;
    }

    public void changeCursor(Cursor cursor) {
        Cursor old;
        if (mCursor == cursor) {
            old = null;
        }
        else {
            Cursor oldCursor = mCursor;
            mCursor = cursor;
            if (cursor != null) {
                notifyDataSetChanged();
            }
            old = oldCursor;
        }


        if (old != null) {
            old.close();
        }
    }

    public Cursor getCursor() {
        return mCursor;
    }

    public int getCount() {
        return mCursor == null ? 0 : mCursor.getCount();
    }

    protected abstract DataType getItem(int position);

    public boolean isInMultiSelectMode() {
        return mSelectedItems.size() > 0;
    }

    public HashMap<Long, DataType> getSelectedItems() {
        return mSelectedItems;
    }

    public void disableMultiSelectMode() {
        if (isInMultiSelectMode()) {
            mSelectedItems.clear();
            notifyDataSetChanged();
            if (mMultiSelectListener != null) {
                mMultiSelectListener.onMultiSelectStateChanged();
            }
        }
    }

    protected boolean isSelected(long threadId) {
        return mSelectedItems.containsKey(threadId);
    }

    private void setSelected(long threadId, DataType object) {
        if (!mSelectedItems.containsKey(threadId)) {
            mSelectedItems.put(threadId, object);
            notifyDataSetChanged();

            if (mMultiSelectListener != null) {
                mMultiSelectListener.onItemAdded();

                if (mSelectedItems.size() == 1) {
                    mMultiSelectListener.onMultiSelectStateChanged();
                }
            }
        }
    }

    private void setUnselected(long threadId) {
        if (mSelectedItems.containsKey(threadId)) {
            mSelectedItems.remove(threadId);
            notifyDataSetChanged();

            if (mMultiSelectListener != null) {
                mMultiSelectListener.onItemRemoved();

                if (mSelectedItems.size() == 0) {
                    mMultiSelectListener.onMultiSelectStateChanged();
                }
            }
        }
    }

    public void toggleSelection(long threadId, DataType object) {
        if (isSelected(threadId)) {
            setUnselected(threadId);
        }
        else {
            setSelected(threadId, object);
        }
    }

    @Override
    public int getItemCount() {
        return Util.isValid(mCursor) ? mCursor.getCount() : 0;
    }
}
