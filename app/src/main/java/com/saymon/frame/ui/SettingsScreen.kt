package com.saymon.frame.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Настройки: тема, чтение, библиотека, о приложении. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: LibraryViewModel) {
    val context = LocalContext.current
    var cacheMsg by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Настройки") }) }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard("Оформление") {
                ChipRowValues(
                    options = listOf(
                        "Системная" to 0, "Светлая" to 1, "Тёмная" to 2),
                    selected = vm.theme,
                    onSelect = { vm.chooseTheme(it) })
            }

            SectionCard("Режим чтения по умолчанию") {
                var mode by remember { mutableStateOf(vm.store.readMode()) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(
                        "Длинные страницы", "Длинные с зазорами",
                        "Постранично →", "Постранично ← (манга)",
                        "Постранично ↓"
                    ).forEachIndexed { i, label ->
                        RadioLabel(label, mode == i) {
                            mode = i
                            vm.store.setReadMode(i)
                        }
                    }
                }
            }

            SectionCard("Чтение") {
                var orient by remember { mutableStateOf(vm.store.orientation()) }
                var zones by remember { mutableStateOf(vm.store.tapZones()) }
                var dtZoom by remember { mutableStateOf(vm.store.doubleTapZoom()) }
                var keep by remember { mutableStateOf(vm.store.keepScreen()) }

                LabeledGroup("Ориентация") {
                    ChipRowValues(
                        options = listOf(
                            "Системная" to 0, "Портретная" to 2, "Альбомная" to 3),
                        selected = orient,
                        onSelect = { orient = it; vm.store.setOrientation(it) })
                }
                LabeledGroup("Зоны касания") {
                    ChipRowValues(
                        options = listOf("Обычные" to 0, "Выкл" to 1),
                        selected = zones,
                        onSelect = { zones = it; vm.store.setTapZones(it) })
                }
                ToggleRow("Двойное нажатие для увеличения", dtZoom) {
                    dtZoom = it
                    vm.store.setDoubleTapZoom(it)
                }
                ToggleRow("Не гасить экран", keep) {
                    keep = it
                    vm.store.setKeepScreen(it)
                }
            }

            SectionCard("Библиотека") {
                Text(
                    "Папка Frame в корне устройства: " +
                        if (vm.workDir().isDirectory) "на месте" else "нет доступа",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            context.startActivity(Intent(
                                android.provider.Settings
                                    .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")))
                        }
                    } catch (_: Exception) {
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.hasFullAccess()) "Доступ есть ✓"
                    else "Дать доступ к файлам")
                }
                OutlinedButton(onClick = {
                    val mb = vm.store.cacheSize() / 1048576.0
                    cacheMsg = if (vm.store.clearCache()) "Кэш очищен"
                    else "Не вышло (было ${"%.1f".format(mb)} МБ)"
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Очистить кэш глав")
                }
                if (cacheMsg.isNotEmpty()) Text(cacheMsg,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard("О приложении") {
                Text("Frame 0.1.0 · читалка CBZ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/SaymonRen-ui/frame-android/blob/main/PRIVACY.md")))
                    } catch (_: Exception) {
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Политика конфиденциальности")
                }
                Text("AndroidX / Compose — Apache 2.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun LabeledGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRowValues(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) })
        }
    }
}

@Composable
private fun RadioLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
