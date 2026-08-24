package com.zakir.vestra.shared.engine

import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.GarmentImage
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.PersonSource
import com.zakir.vestra.shared.domain.TryOnError
import com.zakir.vestra.shared.domain.TryOnRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeEngine(
    override val tier: EngineTier,
    private val availability: Availability = Availability.Ready,
) : TryOnEngine {
    override fun isAvailable(): Availability = availability
    override fun generate(request: TryOnRequest): Flow<GenerationState> = emptyFlow()
}

class EngineRouterTest {

    private fun request(tier: EngineTier) = TryOnRequest(
        garment = GarmentImage(uri = "file:///garment.jpg"),
        person = PersonSource.AiModel("base-01"),
        tier = tier,
    )

    @Test
    fun autoPrefersProWhenReady() {
        val pro = FakeEngine(EngineTier.PRO)
        val lite = FakeEngine(EngineTier.LITE)
        val router = EngineRouter(listOf(lite, pro))
        assertEquals(pro, router.resolve(EngineTier.AUTO))
    }

    @Test
    fun autoFallsBackToLiteWhenProUnavailable() {
        val pro = FakeEngine(EngineTier.PRO, Availability.Unavailable(UnavailableReason.PACK_NOT_INSTALLED))
        val lite = FakeEngine(EngineTier.LITE)
        val router = EngineRouter(listOf(lite, pro))
        assertEquals(lite, router.resolve(EngineTier.AUTO))
    }

    @Test
    fun autoNeverSelectsCloud() {
        val cloud = FakeEngine(EngineTier.CLOUD)
        val pro = FakeEngine(EngineTier.PRO)
        val lite = FakeEngine(EngineTier.LITE)
        val router = EngineRouter(listOf(cloud, lite, pro))
        assertEquals(pro, router.resolve(EngineTier.AUTO))
        assertEquals(cloud, router.resolve(EngineTier.CLOUD))
    }

    @Test
    fun unavailableEngineFailsWithMappedError() = runTest {
        val pro = FakeEngine(EngineTier.PRO, Availability.Unavailable(UnavailableReason.PACK_NOT_INSTALLED))
        val router = EngineRouter(listOf(pro))
        val terminal = router.generate(request(EngineTier.PRO)).last()
        val failed = assertIs<GenerationState.Failed>(terminal)
        val internal = assertIs<TryOnError.Internal>(failed.error)
        assertEquals(true, internal.message.contains("pack", ignoreCase = true))
    }

    @Test
    fun packVerifyFailedMapsToActionableError() = runTest {
        val lite = FakeEngine(
            EngineTier.LITE,
            Availability.Unavailable(UnavailableReason.PACK_VERIFY_FAILED),
        )
        val router = EngineRouter(listOf(lite))
        val terminal = router.generate(request(EngineTier.LITE)).last()
        val failed = assertIs<GenerationState.Failed>(terminal)
        val internal = assertIs<TryOnError.Internal>(failed.error)
        assertEquals(true, internal.message.contains("lite-v1", ignoreCase = true))
        assertEquals(true, internal.message.contains("verification", ignoreCase = true))
    }

    @Test
    fun packVerifyPendingMapsToWaitMessage() = runTest {
        val lite = FakeEngine(
            EngineTier.LITE,
            Availability.Unavailable(UnavailableReason.PACK_VERIFY_PENDING),
        )
        val router = EngineRouter(listOf(lite))
        val terminal = router.generate(request(EngineTier.LITE)).last()
        val failed = assertIs<GenerationState.Failed>(terminal)
        val internal = assertIs<TryOnError.Internal>(failed.error)
        assertEquals(true, internal.message.contains("verifying", ignoreCase = true))
    }

    @Test
    fun missingEngineFailsInsteadOfThrowing() = runTest {
        val router = EngineRouter(emptyList())
        val terminal = router.generate(request(EngineTier.LITE)).last()
        assertIs<GenerationState.Failed>(terminal)
    }

    @Test
    fun autoFallsBackToLiteWhenProOrtIncompatible() = runTest {
        val pro = object : TryOnEngine {
            override val tier = EngineTier.PRO
            override fun isAvailable() = Availability.Ready
            override fun generate(request: TryOnRequest): Flow<GenerationState> = flowOf(
                GenerationState.Failed(
                    TryOnError.Internal(
                        "Pro pack UNet (unet.onnx) is incompatible with this ONNX Runtime " +
                            "(FP16 type mismatch). Use Lite try-on.",
                    ),
                ),
            )
        }
        val lite = object : TryOnEngine {
            override val tier = EngineTier.LITE
            override fun isAvailable() = Availability.Ready
            override fun generate(request: TryOnRequest): Flow<GenerationState> = flowOf(
                GenerationState.Running(0.5f, "Lite running"),
                GenerationState.Complete(
                    com.zakir.vestra.shared.domain.TryOnResult(
                        imagePath = "/tmp/lite.jpg",
                        executedTier = EngineTier.LITE,
                        durationMillis = 10,
                        watermarked = false,
                    ),
                ),
            )
        }
        val router = EngineRouter(listOf(pro, lite))
        val states = mutableListOf<GenerationState>()
        router.generate(request(EngineTier.AUTO)).collect { states.add(it) }
        assertTrue(states.any { it is GenerationState.Running && it.stage.contains("Lite", ignoreCase = true) })
        val terminal = states.last()
        assertIs<GenerationState.Complete>(terminal)
        assertEquals(EngineTier.LITE, terminal.result.executedTier)
    }

    @Test
    fun autoDoesNotFallbackWhenProFailsForOtherReasons() = runTest {
        val pro = object : TryOnEngine {
            override val tier = EngineTier.PRO
            override fun isAvailable() = Availability.Ready
            override fun generate(request: TryOnRequest): Flow<GenerationState> = flowOf(
                GenerationState.Failed(TryOnError.Internal("Couldn't read the selected images")),
            )
        }
        val lite = FakeEngine(EngineTier.LITE)
        val router = EngineRouter(listOf(pro, lite))
        val terminal = router.generate(request(EngineTier.AUTO)).last()
        val failed = assertIs<GenerationState.Failed>(terminal)
        val internal = assertIs<TryOnError.Internal>(failed.error)
        assertEquals(true, internal.message.contains("Couldn't read", ignoreCase = true))
    }
}
