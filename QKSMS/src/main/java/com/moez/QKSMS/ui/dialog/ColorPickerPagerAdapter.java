package com.moez.QKSMS.ui.dialog;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import com.moez.QKSMS.R;

public class ColorPickerPagerAdapter extends PagerAdapter {

    private final String palletString;

    public ColorPickerPagerAdapter(Context context) {
        this.palletString = context.getString(R.string.title_palette);
    }

    @NonNull
    public Object instantiateItem(@NonNull ViewGroup collection, int position) {
        return collection.findViewById(R.id.palette);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return palletString;
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public boolean isViewFromObject(@NonNull View arg0, @NonNull Object arg1) {

        return arg0 == arg1;
    }

}
