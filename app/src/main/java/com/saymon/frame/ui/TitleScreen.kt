package com.saymon.frame.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saymon.frame.data.TitleMeta
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Произведение: обложка, название, автор, описание, рейтинг, отзыв, главы.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    val tabs = remember(vm.libTick) { vm.store.tabs() }
    var mine by remember(vm.libTick) { mutableStateOf(vm.titleTabs(titleId)) }
    val favs = remember(vm.libTick) { vm.chapterFavSet(titleId) }
    val cover = vm.covers[titleId]
    val last = remember(vm.libTick) {
        vm.history().firstOrNull { it.titleId == titleId }
    }

    var editing by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var sortByName by remember { mutableStateOf(true) }
    var selected by remember(vm.libTick) { mutableStateOf(emptySet<String>()) }

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
    val readChapters = chapters.count { vm.isChapterRead(titleId, it.uri) }
    val progressPct = if (totalChapters > 0) readChapters.toFloat() / totalChapters else 0f
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val sortedChapters = if (sortByName)
        chapters.sortedBy { it.name } else chapters.sortedByDescending { it.name }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    DropdownMenu(expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("По имени А–Я") },
                            onClick = { sortByName = true; sortMenuOpen = false })
                        DropdownMenuItem(text = { Text("По имени Я–А") },
                            onClick = { sortByName = false; sortMenuOpen = false })
                    }
                    IconButton(onClick = { sortMenuOpen = !sortMenuOpen }) {
                        Icon(Icons.Default.Sort, contentDescription = "Сортировка глав")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = { pickCbz.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить главы")
                    }
                }
            )
        },
        floatingActionButton = {
            if (chapters.isNotEmpty() && selected.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onRead(last?.chapterUri ?: chapters.first().uri) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text(if (last != null) "Продолжить" else "Начать") }
                )
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Выбрано: ${selected.size}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = { selected = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Снять выбор")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val allRead = selected.all { vm.isChapterRead(titleId, it) }
                            OutlinedButton(
                                onClick = {
                                    selected.forEach { vm.setChapterRead(titleId, it, !allRead) }
                                    selected = emptySet()
                                    vm.refresh()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (allRead) "Непрочитано" else "Прочитано") }
                            OutlinedButton(
                                onClick = {
                                    selected.forEach { vm.toggleChapterFav(titleId, it) }
                                    selected = emptySet()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("В избранное") }
                            OutlinedButton(
                                onClick = {
                                    selected.forEach { vm.deleteChapter(it) }
                                    selected = emptySet()
                                    vm.refresh()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(3f / 4f)
                            .clickable { pickImage.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        if (cover != null) {
                            Image(cover, contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center) {
                                Text(name.take(2).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(name, style = MaterialTheme.typography.titleLarge)
                        Text(meta.author.ifBlank { "Неизвестный автор" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalChapters глав · прочитано $readChapters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            RatingStars(meta.rating)
                            if (meta.rating > 0) {
                                Text("${"%.1f".format(meta.rating)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { tab ->
                        FilterChip(
                            selected = mine.contains(tab),
                            onClick = {
                                vm.toggleTitleTab(titleId, tab)
                                mine = vm.titleTabs(titleId)
                                vm.refresh()
                            },
                            label = { Text(tab) })
                    }
                }
            }

            item {
                InfoBlock(
                    title = "Описание",
                    action = {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Default.Edit,
                                contentDescription = "Редактировать",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    Text(meta.description.ifBlank { "Без описания" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (meta.description.isBlank())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface)
                    if (meta.review.isNotBlank()) {
                        Text("Отзыв: ${meta.review}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgress(progressPct)
                    Column {
                        Text("Прогресс чтения",
                            style = MaterialTheme.typography.titleSmall)
                        Text("$readChapters из $totalChapters глав",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Text("$totalChapters глав",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            }

            items(sortedChapters, key = { it.uri }) { ch ->
                val read = vm.isChapterRead(titleId, ch.uri)
                val fav = favs.contains(ch.uri)
                val isSel = selected.contains(ch.uri)
                val displayName = ch.name.removeSuffix(".cbz").removeSuffix(".CBZ")
                val dateStr = dateFormat.format(Date(File(ch.uri).lastModified()))
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (isSel) MaterialTheme.colorScheme.primary
                            .copy(alpha = 0.14f) else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                if (selected.isNotEmpty())
                                    selected = if (isSel) selected - ch.uri else selected + ch.uri
                                else onRead(ch.uri)
                            },
                            onLongClick = {
                                selected = if (isSel) selected - ch.uri else selected + ch.uri
                            }
                        )
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(8.dp).background(
                            MaterialTheme.colorScheme.primary, CircleShape))
                    Column(
                        Modifier.weight(1f).padding(start = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1)
                        Text(dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (fav) {
                        Icon(Icons.Default.Star, contentDescription = "В избранном",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp).padding(end = 2.dp))
                    }
                    Box(
                        Modifier.size(24.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (read) Icon(Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (editing) {
        MetaEditor(
            initial = meta,
            coverPicker = { pickImage.launch("image/*") },
            onDismiss = { editing = false },
            onSave = {
                vm.setTitleMeta(titleId, it)
                editing = false
            }
        )
    }
}

@Composable
private fun RatingStars(rating: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { star ->
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (star <= rating.toInt()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun InfoBlock(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

/** Редактор описания, автора, рейтинга и отзыва. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaEditor(
    initial: TitleMeta,
    coverPicker: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (TitleMeta) -> Unit,
) {
    var edit by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Редактировать", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = coverPicker, modifier = Modifier.fillMaxWidth()) {
                Text("Сменить обложку")
            }
            OutlinedTextField(
                value = edit.author,
                onValueChange = { edit = edit.copy(author = it) },
                label = { Text("Автор") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = edit.description,
                onValueChange = { edit = edit.copy(description = it) },
                label = { Text("Описание") },
                maxLines = 5,
                modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Оценка:", style = MaterialTheme.typography.bodyMedium)
                (1..5).forEach { star ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (star <= edit.rating.toInt())
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(28.dp).clickable {
                            edit = edit.copy(
                                rating = if (edit.rating.toInt() == star) 0.0
                                else star.toDouble())
                        })
                }
            }
            OutlinedTextField(
                value = edit.review,
                onValueChange = { edit = edit.copy(review = it) },
                label = { Text("Отзыв") },
                maxLines = 5,
                modifier = Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                OutlinedButton(onClick = { onSave(edit) }) { Text("Сохранить") }
            }
        }
    }
}

/** Круговой индикатор прогресса прочтения (настоящая дуга). */
@Composable
fun CircularProgress(progress: Float, size: Dp = 60.dp) {
    val p = progress.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.12f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        Text("${(p * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Скопировать выбранное изображение как обложку в папку произведения. */
fun copyCover(context: Context, titleId: String, uri: Uri) {
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
