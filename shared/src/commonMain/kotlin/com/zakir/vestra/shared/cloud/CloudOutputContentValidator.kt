package com.zakir.vestra.shared.cloud

/** Platform hook for content-quality checks after structural validation passes. */
internal expect fun validateImageContentPlatform(bytes: ByteArray): String?
