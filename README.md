# NotePad

项目来自于官方早期的数据库操作的基本教程。
更多的解析参考[Android Sample--NotePad解析](https://blog.csdn.net/llfjfz/article/details/67638499)

## 功能说明

### 基本要求

1. **列表时间戳**：每条笔记显示 `yyyy年MM月dd日 HH:mm` 格式的修改时间，由 `NoteAdapter` 格式化 `modified` 字段。
2. **笔记查询**：列表顶部搜索框按**标题或内容**模糊匹配（`title LIKE ? OR note LIKE ?`），输入停顿 180ms 后查询，右侧一键清空。

### 附加功能

1. **笔记分类**：`notes` 表新增 `category` 列（工作 / 生活 / 学习 / 灵感 / 其他）。列表顶部可按分类筛选，编辑页顶部可切换分类，长按笔记也能直接改分类；分类在卡片左侧以色条、下方以彩色标签呈现。
2. **笔记排序**：右上角排序按钮可在「修改时间 / 创建时间」×「由新到旧 / 由旧到新」四种方式间切换，选择结果持久化。
3. **更换背景颜色**：内置简约蓝、纸白、护眼绿、米黄、樱粉、雾灰 6 套配色，列表页与编辑页同步生效，系统导航栏一并跟随。
4. **UI 美化**：渐变顶栏、卡片式列表、胶囊分类筛选、悬浮新建按钮、空列表引导、自绘编辑工具栏（返回 / 删除 / 保存）。
5. **其它细节**：粘贴剪贴板、长按复制 URI / 删除（带二次确认）、标题自动从正文首行生成、只浏览不修改不会刷新修改时间、放弃空白新笔记时自动清理。

## 代码结构

| 文件 | 说明 |
| --- | --- |
| `NotePad.java` | 契约类，新增 `COLUMN_NAME_CATEGORY` 与四种排序常量 |
| `NotePadProvider.java` | 数据库版本升到 3，新增分类列并用 `ALTER TABLE` 平滑升级 |
| `NotesList.java` | 列表页：搜索、分类筛选、排序、背景切换、长按菜单 |
| `NoteAdapter.java` | 卡片式列表项渲染（标题 / 摘要 / 分类 / 时间戳） |
| `NoteEditor.java` | 编辑页：标题输入、分类选择、时间信息、脏数据判断 |
| `ChipBar.java` | 可复用的胶囊选择栏（分类筛选 / 分类选择） |
| `AppPrefs.java` | 背景配色与排序方式的偏好存储 |
| `CategoryUtil.java` | 分类名称与配色的统一管理 |

## 运行

```bash
./gradlew assembleDebug
```

`minSdkVersion 21`、`targetSdkVersion 34`，无第三方依赖，纯 framework 实现。
