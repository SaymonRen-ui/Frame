package com.saymon.frame.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Библиотека: вкладки, поиск, сортировка, сетка обложек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onTitle: (String) -> Unit) {
    val pickLib = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) vm.setLibraryUri(uri.toString())
    }
    var searching by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Фрейм") },
                actions = {
                    IconButton(onClick = { searching = !searching }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск")
                    }
                    IconButton(onClick = { sorting = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Сортировка")
                    }
                    DropdownMenu(expanded = sorting,
                        onDismissRequest = { sorting = false }) {
                        DropdownMenuItem(text = { Text("А–Я") },
                            onClick = { vm.sortMode = 0; sorting = false })
                        DropdownMenuItem(text = { Text("Недавно читал") },
                            onClick = { vm.sortMode = 1; sorting = false })
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (searching) {
                OutlinedTextField(
                    vm.search, { vm.search = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp))
            }
            val tabs = remember(vm.libTick) {
                listOf("Все") + vm.store.tabs()
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { t ->
                    FilterChip(
                        selected = vm.activeTab == t,
                        onClick = { vm.setTab(t) },
                        label = { Text(t) })
                }
            }
            val libUri = vm.store.libraryUri()
            if (libUri.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Покажи папку библиотеки",
                        style = MaterialTheme.typography.titleLarge)
                    Text("В ней — папки произведений с главами CBZ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { pickLib.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Выбрать папку") }
                }
            } else {
                val mapping = remember(vm.libTick) { vm.store.titleTabs() }
                val hist = remember(vm.libTick) { vm.history() }
                var list = vm.titles.filter {
                    (vm.search.isBlank() ||
                        it.name.contains(vm.search, ignoreCase = true)) &&
                        (vm.activeTab == "Все" ||
                            mapping[it.id]?.contains(vm.activeTab) == true)
                }
                list = when (vm.sortMode) {
                    1 -> list.sortedByDescending { t ->
                        hist.firstOrNull { it.titleId == t.id }?.time ?: 0
                    }
                    else -> list
                }
                if (list.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Тут пусто",
                            style = MaterialTheme.typography.titleLarge)
                        Text("Папки произведений не найдены",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(list, key = { it.id }) { t ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onTitle(t.id) }
                            ) {
                                Card {
                                    val bmp = vm.covers[t.id]
                                    if (bmp != null) {
                                        Image(bmp, contentDescription = t.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth()
                                                .aspectRatio(3f / 4f))
                                    } else {
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .aspectRatio(3f / 4f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(t.name.take(2).uppercase(),
                                                style = MaterialTheme.typography
                                                    .headlineMedium,
                                                color = MaterialTheme.colorScheme
                                                    .onSurfaceVariant)
                                        }
                                    }
                                }
                                val n = remember(vm.libTick) {
                                    vm.chapters(t.id, t.name).size
                                }
                                Text(t.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2)
                                Text("$n гл.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
