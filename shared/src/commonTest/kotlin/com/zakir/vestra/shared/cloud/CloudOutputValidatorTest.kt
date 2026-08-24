package com.zakir.vestra.shared.cloud

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CloudOutputValidatorTest {

    @Test
    fun rejectsEmptyAndTinyPayloads() {
        assertNotNull(CloudOutputValidator.validate(byteArrayOf()))
        assertNotNull(CloudOutputValidator.validate(ByteArray(100)))
    }

    @Test
    fun acceptsValidSizedPng() {
        val png = byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
            0x0D, 0x0A, 0x1A, 0x0A,
            // IHDR chunk length
            0x00, 0x00, 0x00, 0x0D,
            'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
            0x00, 0x00, 0x00, 0x40, // width 64
            0x00, 0x00, 0x00, 0x40, // height 64
            0x08, 0x02, 0x00, 0x00, 0x00,
        ) + ByteArray(2_100)
        assertNull(CloudOutputValidator.validate(png))
    }

    @Test
    fun rejectsBelowTwoKilobyteFloor() {
        val png = byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
            0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
            0x00, 0x00, 0x00, 0x40,
            0x00, 0x00, 0x00, 0x40,
            0x08, 0x02, 0x00, 0x00, 0x00,
        ) + ByteArray(1_500)
        assertNotNull(CloudOutputValidator.validate(png))
    }

    @Test
    fun rejectsTinyPng() {
        val tiny = byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
            0x0D, 0x0A, 0x1A, 0x0A,
        ) + ByteArray(60)
        assertNotNull(CloudOutputValidator.validate(tiny))
    }

    @Test
    fun acceptsMp4HeaderForVideo() {
        val mp4 = byteArrayOf(0, 0, 0, 0, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()) +
            ByteArray(10_000)
        assertNull(CloudOutputValidator.validate(mp4, isVideo = true))
    }
}
