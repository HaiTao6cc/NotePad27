# 记事本 NotePad

基于 Android 官方 **NotePad** 示例扩展而来的本地记事本应用。在保留 `ContentProvider + SQLite` 原始架构的前提下，补齐了时间戳、搜索、分类、排序、换肤等能力，并重做了整套界面。

> 技术栈：Java · ContentProvider · SQLite · 原生 View / 自定义 View · Gradle
> 无任何第三方依赖，不使用 AppCompat / Material / Room。

---

## 目录

- [功能特性](#功能特性)
- [界面与操作](#界面与操作)
- [技术实现](#技术实现)
- [项目结构](#项目结构)
- [构建与运行](#构建与运行)
- [已知限制](#已知限制)
- [参考与许可](#参考与许可)

---

## 功能特性

### 基本要求

| 功能 | 实现说明 |
| --- | --- |
| **列表时间戳** | 每张卡片右下角显示 `yyyy年MM月dd日 HH:mm`（年月日 + 时分），由 `NoteAdapter` 对 `modified` 字段格式化。 |
| **笔记查询** | 顶部搜索框按 **标题或内容** 模糊匹配，SQL 条件为 `(title LIKE ? OR note LIKE ?)`；输入停顿 180ms 才发起查询，避免逐字刷新；右侧有 × 一键清空；无结果时展示空状态提示。 |

### 附加功能

| 功能 | 实现说明 |
| --- | --- |
| **笔记分类** | `notes` 表新增 `category` 列，内置 **工作 / 生活 / 学习 / 灵感 / 其他** 五类。列表顶部胶囊栏可筛选（含「全部」），编辑页顶部可切换，长按卡片也能直接改分类。卡片左侧显示分类色条，底部显示彩色标签。 |
| **笔记排序** | 右上角排序按钮，四种方式：修改时间 / 创建时间 × 由新到旧 / 由旧到新。选择结果写入 `SharedPreferences`，重启后保持。 |
| **更换背景颜色** | 内置 **简约蓝 / 纸白 / 护眼绿 / 米黄 / 樱粉 / 雾灰** 六套浅色主题，列表页与编辑页同步生效；系统导航栏跟随主题，状态栏保持品牌蓝以保证顶栏连贯，选择持久化。 |
| **UI 美化** | 渐变圆角顶栏、卡片式列表（圆角 + 阴影 + 点击水波）、胶囊分类栏、悬浮新建按钮、空列表引导页、自绘编辑工具栏（返回 / 删除 / 保存）。 |

### 交互细节

- **粘贴**：编辑页菜单可把剪贴板内容插入正文。
- **长按菜单**：打开 / 更改分类 / 复制 / 删除（删除带二次确认）。
- **标题自动生成**：标题留空时，自动取正文首行作为标题。
- **只读不脏写**：打开笔记不做修改就返回，**不会**刷新修改时间、不会把笔记顶到列表最前（原示例存在这个问题）。
- **空白笔记自动清理**：新建后什么都没写就退出，这条笔记会被自动删除。

---

## 界面与操作

| 想做什么 | 操作位置 |
| --- | --- |
| 新建笔记 | 列表页右下角悬浮按钮 `+` |
| 搜索 | 列表页顶部搜索框，输入即筛（标题 + 内容） |
| 按分类筛选 | 列表页顶部胶囊栏，点击「全部 / 工作 / 生活 …」 |
| 切换排序方式 | 列表页右上角排序图标 → 四选一 |
| 切换背景颜色 | 列表页右上角调色板图标 → 六选一 |
| 改分类 / 删除 / 复制 | **长按**列表卡片 → 弹出菜单 |
| 编辑标题 | 编辑页顶部标题输入框 |
| 切换分类 | 编辑页顶部分类胶囊栏 |
| 保存 | 编辑页右上角保存按钮（返回时也会自动保存） |

---

## 技术实现

### 整体架构

沿用官方示例的分层，UI 不做直接数据库操作，全部经 `ContentResolver` 走 `NotePadProvider`：

```
NotesList / NoteEditor / TitleEditor     ← Activity（UI 层）
            │  ContentResolver
            ▼
     NotePadProvider                     ← ContentProvider（数据访问层）
            │  SQLiteDatabase
            ▼
      notes 表（notepad.db）              ← 持久化
```

`NotePad.java` 是契约类，集中定义 AUTHORITY、表名、列名、Uri 与排序常量，UI 层与 Provider 层共用，避免字符串散落。

### 数据库表结构

表名 `notes`，数据库版本 **3**：

| 列名 | 类型 | 说明 |
| --- | --- | --- |
| `_id` | INTEGER | 主键，自增 |
| `title` | TEXT | 标题，可为空字符串 |
| `note` | TEXT | 正文 |
| `created` | INTEGER | 创建时间戳（毫秒） |
| `modified` | INTEGER | 修改时间戳（毫秒） |
| `category` | TEXT | 分类，默认 `其他`（v3 新增） |

**版本升级**：v2 → v3 用 `ALTER TABLE ... ADD COLUMN` 增量加列，**不清空已有笔记**；只有在 `ALTER TABLE` 异常时才降级为重建表：

```java
try {
    db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT DEFAULT '其他'");
} catch (SQLException e) {
    Log.w(TAG, "ALTER TABLE failed, recreating table", e);
    db.execSQL("DROP TABLE IF EXISTS notes");
    onCreate(db);
}
```

### 核心模块

| 文件 | 行数 | 职责 |
| --- | ---: | --- |
| `NotePad.java` | 173 | 契约类：表名 / 列名 / Uri / 四种排序常量 / 默认分类 |
| `NotePadProvider.java` | 775 | 数据库创建与升级、Uri 匹配、增删改查、默认值填充 |
| `NotesList.java` | 484 | 列表页：搜索去抖、分类筛选、排序、换肤、长按菜单、空状态 |
| `NoteEditor.java` | 713 | 编辑页：标题输入、分类选择、时间显示、脏数据判断、自动保存 |
| `NoteAdapter.java` | 116 | 卡片渲染：标题 / 摘要 / 分类色条与标签 / 时间戳 |
| `ChipBar.java` | 137 | 自定义胶囊选择栏，列表页（深色底）与编辑页（浅色底）复用 |
| `AppPrefs.java` | 73 | 主题与排序的偏好存取 |
| `CategoryUtil.java` | 72 | 分类名称、顺序、配色的统一管理 |
| `TitleEditor.java` | 167 | 官方示例保留的「单独编辑标题」入口 |
| `NotesLiveFolder.java` | 113 | 官方示例保留的 Live Folder 支持 |

### 几个关键点

**1. 搜索去抖 + 条件组合**
每次输入不立即查库，`Handler.postDelayed` 延迟 180ms，连续输入会不断取消上一次任务，只查最后一次：

```java
private static final long SEARCH_DEBOUNCE = 180L;
// afterTextChanged 中：
mHandler.removeCallbacks(mSearchTask);
mHandler.postDelayed(mSearchTask, SEARCH_DEBOUNCE);
```

分类与关键词用 `StringBuilder` 拼成组合条件，参数全部走 `?` 占位符，避免 SQL 注入：

```java
selection.append("(" + COLUMN_NAME_TITLE + " LIKE ? OR " + COLUMN_NAME_NOTE + " LIKE ?)");
```

**2. 脏数据判断，避免无意义写库**
进入编辑页时对标题 / 正文 / 分类做一次快照，`onPause` 时比对，只有真正变化才 `update` 并刷新 `modified`。这修掉了原示例「看一眼就把笔记顶到最前」的老毛病。

**3. 游标生命周期**
`CursorAdapter.changeCursor()` 会自行关闭旧游标，因此在 `requery()` 里只调用 `changeCursor`，不再手动 `close`，否则可能重复关闭抛异常；`onDestroy` 里才统一 `changeCursor(null)` 释放。

**4. 自定义 View 做胶囊栏**
`ChipBar` 不是 `View` 子类，而是一个「渲染器」：构造时接收外部传入的 `LinearLayout` 容器与数据数组，按需生成圆角 `TextView`（背景用 `GradientDrawable` 动态绘制），并通过 `Listener` 回调把选中项抛给调用方。选中态与普通态的配色由调用方设置，因此同一份逻辑能在列表页（深色底、白色选中）和编辑页（浅色底、主题色选中）两边复用。

**5. 主题贯穿全链路**
背景色不只作用在根布局：列表页根容器、编辑页根容器、系统导航栏（`getWindow().setNavigationBarColor()`）都取自 `AppPrefs.getBackgroundColor()`；状态栏固定为品牌蓝 `@color/primary`，与渐变顶栏连成一体。卡片始终保持白色，保证任何主题下都有足够对比度。

**6. XML 里的内部类写法**
`NoteEditor` 内部自定义了带横线的 `EditText`，布局中不能写 `<com.example.android.notepad.NoteEditor$LinedEditText>`（`$` 不是合法 XML 名称字符），必须写成：

```xml
<view class="com.example.android.notepad.NoteEditor$LinedEditText" ... />
```

---

## 项目结构

```
NotePad/
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/android/notepad/
│       │   │   ├── NotePad.java            契约类
│       │   │   ├── NotePadProvider.java    ContentProvider + SQLite
│       │   │   ├── NotesList.java          列表页
│       │   │   ├── NoteEditor.java         编辑页
│       │   │   ├── TitleEditor.java        标题编辑
│       │   │   ├── NotesLiveFolder.java    Live Folder
│       │   │   ├── NoteAdapter.java        列表适配器
│       │   │   ├── ChipBar.java            自定义胶囊栏
│       │   │   ├── AppPrefs.java           偏好存储
│       │   │   └── CategoryUtil.java       分类与配色
│       │   └── res/
│       │       ├── layout/                 notes_list / noteslist_item / note_editor / title_editor
│       │       ├── drawable/               背景与图标（全部矢量 VectorDrawable）
│       │       ├── menu/                   长按菜单、编辑页菜单
│       │       └── values/                 colors / styles / strings / arrays
│       └── androidTest/                    Provider 与 Activity 的仪器测试
├── build.gradle
├── settings.gradle
└── gradle/wrapper/
```

---

## 构建与运行

### 环境

| 项 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.12.2 |
| Gradle | 8.13 |
| JDK | 17 |
| compileSdk / buildTools | 37 / 36.0.0 |
| minSdk / targetSdk | 21 / 34 |

仓库里的 `gradle-wrapper.properties` 指向腾讯云镜像，`build.gradle` 优先走阿里云 Maven 镜像，国内网络下首次构建会快很多。

### 命令行

```bash
# 编译 Debug 包
./gradlew assembleDebug

# 产物位置
app/build/outputs/apk/debug/app-debug.apk
```

跑到设备上：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.android.notepad/.NotesList
```

### Android Studio

直接 `File → Open` 打开项目根目录，等待 Gradle sync 完成后点 Run 即可。`local.properties` 里的 SDK 路径由 IDE 自动生成，已被 `.gitignore` 排除。

---

## 已知限制

- **搜索基于 SQL `LIKE`**：只做子串匹配，没有分词、拼音检索或相关性排序。
- **分类是固定五类**：写在 `CategoryUtil` 中，暂不支持用户自定义增删。
- **数据库升级的降级分支会丢数据**：正常路径走 `ALTER TABLE` 不丢数据；仅当加列失败时才重建表（这是为兼容异常旧库留的兜底）。
- **无云同步 / 无导出**：数据只存在本机 `notepad.db`，卸载应用即丢失。
- **targetSdk 34**：在 Android 15 及以上系统上需自行确认运行时行为（本项目已在 API 35 模拟器实测通过）。

---

## 参考与许可

- 原始工程：[Android Sample — NotePad](https://github.com/android/platform_development/tree/master/samples/NotePad)
- 源码解析：[Android Sample--NotePad 解析](https://blog.csdn.net/llfjfz/article/details/67638499)
- 本项目沿用官方示例的 **Apache License 2.0**，详见源文件头部声明。
