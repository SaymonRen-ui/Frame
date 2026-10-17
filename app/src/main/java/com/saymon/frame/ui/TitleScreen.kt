package com.saymon.frame.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Произведение: обложка, название, автор, описание, главы, кнопка начать.
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
    var sortMenuOpen by remember { mutableStateOf(false) }
    var mainMenuOpen by remember { mutableStateOf(false) }
    var sortByName by remember { mutableStateOf(true) }

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

    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    var tabsTick by remember { mutableStateOf(0) }
    LaunchedEffect(mine) {
        tabsTick = vm.libTick
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("По имени А–Я") },
                            onClick = { sortByName = true; sortMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("По имени Я–А") },
                            onClick = { sortByName = false; sortMenuOpen = false }
                        )
                    }
                    IconButton(onClick = { sortMenuOpen = !sortMenuOpen }) {
                        Icon(imageVector = Icons.Default.Sort, contentDescription = "Сортировка глав")
                    }
                    IconButton(onClick = { pickCbz.launch(arrayOf("*/*")) }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить главы")
                    }
                    DropdownMenu(
                        expanded = mainMenuOpen,
                        onDismissRequest = { mainMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Обновить главы") },
                            onClick = { vm.refresh(); mainMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Изменить категорию") },
                            onClick = { mainMenuOpen = false }
                        )
                    }
                    IconButton(onClick = { mainMenuOpen = !mainMenuOpen }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Ещё")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Обложка + информация ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .width(120.dp)
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

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Название
                    Text(name, style = MaterialTheme.typography.titleLarge)

                    // Автор
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Неизвестный автор",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Статистика
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Добавлено",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("$totalChapters глав",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Описание / рейтинг / отзыв — только в режиме просмотра
                    if (!editingMeta) {
                        if (metaEdit.description.isNotBlank()) {
                            Text(metaEdit.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3)
                        } else {
                            Text("Без описания",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (metaEdit.rating > 0) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..5).forEach { star ->
                                    val filled = star <= metaEdit.rating.toInt()
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (filled)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(" ${"%.1f".format(metaEdit.rating)} / 5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (metaEdit.review.isNotBlank()) {
                            Text("Отзыв: ${metaEdit.review}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3)
                        }
                    }

                    // Кнопка редактирования мета
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

                    // Редактируемые поля — только в режиме редактирования
                    if (editingMeta) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = metaEdit.description,
                                onValueChange = { metaEdit = metaEdit.copy(description = it) },
                                label = { Text("Описание") },
                                singleLine = false,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Рейтинг: ${"%.1f".format(metaEdit.rating)} / 5",
                                    style = MaterialTheme.typography.bodyMedium)
                                (1..5).forEach { star ->
                                    val filled = star <= metaEdit.rating.toInt()
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (filled)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                        .clickable {
                                            metaEdit = metaEdit.copy(rating = star.toDouble())
                                        }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = metaEdit.review,
                                onValueChange = { metaEdit = metaEdit.copy(review = it) },
                                label = { Text("Отзыв") },
                                singleLine = false,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // --- Количество глав ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$totalChapters глав",
                    style = MaterialTheme.typography.titleMedium)
                Text("Отутствовало $totalChapters главы",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // --- Главы ---
            val sortedChapters = if (sortByName) chapters.sortedBy { it.name } else chapters.sortedByDescending { it.name }
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sortedChapters, key = { it.uri }) { ch ->
                    val read = vm.progress(titleId, ch.uri) > 0
                    val displayName = ch.name.removeSuffix(".cbz").removeSuffix(".CBZ")
                    val fileDate = java.io.File(ch.uri).lastModified()
                    val dateStr = dateFormat.format(Date(fileDate))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onRead(ch.uri) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f))
                                Text(dateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (read) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Box(
                                Modifier.size(24.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        }
                    }
                }
            }

            // --- Кнопка Начать ---
            if (chapters.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onRead(last?.chapterUri ?: chapters.first().uri) },
                        modifier = Modifier.width(200.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Text(if (last != null) "Продолжить" else "Начать")
                        }
                    }
                }
            }
        }
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
