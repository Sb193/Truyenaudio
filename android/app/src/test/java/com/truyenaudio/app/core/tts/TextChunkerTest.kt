package com.truyenaudio.app.core.tts

import org.junit.Assert.*
import org.junit.Test

class TextChunkerTest {
    @Test fun preservesEveryCharacterAndOffsets() {
        val text = "  Chương 1.\r\nTrời thu trong xanh!  Lá rơi…\n\nMột câu dài " + "tiếng Việt 😀 ".repeat(100)
        val chunks = TextChunker.split(text, 80)
        assertEquals(text, chunks.joinToString("") { it.text })
        assertEquals(0, chunks.first().start)
        assertEquals(text.length, chunks.last().end)
        chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        assertTrue(chunks.all { it.text.length <= 80 && it.text.isNotEmpty() })
    }
    @Test fun respectsSmallEngineLimits() {
        val text = "ĐườngĐếnVinhQuang".repeat(20)
        val chunks = TextChunker.split(text, 60)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 60 })
        assertEquals(text, chunks.joinToString("") { it.text })
    }
    @Test fun neverCutsASurrogatePair() {
        val text = "😀🦉a".repeat(100)
        for (limit in 2..33) {
            val chunks = TextChunker.split(text, limit)
            assertEquals(text, chunks.joinToString("") { it.text })
            chunks.forEach {
                assertFalse(Character.isLowSurrogate(it.text.first()))
                assertFalse(Character.isHighSurrogate(it.text.last()))
                assertTrue(it.text.length <= limit)
            }
        }
    }
    @Test fun prefersSentenceAndParagraphBoundaries() {
        val chunks = TextChunker.split("Một câu. Hai câu nữa và dài hơn nhiều.", 18)
        assertEquals("Một câu. ", chunks.first().text)
    }
    @Test fun blankTextDoesNotStartPlayback() { assertTrue(TextChunker.split("\n  ", 80).isEmpty()) }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidLimit() { TextChunker.split("a", 1) }
}
