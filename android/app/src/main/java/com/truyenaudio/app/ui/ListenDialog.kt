@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.truyenaudio.app.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.truyenaudio.app.data.ListenPlan
import com.truyenaudio.app.data.ContentSource

@Composable
fun ListenDialog(initial: ListenPlan, numbers: List<Int>?, dismiss: () -> Unit, play: (ListenPlan) -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(if (initial.offset > 0) 3 else 2) }
    var first by remember { mutableStateOf(initial.chapter.toString()) }
    var last by remember { mutableStateOf(initial.last.toString()) }
    var speed by remember { mutableFloatStateOf(initial.speed) }
    var minutes by remember { mutableStateOf(initial.sleepMinutes.toString()) }
    var engine by remember { mutableStateOf(initial.engine) }
    var selected by remember { mutableStateOf(initial.voice) }
    var engines by remember { mutableStateOf(emptyList<TextToSpeech.EngineInfo>()) }
    var voices by remember { mutableStateOf(emptyList<Voice>()) }
    var loading by remember { mutableStateOf(true) }
    var engineError by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    DisposableEffect(engine) {
        var disposed = false
        var instance: TextToSpeech? = null
        loading = true
        engineError = ""
        val listener = TextToSpeech.OnInitListener { status -> Handler(Looper.getMainLooper()).post {
            if (!disposed) {
                loading = false
                if (status != TextToSpeech.SUCCESS) { engineError = "Không tải được engine. Hãy cài giọng TTS trong cài đặt máy."; voices = emptyList() }
                else {
                    engines = instance?.engines.orEmpty()
                    voices = instance?.voices.orEmpty().filter { initial.source != ContentSource.LOCAL || !it.isNetworkConnectionRequired }.sortedWith(compareBy<Voice> { it.locale.language != "vi" }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
                    if (voices.none { it.name == selected }) selected = voices.firstOrNull { it.locale.language == "vi" && !it.isNetworkConnectionRequired }?.name
                        ?: voices.firstOrNull { it.locale.language == "vi" }?.name.orEmpty()
                    accepted = false
                }
            }
        } }
        instance = if (engine.isBlank()) TextToSpeech(context, listener) else TextToSpeech(context, listener, engine)
        onDispose { disposed = true; instance?.shutdown() }
    }
    val voice = voices.find { it.name == selected }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.87f)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp).size(27.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Phiên nghe của bạn", style = MaterialTheme.typography.headlineSmall)
                    Text(initial.storyTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = dismiss) { Icon(Icons.Rounded.Close, "Đóng cấu hình nghe") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(4.dp))
                SheetLabel(Icons.Rounded.PlaylistPlay, "Bạn muốn nghe từ đâu?")
                val modes = listOf("Nghe toàn bộ truyện" to "Bắt đầu từ chương đầu tiên", "Từ chương A đến B" to "Chọn một khoảng chương yêu thích",
                    "Từ chương A đến hết" to "Nghe tiếp đến chương cuối", "Tiếp tục vị trí đã lưu" to "Trở lại phần bạn đang nghe")
                modes.forEachIndexed { index, (title, hint) ->
                    if (index < 3 || initial.offset > 0) Surface(onClick = { mode = index }, shape = RoundedCornerShape(16.dp),
                        color = if (mode == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == index, onClick = null)
                            Column(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (mode in 1..2) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(first, { first = it.filter(Char::isDigit) }, label = { Text("Chương bắt đầu") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                    if (mode == 1) OutlinedTextField(last, { last = it.filter(Char::isDigit) }, label = { Text("Chương kết thúc") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                }
                if (numbers == null) Text("Đang tải mục lục. Nếu mất mạng, đóng và thử lại.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SheetLabel(Icons.Rounded.RecordVoiceOver, "Giọng kể chuyện")
                if (initial.source == ContentSource.LOCAL) Text("Chế độ trên máy chỉ dùng giọng offline. Nếu chưa có giọng, tải giọng tiếng Việt trong cài đặt hệ thống.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Choice("Công cụ giọng đọc", engines.find { it.name == engine }?.label ?: "Mặc định của điện thoại", listOf("" to "Mặc định của điện thoại") + engines.map { it.name to it.label }) { engine = it; selected = "" }
                if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("Đang tìm giọng đọc…", style = MaterialTheme.typography.bodySmall)
                }
                if (engineError.isNotBlank()) Text(engineError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Choice("Giọng đọc", voice?.let { "${it.locale.toLanguageTag()} · ${if (it.isNetworkConnectionRequired) "Cần mạng" else "Không cần mạng"}" } ?: "Chọn giọng đọc", voices.map {
                    it.name to "${it.locale.toLanguageTag()} · ${if (it.isNetworkConnectionRequired) "Cần mạng" else "Offline"} · ${it.name}"
                }) { selected = it; accepted = false }
                if (voice?.isNetworkConnectionRequired == true) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Giọng này cần mạng và có thể gửi nội dung đến nhà cung cấp giọng đọc.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(accepted, { accepted = it }); Text("Tôi đồng ý sử dụng", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                } else if (voice != null) MetaLabel(Icons.Rounded.CloudOff, "Giọng đã chọn không yêu cầu kết nối mạng")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SheetLabel(Icons.Rounded.Speed, "Tốc độ đọc", Modifier.weight(1f))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${"%.1f".format(speed)}×", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                Slider(speed, { speed = it }, valueRange = 0.5f..2f, steps = 14)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chậm · 0.5×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Nhanh · 2.0×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SheetLabel(Icons.Rounded.Bedtime, "Hẹn giờ nghỉ ngơi")
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Dừng sau bao nhiêu phút?") },
                    leadingIcon = { Icon(Icons.Rounded.Timer, null) }, supportingText = { Text("Để 0 nếu bạn muốn nghe không hẹn giờ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) } }) {
                    Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Cài đặt giọng đọc trên máy")
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(enabled = !loading && !numbers.isNullOrEmpty() && voice != null && (!voice.isNetworkConnectionRequired || accepted), onClick = {
                runCatching {
                    val available = requireNotNull(numbers).sorted()
                    val lower = if (mode == 0) available.first() else if (mode == 3) initial.first else first.toIntOrNull() ?: 0
                    val upper = if (mode == 1) last.toIntOrNull() ?: 0 else if (mode == 3) initial.last else available.last()
                    require(lower >= available.first() && upper <= available.last() && lower <= upper) { "Phạm vi phải nằm trong mục lục truyện" }
                    require(lower in available && upper in available) { "Số chương không có trong mục lục" }
                    val value = initial.copy(first = lower, last = upper, chapter = if (mode == 3) initial.chapter else lower,
                        offset = if (mode == 3) initial.offset else 0, speed = speed, engine = engine, voice = selected,
                        sleepMinutes = minutes.toIntOrNull() ?: 0, networkVoiceAccepted = accepted)
                    value.validate(); play(value)
                }.onFailure { error = it.message ?: "Cấu hình không hợp lệ" }
            }, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp).fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Bắt đầu lắng nghe")
            }
        }
    }
}

@Composable
private fun SheetLabel(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Choice(label: String, selected: String, values: List<Pair<String, String>>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedCard(onClick = { if (values.isNotEmpty()) expanded = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selected, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            values.forEach { (id, title) -> DropdownMenuItem(text = { Text(title, style = MaterialTheme.typography.bodyMedium) }, onClick = { select(id); expanded = false }) }
        }
    }
}
