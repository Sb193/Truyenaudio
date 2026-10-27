package com.truyenaudio.app.data

enum class ContentSource { LOCAL, SERVER }

interface ChapterSource {
    suspend fun detail(slug: String): StoryDetail
    suspend fun chapter(slug: String, number: Int): ChapterContentResponse
}

/** A local read never constructs or calls the server client. Missing local chapters stay local errors. */
class ContentRouter(private val local: ChapterSource, private val server: () -> ChapterSource) {
    fun source(mode: ContentSource): ChapterSource = if (mode == ContentSource.LOCAL) local else server()
}
