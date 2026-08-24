package com.zakir.vestra.shared.packs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackHandshakeTest {

    @Test
    fun wiresMapSdturboToCreateEditVideo() {
        val wires = PackHandshakeWires.forPackId("local-sdturbo-v1")
        assertTrue(wires.any { it.contains("Image Create") })
        assertTrue(wires.any { it.contains("Image Edit") })
        assertTrue(wires.any { it.contains("Video") })
    }

    @Test
    fun wiresMapGemmaToCode() {
        val wires = PackHandshakeWires.forPackId("local-gemma-v1")
        assertTrue(wires.any { it.contains("Code", ignoreCase = true) })
    }

    @Test
    fun wiresMapLiteToTryOn() {
        val wires = PackHandshakeWires.forPackId("lite-v1")
        assertTrue(wires.any { it.contains("Lite") })
    }

    @Test
    fun formatDetailIncludesHumanSummaryWhenOk() {
        val result = PackHandshakeResult(
            packId = "lite-v1",
            displayName = "Lite",
            ok = true,
            signal = PackHandshakeResult.SIGNAL_OK,
            message = "Linked to device",
            wires = listOf("Try-on Lite"),
            verifiedAtMs = 1L,
        )
        val detail = PackHandshakeWires.formatDetail(result)
        assertTrue(detail.contains("Linked to this device"))
        assertTrue(detail.contains("Try-on Lite"))
        assertFalse(detail.contains("HANDSHAKE_OK"))
    }

    @Test
    fun reportSignalOkWhenAllPass() {
        val ok = PackHandshakeResult(
            packId = "a",
            displayName = "A",
            ok = true,
            signal = PackHandshakeResult.SIGNAL_OK,
            message = "ok",
            wires = emptyList(),
            verifiedAtMs = 1L,
        )
        val report = PackHandshakeReport(listOf(ok), startedAtMs = 1L, finishedAtMs = 2L)
        assertTrue(report.allOk)
        assertEquals(PackHandshakeResult.SIGNAL_OK, report.signal)
        assertTrue(report.summary.contains("Handshake OK"))
    }

    @Test
    fun reportSignalFailWhenAnyFail() {
        val fail = PackHandshakeResult(
            packId = "a",
            displayName = "A",
            ok = false,
            signal = PackHandshakeResult.SIGNAL_FAIL,
            message = "bad",
            wires = emptyList(),
            verifiedAtMs = 1L,
        )
        val report = PackHandshakeReport(listOf(fail), startedAtMs = 1L, finishedAtMs = 2L)
        assertFalse(report.allOk)
        assertEquals(PackHandshakeResult.SIGNAL_FAIL, report.signal)
    }
}
