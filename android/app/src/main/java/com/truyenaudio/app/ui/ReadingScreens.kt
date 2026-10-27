package com.truyenaudio.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truyenaudio.app.data.*

@Composable
fun LoginScreen(busy: Boolean, login: (String, String, () -> Unit) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))))) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BrandMark(96.dp)
            Text("Lắng nghe một câu chuyện.\nTìm lại một khoảng bình yên.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text("Thư viện truyện tiếng Việt, luôn bên bạn.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), shadowElevation = 3.dp) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Chào mừng trở lại", style = MaterialTheme.typography.titleLarge)
                    Text("Đăng nhập để tiếp tục hành trình của bạn.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(username, { username = it }, label = { Text("Tên tài khoản") },
                        leadingIcon = { Icon(Icons.Rounded.PersonOutline, null) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") },
                        leadingIcon = { Icon(Icons.Rounded.LockOpen, null) }, singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu") } })
                    Button(onClick = { login(username, password) { password = "" } }, enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else { Text("Đăng nhập"); Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Text("Tài khoản của bạn luôn được bảo vệ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ReaderScreen(chapter: ChapterContentResponse, numbers: List<Int>, busy: Boolean,
    fontSize: Float, changeFont: (Float) -> Unit, lineScale: Float, changeLine: (Float) -> Unit,
    read: (Int) -> Unit, listen: () -> Unit) {
    var settings by remember { mutableStateOf(false) }
    val index = numbers.indexOf(chapter.number)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(chapter.storyTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(chapter.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = listen, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(44.dp)) {
                    Icon(Icons.Rounded.Headphones, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Nghe chương")
                }
                TextButton(onClick = { settings = !settings }) {
                    Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Hiển thị")
                }
                Spacer(Modifier.weight(1f))
                MetaLabel(Icons.Rounded.BookmarkBorder, "Chương ${chapter.number}")
            }
            AnimatedVisibility(settings) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cỡ chữ ${fontSize.toInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
                            Slider(fontSize, changeFont, valueRange = 14f..30f, steps = 15)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Giãn dòng", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
                            Slider(lineScale, changeLine, valueRange = 1.2f..2.2f)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        key(chapter.slug, chapter.number) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(chapter.content, fontFamily = FontFamily.Serif, fontSize = fontSize.sp, lineHeight = (fontSize * lineScale).sp,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { read(numbers[index - 1]) }, enabled = index > 0 && !busy, shape = CircleShape) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Chương trước")
            }
            OutlinedButton(onClick = { read(numbers[index + 1]) }, enabled = index >= 0 && index + 1 < numbers.size && !busy, shape = CircleShape) {
                Text("Chương sau"); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
