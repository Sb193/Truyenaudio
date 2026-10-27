package com.truyenaudio.app.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WholeStoryTransferTest {
    private val toc = listOf(ChapterSummary(7, "Cuối truyện"), ChapterSummary(1, "Chương 999"), ChapterSummary(3, "Tên không có số"))
    private fun content(number: Int, slug: String = "story") = ChapterContentResponse(number, "Chương $number", "Nội dung", slug, "Truyện", 3)
    @Test fun downloadsEveryRealChapterInOrderAndSkipsAlreadySavedOnes() = runBlocking {
        val saved = mutableMapOf(3 to content(3))
        val requests = mutableListOf<Int>()
        val progress = mutableListOf<Pair<Int, Int>>()
        WholeStoryTransfer.run("story", toc, { it in saved }, { requests.add(it); content(it) }, { saved[it.number] = it }, { done, total -> progress.add(done to total) })
        assertEquals(listOf(1, 7), requests)
        assertEquals(setOf(1, 3, 7), saved.keys)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
    }
    @Test fun interruptedTransferPreservesChaptersAndNextAttemptOnlyDownloadsMissingOnes() = runBlocking {
        val saved = mutableMapOf<Int, ChapterContentResponse>()
        try {
            WholeStoryTransfer.run("story", toc, { it in saved }, { if (it == 3) throw IOException("Mất mạng") else content(it) }, { saved[it.number] = it }, { _, _ -> })
            fail("Phải dừng khi mất mạng")
        } catch (expected: IOException) { assertEquals(setOf(1), saved.keys) }
        val resumed = mutableListOf<Int>()
        WholeStoryTransfer.run("story", toc, { it in saved }, { resumed.add(it); content(it) }, { saved[it.number] = it }, { _, _ -> })
        assertEquals(listOf(3, 7), resumed)
        assertEquals(3, saved.size)
    }
    @Test fun refusesWrongStoryWithoutWritingItToLocalLibrary() = runBlocking {
        var writes = 0
        try {
            WholeStoryTransfer.run("story", toc, { false }, { content(it, "other-story") }, { writes++ }, { _, _ -> })
            fail("Phải từ chối nội dung sai truyện")
        } catch (expected: IllegalArgumentException) { assertEquals(0, writes) }
    }
}
