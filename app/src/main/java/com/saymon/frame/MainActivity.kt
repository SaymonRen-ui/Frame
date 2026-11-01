package com.saymon.frame

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saymon.frame.ui.HistoryScreen
import com.saymon.frame.ui.LibraryScreen
import com.saymon.frame.ui.LibraryViewModel
import com.saymon.frame.ui.ReaderScreen
import com.saymon.frame.ui.Screen
import com.saymon.frame.ui.SettingsScreen
import com.saymon.frame.ui.TitleScreen
import com.saymon.frame.core.ads.AdManager
import com.saymon.frame.core.ads.BannerAd
import com.saymon.frame.ui.theme.FrameTheme

class MainActivity : ComponentActivity() {
    private lateinit var vm: LibraryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 + targetSdk 35+: рисуемся под системные панели,
        // отступы — через WindowInsets.safeDrawing на каждом экране.
        enableEdgeToEdge()
        // Просим максимальную герцовку дисплея (60/90/120/144 Гц и т.д.),
        // чтобы прокрутка читалки не упиралась в дефолтные 60 Гц.
        try {
            @Suppress("DEPRECATION")
            val d = windowManager.defaultDisplay
            val hz = d.supportedModes.maxOfOrNull { it.refreshRate } ?: d.refreshRate
            if (hz > 0f) {
                window.attributes = window.attributes.apply { preferredRefreshRate = hz }
            }
        } catch (_: Exception) {
        }
        vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[LibraryViewModel::class.java]
        setContent { Root(vm) }
    }
}

@Composable
private fun Root(vm: LibraryViewModel) {
    val dark = when (vm.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    FrameTheme(darkTheme = dark) {
        Surface(Modifier.fillMaxSize()) {
            val activity = LocalContext.current as? android.app.Activity
            // Закрытие главы (выход из читалки) — момент для межстраничной рекламы.
            val goBack: () -> Unit = {
                val closingChapter = vm.screen is Screen.Reader
                vm.back()
                if (closingChapter) activity?.let { AdManager.show(it) }
            }
            BackHandler(enabled = vm.canGoBack) { goBack() }
            when (val s = vm.screen) {
                is Screen.Reader -> ReaderScreen(
                    vm, s.titleId, s.chapterUri,
                    onBack = goBack)
                is Screen.Title -> TitleScreen(
                    vm, s.titleId,
                    onBack = { vm.back() },
                    onRead = { uri -> vm.go(Screen.Reader(s.titleId, uri)) })
                else -> Scaffold(
                    bottomBar = {
                        Column(Modifier.navigationBarsPadding()) {
                            if (vm.bottomTab == 0 || vm.bottomTab == 1) {
                                BannerAd()
                            }
                            // Тонкий разделитель
                            androidx.compose.material3.Divider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant)
                            // Буферная зона 8dp — не реагирует на клики
                            androidx.compose.foundation.layout.Spacer(
                                Modifier.fillMaxWidth().height(8.dp))
                            NavigationBar {
                            NavigationBarItem(
                                selected = vm.bottomTab == 0,
                                onClick = { vm.goTab(0) },
                                icon = {
                                    Icon(Icons.Default.Book,
                                        contentDescription = null)
                                },
                                label = { Text("Библиотека") })
                            NavigationBarItem(
                                selected = vm.bottomTab == 1,
                                onClick = { vm.goTab(1) },
                                icon = {
                                    Icon(Icons.Default.History,
                                        contentDescription = null)
                                },
                                label = { Text("История") })
                            NavigationBarItem(
                                selected = vm.bottomTab == 2,
                                onClick = { vm.goTab(2) },
                                icon = {
                                    Icon(Icons.Default.Settings,
                                        contentDescription = null)
                                },
                                label = { Text("Настройки") })
                        }
                    }
                    }  // close Column
                ) { pad ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.padding(pad)) {
                        when (vm.bottomTab) {
                            1 -> HistoryScreen(vm,
                                onRead = { titleId, chapterUri ->
                                    vm.go(Screen.Reader(titleId, chapterUri))
                                })
                            2 -> SettingsScreen(vm)
                            else -> LibraryScreen(vm,
                                onTitle = { vm.go(Screen.Title(it)) })
                        }
                    }
                }
            }
        }
    }
}
