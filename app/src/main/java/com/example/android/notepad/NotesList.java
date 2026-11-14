/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

/**
 * 笔记列表。
 *
 * 在原始 NotePad 示例的基础上扩展了：
 * 1. 每条笔记显示「年-月-日 时:分」时间戳；
 * 2. 顶部搜索框，按标题或内容模糊查询；
 * 3. 排序方式可切换（修改时间 / 创建时间，正序或倒序）；
 * 4. 分类筛选栏（全部 / 工作 / 生活 / 学习 / 灵感 / 其他）；
 * 5. 背景颜色可更换（设置保存在 SharedPreferences 中）。
 *
 * 注意：为了保持示例的可读性，这里的 provider 操作仍放在 UI 线程执行。
 * 真实项目应使用 AsyncQueryHandler 或后台线程。
 */
public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    /** 列表查询需要的列 */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                              // 0
            NotePad.Notes.COLUMN_NAME_TITLE,                // 1
            NotePad.Notes.COLUMN_NAME_NOTE,                 // 2
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,    // 3
            NotePad.Notes.COLUMN_NAME_CATEGORY,             // 4
    };

    private static final int COLUMN_INDEX_TITLE = 1;

    /** 搜索输入的防抖间隔（毫秒） */
    private static final long SEARCH_DEBOUNCE = 180L;

    private View mRoot;
    private TextView mSubtitle;
    private TextView mEmptyView;
    private EditText mSearchInput;
    private ImageButton mClearButton;
    private ChipBar mCategoryBar;

    private NoteAdapter mAdapter;

    private String mKeyword = "";
    private String mCategory = CategoryUtil.ALL;

    private final Handler mHandler = new Handler();

    private final Runnable mSearchTask = new Runnable() {
        @Override
        public void run() {
            requery();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        setContentView(R.layout.notes_list);

        mRoot = findViewById(R.id.root);
        mSubtitle = (TextView) findViewById(R.id.app_bar_subtitle);
        mEmptyView = (TextView) findViewById(android.R.id.empty);
        mSearchInput = (EditText) findViewById(R.id.search_input);
        mClearButton = (ImageButton) findViewById(R.id.btn_clear);
        mClearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSearchInput.setText("");
            }
        });

        // 分类筛选栏
        LinearLayout categoryContainer = (LinearLayout) findViewById(R.id.category_bar);
        mCategoryBar = new ChipBar(this, categoryContainer, CategoryUtil.FILTER_CATEGORIES, 0,
                new ChipBar.Listener() {
                    @Override
                    public void onChipSelected(int index, String value) {
                        mCategory = value;
                        requery();
                    }
                });
        mCategoryBar.setColors(0xFFFFFFFF, getColorRes(R.color.primary),
                0x33FFFFFF, 0xFFFFFFFF, false);
        mCategoryBar.build();

        // 搜索框：输入停顿后再查询，避免每个字符都查一次数据库
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mKeyword = s.toString().trim();
                mClearButton.setVisibility(mKeyword.length() > 0 ? View.VISIBLE : View.GONE);
                mHandler.removeCallbacks(mSearchTask);
                mHandler.postDelayed(mSearchTask, SEARCH_DEBOUNCE);
            }
        });

        // 新建笔记
        findViewById(R.id.fab_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI));
            }
        });

        // 排序
        findViewById(R.id.btn_sort).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortDialog();
            }
        });

        // 背景颜色
        findViewById(R.id.btn_theme).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThemeDialog();
            }
        });

        // 更多（粘贴剪贴板 / 关于）
        findViewById(R.id.btn_more).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu(v);
            }
        });

        getListView().setOnCreateContextMenuListener(this);

        // 兜底：如果 Activity 是被别的应用用某个 URI 启动的，仍然只展示本地笔记
        if (getIntent().getData() == null) {
            getIntent().setData(NotePad.Notes.CONTENT_URI);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 背景颜色可能在上一个界面被改过
        applyChrome();
        requery();
    }

    /** 让页面与系统导航栏使用同一套背景色，避免底部出现突兀的黑条 */
    private void applyChrome() {
        int bg = AppPrefs.getBackgroundColor(this);
        mRoot.setBackgroundColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mSearchTask);
        if (mAdapter != null) {
            // 传入 null 会顺带关闭旧游标
            mAdapter.changeCursor(null);
            mAdapter = null;
        }
        super.onDestroy();
    }

    /**
     * 按当前的关键字、分类、排序方式重新查询笔记列表。
     */
    private void requery() {
        if (mAdapter == null && isFinishing()) {
            return;
        }

        StringBuilder selection = new StringBuilder();
        java.util.ArrayList<String> args = new java.util.ArrayList<String>();

        if (!CategoryUtil.ALL.equals(mCategory)) {
            selection.append(NotePad.Notes.COLUMN_NAME_CATEGORY).append(" = ?");
            args.add(mCategory);
        }
        if (mKeyword.length() > 0) {
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append("(")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ?)");
            String like = "%" + mKeyword + "%";
            args.add(like);
            args.add(like);
        }

        String selectionStr = selection.length() == 0 ? null : selection.toString();
        String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);

        Cursor cursor;
        try {
            cursor = getContentResolver().query(
                    NotePad.Notes.CONTENT_URI,
                    PROJECTION,
                    selectionStr,
                    selectionArgs,
                    AppPrefs.getSortOrder(this));
        } catch (Exception e) {
            Log.e(TAG, "query failed", e);
            cursor = null;
        }

        if (cursor == null) {
            return;
        }

        if (mAdapter == null) {
            mAdapter = new NoteAdapter(this, cursor);
            setListAdapter(mAdapter);
        } else {
            // changeCursor 会负责关闭旧游标
            mAdapter.changeCursor(cursor);
        }

        updateHeader();
    }

    /** 刷新标题下方"共 N 条"的统计与空列表提示 */
    private void updateHeader() {
        int count = mAdapter == null ? 0 : mAdapter.getCount();
        boolean filtering = mKeyword.length() > 0 || !CategoryUtil.ALL.equals(mCategory);
        mSubtitle.setText(getString(
                filtering ? R.string.count_filtered : R.string.count_format, count));

        if (mEmptyView != null) {
            mEmptyView.setText(mKeyword.length() > 0 ? R.string.empty_search : R.string.empty_notes);
        }
    }

    /** 排序方式选择 */
    private void showSortDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_title)
                .setSingleChoiceItems(R.array.sort_options, AppPrefs.getSortIndex(this),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                AppPrefs.setSortIndex(NotesList.this, which);
                                dialog.dismiss();
                                requery();
                            }
                        })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /** 背景颜色选择 */
    private void showThemeDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(AppPrefs.THEME_NAMES, AppPrefs.getThemeIndex(this),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                AppPrefs.setThemeIndex(NotesList.this, which);
                                applyChrome();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /** 右上角更多菜单 */
    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.menu_paste);
        popup.getMenu().add(0, 2, 1, R.string.menu_sort);
        popup.getMenu().add(0, 3, 2, R.string.menu_theme);
        popup.getMenu().add(0, 4, 3, R.string.menu_about);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        startActivity(new Intent(Intent.ACTION_PASTE, NotePad.Notes.CONTENT_URI));
                        return true;
                    case 2:
                        showSortDialog();
                        return true;
                    case 3:
                        showThemeDialog();
                        return true;
                    case 4:
                        new AlertDialog.Builder(NotesList.this)
                                .setTitle(R.string.app_name)
                                .setMessage(R.string.about_message)
                                .setPositiveButton(R.string.button_ok, null)
                                .show();
                        return true;
                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        String title = null;
        if (mAdapter != null) {
            Cursor cursor = mAdapter.getCursor();
            if (cursor != null && cursor.moveToPosition(info.position)) {
                title = cursor.getString(COLUMN_INDEX_TITLE);
            }
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);
        menu.setHeaderTitle(title == null || title.trim().length() == 0 ? "无标题" : title);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        final Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, info.id);
        int id = item.getItemId();

        if (id == R.id.context_open) {
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_category) {
            showChangeCategoryDialog(noteUri, info.position);
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(), "Note", noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            confirmDelete(noteUri);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /** 长按菜单里直接修改分类 */
    private void showChangeCategoryDialog(final Uri noteUri, int position) {
        String current = NotePad.Notes.DEFAULT_CATEGORY;
        if (mAdapter != null) {
            Cursor cursor = mAdapter.getCursor();
            int idx = cursor == null ? -1 : cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
            if (cursor != null && idx >= 0 && cursor.moveToPosition(position)) {
                current = CategoryUtil.normalize(cursor.getString(idx));
            }
        }

        int checked = 0;
        for (int i = 0; i < CategoryUtil.NOTE_CATEGORIES.length; i++) {
            if (CategoryUtil.NOTE_CATEGORIES[i].equals(current)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_category)
                .setSingleChoiceItems(CategoryUtil.NOTE_CATEGORIES, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ContentValues values = new ContentValues();
                                values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                                        CategoryUtil.NOTE_CATEGORIES[which]);
                                getContentResolver().update(noteUri, values, null, null);
                                dialog.dismiss();
                                requery();
                            }
                        })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void confirmDelete(final Uri noteUri) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_delete)
                .setMessage("删除后无法恢复，确定删除这条笔记吗？")
                .setPositiveButton(R.string.menu_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getContentResolver().delete(noteUri, null, null);
                        requery();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, id);
        String action = getIntent().getAction();

        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }

    @SuppressWarnings("deprecation")
    private int getColorRes(int resId) {
        return getResources().getColor(resId);
    }
}
