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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 笔记编辑界面。
 *
 * 相比原始示例新增：
 * 1. 独立的标题输入框，标题可直接编辑（供列表按标题搜索）；
 * 2. 顶部/底部的分类选择与时间信息；
 * 3. 跟随设置变化的背景色；
 * 4. 自绘横向操作栏（返回 / 删除 / 保存），不依赖系统 ActionBar。
 */
public class NoteEditor extends Activity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /** 查询笔记所需的列 */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_CATEGORY,
    };

    private static final String TIME_PATTERN = "yyyy年MM月dd日 HH:mm";

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";
    private static final String ORIGINAL_TITLE = "origTitle";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mTitle;
    private EditText mText;
    private TextView mTimeView;
    private ChipBar mCategoryBar;

    private String mCategory = NotePad.Notes.DEFAULT_CATEGORY;
    private String mOriginalContent;
    private String mOriginalTitle;
    private long mModifiedMillis;

    /** 上次从数据库读出来的内容，用于判断是否真的有改动 */
    private String mLoadedContent = "";
    private String mLoadedTitle = "";
    private String mLoadedCategory = NotePad.Notes.DEFAULT_CATEGORY;

    /**
     * Defines a custom EditText View that draws lines between each line of text that is displayed.
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // This constructor is used by LayoutInflater
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // Creates a Rect and a Paint object, and sets the style and color of the Paint object.
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x334C6FFF);
            mPaint.setStrokeWidth(1f);
        }

        /**
         * This is called to draw the LinedEditText object
         * @param canvas The canvas on which the background is drawn.
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // Gets the number of lines of text in the View.
            int count = getLineCount();

            // Gets the global Rect and Paint objects
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * Draws one line in the rectangle for every line of text in the EditText
             */
            for (int i = 0; i < count; i++) {

                // Gets the baseline coordinates for the current line of text
                int baseline = getLineBounds(i, r);

                /*
                 * Draws a line in the background from the left of the rectangle to the right,
                 * at a vertical position one dip below the baseline, using the "paint" object
                 * for details.
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // Finishes up by calling the parent method
            super.onDraw(canvas);
        }
    }

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        boolean paste = false;

        // For an edit action:
        if (Intent.ACTION_EDIT.equals(action)) {

            // Sets the Activity state to EDIT, and gets the URI for the data to be edited.
            mState = STATE_EDIT;
            mUri = intent.getData();

            // For an insert or paste action:
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            // Sets the Activity state to INSERT, gets the general note URI, and inserts an
            // empty record in the provider
            mState = STATE_INSERT;
            Uri target = intent.getData() == null ? NotePad.Notes.CONTENT_URI : intent.getData();
            mUri = getContentResolver().insert(target, null);

            /*
             * If the attempt to insert the new note fails, shuts down this Activity. The
             * originating Activity receives back RESULT_CANCELED if it requested a result.
             * Logs that the insert failed.
             */
            if (mUri == null) {

                // Writes the log identifier, a message, and the URI that failed.
                Log.e(TAG, "Failed to insert new note into " + target);

                // Closes the activity.
                finish();
                return;
            }

            // Since the new entry was created, this sets the result to be returned
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

            paste = Intent.ACTION_PASTE.equals(action);

        // If the action was other than EDIT or INSERT:
        } else {

            // Logs an error that the action was not understood, finishes the Activity, and
            // returns RESULT_CANCELED to an originating Activity.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * Using the URI passed in with the triggering Intent, gets the note or notes in
         * the provider.
         */
        mCursor = managedQuery(
            mUri,         // The URI that gets multiple notes from the provider.
            PROJECTION,   // 标题、内容、修改时间与分类
            null,         // No "where" clause selection criteria.
            null,         // No "where" clause selection values.
            null          // Use the default sort order (modification date, descending)
        );

        // Sets the layout for this Activity. See res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // 背景颜色跟随设置
        applyChrome();

        mTitle = (EditText) findViewById(R.id.editor_title);
        mText = (EditText) findViewById(R.id.note);
        mTimeView = (TextView) findViewById(R.id.editor_time);

        setupActions();
        setupCategoryBar();

        /*
         * If this Activity had stopped previously, its state was written the ORIGINAL_CONTENT
         * location in the saved Instance state. This gets the state.
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
            mOriginalTitle = savedInstanceState.getString(ORIGINAL_TITLE);
        }

        // For a paste, initializes the data from clipboard.
        // (Must be done after views are ready.)
        if (paste) {
            performPaste();
        }

        // 新建笔记时直接聚焦标题并弹出键盘，少一次点击
        if (mState == STATE_INSERT) {
            mTitle.requestFocus();
            mTitle.post(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager imm = (InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(mTitle, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
        }
    }

    /** 顶部操作栏：返回 / 删除 / 保存 */
    private void setupActions() {        ImageButton back = (ImageButton) findViewById(R.id.btn_back);
        ImageButton delete = (ImageButton) findViewById(R.id.btn_delete);
        TextView save = (TextView) findViewById(R.id.btn_save);

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCursor != null) {
                    saveNote();
                }
                finish();
            }
        });
    }

    /** 让页面与系统导航栏使用同一套背景色 */
    private void applyChrome() {
        int bg = AppPrefs.getBackgroundColor(this);
        findViewById(R.id.editor_root).setBackgroundColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void setupCategoryBar() {
        LinearLayout container = (LinearLayout) findViewById(R.id.editor_category_bar);
        mCategoryBar = new ChipBar(this, container, CategoryUtil.NOTE_CATEGORIES,
                indexOfCategory(mCategory), new ChipBar.Listener() {
                    @Override
                    public void onChipSelected(int index, String value) {
                        mCategory = value;
                    }
                });
        mCategoryBar.setColors(getColorRes(R.color.primary), 0xFFFFFFFF,
                0xFFFFFFFF, getColorRes(R.color.ink_sub), true);
        mCategoryBar.build();
    }

    private int indexOfCategory(String category) {
        for (int i = 0; i < CategoryUtil.NOTE_CATEGORIES.length; i++) {
            if (CategoryUtil.NOTE_CATEGORIES[i].equals(category)) {
                return i;
            }
        }
        return CategoryUtil.NOTE_CATEGORIES.length - 1;
    }

    /**
     * This method is called when the Activity is about to come to the foreground. This happens
     * when the Activity comes to the top of the task stack, OR when it is first starting.
     */
    @Override
    protected void onResume() {
        super.onResume();

        /*
         * mCursor is initialized, since onCreate() always precedes onResume for any running
         * process. This tests that it's not null, since it should always contain data.
         */
        if (mCursor != null) {
            // Requery in case something changed while paused (such as the title)
            mCursor.requery();

            /* Moves to the first record. Always call moveToFirst() before accessing data in
             * a Cursor for the first time.
             */
            mCursor.moveToFirst();

            int titleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
            int noteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            int categoryIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
            int modifiedIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);

            String title = titleIndex >= 0 ? mCursor.getString(titleIndex) : "";
            String note = noteIndex >= 0 ? mCursor.getString(noteIndex) : "";
            if (note == null) {
                note = "";
            }
            if (title == null) {
                title = "";
            }

            if (categoryIndex >= 0) {
                mCategory = CategoryUtil.normalize(mCursor.getString(categoryIndex));
                mCategoryBar.setSelectedIndex(indexOfCategory(mCategory));
            }
            if (modifiedIndex >= 0) {
                mModifiedMillis = mCursor.getLong(modifiedIndex);
            }

            // Gets the note text from the Cursor and puts it in the TextView, but doesn't change
            // the text cursor's position.
            mText.setTextKeepState(note);
            if (mTitle.getText().length() == 0) {
                mTitle.setTextKeepState(title);
            }

            // Stores the original note text, to allow the user to revert changes.
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }
            if (mOriginalTitle == null) {
                mOriginalTitle = title;
            }

            // 记录刚读出来的内容，onPause 时用来判断是否真的有改动
            mLoadedContent = note;
            mLoadedTitle = mTitle.getText().toString();
            mLoadedCategory = mCategory;

            updateTimeLabel();

        /*
         * Something is wrong. The Cursor should always contain data. Report an error in the
         * note.
         */
        } else {
            mTitle.setText(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    /** 底部时间信息 */
    private void updateTimeLabel() {
        if (mTimeView == null) {
            return;
        }
        if (mModifiedMillis <= 0) {
            mTimeView.setText("");
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat(TIME_PATTERN, Locale.CHINA);
        String text = format.format(new Date(mModifiedMillis));
        mTimeView.setText(getString(
                mState == STATE_INSERT ? R.string.editor_created : R.string.editor_modified, text));
    }

    /**
     * This method is called when an Activity loses focus during its normal operation, and is then
     * later on killed. The Activity has a chance to save its state so that the system can restore
     * it.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
        outState.putString(ORIGINAL_TITLE, mOriginalTitle);
    }

    /**
     * This method is called when the Activity loses focus.
     *
     * If the user hasn't done anything, then this deletes or clears out the note, otherwise it
     * writes the user's work to the provider.
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor == null) {
            return;
        }

        String text = mText.getText().toString();
        String title = mTitle.getText().toString().trim();

        // 新建的笔记什么都没写就离开：直接丢弃，避免列表里多出一条空笔记
        if (mState == STATE_INSERT && text.length() == 0 && title.length() == 0) {
            deleteNote();
            if (!isFinishing()) {
                finish();
            }
            return;
        }

        /*
         * If the Activity is in the midst of finishing and there is no text in the current
         * note, returns a result of CANCELED to the caller, and deletes the note.
         */
        if (isFinishing() && text.length() == 0 && title.length() == 0) {
            setResult(RESULT_CANCELED);
            deleteNote();
            return;
        }

        // 只有内容真的变了才写回，避免只是"看一眼"就把修改时间刷新
        if (isDirty(text, title)) {
            saveNote();
        }
    }

    /** 内容、标题、分类是否与刚读出来时不同 */
    private boolean isDirty(String text, String title) {
        return !text.equals(mLoadedContent)
                || !title.equals(mLoadedTitle)
                || !mCategory.equals(mLoadedCategory);
    }

    /**
     * This method is called when the user clicks the device's Menu button the first time for
     * this Activity.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // Only add extra menu items for a saved note
        if (mState == STATE_EDIT && mUri != null) {
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem revert = menu.findItem(R.id.menu_revert);
        if (revert != null) {
            boolean changed = !mText.getText().toString().equals(
                    mOriginalContent == null ? "" : mOriginalContent);
            revert.setVisible(changed);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This method is called when a menu item is selected.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            if (mCursor != null) {
                saveNote();
            }
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_revert) {
            cancelNote();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A helper method that replaces the note's data with the contents of the clipboard.
     */
    private void performPaste() {

        // Gets a handle to the Clipboard Manager
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // Gets a content resolver instance
        ContentResolver cr = getContentResolver();

        // Gets the clipboard data from the clipboard
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null) {
            return;
        }

        String text = null;
        String title = null;

        // Gets the first item from the clipboard data
        ClipData.Item item = clip.getItemAt(0);

        // Tries to get the item's contents as a URI pointing to a note
        Uri uri = item.getUri();

        // Tests to see that the item actually is an URI, and that the URI
        // is a content URI pointing to a provider whose MIME type is the same
        // as the MIME type supported by the Note pad provider.
        if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

            // The clipboard holds a reference to data with a note MIME type. This copies it.
            Cursor orig = cr.query(uri, PROJECTION, null, null, null);

            // If the Cursor is not null, and it contains at least one record
            // (moveToFirst() returns true), then this gets the note data from it.
            if (orig != null) {
                if (orig.moveToFirst()) {
                    int noteIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                    int titleIndex = orig.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                    text = noteIndex >= 0 ? orig.getString(noteIndex) : null;
                    title = titleIndex >= 0 ? orig.getString(titleIndex) : null;
                }
                orig.close();
            }
        }

        // If the contents of the clipboard wasn't a reference to a note, then
        // this converts whatever it is to text.
        if (text == null) {
            text = item.coerceToText(this).toString();
        }

        mText.setText(text);
        if (title != null) {
            mTitle.setText(title);
        }

        // 粘贴后立即写入 provider，避免退出时被当成空笔记删掉
        saveNote();
    }

    /**
     * 把当前编辑的内容写回 provider：标题、正文、分类以及修改时间。
     */
    private void saveNote() {
        String body = mText.getText().toString();
        String title = mTitle.getText().toString().trim();
        if (title.length() == 0) {
            title = deriveTitle(body);
            // 把自动生成的标题回填到输入框，用户下次打开能看到
            mTitle.setText(title);
        }

        long now = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, body);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mCategory);

        getContentResolver().update(mUri, values, null, null);

        mModifiedMillis = now;
        mState = STATE_EDIT;

        // 同步"已保存"快照，避免 onPause 再写一次
        mLoadedContent = body;
        mLoadedTitle = title;
        mLoadedCategory = mCategory;

        updateTimeLabel();
    }

    /** 没填标题时，用正文第一行前 30 个字作为标题 */
    private String deriveTitle(String body) {
        if (body == null) {
            return "无标题";
        }
        String firstLine = body.trim();
        int newLine = firstLine.indexOf('\n');
        if (newLine > 0) {
            firstLine = firstLine.substring(0, newLine).trim();
        }
        if (firstLine.length() == 0) {
            return "无标题";
        }
        if (firstLine.length() > 30) {
            firstLine = firstLine.substring(0, 30).trim();
        }
        return firstLine;
    }

    /**
     * This helper method cancels the work done on a note. It deletes the note if it was
     * newly created, or reverts to the original text of the note.
     */
    private void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // Put the original note text back into the database
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE,
                        mOriginalContent == null ? "" : mOriginalContent);
                values.put(NotePad.Notes.COLUMN_NAME_TITLE,
                        mOriginalTitle == null ? "" : mOriginalTitle);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // We inserted an empty note, make sure to delete it
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_delete)
                .setMessage("删除后无法恢复，确定删除这条笔记吗？")
                .setPositiveButton(R.string.menu_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteNote();
                        finish();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /**
     * Take care of deleting a note. Simply deletes the entry.
     */
    private void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
            mTitle.setText("");
        }
    }

    @SuppressWarnings("deprecation")
    private int getColorRes(int resId) {
        return getResources().getColor(resId);
    }
}
