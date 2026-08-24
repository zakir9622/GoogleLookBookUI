package com.zakir.vestra.shared.cloud

internal actual fun validateImageContentPlatform(bytes: ByteArray): String? =
    BlankFrameDetector.rejectIfBlank(bytes)
