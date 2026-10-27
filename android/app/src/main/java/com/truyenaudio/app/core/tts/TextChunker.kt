package com.truyenaudio.app.core.tts

data class SpeechChunk(val text: String, val start: Int, val end: Int)

object TextChunker {
    fun splitContentIntoChunks(content: String, engineMaxSpeechLength: Int = 3900): List<String> =
        split(content, engineMaxSpeechLength).map { it.text }

    fun split(content: String, limit: Int): List<SpeechChunk> {
        require(limit >= 2) { "Giới hạn giọng đọc phải ít nhất 2 ký tự" }
        if (content.isBlank()) return emptyList()
        val chunks = mutableListOf<SpeechChunk>()
        var start = 0
        while (start < content.length) {
            var end = minOf(start + limit, content.length)
            if (end < content.length) {
                if (Character.isLowSurrogate(content[end]) && Character.isHighSurrogate(content[end - 1])) end--
                var sentence = -1
                var space = -1
                for (i in start until end) {
                    if (content[i].isWhitespace()) {
                        space = i + 1
                        var before = i - 1
                        while (before >= start && content[before] in listOf('"', '\'', '”', '’')) before--
                        if (content[i] == '\n' || (before >= start && content[before] in listOf('.', '?', '!', '…'))) sentence = i + 1
                    }
                }
                end = when {
                    sentence > start -> sentence
                    space > start -> space
                    else -> end
                }
            }
            chunks.add(SpeechChunk(content.substring(start, end), start, end))
            start = end
        }
        return chunks
    }
}
