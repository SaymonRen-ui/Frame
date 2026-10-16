package com.saymon.frame.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Произведение: обложка, главы, вкладки, продолжить.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(
    vm: LibraryViewModel,
    titleId: String,
    onBack: () -> Unit,
    onRead: (chapterUri: String) -> Unit,
) {
    val name = vm.titleName(titleId)
    val chapters = remember(vm.libTick) { vm.chapters(titleId, name) }
    val pickCbz = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importChapters(titleId, uris) }
    val tabs = remember(vm.libTick) { vm.store.tabs() }
    val mine = remember(vm.libTick) { vm.titleTabs(titleId).toMutableSet() }
    val cover = vm.covers[titleId]
    // Последняя читанная глава — для кнопки «Продолжить»
    val last = remember(vm.libTick) {
        vm.history().firstOrNull { it.titleId == titleId }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(modifier = Modifier.width(120.dp)) {
                        if (cover != null) {
                            Image(cover, contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                                    .aspectRatio(3f / 4f))
                        } else {
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                                contentAlignment = Alignment.Center
                            ) { Text("?") }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, style = MaterialTheme.typography.titleLarge)
                        Text("Глав: ${chapters.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (last != null) {
                            Button(onClick = { onRead(last.chapterUri) },
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Продолжить")
                            }
                        } else if (chapters.isNotEmpty()) {
                            Button(onClick = { onRead(chapters.first().uri) },
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Читать с 1 главы")
                            }
                        }
                    }
                }
            }
            item {
                Text("Мои вкладки",
                    style = MaterialTheme.typography.titleSmall)
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { t ->
                        val has = mine.contains(t)
                        FilterChip(
                            selected = has,
                            onClick = {
                                vm.toggleTitleTab(titleId, t)
                                if (has) mine.remove(t) else mine.add(t)
                            },
                            label = { Text(t) },
                            leadingIcon = if (has) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
            item {
                Text("Главы", style = MaterialTheme.typography.titleSmall)
            }
            item {
                OutlinedButton(
                    onClick = { pickCbz.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Добавить главы (CBZ)") }
            }
            items(chapters, key = { it.uri }) { ch ->
                val read = vm.progress(titleId, ch.uri) > 0
                OutlinedButton(
                    onClick = { onRead(ch.uri) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        (if (read) "✓ " else "") + ch.name,
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
