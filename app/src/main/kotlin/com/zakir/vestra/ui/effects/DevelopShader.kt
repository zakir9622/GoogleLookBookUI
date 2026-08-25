package com.zakir.vestra.ui.effects

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * The "developing photograph" stage shown while an engine works. On Android 13+
 * this is a real AGSL shader — film grain, a champagne develop-front that
 * tracks generation progress, and a breathing vignette. Below 13 (and for
 * reduced-motion users) it falls back to a lightweight Canvas scanline.
 */
private const val DEVELOP_AGSL = """
uniform float2 size;
uniform float time;
uniform float progress;

half hash(float2 p) {
    return half(fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453));
}

half4 main(float2 coord) {
    float2 uv = coord / size;

    // Stage base: near-black with a subtle vertical lift.
    half3 color = half3(0.040, 0.040, 0.047) + half3(0.012) * half(1.0 - uv.y);

    // Film grain, re-seeded every frame.
    half grain = (hash(coord + fract(time) * 173.0) - 0.5) * 0.055;
    color += grain;

    // Developed region below the front gets a faint warm exposure.
    float front = progress;
    if (uv.y < front) {
        half developed = half(smoothstep(front, front - 0.35, uv.y));
        color += half3(0.10, 0.082, 0.048) * developed * 0.55;
    }

    // The develop-front itself: a champagne shimmer band with a moving sparkle.
    float band = 1.0 - smoothstep(0.0, 0.06, abs(uv.y - front));
    float sparkle = 0.75 + 0.25 * sin(uv.x * 40.0 + time * 6.0);
    color += half3(0.847, 0.698, 0.431) * half(band * sparkle * 0.9);

    // Breathing vignette keeps the eye centered.
    float2 centered = uv - 0.5;
    float vignette = 1.0 - dot(centered, centered) * (1.1 + 0.1 * sin(time * 1.4));
    color *= half(clamp(vignette, 0.0, 1.0));

    return half4(color, 1.0);
}
"""

@Composable
fun DevelopStage(
    progress: Float,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    if (reduceMotion) {
        FallbackScanline(progress = progress, time = 0f, modifier = modifier)
        return
    }

    val transition = rememberInfiniteTransition(label = "develop")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )

    if (Build.VERSION.SDK_INT >= 33) {
        val runtimeShader = remember { RuntimeShader(DEVELOP_AGSL) }
        Canvas(modifier.fillMaxSize()) {
            runtimeShader.setFloatUniform("size", size.width, size.height)
            runtimeShader.setFloatUniform("time", time)
            runtimeShader.setFloatUniform("progress", progress.coerceIn(0f, 1f))
            drawRoundRect(
                brush = ShaderBrush(runtimeShader),
                cornerRadius = CornerRadius(24f, 24f),
            )
        }
    } else {
        FallbackScanline(progress, time, modifier)
    }
}

@Composable
private fun FallbackScanline(progress: Float, time: Float, modifier: Modifier) {
    val gold = Color(0xFFD8B26E)
    val stage = Color(0xFF1E1E24)
    Canvas(modifier.fillMaxSize()) {
        drawRoundRect(color = stage, cornerRadius = CornerRadius(24f, 24f))
        val y = size.height * progress.coerceIn(0f, 1f)
        val pulse = 0.4f + 0.15f * kotlin.math.sin(time * 4f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(gold.copy(alpha = 0f), gold.copy(alpha = pulse), gold.copy(alpha = 0f)),
                startY = y - 60f,
                endY = y + 60f,
            ),
            topLeft = Offset(0f, y - 60f),
            size = Size(size.width, 120f),
        )
    }
}
