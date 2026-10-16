package com.saymon.frame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.saymon.frame.data.Progress

/** История: недавно читал — продолжить, очистить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: LibraryViewModel,
    onRead: (titleId: String, chapterUri: String) -> Unit,
) {
    var tick by remember { mutableStateOf(0) }
    val hist = remember(tick) { vm.history() }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("История") }) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Недавно читал",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                if (hist.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        vm.clearHistory()
                        tick++
                    }) { Text("Очистить") }
                }
            }
            if (hist.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Пока пусто",
                        style = MaterialTheme.typography.titleLarge)
                    Text("Открытые главы появятся здесь",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hist, key = { it.titleId + it.chapterUri }) { h: Progress ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(h.titleName,
                                    style = MaterialTheme.typography.titleSmall)
                                Text("${h.chapterName} · стр. ${h.page + 1}/${h.pages}",
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant)
                                OutlinedButton(
                                    onClick = { onRead(h.titleId, h.chapterUri) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Продолжить") }
                            }
                        }
                    }
                }
            }
        }
    }
}
