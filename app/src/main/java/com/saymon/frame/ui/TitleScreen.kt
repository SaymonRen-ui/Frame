package com.saymon.frame.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saymon.frame.data.TitleMeta
import java.io.File

/**
 * Произведение: обложка, описание, рейтинг, отзыв, главы, читать.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(
    vm: LibraryViewModel,
    titleId: String,
    onBack: () -> Unit,
    onRead: (chapterUri: String) -> Unit,
) {
    val context = LocalContext.current
    val name = vm.titleName(titleId)
    val chapters = remember(vm.libTick) { vm.chapters(titleId, name) }
    val meta = remember(vm.libTick) { vm.titleMeta(titleId) }
    var metaEdit by remember { mutableStateOf(meta) }

    val tabs = remember(vm.libTick) { vm.store.tabs() }
    val mine by remember { mutableStateOf(vm.titleTabs(titleId).toMutableSet()) }
    val cover = vm.covers[titleId]

    val last = remember(vm.libTick) {
        vm.history().firstOrNull { it.titleId == titleId }
    }

    var editingMeta by remember { mutableStateOf(false) }
    var sortChapters by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            copyCover(context, titleId, it)
            vm.invalidateCover(titleId)
            vm.refresh()
        }
    }

    val pickCbz = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importChapters(titleId, uris) }

    val totalChapters = chapters.size
    val readChapters = chapters.count { vm.progress(titleId, it.uri) > 0 }
    val progressPct = if (totalChapters > 0) readChapters.toFloat() / totalChapters else 0f

    // Читаем вкладки свежие из store после каждого изменения
    var tabsRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(mine) {
        tabsRefresh = vm.libTick
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Сортировка глав
                    IconButton(onClick = { sortChapters = !sortChapters }) {
                        Icon(
                            imageVector = if (sortChapters) Icons.Default.Sort else Icons.Default.Sort,
                            contentDescription = "Сортировка глав"
                        )
                    }
                    // Добавить главы
                    IconButton(onClick = { pickCbz.launch(arrayOf("*/*")) }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить главы")
                    }
                    // Еще: обновить главы, изменить категорию
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Обновить главы") },
                            onClick = { vm.refresh(); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Изменить категорию") },
                            onClick = { menuOpen = false }
                        )
                    }
                    IconButton(onClick = { menuOpen = !menuOpen }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Ещё")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Обложка + мета (всегда видны) ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(3f / 4f)
                        .clickable { pickImage.launch("image/*") }
                ) {
                    cover?.let {
                        Image(
                            bitmap = it,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } ?: Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, style = MaterialTheme.typography.titleLarge)
                        if (editingMeta) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        vm.setTitleMeta(titleId, metaEdit)
                                        editingMeta = false
                                    }
                                ) { Text("Сохранить") }
                                TextButton(
                                    onClick = {
                                        metaEdit = meta
                                        editingMeta = false
                                    }
                                ) { Text("Отмена") }
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    metaEdit = meta
                                    editingMeta = true
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Редактировать")
                            }
                        }
                    }
                    // Описание — всегда видно
                    if (metaEdit.description.isNotBlank()) {
                        Text(metaEdit.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2)
                    }
                    // Рейтинг — всегда видно
                    if (metaEdit.rating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            (1..5).forEach { star ->
                                val filled = star <= metaEdit.rating.toInt()
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (filled)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(" ${"%.1f".format(metaEdit.rating)} / 5",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Отзыв — всегда видно
                    if (metaEdit.review.isNotBlank()) {
                        Text("Отзыв: ${metaEdit.review}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2)
                    }
                    // Редактируемые поля — только в режиме редактирования
                    if (editingMeta) {
                        OutlinedTextField(
                            value = metaEdit.description,
                            onValueChange = { metaEdit = metaEdit.copy(description = it) },
                            label = { Text("Описание") },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Рейтинг: ${"%.1f".format(metaEdit.rating)} / 5",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            (1..5).forEach { star ->
                                IconButton(
                                    onClick = {
                                        metaEdit = metaEdit.copy(rating = star.toDouble())
                                    }
                                ) {
                                    val filled = star <= metaEdit.rating.toInt()
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (filled)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = metaEdit.review,
                            onValueChange = { metaEdit = metaEdit.copy(review = it) },
                            label = { Text("Отзыв") },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // --- Прогресс ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgress(progress = progressPct)
                Text(
                    text = if (totalChapters > 0)
                        "Глав прочитано: $readChapters / $totalChapters"
                    else "Глав не найдено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Вкладки ---
            Text("Мои вкладки", style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { t ->
                    val has = mine.contains(t)
                    FilterChip(
                        selected = has,
                        onClick = {
                            vm.toggleTitleTab(titleId, t)
                            vm.refresh()
                            if (has) mine.remove(t) else mine.add(t)
                        },
                        label = { Text(t) },
                        leadingIcon = if (has) {
                            { Icon(imageVector = Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
            }

            // --- Главы ---
            Text("Главы", style = MaterialTheme.typography.titleSmall)
            val sortedChapters = if (sortChapters) chapters.sortedBy { it.name } else chapters
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sortedChapters, key = { it.uri }) { ch ->
                    val read = vm.progress(titleId, ch.uri) > 0
                    val displayName = ch.name.removeSuffix(".cbz").removeSuffix(".CBZ")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onRead(ch.uri) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                (if (read) "✓ " else "") + displayName,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (read) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // --- Кнопка читать ---
            if (chapters.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onRead(last?.chapterUri ?: chapters.first().uri) },
                        modifier = Modifier.width(180.dp)
                    ) {
                        Text(if (last != null) "Продолжить" else "Читать")
                    }
                }
            }
        }
    }
}

/** Круговой индикатор прогресса. */
@Composable
fun CircularProgress(progress: Float) {
    val sizeDp = 100.dp
    Box(
        modifier = Modifier.size(sizeDp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(sizeDp)
                .background(
                    Color(
                        red = MaterialTheme.colorScheme.primary.red,
                        green = MaterialTheme.colorScheme.primary.green,
                        blue = MaterialTheme.colorScheme.primary.blue,
                        alpha = 0.1f
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(sizeDp)
                .background(
                    Color(
                        red = MaterialTheme.colorScheme.primary.red,
                        green = MaterialTheme.colorScheme.primary.green,
                        blue = MaterialTheme.colorScheme.primary.blue,
                        alpha = progress.coerceIn(0f, 1f)
                    ),
                    CircleShape
                )
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Скопировать выбранное изображение как обложку в папку произведения. */
fun copyCover(context: android.content.Context, titleId: String, uri: android.net.Uri) {
    try {
        val dir = File(titleId)
        dir.mkdirs()
        val cover = File(dir, "cover.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cover.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } catch (_: Exception) {
    }
}
