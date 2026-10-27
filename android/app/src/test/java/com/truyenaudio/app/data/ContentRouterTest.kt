package com.truyenaudio.app.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContentRouterTest {
    private class MemorySource(private val missing: Boolean = false) : ChapterSource {
        var reads = 0
        override suspend fun detail(slug: String) = StoryDetail(Story(1, slug, "Truyện", "", 1), listOf(ChapterSummary(7, "Chương bảy")))
        override suspend fun chapter(slug: String, number: Int): ChapterContentResponse {
            reads++
            if (missing) throw IOException("Chương chưa có trên máy")
            return ChapterContentResponse(number, "Chương bảy", "Nội dung trên máy", slug, "Truyện", 1)
        }
    }
    @Test fun offlineReadsNeverConstructTheServerClient() = runBlocking {
        val local = MemorySource()
        var serverConstructions = 0
        val router = ContentRouter(local) { serverConstructions++; error("Không được gọi server") }
        assertEquals(7, router.source(ContentSource.LOCAL).detail("truyen").chapters.single().number)
        assertEquals("Nội dung trên máy", router.source(ContentSource.LOCAL).chapter("truyen", 7).content)
        assertEquals(0, serverConstructions)
        assertEquals(1, local.reads)
    }
    @Test fun missingLocalChapterDoesNotSilentlyFallBackToServer() = runBlocking {
        var serverConstructions = 0
        val router = ContentRouter(MemorySource(true)) { serverConstructions++; MemorySource() }
        try { router.source(ContentSource.LOCAL).chapter("truyen", 8); fail("Phải báo chương chưa tải") }
        catch (expected: IOException) { assertEquals(0, serverConstructions) }
    }
    @Test fun serverReadsRequireExplicitServerSource() = runBlocking {
        val server = MemorySource()
        val router = ContentRouter(MemorySource()) { server }
        router.source(ContentSource.SERVER).chapter("truyen", 7)
        assertEquals(1, server.reads)
    }
    @Test fun defaultUiOpensLocalWithoutAuthentication() {
        val state = com.truyenaudio.app.ui.LibraryState()
        assertEquals(ContentSource.LOCAL, state.source)
        assertFalse(state.checking)
        assertNull(state.user)
    }
}
