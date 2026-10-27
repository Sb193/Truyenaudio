package com.truyenaudio.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.truyenaudio.app.data.*
import com.truyenaudio.app.ui.LibraryViewModel
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalLibraryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun story() = Story(1, "qa-${UUID.randomUUID()}", "Kiểm thử local", "Dữ liệu chỉ dành cho kiểm thử", 2)
    private fun chapter(story: Story, number: Int) = ChapterContentResponse(number, "Chương $number", "Nội dung tiếng Việt 😀", story.slug, story.title, 2)
    @Test fun downloadedChaptersSurviveNewStoreAndSessionClear() = runBlocking {
        val library = LocalLibrary(context)
        val story = story()
        try {
            library.putStory(story); library.putChapter(chapter(story, 3)); library.putChapter(chapter(story, 7))
            library.saveProgress(Progress(ListenPlan(story.slug, story.title, 3, 7, 7, source = ContentSource.LOCAL)))
            SecureStore(context).clear()
            val reopened = LocalLibrary(context)
            assertEquals(listOf(3, 7), reopened.detail(story.slug).chapters.map { it.number })
            assertEquals("Nội dung tiếng Việt 😀", reopened.chapter(story.slug, 7).content)
            assertEquals(7, reopened.progress()!!.plan.chapter)
        } finally { library.delete(story.slug) }
    }
    @Test fun restartingDownloadMetadataDoesNotEraseSavedChapters() = runBlocking {
        val library = LocalLibrary(context)
        val story = story()
        try {
            library.putStory(story); library.putChapter(chapter(story, 3))
            library.putStory(story.copy(description = "Mục lục mới"))
            assertTrue(library.hasChapter(story.slug, 3))
            assertFalse(library.hasChapter(story.slug, 4))
            assertEquals(1, library.detail(story.slug).chapters.size)
        } finally { library.delete(story.slug) }
    }
    @Test fun explicitDeleteRemovesOnlySelectedStoryAndRejectsLateProgress() = runBlocking {
        val library = LocalLibrary(context)
        val a = story(); val b = story()
        try {
            library.putStory(a); library.putChapter(chapter(a, 1))
            library.putStory(b); library.putChapter(chapter(b, 1))
            val progress = Progress(ListenPlan(a.slug, a.title, source = ContentSource.LOCAL))
            library.saveProgress(progress)
            library.delete(a.slug)
            library.saveProgress(progress)
            assertFalse(library.hasChapter(a.slug, 1))
            assertTrue(library.hasChapter(b.slug, 1))
            assertNull(library.progress())
            try { library.putStory(a); fail("Tác vụ cũ không được khôi phục truyện đã xóa") }
            catch (expected: kotlinx.coroutines.CancellationException) { assertFalse(library.hasChapter(a.slug, 1)) }
            library.allowDownload(a.slug)
            library.putStory(a); library.putChapter(chapter(a, 1))
            assertTrue(library.hasChapter(a.slug, 1))
        } finally { library.delete(a.slug); library.delete(b.slug) }
    }
    @Test fun actualAppStartsAtLocalLibraryWithoutLogin() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[LibraryViewModel::class.java]
                assertEquals(ContentSource.LOCAL, model.state.value.source)
                assertFalse(model.state.value.checking)
                assertNull(model.state.value.user)
            }
        }
    }
}
