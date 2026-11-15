package io.github.maximbelov.anyline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlotsTest {
    @Test
    fun handsBackTheValueANewOneReplaces() {
        val slots = Slots<String>()
        assertNull(slots.put("scan", "first"))
        assertEquals("first", slots.put("scan", "second"))
        assertEquals("second", slots.take("scan"))
    }

    @Test
    fun takesAValueOnce() {
        val slots = Slots<String>()
        slots.put("init", "call")
        assertEquals("call", slots.take("init"))
        assertNull(slots.take("init"))
    }

    @Test
    fun keepsTheSlotsApart() {
        val slots = Slots<String>()
        slots.put("init", "a")
        slots.put("scan", "b")
        assertEquals("b", slots.take("scan"))
        assertEquals("a", slots.take("init"))
    }
}
