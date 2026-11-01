package com.saymon.frame.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.saymon.frame.data.Progress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** История: недавно читал — миниатюры, группировка по датам, удаление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: LibraryViewModel,
    onRead: (titleId: String, chapterUri: String) -> Unit,
) {
    var tick by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf<String?>(null) }
    val hist = remember(tick, vm.libTick) { vm.history() }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("История") },
                actions = {
                    if (hist.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("Очистить") }
                    }
                })
        }
    ) { pad ->
        if (hist.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Пока пусто", style = MaterialTheme.typography.titleLarge)
                Text("Открытые главы появятся здесь",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val rows = remember(hist) {
                var last = ""
                hist.map { h ->
                    val label = dayLabel(h.time)
                    val header = label.takeIf { it != last }
                    if (header != null) last = label
                    header to h
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(rows, key = { it.second.titleId + it.second.chapterUri }) { row ->
                    val (label, h) = row
                    if (label != null) {
                        Text(label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                if (vm.exists(h.chapterUri)) {
                                    onRead(h.titleId, h.chapterUri)
                                } else {
                                    vm.removeHistoryEntry(h.titleId, h.chapterUri)
                                    missing = h.chapterName
                                    tick++
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(width = 46.dp, height = 62.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = vm.covers[h.titleId]
                            if (bmp != null) {
                                Image(bmp, contentDescription = h.titleName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize())
                            } else {
                                Text(h.titleName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(
                            Modifier.weight(1f).padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(h.titleName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1)
                            Text("${h.chapterName.replace(Regex("(?i)\\.(cbz|pdf)$"), "")} · ${timeLabel(h.time)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1)
                        }
                        IconButton(onClick = {
                            vm.removeHistoryEntry(h.titleId, h.chapterUri)
                            tick++
                        }) {
                            Icon(Icons.Default.Delete,
                                contentDescription = "Удалить из истории",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все записи истории будут удалены. Прогресс чтения сохранится.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    confirmClear = false
                    tick++
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            })
    }

    val miss = missing
    if (miss != null) {
        AlertDialog(
            onDismissRequest = { missing = null },
            title = { Text("Глава не найдена") },
            text = { Text("«$miss» больше нет на устройстве. Запись удалена из истории.") },
            confirmButton = {
                TextButton(onClick = { missing = null }) { Text("Ок") }
            })
    }
}

private fun timeLabel(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

private fun dayLabel(time: Long): String {
    fun midnight(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    val diff = ((midnight(System.currentTimeMillis()) - midnight(time)) / 86_400_000L).toInt()
    return when {
        diff <= 0 -> "Сегодня"
        diff == 1 -> "Вчера"
        diff in 2..6 -> {
            val word = when {
                diff % 10 == 1 && diff % 100 != 11 -> "день"
                diff % 10 in 2..4 && diff % 100 !in 12..14 -> "дня"
                else -> "дней"
            }
            "$diff $word назад"
        }
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(time))
    }
}
