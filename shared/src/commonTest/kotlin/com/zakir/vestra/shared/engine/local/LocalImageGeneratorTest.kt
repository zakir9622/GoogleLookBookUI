package com.zakir.vestra.shared.engine.local

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalImageGeneratorTest {

    @Test
    fun unimplementedNeverReady() {
        assertFalse(UnimplementedLocalImageGenerator.isReady())
        val result = UnimplementedLocalImageGenerator.generate("abaya")
        assertTrue(result is LocalImageResult.Unavailable)
    }

    @Test
    fun packAwareNotReadyUntilRunnerImplemented() {
        val gen = PackAwareLocalImageGenerator(packReady = { true }, runnerImplemented = false)
        assertFalse(gen.isReady())
        val result = gen.generate("abaya")
        assertTrue(result is LocalImageResult.Unavailable)
        assertTrue((result as LocalImageResult.Unavailable).reason.contains("not wired", ignoreCase = true))
    }

    @Test
    fun packAwareReadyOnlyWhenRunnerAndPack() {
        val gen = PackAwareLocalImageGenerator(packReady = { true }, runnerImplemented = true)
        assertTrue(gen.isReady())
    }
}
