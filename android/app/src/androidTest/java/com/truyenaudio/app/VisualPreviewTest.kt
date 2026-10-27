package com.truyenaudio.app

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.truyenaudio.app.data.*
import com.truyenaudio.app.ui.*
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/** Visual fixtures are test-only: these screens never replace the production Repository/API. */
@RunWith(AndroidJUnit4::class)
class VisualPreviewTest {
    private val stories = listOf(
        Story(1, "hanh-trinh", "Hành Trình Qua Những Miền Đất Lạ", "Một chuyến đi, những cuộc gặp và câu chuyện về lòng can đảm.", 128),
        Story(2, "mua-ha", "Mùa Hạ Của Chúng Ta", "Những ngày nắng vàng và ký ức không bao giờ phai.", 86),
        Story(3, "tinh-khong", "Dưới Bầu Trời Tinh Không", "Theo những vì sao, tìm lại con đường của chính mình.", 240),
        Story(4, "ngay-moi", "Khi Ngày Mới Bắt Đầu", "Chậm lại một chút để lắng nghe những điều bình dị.", 64)
    )
    private val chapters = (1..12).map { ChapterSummary(it, "Chương $it: Những bước chân đầu tiên") }
    @Test fun captureActualComposeScreens() {
        capture("login-light", false) { LoginScreen(false) { _, _, _ -> } }
        capture("login-dark", true) { LoginScreen(false) { _, _, _ -> } }
        capture("library-light", false) {
            LibraryScreen(LibraryState(checking = false, user = User("preview", "Bạn đọc"), stories = stories, page = 1, more = false,
                progress = Progress(ListenPlan("hanh-trinh", stories[0].title, 1, 128, 12, 320), "Chương 12: Một khởi đầu mới")), {}, {}, {}, {})
        }
        capture("library-dark", true) {
            LibraryScreen(LibraryState(checking = false, user = User("preview", "Bạn đọc"), stories = stories, page = 1, more = false), {}, {}, {}, {})
        }
        capture("detail-light", false) { DetailScreen(StoryDetail(stories[0], chapters), false, {}, {}) }
        capture("reader-dark", true) {
            ReaderScreen(ChapterContentResponse(1, "Chương 1: Những bước chân đầu tiên",
                "Buổi sớm, khi những tia nắng đầu tiên vừa chạm xuống mái nhà, con đường nhỏ đã thức giấc.\n\n" +
                    "Tiếng chim ríu rít trong vòm lá. Cô khép cuốn sách, nhìn ra khoảng trời xanh và mỉm cười. Hôm nay sẽ là một ngày thật khác.\n\n" +
                    "Có những hành trình bắt đầu từ một bước chân. Cũng có những hành trình chỉ cần một câu chuyện để mở ra cả thế giới.",
                stories[0].slug, stories[0].title, 128), (1..128).toList(), false, 19f, {}, 1.7f, {}, {}, {})
        }
    }
    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val scenario = ActivityScenario.launch(VisualPreviewActivity::class.java)
        try {
            scenario.onActivity { activity -> activity.setContent {
                TruyenAudioTheme(dark) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                            AppHeader(if (name.startsWith("login")) null else "Bạn đọc",
                                if (name.startsWith("detail")) "Chi tiết truyện" else if (name.startsWith("reader")) "Đang đọc" else null,
                                dark, {}, if (name.startsWith("detail") || name.startsWith("reader")) ({}) else null, {})
                            Box(Modifier.weight(1f)) { content() }
                        }
                    }
                }
            } }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            SystemClock.sleep(1200)
            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        } finally { scenario.close() }
    }
}
