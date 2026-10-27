package com.truyenaudio.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.truyenaudio.app.R
import com.truyenaudio.app.core.tts.*

@Composable
fun BrandMark(size: Dp = 44.dp) {
    Image(painterResource(R.drawable.app_brand), contentDescription = "Biểu tượng TruyenAudio",
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
}

@Composable
fun AppHeader(username: String?, title: String?, dark: Boolean, toggleDark: () -> Unit, back: (() -> Unit)?, logout: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Trở lại") }
        else { BrandMark(); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title ?: "TruyenAudio", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (back == null) "Mỗi trang sách, một hành trình" else "Thư viện truyện của bạn",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = toggleDark) { Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, if (dark) "Chế độ sáng" else "Chế độ tối") }
        if (username != null) Box {
            FilledTonalIconButton(onClick = { menu = true }, shape = CircleShape, modifier = Modifier.size(42.dp)) { Icon(Icons.Rounded.PersonOutline, "Tài khoản") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(username, fontWeight = FontWeight.Bold) }, onClick = { menu = false }, leadingIcon = { Icon(Icons.Rounded.Person, null) })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Đăng xuất") }, onClick = { menu = false; logout() }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) })
            }
        }
    }
}

@Composable
fun ErrorNotice(message: String) {
    if (message.isBlank()) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun EmptyMessage(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(22.dp).size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
fun BookCover(title: String, slug: String, modifier: Modifier = Modifier) {
    val palettes = listOf(
        listOf(Color(0xFF234E59), Color(0xFF102D39)),
        listOf(Color(0xFF826448), Color(0xFF433A39)),
        listOf(Color(0xFF666294), Color(0xFF343552)),
        listOf(Color(0xFF4A6F5E), Color(0xFF243F37)),
        listOf(Color(0xFF9C605C), Color(0xFF533D4B))
    )
    val colors = palettes[Math.floorMod(slug.hashCode(), palettes.size)]
    BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(colors))) {
        val compact = maxWidth < 100.dp
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.045f), size.width * 0.8f, Offset(size.width, 0f))
            drawCircle(Color.White.copy(alpha = 0.07f), size.width * 0.75f, Offset(size.width * 0.9f, size.height * 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.05f), size.width * 0.95f, Offset(size.width * 0.9f, size.height * 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawLine(Color.White.copy(alpha = 0.13f), Offset(8.dp.toPx(), 0f), Offset(8.dp.toPx(), size.height), 1.dp.toPx())
        }
        Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(25.dp))
            if (!compact) Text(title, color = Color(0xFFF7F1E4), style = MaterialTheme.typography.titleMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Rounded.Headphones, null, tint = Mint.copy(alpha = 0.85f), modifier = Modifier.size(12.dp))
                if (!compact) Text("TRUYỆN AUDIO", color = Color.White.copy(alpha = 0.64f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun MetaLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MiniPlayer(playback: PlaybackSnapshot, send: (String) -> Unit) {
    val plan = playback.plan ?: return
    val active = playback.status in listOf(PlaybackStatus.PLAYING, PlaybackStatus.LOADING)
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp, tonalElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(42.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(plan.storyTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(playback.title.ifBlank { playback.message }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { send(TtsPlaybackService.PREV) }, enabled = plan.chapter > plan.first, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.SkipPrevious, "Chương trước") }
                FilledIconButton(onClick = { send(if (active) TtsPlaybackService.PAUSE else TtsPlaybackService.PLAY) }, shape = CircleShape, modifier = Modifier.size(46.dp)) {
                    if (playback.status == PlaybackStatus.LOADING) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (active) "Tạm dừng" else "Tiếp tục")
                }
                IconButton(onClick = { send(TtsPlaybackService.NEXT) }, enabled = plan.chapter < plan.last, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.SkipNext, "Chương sau") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (playback.chunks > 0) "Chương ${plan.chapter} · Đoạn ${playback.chunk + 1}/${playback.chunks}" else playback.message,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { send(TtsPlaybackService.STOP) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Dừng nghe", modifier = Modifier.size(16.dp)) }
            }
            if (playback.chunks > 0) LinearProgressIndicator(
                progress = { (playback.chunk + 1).toFloat() / playback.chunks },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
