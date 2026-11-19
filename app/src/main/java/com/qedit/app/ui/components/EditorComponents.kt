package com.qedit.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qedit.app.model.DocumentTab
import com.qedit.app.model.FolderEntry
import com.qedit.app.model.RecentFileEntry
import com.qedit.app.model.SyntaxDiagnostic

@Composable
fun TabStrip(
    tabs: List<DocumentTab>,
    activeTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == activeTabIndex
            val bg = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                Color.Transparent
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelectTab(index) }
                    .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.isDirty) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = tab.displayName,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onCloseTab(index) }
                )
            }
        }
    }
}

@Composable
fun SearchReplaceBar(
    searchQuery: String,
    replaceQuery: String,
    matchCount: Int,
    activeMatchIndex: Int,
    onSearchQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactInputBox(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Find...",
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (matchCount == 0) "0/0" else "${activeMatchIndex + 1}/$matchCount",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onPrevMatch, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onNextMatch, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(18.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactInputBox(
                value = replaceQuery,
                onValueChange = onReplaceQueryChange,
                placeholder = "Replace...",
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onReplaceOne,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Replace", fontSize = 12.sp)
            }
            TextButton(
                onClick = onReplaceAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("All", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CompactInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StatusBar(
    fileTypeLabel: String,
    lineEndingLabel: String,
    line: Int,
    column: Int,
    diagnostic: SyntaxDiagnostic?,
    onToggleLineEnding: () -> Unit,
    onDiagnosticClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = fileTypeLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = lineEndingLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onToggleLineEnding() }
            )
            Text(
                text = "UTF-8",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Ln $line, Col $column",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (diagnostic != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onDiagnosticClick(diagnostic.line) }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (diagnostic.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (diagnostic.isError) MaterialTheme.colorScheme.error else Color(0xFF4ADE80),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (diagnostic.isError) {
                        "L${diagnostic.line}:${diagnostic.column} ${diagnostic.message}"
                    } else {
                        diagnostic.message
                    },
                    fontSize = 11.sp,
                    color = if (diagnostic.isError) MaterialTheme.colorScheme.error else Color(0xFF4ADE80),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun KeyboardAccessoryBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onMoveCursor: (Int) -> Unit,
    onInsertSymbol: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    val symbols = listOf("-", ":", "\"", "'", "#", "{", "}", "[", "]", "=", "/", "_")

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AccessoryKeyButton(label = "Tab", onClick = onIndent)
            AccessoryKeyButton(label = "⇧Tab", onClick = onOutdent)
            AccessoryKeyButton(label = "←", onClick = { onMoveCursor(-1) })
            AccessoryKeyButton(label = "→", onClick = { onMoveCursor(1) })
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier.size(18.dp)
                )
            }
            symbols.forEach { sym ->
                AccessoryKeyButton(label = sym, onClick = { onInsertSymbol(sym) })
            }
        }
    }
}

@Composable
private fun AccessoryKeyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun WorkspaceSheetContent(
    folderEntries: List<FolderEntry>,
    recentFiles: List<RecentFileEntry>,
    onPickFolder: () -> Unit,
    onOpenFolderEntry: (FolderEntry) -> Unit,
    onOpenRecentUri: (Uri) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workspace Folder",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onPickFolder) {
                    Text("Choose Folder")
                }
            }
        }

        if (folderEntries.isEmpty()) {
            item {
                Text(
                    text = "No folder opened yet.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        } else {
            items(folderEntries) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenFolderEntry(entry) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = entry.name,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Recent Files",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (recentFiles.isEmpty()) {
            item {
                Text(
                    text = "No recent files.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        } else {
            items(recentFiles) { recent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenRecentUri(Uri.parse(recent.uriString)) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = recent.displayName,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
