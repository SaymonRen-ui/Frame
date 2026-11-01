package com.saymon.frame.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.saymon.frame.core.cbz.clearImageCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saymon.frame.data.Orient
import com.saymon.frame.data.ReadMode
import com.saymon.frame.data.TapZone

/** Настройки: тема, чтение, библиотека, о приложении. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: LibraryViewModel) {
    val context = LocalContext.current
    var cacheMsg by remember { mutableStateOf("") }
    var hasAccess by remember { mutableStateOf(vm.hasFullAccess()) }
    var saf by remember { mutableStateOf(vm.libraryUsesSaf()) }
    var libLabel by remember { mutableStateOf(vm.libraryLocation()) }
    var backupMsg by remember { mutableStateOf("") }
var corruptMsg by remember { mutableStateOf("") }
    val pickLibrary = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val ok = vm.setLibraryTree(uri)
            saf = vm.libraryUsesSaf()
            hasAccess = vm.hasFullAccess()
            libLabel = vm.libraryLocation()
            android.widget.Toast.makeText(
                context,
                if (ok) "Папка библиотеки выбрана" else "Нет доступа на запись в эту папку",
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(vm.store.exportBackup().toByteArray(Charsets.UTF_8))
            } != null
        } catch (_: Exception) {
            false
        }
        backupMsg = if (ok) "Копия сохранена" else "Не удалось сохранить копию"
    }
    val loadBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = try {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
        if (raw != null && vm.store.importBackup(raw)) {
            vm.chooseTheme(vm.store.theme())
            vm.refresh()
            vm.touch()
            backupMsg = "Настройки и прогресс восстановлены"
        } else {
            backupMsg = "Файл не подходит для восстановления"
        }
    }
    val requestStorageAccess = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasAccess = vm.hasFullAccess()
        if (hasAccess) {
            vm.refresh()
        } else if (grants.isNotEmpty()) {
            android.widget.Toast.makeText(
                context, "Доступ к файлам не предоставлен",
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = vm.hasFullAccess()
                saf = vm.libraryUsesSaf()
                libLabel = vm.libraryLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Настройки") }) }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CollapsibleCard("Оформление", initialExpanded = true) {
                ChipRowValues(
                    options = listOf(
                        "Системная" to 0, "Светлая" to 1, "Тёмная" to 2),
                    selected = vm.theme,
                    onSelect = { vm.chooseTheme(it) })
                var compact by remember { mutableStateOf(vm.store.compactLibrary()) }
                ToggleRow("Компактная сетка библиотеки", compact) {
                    compact = it
                    vm.store.setCompactLibrary(it)
                    vm.touch()
                }
            }

            CollapsibleCard("Режим чтения по умолчанию") {
                var mode by remember { mutableStateOf(vm.store.readMode()) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ReadMode.labels.forEachIndexed { i, label ->
                        RadioLabel(label, mode == i) {
                            mode = i
                            vm.store.setReadMode(i)
                        }
                    }
                }
            }

            CollapsibleCard("Чтение") {
                var orient by remember { mutableStateOf(vm.store.orientation()) }
                var zones by remember { mutableStateOf(vm.store.tapZones()) }
                var dtZoom by remember { mutableStateOf(vm.store.doubleTapZoom()) }
                var keep by remember { mutableStateOf(vm.store.keepScreen()) }

                LabeledGroup("Ориентация") {
                    ChipRowValues(
                        options = Orient.labels.mapIndexed { i, l -> l to i },
                        selected = orient,
                        onSelect = { orient = it; vm.store.setOrientation(it) })
                }
                LabeledGroup("Зоны касания") {
                    ChipRowValues(
                        options = TapZone.labels.mapIndexed { i, l -> l to i },
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
                var autoDel by remember { mutableStateOf(vm.autoDeleteRead()) }
                ToggleRow("Удалять главу после прочтения", autoDel) {
                    autoDel = it
                    vm.setAutoDeleteRead(it)
                }
            }

            CollapsibleCard("Библиотека", initialExpanded = true) {
                Text(
                    "Папка библиотеки: $libLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { pickLibrary.launch(null) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (saf) "Сменить папку" else "Выбрать папку библиотеки")
                }
                if (saf) {
                    OutlinedButton(onClick = {
                        if (!openFolder(context, Uri.parse(vm.store.libraryUri()))) {
                            android.widget.Toast.makeText(
                                context, "Не удалось открыть проводник",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть папку")
                    }
                } else if (hasAccess) {
                    OutlinedButton(onClick = {
                        if (!openFolder(context, vm.workDir())) {
                            android.widget.Toast.makeText(
                                context, "Не удалось открыть проводник",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть папку Frame")
                    }
                }
                // На Android 10 и ниже можно работать с /sdcard/Frame напрямую.
                if (!saf && Build.VERSION.SDK_INT < 30) {
                    if (hasAccess) {
                        Text("Прямой доступ к папке Frame разрешён",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Button(onClick = {
                            val permissions = buildList {
                                if (ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.READ_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                if (Build.VERSION.SDK_INT <= 29 &&
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                            if (permissions.isEmpty()) {
                                hasAccess = vm.hasFullAccess()
                                vm.refresh()
                            } else {
                                requestStorageAccess.launch(permissions.toTypedArray())
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Разрешить доступ к папке Frame")
                        }
                    }
                OutlinedButton(onClick = {
                    corruptMsg = "Проверка..."
                    vm.checkCorruptFiles { corrupt ->
                        corruptMsg = if (corrupt.isEmpty()) {
                            "Все главы целые"
                        } else {
                            "Повреждённые: ${corrupt.size}\n" + corrupt.joinToString("\n")
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Проверить главы на целостность")
                }
                if (corruptMsg.isNotEmpty()) Text(corruptMsg,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val freed = vm.store.clearCache()
                    clearImageCache()
                    vm.clearCovers()
                    cacheMsg = if (freed >= 0)
                        "Кэш очищен (${"%.1f".format(freed / 1048576.0)} МБ)"
                    else "Не удалось очистить кэш"
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Очистить кэш")
                }
                if (cacheMsg.isNotEmpty()) Text(cacheMsg,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
                Text(
                    "Настройки, прогресс, история и избранное — в одном JSON-файле.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { saveBackup.launch("frame-backup.json") },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить копию")
                }
                OutlinedButton(onClick = {
                    loadBackup.launch(arrayOf(
                        "application/json", "text/plain", "application/octet-stream"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Восстановить из файла")
                }
                if (backupMsg.isNotEmpty()) Text(backupMsg,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            CollapsibleCard("О приложении") {
                val context = LocalContext.current
                val version = remember {
                    try {
                        val pm = context.packageManager
                        @Suppress("DEPRECATION")
                        val info = if (Build.VERSION.SDK_INT >= 33) {
                            pm.getPackageInfo(context.packageName,
                                PackageManager.PackageInfoFlags.of(0))
                        } else {
                            pm.getPackageInfo(context.packageName, 0)
                        }
                        info.versionName ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                }
                fun openUrl(url: String) {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    } catch (_: Exception) {
                    }
                }
                Text(if (version.isNotEmpty()) "Frame $version · читалка CBZ и PDF"
                else "Frame · читалка CBZ и PDF",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    openUrl("https://github.com/SaymonRen-ui/Frame/blob/main/PRIVACY.md")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Политика конфиденциальности")
                }
                OutlinedButton(onClick = {
                    openUrl("https://github.com/SaymonRen-ui/Frame")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Исходный код на GitHub")
                }
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO,
                                android.net.Uri.parse("mailto:app.project@bk.ru")).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "Frame")
                            })
                    } catch (_: Exception) {
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Написать в поддержку")
                }
                Text("AndroidX, Jetpack Compose — Apache 2.0. " +
                    "Yandex Mobile Ads SDK — проприетарный SDK Яндекса для показа рекламы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    initialExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
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
