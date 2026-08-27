package com.zakir.vestra.shared.cloud

/** A user-facing edit intent translated into provider-ready prompt language. */
data class ImageEditIntent(
    val id: String,
    val label: String,
    val promptClause: String,
)

object ImageEditIntentCatalog {
    val all: List<ImageEditIntent> = listOf(
        ImageEditIntent("reframe", "Reframe", "reframe the composition while preserving the subject and styling"),
        ImageEditIntent("new-light", "Relight", "rework the lighting and atmosphere while preserving identity and materials"),
        ImageEditIntent("background", "New background", "replace the background with a coherent setting and natural edge integration"),
        ImageEditIntent("clean-up", "Clean up", "remove distracting objects and artifacts while preserving the intended composition"),
        ImageEditIntent("detail", "Add detail", "increase believable fabric, material, and surface detail without changing the design"),
        ImageEditIntent("color", "Shift color", "shift the palette toward a more intentional, balanced color story"),
    )

    fun find(id: String): ImageEditIntent? = all.firstOrNull { it.id == id }
}
