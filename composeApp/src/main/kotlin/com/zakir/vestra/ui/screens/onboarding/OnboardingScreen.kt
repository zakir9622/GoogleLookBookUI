package com.zakir.vestra.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.ui.components.GlassPrimaryButton
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.theme.VestraColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

private data class OnboardingPage(val title: String, val body: String)

private val pages = listOf(
    OnboardingPage(
        "Always the perfect look",
        "Generate abaya, hijab, niqab, and shalwar looks with on-device AI — fully offline after the Pro pack.",
    ),
    OnboardingPage(
        "Cast your scene",
        "Set ethnicity, body type, hair coverage, color, and scenario. One garment photo becomes a full shoot.",
    ),
    OnboardingPage(
        "Create stills, video, code",
        "Free cloud studios for shoppers, sellers, and makers — Image, Video, and Code beside local try-on.",
    ),
    OnboardingPage(
        "Keys unlock free cloud",
        "Paste free Hugging Face, Groq, or OpenRouter tokens in Settings. Local Lite/Pro never need a key.",
    ),
)

@Composable
fun OnboardingScreen(appSettings: AppSettings, onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val slide = pages[page]

    SpatialBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                LookbookCopy.PRODUCT_NAME,
                style = MaterialTheme.typography.displaySmall,
                color = VestraColors.Ink,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                LookbookCopy.PRODUCT_TAGLINE,
                style = MaterialTheme.typography.labelLarge,
                color = VestraColors.Accent,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))

            // Collage strip — first viewport brand + atmosphere (no secondary marketing clutter).
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(VestraColors.AtelierContainer, VestraColors.AtelierCanvas),
                        ),
                    ),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 28.dp)
                        .size(110.dp, 150.dp)
                        .rotate(-10f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF3D2A18), VestraColors.SaffronDeep),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(120.dp, 160.dp)
                        .rotate(4f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E2430), VestraColors.AccentSoft.copy(alpha = 0.7f)),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .size(100.dp, 140.dp)
                        .rotate(14f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2C1810), Color(0xFF5C3A22)),
                            ),
                        ),
                )
            }

            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = slide,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 5 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 5 })
                },
                label = "onboardSlide",
                modifier = Modifier.fillMaxWidth(),
            ) { current ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = VestraColors.Ink,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = current.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = VestraColors.InkMuted,
                        textAlign = TextAlign.Start,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == page) VestraColors.Accent else VestraColors.InkMuted.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            if (page < pages.lastIndex) {
                GlassPrimaryButton(
                    text = "Continue",
                    onClick = { page += 1 },
                )
                TextButton(onClick = {
                    appSettings.setOnboardingComplete()
                    onDone()
                }) { Text("Skip") }
            } else {
                GlassPrimaryButton(
                    text = "Get started",
                    onClick = {
                        appSettings.setOnboardingComplete()
                        onDone()
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
