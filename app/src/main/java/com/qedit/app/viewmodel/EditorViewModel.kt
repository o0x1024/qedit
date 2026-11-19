package com.qedit.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qedit.app.data.SafFileRepository
import com.qedit.app.editor.SyntaxEngine
import com.qedit.app.editor.TextEditingActions
import com.qedit.app.model.DocumentTab
import com.qedit.app.model.FileType
import com.qedit.app.model.FolderEntry
import com.qedit.app.model.LineEnding
import com.qedit.app.model.RecentFileEntry
import com.qedit.app.model.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class UndoHistory(
    val undoStack: ArrayDeque<TextFieldValue> = ArrayDeque(),
    val redoStack: ArrayDeque<TextFieldValue> = ArrayDeque()
)

data class EditorUiState(
    val tabs: List<DocumentTab> = listOf(
        DocumentTab(
            displayName = "welcome.yaml",
            fileType = FileType.YAML,
            fieldValue = TextFieldValue(
                "# QEdit - Mobile Plain Text & YAML Editor\n" +
                    "app:\n" +
                    "  name: QEdit\n" +
                    "  features:\n" +
                    "    - SAF file & folder access\n" +
                    "    - Auto-indent for YAML\n" +
                    "    - Live syntax validation\n"
            ),
            savedContent = ""
        )
    ),
    val activeTabIndex: Int = 0,
    val softWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val activeMatchIndex: Int = -1,
    val recentFiles: List<RecentFileEntry> = emptyList(),
    val currentFolderUri: Uri? = null,
    val folderEntries: List<FolderEntry> = emptyList(),
    val isLoading: Boolean = false
) {
    val activeTab: DocumentTab?
        get() = tabs.getOrNull(activeTabIndex)
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SafFileRepository(application.applicationContext)
    private val historyMap = mutableMapOf<String, UndoHistory>()
    private var validationJob: Job? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refreshRecents()
        repository.getLastFolderUri()?.let { folderUri ->
            loadFolder(folderUri, silentOnError = true)
        }
        validateActiveTabDebounced(immediate = true)
    }

    fun selectTab(index: Int) {
        _uiState.update { state ->
            if (index in state.tabs.indices) {
                state.copy(activeTabIndex = index)
            } else {
                state
            }
        }
        recomputeSearchMatches()
        validateActiveTabDebounced(immediate = true)
    }

    fun newTab(fileName: String = "untitled.txt") {
        val tab = DocumentTab(
            displayName = fileName,
            fileType = FileType.fromFileName(fileName)
        )
        _uiState.update { state ->
            val updated = state.tabs + tab
            state.copy(tabs = updated, activeTabIndex = updated.lastIndex)
        }
        recomputeSearchMatches()
    }

    fun closeTab(index: Int) {
        _uiState.update { state ->
            if (index !in state.tabs.indices) return@update state
            val removed = state.tabs[index]
            historyMap.remove(removed.id)
            val remaining = state.tabs.toMutableList().apply { removeAt(index) }
            if (remaining.isEmpty()) {
                val fresh = DocumentTab()
                state.copy(tabs = listOf(fresh), activeTabIndex = 0)
            } else {
                val nextIndex = state.activeTabIndex.coerceAtMost(remaining.lastIndex)
                state.copy(tabs = remaining, activeTabIndex = nextIndex)
            }
        }
        recomputeSearchMatches()
        validateActiveTabDebounced(immediate = true)
    }

    fun openUri(uri: Uri) {
        val existingIndex = _uiState.value.tabs.indexOfFirst { it.uri == uri }
        if (existingIndex >= 0) {
            selectTab(existingIndex)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.takePersistablePermission(uri, allowWrite = true)
            repository.readDocument(uri)
                .onSuccess { loaded ->
                    val newTab = DocumentTab(
                        uri = loaded.uri,
                        displayName = loaded.displayName,
                        fieldValue = TextFieldValue(loaded.normalizedText),
                        savedContent = loaded.normalizedText,
                        lineEnding = loaded.lineEnding,
                        fileType = FileType.fromFileName(loaded.displayName)
                    )
                    repository.recordRecentFile(loaded.uri, loaded.displayName)
                    _uiState.update { state ->
                        val tabs = if (
                            state.tabs.size == 1 &&
                            state.tabs[0].uri == null &&
                            !state.tabs[0].isDirty &&
                            state.tabs[0].displayName == "welcome.yaml"
                        ) {
                            listOf(newTab)
                        } else {
                            state.tabs + newTab
                        }
                        state.copy(
                            tabs = tabs,
                            activeTabIndex = tabs.lastIndex,
                            isLoading = false,
                            recentFiles = repository.getRecentFiles()
                        )
                    }
                    recomputeSearchMatches()
                    validateActiveTabDebounced(immediate = true)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _messages.tryEmit("Open failed: ${error.localizedMessage ?: "Unknown error"}")
                }
        }
    }

    fun openFolder(treeUri: Uri) {
        repository.takePersistablePermission(treeUri, allowWrite = true)
        repository.saveLastFolderUri(treeUri)
        loadFolder(treeUri, silentOnError = false)
    }

    private fun loadFolder(treeUri: Uri, silentOnError: Boolean) {
        viewModelScope.launch {
            repository.listDirectory(treeUri)
                .onSuccess { entries ->
                    _uiState.update {
                        it.copy(currentFolderUri = treeUri, folderEntries = entries)
                    }
                }
                .onFailure { error ->
                    if (!silentOnError) {
                        _messages.tryEmit("Folder error: ${error.localizedMessage ?: "Cannot read folder"}")
                    }
                }
        }
    }

    fun saveActiveTab(onNeedSaveAs: () -> Unit) {
        val tab = _uiState.value.activeTab ?: return
        val uri = tab.uri
        if (uri == null) {
            onNeedSaveAs()
            return
        }
        writeToUri(tab, uri)
    }

    fun saveActiveTabAs(targetUri: Uri) {
        val tab = _uiState.value.activeTab ?: return
        repository.takePersistablePermission(targetUri, allowWrite = true)
        writeToUri(tab, targetUri)
    }

    private fun writeToUri(tab: DocumentTab, targetUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val textToSave = tab.fieldValue.text
            repository.writeDocument(targetUri, textToSave, tab.lineEnding)
                .onSuccess {
                    val newName = repository.resolveDisplayName(targetUri)
                    repository.recordRecentFile(targetUri, newName)
                    _uiState.update { state ->
                        val updatedTabs = state.tabs.map { current ->
                            if (current.id == tab.id) {
                                current.copy(
                                    uri = targetUri,
                                    displayName = newName,
                                    savedContent = textToSave,
                                    fileType = FileType.fromFileName(newName)
                                )
                            } else {
                                current
                            }
                        }
                        state.copy(
                            tabs = updatedTabs,
                            isLoading = false,
                            recentFiles = repository.getRecentFiles()
                        )
                    }
                    _messages.tryEmit("Saved $newName")
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _messages.tryEmit("Save failed: ${error.localizedMessage ?: "Permission denied"}")
                }
        }
    }

    fun onTextChanged(incoming: TextFieldValue) {
        val tab = _uiState.value.activeTab ?: return
        val previous = tab.fieldValue
        val processed = if (incoming.text != previous.text) {
            TextEditingActions.applySmartNewlineIfTriggered(previous, incoming, tab.fileType)
        } else {
            incoming
        }
        applyFieldValue(tab, processed, recordUndo = processed.text != previous.text)
    }

    fun indent() {
        val tab = _uiState.value.activeTab ?: return
        val next = TextEditingActions.indent(tab.fieldValue, tab.fileType.defaultIndentSize)
        applyFieldValue(tab, next, recordUndo = next.text != tab.fieldValue.text)
    }

    fun outdent() {
        val tab = _uiState.value.activeTab ?: return
        val next = TextEditingActions.outdent(tab.fieldValue, tab.fileType.defaultIndentSize)
        applyFieldValue(tab, next, recordUndo = next.text != tab.fieldValue.text)
    }

    fun insertSymbol(symbol: String) {
        val tab = _uiState.value.activeTab ?: return
        val next = TextEditingActions.insertSymbol(tab.fieldValue, symbol)
        applyFieldValue(tab, next, recordUndo = true)
    }

    fun moveCursor(delta: Int) {
        val tab = _uiState.value.activeTab ?: return
        val next = TextEditingActions.moveCursor(tab.fieldValue, delta)
        applyFieldValue(tab, next, recordUndo = false)
    }

    fun undo() {
        val tab = _uiState.value.activeTab ?: return
        val history = historyMap[tab.id] ?: return
        val previous = history.undoStack.removeLastOrNull() ?: return
        history.redoStack.addLast(tab.fieldValue)
        updateTabFieldValue(tab.id, previous)
    }

    fun redo() {
        val tab = _uiState.value.activeTab ?: return
        val history = historyMap[tab.id] ?: return
        val next = history.redoStack.removeLastOrNull() ?: return
        history.undoStack.addLast(tab.fieldValue)
        updateTabFieldValue(tab.id, next)
    }

    fun canUndo(): Boolean {
        val tab = _uiState.value.activeTab ?: return false
        return historyMap[tab.id]?.undoStack?.isNotEmpty() == true
    }

    fun canRedo(): Boolean {
        val tab = _uiState.value.activeTab ?: return false
        return historyMap[tab.id]?.redoStack?.isNotEmpty() == true
    }

    fun toggleSoftWrap() {
        _uiState.update { it.copy(softWrap = !it.softWrap) }
    }

    fun toggleLineNumbers() {
        _uiState.update { it.copy(showLineNumbers = !it.showLineNumbers) }
    }

    fun toggleLineEnding() {
        val tab = _uiState.value.activeTab ?: return
        val next = if (tab.lineEnding == LineEnding.LF) LineEnding.CRLF else LineEnding.LF
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map {
                    if (it.id == tab.id) it.copy(lineEnding = next) else it
                }
            )
        }
    }

    fun toggleSearch() {
        _uiState.update { it.copy(isSearchVisible = !it.isSearchVisible) }
        if (_uiState.value.isSearchVisible) {
            recomputeSearchMatches()
        } else {
            _uiState.update { it.copy(searchMatches = emptyList(), activeMatchIndex = -1) }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recomputeSearchMatches()
    }

    fun updateReplaceQuery(query: String) {
        _uiState.update { it.copy(replaceQuery = query) }
    }

    fun jumpToNextMatch(forward: Boolean) {
        val matches = _uiState.value.searchMatches
        if (matches.isEmpty()) return
        val current = _uiState.value.activeMatchIndex
        val nextIndex = if (forward) {
            if (current < 0 || current >= matches.lastIndex) 0 else current + 1
        } else {
            if (current <= 0) matches.lastIndex else current - 1
        }
        val target = matches[nextIndex]
        val tab = _uiState.value.activeTab ?: return
        _uiState.update { it.copy(activeMatchIndex = nextIndex) }
        updateTabFieldValue(tab.id, tab.fieldValue.copy(selection = target.range))
    }

    fun replaceCurrentMatch() {
        val state = _uiState.value
        val tab = state.activeTab ?: return
        val match = state.searchMatches.getOrNull(state.activeMatchIndex) ?: return
        val text = tab.fieldValue.text
        if (match.start < 0 || match.end > text.length) return
        val replaced = text.substring(0, match.start) + state.replaceQuery + text.substring(match.end)
        val cursor = match.start + state.replaceQuery.length
        applyFieldValue(
            tab,
            tab.fieldValue.copy(text = replaced, selection = TextRange(cursor, cursor)),
            recordUndo = true
        )
    }

    fun replaceAllMatches() {
        val state = _uiState.value
        val tab = state.activeTab ?: return
        if (state.searchQuery.isEmpty() || state.searchMatches.isEmpty()) return
        val replaced = tab.fieldValue.text.replace(state.searchQuery, state.replaceQuery, ignoreCase = true)
        applyFieldValue(
            tab,
            tab.fieldValue.copy(text = replaced, selection = TextRange(0, 0)),
            recordUndo = true
        )
    }

    fun jumpToLine(lineNumber: Int) {
        val tab = _uiState.value.activeTab ?: return
        val text = tab.fieldValue.text
        var currentLine = 1
        var offset = 0
        while (currentLine < lineNumber && offset < text.length) {
            val nl = text.indexOf('\n', offset)
            if (nl == -1) {
                offset = text.length
                break
            }
            offset = nl + 1
            currentLine++
        }
        updateTabFieldValue(tab.id, tab.fieldValue.copy(selection = TextRange(offset, offset)))
    }

    private fun applyFieldValue(tab: DocumentTab, next: TextFieldValue, recordUndo: Boolean) {
        if (recordUndo) {
            val history = historyMap.getOrPut(tab.id) { UndoHistory() }
            history.undoStack.addLast(tab.fieldValue)
            if (history.undoStack.size > 100) {
                history.undoStack.removeFirst()
            }
            history.redoStack.clear()
        }
        updateTabFieldValue(tab.id, next)
        if (next.text != tab.fieldValue.text) {
            recomputeSearchMatches()
            validateActiveTabDebounced(immediate = false)
        }
    }

    private fun updateTabFieldValue(tabId: String, next: TextFieldValue) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(fieldValue = next) else tab
                }
            )
        }
    }

    private fun recomputeSearchMatches() {
        val state = _uiState.value
        if (!state.isSearchVisible || state.searchQuery.isEmpty()) {
            _uiState.update { it.copy(searchMatches = emptyList(), activeMatchIndex = -1) }
            return
        }
        val text = state.activeTab?.fieldValue?.text.orEmpty()
        val query = state.searchQuery
        val matches = buildList {
            var index = text.indexOf(query, startIndex = 0, ignoreCase = true)
            while (index >= 0) {
                add(SearchMatch(index, index + query.length))
                index = text.indexOf(query, startIndex = index + query.length.coerceAtLeast(1), ignoreCase = true)
            }
        }
        _uiState.update {
            it.copy(
                searchMatches = matches,
                activeMatchIndex = if (matches.isEmpty()) -1 else 0
            )
        }
    }

    private fun validateActiveTabDebounced(immediate: Boolean) {
        val tab = _uiState.value.activeTab ?: return
        validationJob?.cancel()
        validationJob = viewModelScope.launch {
            if (!immediate) delay(250)
            val diagnostic = withContext(Dispatchers.Default) {
                SyntaxEngine.validate(tab.fieldValue.text, tab.fileType)
            }
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map {
                        if (it.id == tab.id) it.copy(diagnostic = diagnostic) else it
                    }
                )
            }
        }
    }

    private fun refreshRecents() {
        _uiState.update { it.copy(recentFiles = repository.getRecentFiles()) }
    }
}
