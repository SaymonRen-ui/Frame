package com.saymon.frame.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saymon.frame.data.ReadMode

/** Настройки: тема, чтение, библиотека, о приложении. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LibraryViewModel) {
    val context = LocalContext.current
    var cacheMsg by remember { mutableStateOf("") }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Настройки") }) }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Оформление",
                        style = MaterialTheme.typography.titleMedium)
                    for ((v, label) in listOf(
                        0 to "Системная", 1 to "Светлая", 2 to "Тёмная")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = vm.theme == v,
                                onClick = { vm.chooseTheme(v) })
                            TextButton(onClick = { vm.chooseTheme(v) }) {
                                Text(label)
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                val mode = remember { mutableStateOf(vm.store.readMode()) }
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Режим чтения по умолчанию",
                        style = MaterialTheme.typography.titleMedium)
                    val labels = listOf(
                        "Длинные страницы", "Длинные с зазорами",
                        "Постранично →", "Постранично ← (манга)",
                        "Постранично ↓")
                    labels.forEachIndexed { i, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode.value == i,
                                onClick = {
                                    mode.value = i
                                    vm.store.setReadMode(i)
                                })
                            TextButton(onClick = {
                                mode.value = i
                                vm.store.setReadMode(i)
                            }) { Text(label) }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                var orient by remember { mutableStateOf(vm.store.orientation()) }
                var zones by remember { mutableStateOf(vm.store.tapZones()) }
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Чтение", style = MaterialTheme.typography.titleMedium)
                    Text("Ориентация")
                    Row {
                        listOf("Система", "Портрет", "Альбом").forEachIndexed { i, l ->
                            TextButton(onClick = {
                                orient = i
                                vm.store.setOrientation(i)
                            }) {
                                Text(l,
                                    color = if (orient == i)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Text("Зоны касания")
                    Row {
                        listOf("Обычные", "Выкл").forEachIndexed { i, l ->
                            TextButton(onClick = {
                                zones = i
                                vm.store.setTapZones(i)
                            }) {
                                Text(l,
                                    color = if (zones == i)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    @Composable
                    fun ToggleRow(
                        label: String,
                        v: Boolean,
                        set: (Boolean) -> Unit,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, modifier = Modifier.weight(1f))
                            Switch(checked = v, onCheckedChange = set)
                        }
                    }
                    var dtZoom by remember { mutableStateOf(vm.store.doubleTapZoom()) }
                    var pgNum by remember { mutableStateOf(vm.store.pageNumber()) }
                    var keep by remember { mutableStateOf(vm.store.keepScreen()) }
                    ToggleRow("Двойное нажатие для увеличения", dtZoom) {
                        dtZoom = it
                        vm.store.setDoubleTapZoom(it)
                    }
                    ToggleRow("Номер страницы", pgNum) {
                        pgNum = it
                        vm.store.setPageNumber(it)
                    }
                    ToggleRow("Не гасить экран", keep) {
                        keep = it
                        vm.store.setKeepScreen(it)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Библиотека",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Папка Фрейм в корне устройства: " +
                        if (vm.workDir().isDirectory) "на месте"
                        else "нет доступа",
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
                    },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (vm.hasFullAccess()) "Доступ есть ✓"
                        else "Дать доступ к файлам")
                    }
                    OutlinedButton(
                        onClick = {
                            val mb = vm.store.cacheSize() / 1048576.0
                            cacheMsg = if (vm.store.clearCache()) "Кэш очищен"
                            else "Не вышло (было ${"%.1f".format(mb)} МБ)"
                        },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Очистить кэш глав")
                    }
                    if (cacheMsg.isNotEmpty()) Text(cacheMsg,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("О приложении",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Фрейм 0.1.0 · читалка CBZ",
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
}
