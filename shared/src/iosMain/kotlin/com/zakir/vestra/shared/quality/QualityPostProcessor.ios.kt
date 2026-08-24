package com.zakir.vestra.shared.quality

import com.zakir.vestra.shared.packs.ModelPackManager

actual fun createQualityPostProcessor(packs: ModelPackManager): QualityPostProcessor =
    NoOpQualityPostProcessor
