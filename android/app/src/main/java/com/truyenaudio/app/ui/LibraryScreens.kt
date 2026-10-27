@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.truyenaudio.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.truyenaudio.app.data.*

@Composable
fun LibraryScreen(state: LibraryState, refresh: () -> Unit, more: () -> Unit, open: (String) -> Unit, listen: (ListenPlan) -> Unit, browse: () -> Unit = {}) {
    var search by remember { mutableStateOf("") }
    val filtered = state.stories.filter { it.title.contains(search, ignoreCase = true) }
    PullToRefreshBox(isRefreshing = state.busy && state.page > 0, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (state.source == ContentSource.LOCAL) "Không cần mạng · Không cần đăng nhập" else "Xin chào, ${state.user?.username.orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Hôm nay bạn\nmuốn nghe gì?", style = MaterialTheme.typography.headlineLarge)
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp).size(28.dp))
                        }
                    }
                    OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text(if (state.source == ContentSource.LOCAL) "Tìm truyện trên máy" else "Tìm trong danh sách đã tải", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "Xóa tìm kiếm") } },
                        shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface))
                    if (search.isBlank()) {
                        if (state.progress != null) ContinueCard(state.progress, listen, open)
                        else if (state.stories.isNotEmpty() || state.source == ContentSource.SERVER) DiscoveryBanner()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (search.isNotBlank()) "Kết quả tìm kiếm" else if (state.source == ContentSource.LOCAL) "Truyện trên máy" else "Khám phá thư viện", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("${state.stories.size} truyện", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        IconButton(onClick = refresh, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, "Tải lại thư viện") }
                    }
                }
            }
            items(filtered, key = { it.slug }) { story ->
                Card(onClick = { if (!state.busy) open(story.slug) }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        BookCover(story.title, story.slug, Modifier.fillMaxWidth().height(170.dp))
                        Column(Modifier.padding(horizontal = 5.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(story.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(story.description.orEmpty().ifBlank { "Mở truyện để bắt đầu hành trình" }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                MetaLabel(Icons.AutoMirrored.Rounded.MenuBook, "${story.chapterCount} chương", Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty() && !state.busy) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyMessage(Icons.Rounded.SearchOff, if (search.isBlank()) "Thư viện đang trống" else "Chưa tìm thấy truyện",
                    if (search.isBlank() && state.source == ContentSource.LOCAL) "Tải truyện một lần, sau đó đọc và nghe bất cứ lúc nào." else if (search.isBlank()) "Kéo xuống để tải lại thư viện của bạn." else "Thử từ khóa khác hoặc tải thêm truyện bên dưới.",
                    action = if (state.source == ContentSource.LOCAL && search.isBlank()) ({
                        Button(onClick = browse) { Icon(Icons.Rounded.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Tải truyện về máy") }
                    }) else null)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (state.more) OutlinedButton(onClick = more, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (state.busy) "Đang tải truyện…" else "Khám phá thêm")
                } else Text("Bạn đã xem hết thư viện đã tải", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
    }
}

@Composable
private fun DiscoveryBanner() {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Ink, Color(0xFF14574F))))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Mint.copy(alpha = 0.08f), size.width * 0.4f, Offset(size.width, size.height))
            drawCircle(Mint.copy(alpha = 0.12f), size.width * 0.22f, Offset(size.width * 0.93f, size.height * 0.2f))
        }
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MỘT KHOẢNG LẶNG CHO BẠN", style = MaterialTheme.typography.labelSmall, color = Mint)
                Text("Để câu chuyện\ndẫn lối cảm xúc.", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Chọn một truyện và bắt đầu lắng nghe.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
            Icon(Icons.Rounded.Headphones, null, tint = Mint.copy(alpha = 0.85f), modifier = Modifier.size(62.dp))
        }
    }
}

