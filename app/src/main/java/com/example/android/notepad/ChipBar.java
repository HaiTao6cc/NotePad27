package com.example.android.notepad;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 横向排列的"胶囊"选择栏，用于分类筛选与分类选择。
 * 由调用方决定配色，因此列表页（深色背景）与编辑页（浅色背景）可以复用。
 */
public class ChipBar {

    public interface Listener {
        void onChipSelected(int index, String value);
    }

    private final Context mContext;
    private final LinearLayout mContainer;
    private final String[] mItems;
    private final Listener mListener;
    private final List<TextView> mChips = new ArrayList<TextView>();

    private int mSelectedIndex;
    private int mSelectedBg = 0xFFFFFFFF;
    private int mSelectedText = 0xFF4C6FFF;
    private int mNormalBg = 0x33FFFFFF;
    private int mNormalText = 0xFFFFFFFF;
    private boolean mNormalBorder = false;

    public ChipBar(Context context, LinearLayout container, String[] items,
                   int selectedIndex, Listener listener) {
        mContext = context;
        mContainer = container;
        mItems = items;
        mSelectedIndex = selectedIndex;
        mListener = listener;
    }

    /** 设置配色。normalBorder 为 true 时未选中项会带一圈淡描边 */
    public ChipBar setColors(int selectedBg, int selectedText,
                             int normalBg, int normalText, boolean normalBorder) {
        mSelectedBg = selectedBg;
        mSelectedText = selectedText;
        mNormalBg = normalBg;
        mNormalText = normalText;
        mNormalBorder = normalBorder;
        return this;
    }

    public void build() {
        mContainer.removeAllViews();
        mChips.clear();
        for (int i = 0; i < mItems.length; i++) {
            final int index = i;
            TextView chip = new TextView(mContext);
            chip.setText(mItems[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            int padH = dp(14);
            int padV = dp(7);
            chip.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mSelectedIndex == index) {
                        return;
                    }
                    mSelectedIndex = index;
                    render();
                    if (mListener != null) {
                        mListener.onChipSelected(index, mItems[index]);
                    }
                }
            });

            mChips.add(chip);
            mContainer.addView(chip);
        }
        render();
    }

    public int getSelectedIndex() {
        return mSelectedIndex;
    }

    public String getSelectedValue() {
        return mItems[mSelectedIndex];
    }

    /** 外部改变选中项（不会触发回调） */
    public void setSelectedIndex(int index) {
        if (index < 0 || index >= mItems.length || index == mSelectedIndex) {
            return;
        }
        mSelectedIndex = index;
        render();
    }

    private void render() {
        for (int i = 0; i < mChips.size(); i++) {
            TextView chip = mChips.get(i);
            boolean selected = (i == mSelectedIndex);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(100));
            bg.setColor(selected ? mSelectedBg : mNormalBg);
            if (!selected && mNormalBorder) {
                bg.setStroke(dp(1), CARD_STROKE);
            }
            chip.setBackground(bg);
            chip.setTextColor(selected ? mSelectedText : mNormalText);
            chip.getPaint().setFakeBoldText(selected);
            chip.setAlpha(selected ? 1f : 0.92f);
        }
    }

    private static final int CARD_STROKE = 0x1F000000;

    private int dp(float value) {
        return (int) (value * mContext.getResources().getDisplayMetrics().density + 0.5f);
    }
}
