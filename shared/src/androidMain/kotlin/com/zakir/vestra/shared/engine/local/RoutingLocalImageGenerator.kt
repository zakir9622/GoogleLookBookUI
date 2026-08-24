package com.zakir.vestra.shared.engine.local

import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.settings.AppSettings

/**
 * Routes Create Studio to the user's selected on-device image generator: tiny-SD (ONNX,
 * supports edit) or Bonsai Image 4B (LiteRT, text-to-image only). Edit always uses tiny-SD —
 * Bonsai has no reference-image conditioning, and [LocalModelCatalog] never lists it under
 * [AiCapability.IMAGE_EDIT], so it can't be selected there.
 */
class RoutingLocalImageGenerator(
    private val settings: AppSettings,
    private val sdturbo: LocalImageGenerator,
    private val bonsai: LocalImageGenerator,
) : LocalImageGenerator {

    private fun generatorFor(capability: AiCapability): LocalImageGenerator =
        if (settings.selectionId(capability) == BonsaiImageEngine.PACK_ID) bonsai else sdturbo

    override fun isReady(): Boolean = generatorFor(AiCapability.IMAGE_GEN).isReady()

    override fun isEditReady(): Boolean = sdturbo.isEditReady()

    override fun warmUp(): String? = generatorFor(AiCapability.IMAGE_GEN).warmUp()

    override fun generate(prompt: String, seed: Long?, referenceImageUri: String?): LocalImageResult {
        val generator = if (!referenceImageUri.isNullOrBlank()) sdturbo else generatorFor(AiCapability.IMAGE_GEN)
        return generator.generate(prompt, seed, referenceImageUri)
    }

    override fun generateStream(prompt: String, seed: Long?, referenceImageUri: String?) =
        (if (!referenceImageUri.isNullOrBlank()) sdturbo else generatorFor(AiCapability.IMAGE_GEN))
            .generateStream(prompt, seed, referenceImageUri)
}
