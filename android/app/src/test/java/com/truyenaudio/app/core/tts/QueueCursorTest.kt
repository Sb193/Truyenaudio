package com.truyenaudio.app.core.tts

import org.junit.Assert.*
import org.junit.Test

class QueueCursorTest {
    @Test fun duplicateCallbackCannotSkipAChunk() {
        val queue = QueueCursor()
        queue.load(3); queue.resume()
        val id = queue.id()
        assertTrue(queue.complete(id))
        assertFalse(queue.complete(id))
        assertEquals(1, queue.index)
    }
    @Test fun staleCallbacksCannotPlayAnotherStory() {
        val queue = QueueCursor()
        queue.load(5); queue.resume()
        val old = queue.id()
        queue.load(2); queue.resume()
        assertFalse(queue.complete(old))
        assertEquals(0, queue.index)
    }
    @Test fun pauseInvalidatesCallbacksAndResumeKeepsPosition() {
        val queue = QueueCursor()
        queue.load(4, 2); queue.resume()
        val old = queue.id()
        queue.invalidate()
        assertFalse(queue.accepts(old))
        queue.resume()
        assertFalse(queue.complete(old))
        assertEquals(2, queue.index)
        assertTrue(queue.complete(queue.id()))
    }
    @Test fun finalChunkCompletesExactlyOnce() {
        val queue = QueueCursor()
        queue.load(1); queue.resume()
        val id = queue.id()
        assertTrue(queue.complete(id))
        assertFalse(queue.active)
        assertFalse(queue.complete(id))
    }
}
