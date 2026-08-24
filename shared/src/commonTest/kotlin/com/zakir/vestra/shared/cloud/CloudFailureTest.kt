package com.zakir.vestra.shared.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudFailureTest {

    @Test
    fun blankFrameClassifiesAsBadOutput() {
        val failure = CloudFailureClassifier.fromMessage("Downloaded image looks blank (low variance)")
        assertEquals(CloudFailure.BadOutput, failure)
        assertTrue(failure.retryVariants)
        assertFalse(failure.advanceModel)
    }

    @Test
    fun routeUnsupportedAdvancesModel() {
        val failure = CloudFailureClassifier.fromMessage("HTTP 400: Model not supported by provider nscale")
        assertEquals(CloudFailure.RouteUnsupported, failure)
        assertTrue(failure.advanceModel)
        assertFalse(failure.retryVariants)
    }

    @Test
    fun safetyBlockedRetriesVariantsOnly() {
        val failure = CloudFailureClassifier.fromMessage("Content blocked by NSFW safety filter")
        assertEquals(CloudFailure.SafetyBlocked, failure)
        assertFalse(failure.advanceModel)
        assertTrue(failure.retryVariants)
    }

    @Test
    fun connectionAbortIsTimeoutNotOffline() {
        val failure = CloudFailureClassifier.fromMessage("Software caused connection abort")
        assertEquals(CloudFailure.Timeout, failure)
        assertTrue(failure.advanceModel)
        assertTrue(failure.retryable)
    }

    @Test
    fun offlineShortCircuits() {
        val failure = CloudFailureClassifier.fromMessage("Unable to resolve host \"example.hf.space\"")
        assertEquals(CloudFailure.Offline, failure)
        assertFalse(failure.advanceModel)
    }

    @Test
    fun failedToConnectIsTimeoutNotOffline() {
        val failure = CloudFailureClassifier.fromMessage("failed to connect to /142.250.0.0")
        assertEquals(CloudFailure.Timeout, failure)
        assertTrue(failure.retryable)
    }

    @Test
    fun creditsExhaustedSkipsInferenceChain() {
        val failure = CloudFailureClassifier.fromMessage("HTTP 402: depleted your monthly Inference Providers credits")
        assertEquals(CloudFailure.CreditsExhausted, failure)
        assertTrue(failure.advanceModel)
    }

    @Test
    fun space404IsHostUnavailable() {
        val failure = CloudFailureClassifier.fromMessage(
            "Hugging Face Space black-forest-labs-flux-1-schnell.hf.space/infer HTTP 404: Not Found",
        )
        assertEquals(CloudFailure.HostUnavailable, failure)
        assertTrue(failure.advanceModel)
        assertTrue(failure.retryable)
    }

    @Test
    fun friendlyOffline404StillClassifies() {
        val failure = CloudFailureClassifier.fromMessage("FLUX.1 Schnell Space looks offline (404). Switch model in Settings.")
        assertEquals(CloudFailure.HostUnavailable, failure)
    }

    @Test
    fun unknownSanitizesHostnamesInHint() {
        val failure = CloudFailureClassifier.fromMessage(
            "Gradio error from https://someone-tryon.hf.space/call/predict: boom",
        )
        assertTrue(failure is CloudFailure.Unknown)
        val hint = failure.toUserHint()
        assertFalse(hint.contains("hf.space", ignoreCase = true))
        assertFalse(hint.contains("https://", ignoreCase = true))
        assertTrue(hint.contains("[host]"))
    }

    @Test
    fun sanitizeHostnamesStripsBareSpaceHost() {
        val cleaned = sanitizeHostnames("failed at ootdiffusion.hf.space with 500")
        assertFalse(cleaned.contains("ootdiffusion"))
        assertTrue(cleaned.contains("[host]"))
    }
}
