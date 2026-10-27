@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.truyenaudio.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.truyenaudio.app.core.tts.*
import com.truyenaudio.app.data.*
import com.truyenaudio.app.ui.LibraryViewModel
import com.truyenaudio.app.ui.ListenDialog

class MainActivity : ComponentActivity() {
    private val model: LibraryViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("display", MODE_PRIVATE)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by remember { mutableStateOf(preferences.getBoolean("dark", systemDark)) }
            var fontSize by remember { mutableFloatStateOf(preferences.getFloat("font", 19f)) }
            var lineScale by remember { mutableFloatStateOf(preferences.getFloat("line", 1.6f)) }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val state by model.state.collectAsStateWithLifecycle()
                val playback by PlaybackStore.state.collectAsStateWithLifecycle()
                LaunchedEffect(playback.status) { model.reloadProgress() }
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
                BackHandler(enabled = state.detail != null) { model.back() }
                LaunchedEffect(state.user?.id) { if (state.user == null) config = null }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (state.detail != null) TextButton(onClick = model::back) { Text("‹ Trở lại") }
                            Text("TruyenAudio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                dark = !dark; preferences.edit().putBoolean("dark", dark).apply()
                            }) { Text(if (dark) "Sáng" else "Tối") }
                            if (state.user != null) TextButton(onClick = model::logout, enabled = !state.busy) { Text("Đăng xuất") }
                        }
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp))
                        if (localError.isNotBlank()) Text(localError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                        when {
                            state.checking -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Đang kiểm tra phiên…") }
                            state.sessionNetworkError -> Column(Modifier.padding(24.dp)) {
                                Text("Chưa kiểm tra được phiên. Dữ liệu đăng nhập vẫn được giữ trên máy.")
                                Button(onClick = model::checkSession, enabled = !state.busy) { Text("Thử lại") }
                            }
                            state.user == null -> LoginForm(state.busy, model::login)
                            state.chapter != null -> {
                                val chapter = state.chapter!!
                                val numbers = state.detail?.chapters.orEmpty().map { it.number }.sorted()
                                val index = numbers.indexOf(chapter.number)
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                    Text(chapter.title, style = MaterialTheme.typography.titleLarge)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Chữ ${fontSize.toInt()}", modifier = Modifier.width(60.dp))
                                        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 14f..30f,
                                            onValueChangeFinished = { preferences.edit().putFloat("font", fontSize).apply() })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Giãn dòng", modifier = Modifier.width(80.dp))
                                        Slider(value = lineScale, onValueChange = { lineScale = it }, valueRange = 1.2f..2.2f,
                                            onValueChangeFinished = { preferences.edit().putFloat("line", lineScale).apply() })
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        TextButton(onClick = { model.openChapter(numbers[index - 1]) }, enabled = index > 0 && !state.busy) { Text("‹ Chương trước") }
                                        Button(onClick = { config = ListenPlan(chapter.slug, chapter.storyTitle,
                                            first = numbers.firstOrNull() ?: chapter.number, last = numbers.lastOrNull() ?: chapter.number, chapter = chapter.number) }) { Text("Nghe") }
                                        TextButton(onClick = { model.openChapter(numbers[index + 1]) }, enabled = index >= 0 && index + 1 < numbers.size && !state.busy) { Text("Chương sau ›") }
                                    }
                                    key(chapter.slug, chapter.number) {
                                        Text(chapter.content, fontSize = fontSize.sp, lineHeight = (fontSize * lineScale).sp,
                                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp))
                                    }
                                }
                            }
                            state.detail != null -> {
                                val detail = state.detail!!
                                var search by remember(detail.story.slug) { mutableStateOf("") }
                                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        Text(detail.story.title, style = MaterialTheme.typography.headlineMedium)
                                        Text(detail.story.description.orEmpty(), modifier = Modifier.padding(vertical = 8.dp))
                                        Text("${detail.chapters.size} chương")
                                        Button(onClick = {
                                            val numbers = detail.chapters.map { it.number }.sorted()
                                            if (numbers.isNotEmpty()) config = ListenPlan(detail.story.slug, detail.story.title,
                                                numbers.first(), numbers.last(), numbers.first())
                                        }, enabled = detail.chapters.isNotEmpty()) { Text("Cấu hình nghe") }
                                        OutlinedTextField(search, { search = it.filter(Char::isDigit) }, label = { Text("Tìm số chương trong mục lục") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                    }
                                    items(detail.chapters.filter { search.isBlank() || it.number.toString().contains(search) }, key = { it.number }) { chapter ->
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text(chapter.title, fontWeight = FontWeight.Medium)
                                                Row {
                                                    TextButton(onClick = { model.openChapter(chapter.number) }, enabled = !state.busy) { Text("Đọc") }
                                                    TextButton(onClick = { config = ListenPlan(detail.story.slug, detail.story.title,
                                                        detail.chapters.minOf { it.number }, detail.chapters.maxOf { it.number }, chapter.number) }) { Text("Nghe từ chương này") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                var search by remember { mutableStateOf("") }
                                PullToRefreshBox(isRefreshing = state.busy && state.page > 0, onRefresh = model::refresh, modifier = Modifier.weight(1f)) {
                                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        item {
                                            Text("Thư viện của ${state.user?.username}", style = MaterialTheme.typography.headlineSmall)
                                            TextButton(onClick = model::refresh, enabled = !state.busy) { Text("Tải lại danh sách") }
                                            OutlinedTextField(search, { search = it }, label = { Text("Tìm trong các truyện đã tải") }, modifier = Modifier.fillMaxWidth())
                                        }
                                        val saved = state.progress
                                        if (saved != null) item {
                                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                                Column(Modifier.padding(16.dp)) {
                                                    Text("Tiếp tục nghe", style = MaterialTheme.typography.titleMedium)
                                                    Text(saved.plan.storyTitle)
                                                    Text("Chương ${saved.plan.chapter} · vị trí ${saved.plan.offset} ký tự")
                                                    Row {
                                                        TextButton(onClick = { config = saved.plan.copy(sleepMinutes = 0) }) { Text("Tiếp tục nghe") }
                                                        TextButton(onClick = { model.openStory(saved.plan.slug) }) { Text("Mục lục") }
                                                    }
                                                }
                                            }
                                        }
                                        items(state.stories.filter { it.title.contains(search, ignoreCase = true) }, key = { it.slug }) { story ->
                                            Card(onClick = { if (!state.busy) model.openStory(story.slug) }, modifier = Modifier.fillMaxWidth()) {
                                                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                    Box(Modifier.size(64.dp, 84.dp).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                                        Text(story.title.take(1), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                    }
                                                    Column(Modifier.weight(1f)) {
                                                        Text(story.title, style = MaterialTheme.typography.titleMedium)
                                                        Text(story.description.orEmpty(), maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                                                        Text("${story.chapterCount} chương", style = MaterialTheme.typography.labelLarge)
                                                    }
                                                }
                                            }
                                        }
                                        item {
                                            if (state.stories.isEmpty() && !state.busy) Text("Chưa có truyện để hiển thị")
                                            if (state.more) Button(onClick = model::more, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Tải trang tiếp theo") }
                                            Spacer(Modifier.height(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        if (state.user != null && playback.plan != null) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 4.dp) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(playback.plan!!.storyTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(playback.title, maxLines = 1)
                                    Text("${playback.message} · Đoạn ${if (playback.chunks == 0) 0 else playback.chunk + 1}/${playback.chunks}", style = MaterialTheme.typography.bodySmall)
                                    Row {
                                        TextButton(onClick = { send(TtsPlaybackService.PREV) }) { Text("‹ Chương") }
                                        TextButton(onClick = { send(if (playback.status in listOf(PlaybackStatus.PLAYING, PlaybackStatus.LOADING)) TtsPlaybackService.PAUSE else TtsPlaybackService.PLAY) }) {
                                            Text(if (playback.status in listOf(PlaybackStatus.PLAYING, PlaybackStatus.LOADING)) "Tạm dừng" else "Tiếp tục")
                                        }
                                        TextButton(onClick = { send(TtsPlaybackService.NEXT) }) { Text("Chương ›") }
                                        TextButton(onClick = { send(TtsPlaybackService.STOP); model.reloadProgress() }) { Text("Dừng") }
                                    }
                                }
                            }
                        } else if (state.user != null && playback.status == PlaybackStatus.ERROR) Text(playback.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                val initial = config
                if (initial != null && state.user != null) ListenDialog(initial,
                    numbers = state.detail?.takeIf { it.story.slug == initial.slug }?.chapters?.map { it.number },
                    dismiss = { config = null }, play = { plan -> config = null; send(TtsPlaybackService.PLAY, plan) })
            }
        }
    }
}

@Composable
private fun LoginForm(busy: Boolean, login: (String, String, () -> Unit) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Chào mừng trở lại", style = MaterialTheme.typography.headlineMedium)
        Text("Đăng nhập để đọc và nghe thư viện truyện của bạn.")
        OutlinedTextField(username, { username = it }, label = { Text("Tên tài khoản") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ẩn" else "Hiện") } })
        Button(onClick = { login(username, password) { password = "" } }, enabled = !busy && username.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Đang đăng nhập…" else "Đăng nhập")
        }
        Text("Phiên đăng nhập được bảo vệ trên thiết bị. Ứng dụng không lưu mật khẩu.", style = MaterialTheme.typography.bodySmall)
    }
}
