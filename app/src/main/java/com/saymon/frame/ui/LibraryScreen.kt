package com.saymon.frame.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Библиотека: вкладки, поиск, сортировка, сетка обложек.
 * Долгое нажатие на карточку — меню действий снизу.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onTitle: (String) -> Unit) {
    val context = LocalContext.current
    val pickLibrary = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val ok = vm.setLibraryTree(uri)
            android.widget.Toast.makeText(
                context,
                if (ok) "Папка библиотеки выбрана"
                else "Нет доступа на запись в эту папку",
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var searching by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf("") }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    var confirmChaptersFor by remember { mutableStateOf<String?>(null) }
    var confirmTitleFor by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = searching) { searching = false }

    LaunchedEffect(Unit) {
        vm.refresh()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Frame") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = {
                        addError = ""
                        adding = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Новое произведение")
                    }
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
                        DropdownMenuItem(text = { Text("Недавно добавлено") },
                            onClick = { vm.sortMode = 2; sorting = false })
                    }
                }
            )
        }
    ) { pad ->
        if (adding) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { adding = false },
                title = { Text("Новое произведение") },
                text = {
                    Column {
                        OutlinedTextField(name, {
                            name = it
                            addError = ""
                        },
                            label = { Text("Название") },
                            singleLine = true,
                            isError = addError.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth())
                        if (addError.isNotEmpty()) {
                            Text(
                                addError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (vm.addTitle(name)) {
                            adding = false
                        } else {
                            addError = if (name.trim().isEmpty())
                                "Введите название"
                            else
                                "Папка уже существует или нет доступа"
                        }
                    }) { Text("Создать") }
                },
                dismissButton = {
                    TextButton(onClick = { adding = false }) { Text("Отмена") }
                })
        }

        Column(Modifier.fillMaxSize().padding(pad)) {
            if (searching) {
                OutlinedTextField(
                    vm.search, { vm.search = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp))
            }
            val tabs = remember(vm.libTick) {
                listOf("Все") + vm.store.tabs()
            }
            LaunchedEffect(tabs) {
                if (vm.activeTab !in tabs) vm.setTab("Все")
            }
            val tabState = rememberLazyListState()
            // Категории не помещаются на экране — подсказываем это затуханием
            // у правого края списка и стрелкой в отдельной полосе справа.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    LazyRow(
                        state = tabState,
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(count = tabs.size) { index ->
                            val t = tabs[index]
                            FilterChip(
                                selected = vm.activeTab == t,
                                onClick = { vm.setTab(t) },
                                label = { Text(t) })
                        }
                    }
                    if (tabState.canScrollForward) {
                        Box(
                            Modifier.align(Alignment.CenterEnd)
                                .height(40.dp)
                                .width(28.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent,
                                            MaterialTheme.colorScheme.background)
                                    )
                                )
                        )
                    }
                }
                // Место под стрелку зарезервировано всегда, чтобы строка
                // не «прыгала» вбок при достижении конца списка.
                Box(Modifier.width(30.dp).height(40.dp),
                    contentAlignment = Alignment.Center) {
                    if (tabState.canScrollForward) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Прокрутить категории",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            val titles = vm.titles
            val setup = vm.needsLibrarySetup()
            if (setup) {
                Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        "Библиотека не выбрана",
                        "Выберите папку с произведениями: каждая папка — " +
                            "отдельный комикс с главами CBZ или PDF")
                    Column(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { pickLibrary.launch(null) }) {
                            Text("Выбрать папку библиотеки")
                        }
                        TextButton(onClick = { vm.goTab(2) }) {
                            Text("Открыть настройки")
                        }
                    }
                }
            } else if (titles.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        "Тут пусто",
                        "Добавьте произведения в выбранную папку " +
                            "или создайте новое кнопкой «+»")
                    TextButton(
                        onClick = { vm.goTab(2) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    ) { Text("Открыть настройки") }
                }
            } else {
                val mapping = remember(vm.libTick) { vm.store.titleTabs() }
                val hist = remember(vm.libTick) { vm.history() }
                var list = titles.filter {
                    (vm.search.isBlank() ||
                        it.name.contains(vm.search, ignoreCase = true)) &&
                        (vm.activeTab == "Все" ||
                            mapping[it.id]?.contains(vm.activeTab) == true)
                }
                list = when (vm.sortMode) {
                    1 -> list.sortedByDescending { t ->
                        hist.firstOrNull { it.titleId == t.id }?.time ?: 0
                    }
                    2 -> list.sortedByDescending { t -> vm.titleModified(t.id) }
                    else -> list
                }
                if (list.isEmpty()) {
                    EmptyState("Тут пусто", "Ничего не найдено")
                } else {
                    val compact = remember(vm.libTick) { vm.store.compactLibrary() }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (compact) 92.dp else 120.dp),
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        gridItems(list, key = { it.id }) { t ->
                            val chs = remember(vm.libTick) { vm.chapters(t.id, t.name) }
                            val n = chs.size
                            val unread = remember(vm.libTick) {
                                chs.count { !vm.isChapterRead(t.id, it.uri) }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onTitle(t.id) },
                                    onLongClick = { sheetFor = t.id }
                                )
                            ) {
                                Box {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
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
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(t.name,
                                                    maxLines = 1,
                                                    style = MaterialTheme.typography
                                                        .headlineMedium,
                                                    color = MaterialTheme.colorScheme
                                                        .onSurfaceVariant)
                                            }
                                        }
                                    }
                                    if (unread > 0) {
                                        Text("$unread",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(8.dp))
                                                .padding(horizontal = 7.dp, vertical = 2.dp))
                                    }
                                }
                                Text(t.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 6.dp))
                                Text("$n гл.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Меню действий по долгому нажатию на карточку
    val sid = sheetFor
    if (sid != null) {
        val name = remember(sid) { vm.titleName(sid) }
        var cats by remember(sid) { mutableStateOf(vm.titleTabs(sid)) }
        val allTabs = remember(vm.libTick) { vm.store.tabs() }
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text("Категории",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
                allTabs.forEach { tab ->
                    val selected = cats.contains(tab)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                val s = cats.toMutableSet().apply {
                                    if (selected) remove(tab) else add(tab)
                                }
                                cats = s
                                vm.store.setTitleTabs(sid, s)
                                vm.touch()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(22.dp))
                        Text(tab, modifier = Modifier.padding(start = 16.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(
                    MaterialTheme.colorScheme.outlineVariant))
                SheetAction("Открыть папку", Icons.Default.Folder) {
                    val opened = if (sid.startsWith("content://")) {
                        openFolder(context, android.net.Uri.parse(sid))
                    } else {
                        openFolder(context, java.io.File(sid))
                    }
                    if (!opened) {
                        android.widget.Toast.makeText(
                            context, "Не удалось открыть проводник",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                    sheetFor = null
                }
                SheetAction("Удалить главы", Icons.Default.DeleteSweep) {
                    confirmChaptersFor = sid
                    sheetFor = null
                }
                SheetAction("Удалить произведение", Icons.Default.DeleteForever) {
                    confirmTitleFor = sid
                    sheetFor = null
                }
            }
        }
    }

    val delChapters = confirmChaptersFor
    if (delChapters != null) {
        AlertDialog(
            onDismissRequest = { confirmChaptersFor = null },
            title = { Text("Удалить главы?") },
            text = { Text("Все файлы глав в «${vm.titleName(delChapters)}» будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteChapters(delChapters)
                    confirmChaptersFor = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmChaptersFor = null }) { Text("Отмена") }
            })
    }

    val delTitle = confirmTitleFor
    if (delTitle != null) {
        AlertDialog(
            onDismissRequest = { confirmTitleFor = null },
            title = { Text("Удалить произведение?") },
            text = { Text("Папка «${vm.titleName(delTitle)}» с главами и обложкой будет удалена.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTitle(delTitle)
                    confirmTitleFor = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmTitleFor = null }) { Text("Отмена") }
            })
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp))
        Text(label,
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
    }
}
