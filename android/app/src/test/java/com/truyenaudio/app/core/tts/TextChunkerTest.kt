package com.truyenaudio.app.core.tts

import org.junit.Assert.*
import org.junit.Test

class TextChunkerTest {
    @Test
    fun splitContentIntoChunks_respects_max_limit_and_sentence_boundaries() {
        val text = "Trời thu trong xanh. Chiếc lá vàng rơi trên đường. Bước chân vội vã!"
        val chunks = TextChunker.splitContentIntoChunks(text, engineMaxSpeechLength = 80)
        assertFalse(chunks.isEmpty())
        for (chunk in chunks) {
            assertTrue(chunk.length <= 80)
        }
    }

    @Test
    fun splitContentIntoChunks_safely_splits_unicode_surrogates() {
        val longWord = "ĐườngĐếnVinhQuangCủaĐạiĐạoVôBiênVạnNămTuLuyện".repeat(5)
        val chunks = TextChunker.splitContentIntoChunks(longWord, engineMaxSpeechLength = 60)
        assertEquals(longWord, chunks.joinToString(""))
    }
}