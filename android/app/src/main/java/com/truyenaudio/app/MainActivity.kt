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
                var downloadConfirm by remember { mutableStateOf<Story?>(null) }
                var deleteConfirm by remember { mutableStateOf<Story?>(null) }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                fun send(action: String, plan: ListenPlan? = null) {
                    runCatching {
                        val intent = Intent(this@MainActivity, TtsPlaybackService::class.java).setAction(action)
                        val target = plan ?: playback.plan?.takeIf { action == TtsPlaybackService.PLAY && playback.status != PlaybackStatus.PLAYING }
                        if (target != null) intent.putExtra(TtsPlaybackService.PLAN, Gson().toJson(target))
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
                LaunchedEffect(state.source, state.user?.id) { if (state.source == ContentSource.SERVER && state.user == null) config = null }
                LaunchedEffect(playback.status) { model.reloadProgress() }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        AppHeader(if (state.source == ContentSource.SERVER) state.user?.username else null,
                            if (state.chapter != null) "Đang đọc" else if (state.detail != null) "Chi tiết truyện" else if (state.source == ContentSource.LOCAL) "Thư viện của tôi" else "Kho truyện online",
                            dark, toggleDark = { dark = !dark; preferences.edit().putBoolean("dark", dark).apply() },
                            back = if (state.detail != null) ({ model.back() }) else null, logout = model::logout)
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                        ErrorNotice(state.error)
                        ErrorNotice(localError)
                        state.downloads.firstOrNull { it.active }?.let { download -> DownloadBanner(download) { model.cancelDownload(download.id) } }
                        if (state.source == ContentSource.SERVER) state.downloads.firstOrNull { it.error.isNotBlank() }?.let { ErrorNotice(it.error) }
                        Box(Modifier.weight(1f)) {
                            when {
                                state.checking -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    BrandMark(84.dp); Spacer(Modifier.height(24.dp)); CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                    Spacer(Modifier.height(16.dp)); Text("Đang mở thư viện của bạn…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                state.sessionNetworkError -> EmptyMessage(Icons.Rounded.WifiOff, "Chưa thể kết nối", "Kiểm tra mạng rồi thử lại. Phiên đăng nhập vẫn được giữ trên máy.", modifier = Modifier.align(Alignment.Center)) {
                                    Button(onClick = model::checkSession, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Kết nối lại") }
                                }
                                state.source == ContentSource.SERVER && state.user == null -> LoginScreen(state.busy, model::login)
                                state.chapter != null -> ReaderScreen(state.chapter!!, state.detail?.chapters.orEmpty().map { it.number }.sorted(), state.busy,
                                    fontSize, { fontSize = it; preferences.edit().putFloat("font", it).apply() },
                                    lineScale, { lineScale = it; preferences.edit().putFloat("line", it).apply() }, model::openChapter,
                                    listen = {
                                        val chapter = state.chapter!!
                                        val numbers = state.detail?.chapters.orEmpty().map { it.number }.sorted()
                                        listen(ListenPlan(chapter.slug, chapter.storyTitle, numbers.firstOrNull() ?: chapter.number,
                                            numbers.lastOrNull() ?: chapter.number, chapter.number, source = state.source))
                                    })
                                state.detail != null -> DetailScreen(state.detail!!, state.busy, model::openChapter, ::listen,
                                    source = state.source,
                                    download = if (state.source == ContentSource.SERVER) ({ downloadConfirm = state.detail!!.story }) else null,
                                    delete = if (state.source == ContentSource.LOCAL) ({ deleteConfirm = state.detail!!.story }) else null,
                                    savedCount = state.localCounts[state.detail!!.story.slug] ?: 0,
                                    isDownloading = state.downloads.any { it.active && it.slug == state.detail!!.story.slug })
                                else -> LibraryScreen(state, model::refresh, model::more, model::openStory, ::listen, model::enterServer)
                            }
                        }
                        if (playback.plan != null) MiniPlayer(playback) { action -> send(action); if (action == TtsPlaybackService.STOP) model.reloadProgress() }
                        if (playback.status == PlaybackStatus.ERROR) ErrorNotice(playback.message)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                            NavigationBarItem(selected = state.source == ContentSource.LOCAL,
                                onClick = { config = null; if (state.source != ContentSource.LOCAL) model.showLocal() },
                                icon = { Icon(Icons.Rounded.OfflinePin, null) }, label = { Text("Trên máy") })
                            NavigationBarItem(selected = state.source == ContentSource.SERVER,
                                onClick = { config = null; model.enterServer() },
                                icon = { Icon(Icons.Rounded.CloudDownload, null) }, label = { Text("Tải thêm") })
                        }
                    }
                }
                downloadConfirm?.let { story ->
                    AlertDialog(onDismissRequest = { downloadConfirm = null }, icon = { Icon(Icons.Rounded.Download, null) },
                        title = { Text("Tải toàn bộ truyện?") },
                        text = { Text("${story.title}\n\nLưu toàn bộ ${state.detail?.chapters?.size ?: story.chapterCount} chương trên máy để đọc/nghe không cần đăng nhập. Chương đã có sẽ được bỏ qua. Đăng xuất không xóa truyện đã tải.") },
                        confirmButton = { TextButton(onClick = {
                            downloadConfirm = null
                            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            model.download(story)
                        }) { Text("Tải truyện") } }, dismissButton = { TextButton(onClick = { downloadConfirm = null }) { Text("Để sau") } })
                }
                deleteConfirm?.let { story ->
                    AlertDialog(onDismissRequest = { deleteConfirm = null }, icon = { Icon(Icons.Rounded.DeleteOutline, null) },
                        title = { Text("Xóa bản trên máy?") }, text = { Text("${story.title}\n\nChỉ xóa bản đã tải và tiến độ nghe trên máy. Bạn có thể tải lại khi cần.") },
                        confirmButton = { TextButton(onClick = { deleteConfirm = null; model.deleteLocal(story.slug) }) { Text("Xóa khỏi máy", color = MaterialTheme.colorScheme.error) } },
                        dismissButton = { TextButton(onClick = { deleteConfirm = null }) { Text("Giữ lại") } })
                }
                config?.takeIf { it.source == ContentSource.LOCAL || state.user != null }?.let { initial ->
                    ListenDialog(initial, numbers = state.detail?.takeIf { it.story.slug == initial.slug }?.chapters?.map { it.number },
                        dismiss = { config = null }, play = { plan -> config = null; send(TtsPlaybackService.PLAY, plan) })
                }
            }
        }
    }
}
