package com.truyenaudio.app.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.truyenaudio.app.data.ListenPlan

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
                    voices = instance?.voices.orEmpty().sortedWith(compareBy<Voice> { it.locale.language != "vi" }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
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
    AlertDialog(onDismissRequest = dismiss, title = { Text("Cấu hình nghe") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Nghe toàn bộ", "Từ chương A đến B", "Từ chương A đến hết", "Tiếp tục vị trí đã lưu").forEachIndexed { index, label ->
                if (index < 3 || initial.offset > 0) Row {
                    RadioButton(selected = mode == index, onClick = { mode = index })
                    TextButton(onClick = { mode = index }) { Text(label) }
                }
            }
            if (mode in 1..2) OutlinedTextField(first, { first = it.filter(Char::isDigit) }, label = { Text("Chương A") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (mode == 1) OutlinedTextField(last, { last = it.filter(Char::isDigit) }, label = { Text("Chương B") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Choice("Engine", engines.find { it.name == engine }?.label ?: "Mặc định của máy", listOf("" to "Mặc định của máy") + engines.map { it.name to it.label }) { engine = it; selected = "" }
            if (loading) Text("Đang kiểm tra giọng đọc…")
            if (engineError.isNotBlank()) Text(engineError, color = MaterialTheme.colorScheme.error)
            Choice("Giọng đọc", voice?.let { "${it.locale.toLanguageTag()} · ${it.name}" } ?: "Chưa có giọng", voices.map {
                it.name to "${it.locale.toLanguageTag()} · ${if (it.isNetworkConnectionRequired) "Cần mạng" else "Offline"} · ${it.name}"
            }) { selected = it; accepted = false }
            if (voice?.isNetworkConnectionRequired == true) {
                Text("Giọng này cần mạng. Engine hệ thống có thể gửi nội dung truyện đến nhà cung cấp giọng đọc.", color = MaterialTheme.colorScheme.error)
                Row { Checkbox(accepted, { accepted = it }); Text("Tôi đồng ý dùng giọng cần mạng") }
            } else Text("Ưu tiên giọng offline; không dùng Gemini hoặc dịch vụ TTS đám mây do app tích hợp.")
            Text("Tốc độ: ${"%.1f".format(speed)}x")
            Slider(speed, { speed = it }, valueRange = 0.5f..2f, steps = 14)
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Hẹn giờ dừng (phút, 0 = không hẹn)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            TextButton(onClick = { runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) } }) { Text("Mở cài đặt giọng đọc của máy") }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(enabled = !loading && voice != null && (!voice.isNetworkConnectionRequired || accepted), onClick = {
            runCatching {
                val available = numbers?.sorted()
                val lower = if (mode == 0) available?.firstOrNull() ?: initial.first else if (mode == 3) initial.first else first.toIntOrNull() ?: 0
                val upper = if (mode == 1) last.toIntOrNull() ?: 0 else available?.lastOrNull() ?: initial.last
                require(lower >= initial.first && upper <= initial.last && lower <= upper) { "Phạm vi phải nằm trong mục lục truyện" }
                if (available != null) require(lower in available && upper in available) { "Số chương không có trong mục lục" }
                val value = initial.copy(first = lower, last = upper, chapter = if (mode == 3) initial.chapter else lower,
                    offset = if (mode == 3) initial.offset else 0, speed = speed, engine = engine, voice = selected,
                    sleepMinutes = minutes.toIntOrNull() ?: 0, networkVoiceAccepted = accepted)
                value.validate(); play(value)
            }.onFailure { error = it.message ?: "Cấu hình không hợp lệ" }
        }) { Text("Bắt đầu nghe") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Hủy") } })
}

@Composable
private fun Choice(label: String, selected: String, values: List<Pair<String, String>>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = values.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("$label: $selected", maxLines = 2) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            values.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { select(id); expanded = false }) }
        }
    }
}
