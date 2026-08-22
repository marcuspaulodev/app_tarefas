package com.marcuspaulo.tarefas.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.ui.components.TagChip
import com.marcuspaulo.tarefas.ui.components.TagEditDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    tags: List<Tag>,
    onBack: () -> Unit,
    onAddTag: (name: String, colorHex: String) -> Unit,
    onUpdateTag: (Tag) -> Unit,
    onDeleteTag: (Tag) -> Unit
) {
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nova tag")
            }
        }
    ) { padding ->
        if (tags.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma tag cadastrada ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(tags, key = { it.id }) { tag ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TagChip(tag = tag)
                            Row {
                                IconButton(onClick = { editingTag = tag }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar tag")
                                }
                                IconButton(onClick = { onDeleteTag(tag) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir tag")
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        TagEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, colorHex ->
                onAddTag(name, colorHex)
                showAddDialog = false
            }
        )
    }

    editingTag?.let { tag ->
        TagEditDialog(
            initial = tag,
            onDismiss = { editingTag = null },
            onConfirm = { name, colorHex ->
                onUpdateTag(tag.copy(name = name.trim(), colorHex = colorHex))
                editingTag = null
            }
        )
    }
}
