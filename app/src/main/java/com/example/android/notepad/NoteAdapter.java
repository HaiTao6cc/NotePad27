package com.example.android.notepad;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 笔记列表适配器：把一条笔记渲染成
 * 「分类色条 + 标题 + 内容摘要 + 分类标签 + 时间戳」的卡片。
 */
public class NoteAdapter extends CursorAdapter {

    /** 时间戳统一格式：年月日 + 时间 */
    private static final String TIME_PATTERN = "yyyy年MM月dd日 HH:mm";

    private final LayoutInflater mInflater;
    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat(TIME_PATTERN, Locale.CHINA);
    private final float mDensity;

    private final int mTitleIndex;
    private final int mNoteIndex;
    private final int mModifiedIndex;
    private final int mCategoryIndex;

    private static class Holder {
        View accent;
        TextView title;
        TextView snippet;
        TextView category;
        TextView time;
    }

    public NoteAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);
        mInflater = LayoutInflater.from(context);
        mDensity = context.getResources().getDisplayMetrics().density;

        mTitleIndex = cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE);
        mNoteIndex = cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_NOTE);
        mModifiedIndex = cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);
        mCategoryIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.noteslist_item, parent, false);
        Holder holder = new Holder();
        holder.accent = view.findViewById(R.id.item_accent);
        holder.title = (TextView) view.findViewById(R.id.item_title);
        holder.snippet = (TextView) view.findViewById(R.id.item_snippet);
        holder.category = (TextView) view.findViewById(R.id.item_category);
        holder.time = (TextView) view.findViewById(R.id.item_time);
        view.setTag(holder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        Holder holder = (Holder) view.getTag();

        // 标题（为空时用占位文案，避免条目看起来是空白）
        String title = cursor.getString(mTitleIndex);
        if (title == null || title.trim().length() == 0) {
            title = "无标题";
        }
        holder.title.setText(title);

        // 内容摘要：换行折叠成空格，最多显示两行
        String note = cursor.getString(mNoteIndex);
        String snippet = note == null ? "" : note.replace('\n', ' ').replace('\r', ' ').trim();
        if (snippet.length() == 0) {
            holder.snippet.setVisibility(View.GONE);
        } else {
            holder.snippet.setVisibility(View.VISIBLE);
            holder.snippet.setText(snippet);
        }

        // 时间戳：yyyy年MM月dd日 HH:mm
        long modified = cursor.getLong(mModifiedIndex);
        holder.time.setText(mDateFormat.format(new Date(modified)));

        // 分类标签与左侧色条
        String category = mCategoryIndex >= 0 ? cursor.getString(mCategoryIndex) : null;
        category = CategoryUtil.normalize(category);
        holder.category.setText(category);

        int color = CategoryUtil.colorOf(category);

        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setShape(GradientDrawable.RECTANGLE);
        accentBg.setColor(color);
        accentBg.setCornerRadius(dp(2));
        holder.accent.setBackground(accentBg);

        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setShape(GradientDrawable.RECTANGLE);
        chipBg.setColor(CategoryUtil.tintOf(category));
        chipBg.setCornerRadius(dp(100));
        holder.category.setBackground(chipBg);
        holder.category.setTextColor(CategoryUtil.darken(color, 0.82f));
    }

    private int dp(float value) {
        return (int) (value * mDensity + 0.5f);
    }
}
