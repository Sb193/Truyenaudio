package com.truyenaudio.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Saves one chapter at a time and resumes by checking committed local chapters. */
object WholeStoryTransfer {
    suspend fun run(slug: String, chapters: List<ChapterSummary>,
        exists: suspend (Int) -> Boolean, read: suspend (Int) -> ChapterContentResponse,
        save: suspend (ChapterContentResponse) -> Unit, report: suspend (Int, Int) -> Unit) {
        val ordered = chapters.distinctBy { it.number }.sortedBy { it.number }
        require(ordered.isNotEmpty()) { "Truyện chưa có chương để tải" }
        for ((index, chapter) in ordered.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (!exists(chapter.number)) {
                val content = read(chapter.number)
                currentCoroutineContext().ensureActive()
                require(content.slug == slug && content.number == chapter.number) { "Máy chủ trả về chương không khớp" }
                save(content)
            }
            report(index + 1, ordered.size)
        }
    }
}