@Composable
private fun ContinueCard(progress: Progress, listen: (ListenPlan) -> Unit, open: (String) -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Rounded.History, null, tint = Mint, modifier = Modifier.size(16.dp))
                Text("TIẾP TỤC CÂU CHUYỆN", style = MaterialTheme.typography.labelSmall, color = Mint)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BookCover(progress.plan.storyTitle, progress.plan.slug, Modifier.size(70.dp, 92.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(progress.plan.storyTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Chương ${progress.plan.chapter}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                    Text(progress.chapterTitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledIconButton(onClick = { listen(progress.plan.copy(sleepMinutes = 0)) }, shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Mint, contentColor = Ink), modifier = Modifier.size(50.dp)) {
                    Icon(Icons.Rounded.PlayArrow, "Tiếp tục nghe", modifier = Modifier.size(30.dp))
                }
            }
            TextButton(onClick = { open(progress.plan.slug) }, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = Mint)) {
                Text("Xem mục lục"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun DetailScreen(detail: StoryDetail, busy: Boolean, read: (Int) -> Unit, listen: (ListenPlan) -> Unit,
    source: ContentSource = ContentSource.SERVER, download: (() -> Unit)? = null, delete: (() -> Unit)? = null,
    savedCount: Int = 0, isDownloading: Boolean = false) {
    var search by remember(detail.story.slug) { mutableStateOf("") }
    val numbers = detail.chapters.map { it.number }.sorted()
    fun plan(number: Int) = ListenPlan(detail.story.slug, detail.story.title, numbers.first(), numbers.last(), number, source = source)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BookCover(detail.story.title, detail.story.slug, Modifier.size(110.dp, 154.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("TRUYỆN & AUDIO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Text(detail.story.title, style = MaterialTheme.typography.headlineSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    MetaLabel(Icons.AutoMirrored.Rounded.MenuBook, "${detail.chapters.size} chương")
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { if (numbers.isNotEmpty()) listen(plan(numbers.first())) }, enabled = numbers.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.Headphones, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Nghe truyện")
                }
                OutlinedButton(onClick = { if (numbers.isNotEmpty()) read(numbers.first()) }, enabled = numbers.isNotEmpty() && !busy,
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Đọc truyện")
                }
            }
            if (source == ContentSource.SERVER && download != null) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = download, enabled = !isDownloading && detail.chapters.isNotEmpty() && savedCount < detail.chapters.size,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(if (savedCount >= detail.chapters.size) Icons.Rounded.CloudDone else Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDownloading) "Đang tải toàn bộ truyện…" else if (savedCount >= detail.chapters.size) "Đã có đủ chương trên máy" else "Tải toàn bộ truyện về máy")
                }
                if (savedCount > 0) Text("Đã lưu $savedCount/${detail.chapters.size} chương · Đăng xuất vẫn giữ truyện", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (source == ContentSource.LOCAL) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaLabel(Icons.Rounded.OfflinePin, "${detail.chapters.size} chương có sẵn · Nghe offline", Modifier.weight(1f))
                    if (delete != null) IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, "Xóa bản trên máy", tint = MaterialTheme.colorScheme.error) }
                }
            }
            if (!detail.story.description.isNullOrBlank()) {
                Spacer(Modifier.height(22.dp))
                Text("Về câu chuyện", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(detail.story.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.FormatListNumbered, null, tint = MaterialTheme.colorScheme.primary)
                Text("Mục lục", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(search, { search = it.filter(Char::isDigit) }, placeholder = { Text("Tìm số chương…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            Spacer(Modifier.height(6.dp))
        }
        items(detail.chapters.filter { search.isBlank() || it.number.toString().contains(search) }, key = { it.number }) { chapter ->
            Card(onClick = { if (!busy) read(chapter.number) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(chapter.number.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(chapter.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Chạm để đọc chương", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { listen(plan(chapter.number)) }, shape = CircleShape) { Icon(Icons.Rounded.Headphones, "Nghe chương ${chapter.number}", modifier = Modifier.size(20.dp)) }
                }
            }
        }
        if (detail.chapters.isEmpty()) item { EmptyMessage(Icons.AutoMirrored.Rounded.MenuBook, "Chưa có chương", "Nội dung sẽ xuất hiện khi truyện có chương.") }
    }
}
