# QEdit

[English](README.md) | [简体中文](README.zh-CN.md)

A lightweight, native Android plain-text and structured-config editor built with **Kotlin** and **Jetpack Compose**, designed for editing `.txt`, `.yaml`/`.yml`, `.json`, `.md`, and `.ini`/`.conf`/`.toml` files directly on mobile devices.

## Features

- **Storage Access Framework (SAF) Integration**
  - Open, save, and "Save As" files from local storage or any Android `DocumentProvider`.
  - Browse workspace folders (`OpenDocumentTree`) and quickly reopen recent files.
  - Supports `ACTION_VIEW` / `ACTION_EDIT` intents from external file managers.
  - Detects and preserves original line endings (`LF` / `CRLF`) using UTF-8 encoding.
- **Mobile-First Editing Ergonomics**
  - Multi-tab document switching with unsaved-change indicators.
  - Synchronized line-number gutter and monospace editor with optional soft word wrap.
  - **Keyboard Accessory Bar** docked above the soft keyboard: `Tab`, `⇧Tab`, `←`, `→`, `Undo`, `Redo`, and quick symbol keys (`-`, `:`, `"`, `'`, `#`, `{`, `}`, `[`, `]`, `=`, `/`, `_`), including paired wrapping when text is selected.
  - **Smart Auto-Indent**: preserves leading indentation on newline and adds YAML indentation after `:` or `- `.
- **Syntax Highlighting & Live Validation**
  - Syntax highlighting for YAML, JSON, Markdown, and INI/CONF/TOML files.
  - Background YAML (via SnakeYAML) and JSON syntax validation with clickable line/column error jump in the status bar.
  - Built-in **Find & Replace** with match navigation and batch replacement.
- **System Theme Support**
  - Automatically follows the system light/dark theme for both UI surfaces and syntax-highlighting palettes.

## Requirements

- Android 8.0+ (`minSdk = 26`, `targetSdk = 36`)
- JDK 17+ (tested with OpenJDK 25 & Gradle 9.7.1)
- Android SDK (`compileSdk = 36`)

## Build & Run

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Install on a connected Android device via adb
./gradlew installDebug
```

## Project Structure

```text
app/src/main/java/com/qedit/app/
├── MainActivity.kt              # SAF launchers & VIEW/EDIT intent handling
├── data/SafFileRepository.kt    # UTF-8 SAF file I/O, CRLF/LF detection, recents
├── editor/
│   ├── SyntaxEngine.kt          # Light/dark syntax highlighting & YAML/JSON validation
│   └── TextEditingActions.kt    # Smart newline, indent/outdent, symbol pairing
├── model/EditorModels.kt        # DocumentTab, FileType, LineEnding, diagnostics
├── ui/
│   ├── EditorScreen.kt          # Main editor layout, dialogs, and workspace sheet
│   ├── components/              # TabStrip, SearchReplaceBar, StatusBar, AccessoryBar
│   └── theme/QEditTheme.kt      # System-aware Material 3 light/dark theme
└── viewmodel/EditorViewModel.kt # Multi-tab state, undo/redo stacks, debounced validation
```
