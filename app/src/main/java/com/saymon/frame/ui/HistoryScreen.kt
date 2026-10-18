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
    val hist = remember(tick, vm.libTick) { vm.history() }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("История") },
                actions = {
                    if (hist.isNotEmpty()) {
                        TextButton(onClick = {
                            vm.clearHistory()
                            tick++
                        }) { Text("Очистить") }
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
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                var lastLabel = ""
                items(hist, key = { it.titleId + it.chapterUri }) { h ->
                    val label = dayLabel(h.time)
                    if (label != lastLabel) {
                        lastLabel = label
                        Text(label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onRead(h.titleId, h.chapterUri) }
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
                            Text("Гл. ${h.chapterName.removeSuffix(".cbz")} · ${timeLabel(h.time)}",
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
        diff < 7 -> "$diff дня назад"
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(time))
    }
}
