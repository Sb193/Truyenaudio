package com.truyenaudio.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.truyenaudio.app.core.tts.*
import com.truyenaudio.app.data.*
import com.truyenaudio.app.ui.*

class MainActivity : ComponentActivity() {
    private val model: LibraryViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("display", MODE_PRIVATE)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by remember { mutableStateOf(preferences.getBoolean("dark", systemDark)) }
            var fontSize by remember { mutableFloatStateOf(preferences.getFloat("font", 19f)) }
            var lineScale by remember { mutableFloatStateOf(preferences.getFloat("line", 1.6f)) }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            TruyenAudioTheme(dark) {
                val state by model.state.collectAsStateWithLifecycle()
                val playback by PlaybackStore.state.collectAsStateWithLifecycle()
                var config by remember { mutableStateOf<ListenPlan?>(null) }
                var localError by remember { mutableStateOf("") }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                fun send(action: String, plan: ListenPlan? = null) {
                    runCatching {
                        val intent = Intent(this@MainActivity, TtsPlaybackService::class.java).setAction(action)
                        if (plan != null) intent.putExtra(TtsPlaybackService.PLAN, Gson().toJson(plan))
                        if (action == TtsPlaybackService.PLAY) {
                            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        } else startService(intent)
                        localError = ""
                    }.onFailure { localError = it.message ?: "Không thể điều khiển phát. Hãy mở lại ứng dụng." }
                }
                fun listen(plan: ListenPlan) {
                    if (state.detail?.story?.slug != plan.slug) model.openStory(plan.slug)
                    config = plan
                }
                BackHandler(enabled = state.detail != null) { model.back() }
                LaunchedEffect(state.user?.id) { if (state.user == null) config = null }
                LaunchedEffect(playback.status) { model.reloadProgress() }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        AppHeader(state.user?.username,
                            if (state.chapter != null) "Đang đọc" else if (state.detail != null) "Chi tiết truyện" else null,
                            dark, toggleDark = { dark = !dark; preferences.edit().putBoolean("dark", dark).apply() },
                            back = if (state.detail != null) ({ model.back() }) else null, logout = model::logout)
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                        ErrorNotice(state.error)
                        ErrorNotice(localError)
                        Box(Modifier.weight(1f)) {
                            when {
                                state.checking -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    BrandMark(84.dp); Spacer(Modifier.height(24.dp)); CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                    Spacer(Modifier.height(16.dp)); Text("Đang mở thư viện của bạn…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                state.sessionNetworkError -> EmptyMessage(Icons.Rounded.WifiOff, "Chưa thể kết nối", "Kiểm tra mạng rồi thử lại. Phiên đăng nhập vẫn được giữ trên máy.", modifier = Modifier.align(Alignment.Center)) {
                                    Button(onClick = model::checkSession, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Kết nối lại") }
                                }
                                state.user == null -> LoginScreen(state.busy, model::login)
                                state.chapter != null -> ReaderScreen(state.chapter!!, state.detail?.chapters.orEmpty().map { it.number }.sorted(), state.busy,
                                    fontSize, { fontSize = it; preferences.edit().putFloat("font", it).apply() },
                                    lineScale, { lineScale = it; preferences.edit().putFloat("line", it).apply() }, model::openChapter,
                                    listen = {
                                        val chapter = state.chapter!!
                                        val numbers = state.detail?.chapters.orEmpty().map { it.number }.sorted()
                                        listen(ListenPlan(chapter.slug, chapter.storyTitle, numbers.firstOrNull() ?: chapter.number,
                                            numbers.lastOrNull() ?: chapter.number, chapter.number))
                                    })
                                state.detail != null -> DetailScreen(state.detail!!, state.busy, model::openChapter, ::listen)
                                else -> LibraryScreen(state, model::refresh, model::more, model::openStory, ::listen)
                            }
                        }
                        if (state.user != null && playback.plan != null) MiniPlayer(playback) { action -> send(action); if (action == TtsPlaybackService.STOP) model.reloadProgress() }
                        if (state.user != null && playback.status == PlaybackStatus.ERROR) ErrorNotice(playback.message)
                    }
                }
                config?.takeIf { state.user != null }?.let { initial ->
                    ListenDialog(initial, numbers = state.detail?.takeIf { it.story.slug == initial.slug }?.chapters?.map { it.number },
                        dismiss = { config = null }, play = { plan -> config = null; send(TtsPlaybackService.PLAY, plan) })
                }
            }
        }
    }
}
