# QEdit

[English](README.md) | [简体中文](README.zh-CN.md)

基于 **Kotlin** 与 **Jetpack Compose** 构建的轻量级原生 Android 纯文本与结构化配置文件编辑器，专为手机端高效编辑 `.txt`、`.yaml`/`.yml`、`.json`、`.md` 以及 `.ini`/`.conf`/`.toml` 等文件而设计。

## 核心特性

- **Android SAF 文件与目录访问**
  - 基于 Storage Access Framework（`OpenDocument` / `CreateDocument` / `OpenDocumentTree`）打开、保存、另存为文件及浏览工作区文件夹，并持久化最近打开记录。
  - 支持从外部文件管理器通过 `ACTION_VIEW` / `ACTION_EDIT` 直接调起编辑。
  - 使用 UTF-8 读写，自动识别并保留原文件换行符（`LF` / `CRLF`）。
- **移动端输入与排版优化**
  - 多标签页切换与未保存状态提示。
  - 同步行号栏与等宽字体编辑区，支持自动换行（Soft Wrap）开关。
  - **键盘快捷扩展栏（Accessory Bar）**：随软键盘联动吸附，提供 `Tab`、`⇧Tab`、`←`、`→`、撤销/重做及 `-` `:` `"` `'` `#` `{` `}` `[` `]` `=` `/` `_` 高频符号；选中文本点击引号或括号可自动成对包裹。
  - **智能缩进**：换行自动继承上一行缩进，YAML 在冒号 `:` 或列表项 `- ` 后自动补全缩进。
- **语法高亮与实时校验**
  - 支持 YAML、JSON、Markdown 及 INI/CONF/TOML 语法高亮。
  - 后台实时校验 YAML（基于 SnakeYAML）与 JSON 语法，状态栏提示错误行号并支持点击跳转。
  - 内置**查找与替换**（匹配计数、上/下一处跳转、单条替换与全部替换）。
- **跟随系统主题**
  - 界面配色与语法高亮调色板均自动随系统浅色/深色模式切换。

## 环境要求

- Android 8.0+（`minSdk = 26`，`targetSdk = 36`）
- JDK 17+（已在 OpenJDK 25 + Gradle 9.7.1 验证）
- Android SDK（`compileSdk = 36`）

## 构建与运行

```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 构建 Debug APK
./gradlew assembleDebug

# 安装到已连接的 Android 设备
./gradlew installDebug
```

## 目录结构

```text
app/src/main/java/com/qedit/app/
├── MainActivity.kt              # SAF 授权启动器与外部 VIEW/EDIT Intent 处理
├── data/SafFileRepository.kt    # UTF-8 文件读写、LF/CRLF 识别与最近文件记录
├── editor/
│   ├── SyntaxEngine.kt          # 浅色/深色语法高亮与 YAML/JSON 实时校验
│   └── TextEditingActions.kt    # 智能换行、缩进/反缩进、符号成对包裹
├── model/EditorModels.kt        # 标签页、文件类型、换行符与诊断数据模型
├── ui/
│   ├── EditorScreen.kt          # 主编辑界面、新建弹窗与工作区抽屉
│   ├── components/              # 标签栏、查找替换栏、状态栏、键盘快捷扩展栏
│   └── theme/QEditTheme.kt      # 跟随系统的 Material 3 浅色/深色主题
└── viewmodel/EditorViewModel.kt # 多标签状态管理、撤销/重做栈与防抖校验
```
