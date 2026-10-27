package com.truyenaudio.app.core.tts

object TextChunker {
    private const val SAFETY_MARGIN = 64
    private const val DEFAULT_FALLBACK_MAX_LENGTH = 3900

    fun splitContentIntoChunks(
        content: String,
        engineMaxSpeechLength: Int = DEFAULT_FALLBACK_MAX_LENGTH
    ): List<String> {
        if (content.isBlank()) return emptyList()
        val actualMax = if (engineMaxSpeechLength > SAFETY_MARGIN) {
            engineMaxSpeechLength - SAFETY_MARGIN
        } else {
            DEFAULT_FALLBACK_MAX_LENGTH
        }

        val paragraphs = content.split(Regex("(?:\r\n|\r|\n)+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val atomicSentences = mutableListOf<String>()
        for (paragraph in paragraphs) {
            val sentences = splitParagraphIntoSentences(paragraph)
            for (sentence in sentences) {
                if (sentence.length <= actualMax) {
                    atomicSentences.add(sentence)
                } else {
                    atomicSentences.addAll(splitLongSentence(sentence, actualMax))
                }
            }
        }

        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (sentence in atomicSentences) {
            if (currentChunk.isEmpty()) {
                currentChunk.append(sentence)
            } else {
                if (currentChunk.length + 1 + sentence.length <= actualMax) {
                    currentChunk.append(" ").append(sentence)
                } else {
                    chunks.add(currentChunk.toString())
                    currentChunk.clear()
                    currentChunk.append(sentence)
                }
            }
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk.toString())
        return chunks
    }

    private fun splitParagraphIntoSentences(paragraph: String): List<String> {
        val sentenceRegex = Regex("(?<=[.?!…]['\"”’]?)\s+")
        return paragraph.split(sentenceRegex).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun splitLongSentence(sentence: String, maxLength: Int): List<String> {
        val result = mutableListOf<String>()
        var remaining = sentence.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLength) {
                result.add(remaining)
                break
            }
            val splitIndex = remaining.lastIndexOf(' ', maxLength)
            if (splitIndex > 0) {
                result.add(remaining.substring(0, splitIndex).trim())
                remaining = remaining.substring(splitIndex + 1).trim()
            } else {
                val safeIndex = findSafeUnicodeCutIndex(remaining, maxLength)
                result.add(remaining.substring(0, safeIndex).trim())
                remaining = remaining.substring(safeIndex).trim()
            }
        }
        return result
    }

    private fun findSafeUnicodeCutIndex(text: String, targetIndex: Int): Int {
        if (targetIndex >= text.length) return text.length
        var index = targetIndex
        if (Character.isLowSurrogate(text[index]) && index > 0 && Character.isHighSurrogate(text[index - 1])) {
            index--
        }
        return if (index > 0) index else targetIndex
    }
}