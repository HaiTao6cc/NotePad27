package com.example.android.notepad;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用偏好设置：背景颜色与列表排序方式。
 */
public final class AppPrefs {

    private static final String FILE = "notepad_prefs";
    private static final String KEY_THEME = "theme_index";
    private static final String KEY_SORT = "sort_index";

    /** 可选的背景配色（名称与色值一一对应） */
    public static final String[] THEME_NAMES = {"简约蓝", "纸白", "护眼绿", "米黄", "樱粉", "雾灰"};

    public static final int[] THEME_COLORS = {
            0xFFF3F6FF, // 简约蓝
            0xFFFDFCF8, // 纸白
            0xFFEDF7EF, // 护眼绿
            0xFFFFF8E7, // 米黄
            0xFFFDF0F3, // 樱粉
            0xFFF0F2F5  // 雾灰
    };

    /** 与排序弹窗选项一一对应的 SQL 排序方式 */
    public static final String[] SORT_ORDERS = {
            NotePad.Notes.SORT_MODIFIED_DESC,
            NotePad.Notes.SORT_MODIFIED_ASC,
            NotePad.Notes.SORT_CREATED_DESC,
            NotePad.Notes.SORT_CREATED_ASC
    };

    private AppPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int getThemeIndex(Context context) {
        int index = prefs(context).getInt(KEY_THEME, 0);
        return (index < 0 || index >= THEME_COLORS.length) ? 0 : index;
    }

    public static void setThemeIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY_THEME, index).apply();
    }

    public static String getThemeName(Context context) {
        return THEME_NAMES[getThemeIndex(context)];
    }

    /** 当前生效的页面背景色 */
    public static int getBackgroundColor(Context context) {
        return THEME_COLORS[getThemeIndex(context)];
    }

    public static int getSortIndex(Context context) {
        int index = prefs(context).getInt(KEY_SORT, 0);
        return (index < 0 || index >= SORT_ORDERS.length) ? 0 : index;
    }

    public static void setSortIndex(Context context, int index) {
        prefs(context).edit().putInt(KEY_SORT, index).apply();
    }

    /** 当前生效的 SQL 排序方式 */
    public static String getSortOrder(Context context) {
        return SORT_ORDERS[getSortIndex(context)];
    }
}
