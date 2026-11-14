package com.example.android.notepad;

import android.graphics.Color;

/**
 * 笔记分类的统一定义：名称与配色。
 */
public final class CategoryUtil {

    /** 列表筛选中代表"不筛选"的伪分类 */
    public static final String ALL = "全部";

    /** 列表筛选栏使用的分类（含"全部"） */
    public static final String[] FILTER_CATEGORIES = {ALL, "工作", "生活", "学习", "灵感", "其他"};

    /** 笔记真正可以归属的分类 */
    public static final String[] NOTE_CATEGORIES = {"工作", "生活", "学习", "灵感", "其他"};

    private static final int COLOR_WORK = 0xFF4C6FFF;
    private static final int COLOR_LIFE = 0xFFFF9F43;
    private static final int COLOR_STUDY = 0xFF22B573;
    private static final int COLOR_IDEA = 0xFFA55EEA;
    private static final int COLOR_OTHER = 0xFF8395A7;

    private CategoryUtil() {
    }

    /** 校验一个分类名是否合法，非法则退回默认分类 */
    public static String normalize(String category) {
        if (category != null) {
            for (String c : NOTE_CATEGORIES) {
                if (c.equals(category)) {
                    return category;
                }
            }
        }
        return NotePad.Notes.DEFAULT_CATEGORY;
    }

    /** 分类对应的强调色 */
    public static int colorOf(String category) {
        String c = normalize(category);
        if ("工作".equals(c)) {
            return COLOR_WORK;
        } else if ("生活".equals(c)) {
            return COLOR_LIFE;
        } else if ("学习".equals(c)) {
            return COLOR_STUDY;
        } else if ("灵感".equals(c)) {
            return COLOR_IDEA;
        }
        return COLOR_OTHER;
    }

    /** 分类对应的浅色底（用于标签背景） */
    public static int tintOf(String category) {
        return withAlpha(colorOf(category), 0x1F);
    }

    /** 给颜色换一个透明度 */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** 颜色加深，用于浅色底上的文字 */
    public static int darken(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.rgb(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }
}
