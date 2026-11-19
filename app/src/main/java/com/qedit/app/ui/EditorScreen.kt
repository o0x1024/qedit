package com.qedit.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qedit.app.R
import com.qedit.app.editor.EditorVisualTransformation
import com.qedit.app.ui.components.KeyboardAccessoryBar
import com.qedit.app.ui.components.SearchReplaceBar
import com.qedit.app.ui.components.StatusBar
import com.qedit.app.ui.components.TabStrip
import com.qedit.app.ui.components.WorkspaceSheetContent
import com.qedit.app.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onRequestOpenFile: () -> Unit,
    onRequestSaveAs: (suggestedName: String) -> Unit,
    onRequestOpenFolder: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showMenu by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showWorkspaceSheet by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("note.yaml") }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val activeTab = uiState.activeTab

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeTab?.let {
                            if (it.isDirty) "${it.displayName} *" else it.displayName
                        } ?: "QEdit",
                        maxLines = 1,
                        fontSize = 17.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { showWorkspaceSheet = true }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.cd_workspace_recents))
                    }
                    IconButton(onClick = onRequestOpenFile) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_open_file))
                    }
                    IconButton(
                        onClick = {
                            val name = activeTab?.displayName ?: "untitled.txt"
                            viewModel.saveActiveTab { onRequestSaveAs(name) }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.cd_save))
                    }
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_new_file)) },
                                onClick = {
                                    showMenu = false
                                    showNewFileDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_save_as)) },
                                onClick = {
                                    showMenu = false
                                    onRequestSaveAs(activeTab?.displayName ?: "untitled.txt")
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (uiState.softWrap) R.string.menu_disable_wrap else R.string.menu_enable_wrap
                                        )
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleSoftWrap()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (uiState.showLineNumbers) R.string.menu_hide_line_numbers else R.string.menu_show_line_numbers
                                        )
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleLineNumbers()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            TabStrip(
                tabs = uiState.tabs,
                activeTabIndex = uiState.activeTabIndex,
                onSelectTab = viewModel::selectTab,
                onCloseTab = viewModel::closeTab
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (uiState.isSearchVisible) {
                SearchReplaceBar(
                    searchQuery = uiState.searchQuery,
                    replaceQuery = uiState.replaceQuery,
                    matchCount = uiState.searchMatches.size,
                    activeMatchIndex = uiState.activeMatchIndex,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onReplaceQueryChange = viewModel::updateReplaceQuery,
                    onPrevMatch = { viewModel.jumpToNextMatch(forward = false) },
                    onNextMatch = { viewModel.jumpToNextMatch(forward = true) },
                    onReplaceOne = viewModel::replaceCurrentMatch,
                    onReplaceAll = viewModel::replaceAllMatches,
                    onClose = viewModel::toggleSearch
                )
            }

            if (activeTab != null) {
                val verticalScroll = rememberScrollState()
                val horizontalScroll = rememberScrollState()
                val isDark = isSystemInDarkTheme()
                val visualTransformation = remember(
                    activeTab.fileType,
                    uiState.searchMatches,
                    uiState.activeMatchIndex,
                    isDark
                ) {
                    EditorVisualTransformation(
                        fileType = activeTab.fileType,
                        searchMatches = uiState.searchMatches,
                        activeMatchIndex = uiState.activeMatchIndex,
                        isDarkTheme = isDark
                    )
                }

                val lineCount = remember(activeTab.fieldValue.text) {
                    activeTab.fieldValue.text.count { it == '\n' } + 1
                }
                val (cursorLine, cursorCol) = activeTab.cursorLineAndColumn()

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(verticalScroll)
                ) {
                    if (uiState.showLineNumbers) {
                        val gutterText = remember(lineCount) {
                            (1..lineCount).joinToString("\n")
                        }
                        Text(
                            text = gutterText,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier
                                .widthIn(min = 42.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        )
                    }

                    val editorModifier = if (uiState.softWrap) {
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(horizontalScroll)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    }

                    BasicTextField(
                        value = activeTab.fieldValue,
                        onValueChange = viewModel::onTextChanged,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = visualTransformation,
                        modifier = editorModifier
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                StatusBar(
                    fileTypeLabel = activeTab.fileType.label,
                    lineEndingLabel = activeTab.lineEnding.label,
                    line = cursorLine,
                    column = cursorCol,
                    diagnostic = activeTab.diagnostic,
                    onToggleLineEnding = viewModel::toggleLineEnding,
                    onDiagnosticClick = viewModel::jumpToLine
                )

                KeyboardAccessoryBar(
                    canUndo = viewModel.canUndo(),
                    canRedo = viewModel.canRedo(),
                    onIndent = viewModel::indent,
                    onOutdent = viewModel::outdent,
                    onMoveCursor = viewModel::moveCursor,
                    onInsertSymbol = viewModel::insertSymbol,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo
                )
            }
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(stringResource(R.string.dialog_new_file_title)) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text(stringResource(R.string.dialog_new_file_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = newFileName.trim().ifEmpty { "untitled.txt" }
                        viewModel.newTab(clean)
                        showNewFileDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showWorkspaceSheet) {
        ModalBottomSheet(onDismissRequest = { showWorkspaceSheet = false }) {
            WorkspaceSheetContent(
                folderEntries = uiState.folderEntries,
                recentFiles = uiState.recentFiles,
                onPickFolder = {
                    showWorkspaceSheet = false
                    onRequestOpenFolder()
                },
                onOpenFolderEntry = { entry ->
                    if (entry.isDirectory) {
                        viewModel.openFolder(entry.uri)
                    } else {
                        showWorkspaceSheet = false
                        viewModel.openUri(entry.uri)
                    }
                },
                onOpenRecentUri = { uri: Uri ->
                    showWorkspaceSheet = false
                    viewModel.openUri(uri)
                }
            )
        }
    }
}
