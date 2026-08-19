package com.thelightphone.lightpage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderStateBridgeTest {

    @Test
    fun `onReaderApplied emits applied value`() {
        val applied = mutableListOf<Boolean>()
        val bridge = ReaderStateBridge(
            onReaderApplied = { applied += it },
            onReaderError = { }
        )

        bridge.onReaderApplied(true)
        bridge.onReaderApplied(false)

        assertTrue(applied[0])
        assertFalse(applied[1])
    }

    @Test
    fun `onReaderError emits reason`() {
        val errors = mutableListOf<String>()
        val bridge = ReaderStateBridge(
            onReaderApplied = { },
            onReaderError = { errors += it }
        )

        bridge.onReaderError("TOO_SHORT")
        bridge.onReaderError("EXCEPTION")

        assertEquals("TOO_SHORT", errors[0])
        assertEquals("EXCEPTION", errors[1])
    }
}
