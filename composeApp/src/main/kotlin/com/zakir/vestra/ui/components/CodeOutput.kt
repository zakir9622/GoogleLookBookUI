package com.zakir.vestra.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zakir.vestra.ui.theme.VestraColors

/** One parsed segment of a model's answer: either prose or a fenced code block. */
sealed interface CodeSegment {
    data class Prose(val text: String) : CodeSegment
    data class Code(val language: String?, val code: String) : CodeSegment
}

/**
 * Splits an LLM answer into prose and ``` fenced code blocks.
 *
 * Rendering the whole reply as one monospace blob made code and explanation indistinguishable
 * and forced "copy everything" — pasting prose into an editor along with the code.
 */
object CodeSegments {
    fun parse(text: String): List<CodeSegment> {
        val segments = mutableListOf<CodeSegment>()
        val fence = Regex("```([A-Za-z0-9+#._-]*)\\s*\\n?([\\s\\S]*?)```")
        var cursor = 0
        fence.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                text.substring(cursor, match.range.first)
                    .takeIf { it.isNotBlank() }
                    ?.let { segments += CodeSegment.Prose(it.trim()) }
            }
            val language = match.groupValues[1].takeIf { it.isNotBlank() }
            val body = match.groupValues[2].trimEnd()
            if (body.isNotBlank()) segments += CodeSegment.Code(language, body)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            text.substring(cursor).takeIf { it.isNotBlank() }
                ?.let { segments += CodeSegment.Prose(it.trim()) }
        }
        // An answer with no fences is still worth showing — treat it as a single block so the
        // copy button remains useful.
        if (segments.isEmpty() && text.isNotBlank()) segments += CodeSegment.Prose(text.trim())
        return segments
    }
}

/**
 * Renders a code answer as alternating prose and code blocks, each block independently
 * copyable, horizontally scrollable (so long lines are readable rather than wrapped mid-token)
 * and labelled with its language.
 */
@Composable
fun CodeOutput(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val segments = remember(text) { CodeSegments.parse(text) }

    Column(modifier) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is CodeSegment.Prose -> {
                    Text(
                        segment.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VestraColors.Ink,
                    )
                }
                is CodeSegment.Code -> {
                    val lineCount = segment.code.count { it == '\n' } + 1
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            listOfNotNull(
                                segment.language?.uppercase() ?: "CODE",
                                "$lineCount ${if (lineCount == 1) "line" else "lines"}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = VestraColors.Accent,
                        )
                        // A compact affordance, not GlassSecondaryButton: that one fills its
                        // width and swallowed the language label sitting beside it.
                        Text(
                            "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = VestraColors.Accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    val cm = context.getSystemService(ClipboardManager::class.java)
                                    cm?.setPrimaryClip(
                                        ClipData.newPlainText("lookbook-code", segment.code),
                                    )
                                    Toast.makeText(context, "Block copied", Toast.LENGTH_SHORT)
                                        .show()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VestraColors.GlassFill)
                            .padding(12.dp),
                    ) {
                        Text(
                            segment.code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = VestraColors.Ink,
                            // Horizontal scroll rather than wrapping: a wrapped line of code is
                            // harder to read than one you can scroll.
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
            if (index != segments.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}
