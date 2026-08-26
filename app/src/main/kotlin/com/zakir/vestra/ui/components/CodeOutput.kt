package com.zakir.vestra.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.delay

/** One parsed segment of a model's answer: either prose or a fenced code block. */
sealed interface CodeSegment {
    data class Prose(val text: String) : CodeSegment
    data class Code(val language: String?, val code: String) : CodeSegment
}

/**
 * Parses markdown prose vs. fenced code blocks.
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
        if (segments.isEmpty() && text.isNotBlank()) segments += CodeSegment.Prose(text.trim())
        return segments
    }
}

/**
 * Syntax highlighter token parser for Kotlin, Python, JSON, and Bash.
 */
object SyntaxHighlighter {
    private val KEYWORDS = setOf(
        "val", "var", "fun", "class", "data", "object", "interface", "sealed", "import", "package",
        "return", "if", "else", "when", "for", "while", "do", "in", "is", "as", "try", "catch",
        "finally", "throw", "true", "false", "null", "def", "import", "from", "lambda", "async",
        "await", "yield", "with", "const", "let", "type", "export", "default", "struct", "impl"
    )

    fun highlight(code: String, language: String?): AnnotatedString {
        return buildAnnotatedString {
            val lines = code.lines()
            lines.forEachIndexed { lineIdx, line ->
                var i = 0
                while (i < line.length) {
                    when {
                        // Comments: // or #
                        line.substring(i).startsWith("//") || line.substring(i).startsWith("#") -> {
                            val comment = line.substring(i)
                            pushStyle(SpanStyle(color = Color(0xFF6E7681), fontWeight = FontWeight.Normal))
                            append(comment)
                            pop()
                            i = line.length
                        }
                        // Strings: "..." or '...'
                        line[i] == '"' || line[i] == '\'' -> {
                            val quote = line[i]
                            var j = i + 1
                            while (j < line.length && line[j] != quote) {
                                if (line[j] == '\\' && j + 1 < line.length) j++
                                j++
                            }
                            val end = if (j < line.length) j + 1 else line.length
                            pushStyle(SpanStyle(color = Color(0xFF9ECE6A))) // Green string
                            append(line.substring(i, end))
                            pop()
                            i = end
                        }
                        // Numbers
                        line[i].isDigit() -> {
                            var j = i
                            while (j < line.length && (line[j].isDigit() || line[j] == '.' || line[j] == 'f' || line[j] == 'L')) j++
                            pushStyle(SpanStyle(color = Color(0xFFFF9E64))) // Orange number
                            append(line.substring(i, j))
                            pop()
                            i = j
                        }
                        // Words (keywords, identifiers)
                        line[i].isLetter() || line[i] == '_' -> {
                            var j = i
                            while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                            val word = line.substring(i, j)
                            if (word in KEYWORDS) {
                                pushStyle(SpanStyle(color = Color(0xFFBB9AF7), fontWeight = FontWeight.SemiBold)) // Purple keyword
                                append(word)
                                pop()
                            } else if (word.first().isUpperCase()) {
                                pushStyle(SpanStyle(color = Color(0xFF7AA2F7))) // Blue Type
                                append(word)
                                pop()
                            } else {
                                pushStyle(SpanStyle(color = Color(0xFFC0CAF5))) // Text
                                append(word)
                                pop()
                            }
                            i = j
                        }
                        else -> {
                            val char = line[i]
                            val style = when (char) {
                                '{', '}', '(', ')', '[', ']' -> SpanStyle(color = Color(0xFF89DDFF))
                                '=', '+', '-', '*', '/', ':', ',', '.' -> SpanStyle(color = Color(0xFF7DCFFF))
                                else -> SpanStyle(color = Color(0xFFC0CAF5))
                            }
                            pushStyle(style)
                            append(char)
                            pop()
                            i++
                        }
                    }
                }
                if (lineIdx < lines.size - 1) append("\n")
            }
        }
    }
}

/**
 * Syntax-highlighted code viewer with line numbers, copy button with animated confirmation,
 * and share sheet.
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
                    var copied by remember { mutableStateOf(false) }
                    LaunchedEffect(copied) {
                        if (copied) {
                            delay(2000)
                            copied = false
                        }
                    }

                    val lineCount = segment.code.count { it == '\n' } + 1
                    val highlightedText = remember(segment.code, segment.language) {
                        SyntaxHighlighter.highlight(segment.code, segment.language)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF13141C))
                            .border(1.dp, Color(0xFF2A2B3D), RoundedCornerShape(14.dp)),
                    ) {
                        // Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C1D2B))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(VestraColors.Accent.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = (segment.language ?: "CODE").uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                        ),
                                        color = VestraColors.Accent,
                                    )
                                }
                                Text(
                                    text = "$lineCount ${if (lineCount == 1) "line" else "lines"}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Color(0xFF7A7E9D),
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Share button
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF25273A))
                                        .clickable {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, segment.code)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share code"))
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = "Share code",
                                        tint = Color(0xFFA6ACCD),
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        "Share",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = Color(0xFFA6ACCD),
                                    )
                                }

                                // Copy button with check animation
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (copied) Color(0xFF1E3A2F) else Color(0xFF25273A))
                                        .clickable {
                                            val cm = context.getSystemService(ClipboardManager::class.java)
                                            cm?.setPrimaryClip(
                                                ClipData.newPlainText("code-snippet", segment.code),
                                            )
                                            copied = true
                                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = if (copied) Color(0xFF9ECE6A) else Color(0xFFA6ACCD),
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = if (copied) "Copied!" else "Copy",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (copied) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                        color = if (copied) Color(0xFF9ECE6A) else Color(0xFFA6ACCD),
                                    )
                                }
                            }
                        }

                        // Code Body with Line Numbers & Monospace Font
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            // Line numbers column
                            val lineNumbers = (1..lineCount).joinToString("\n")
                            Text(
                                text = lineNumbers,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = Color(0xFF4A4E69),
                                modifier = Modifier.padding(end = 12.dp),
                            )

                            // Highlighted code with horizontal scrolling
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = highlightedText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            if (index != segments.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}
