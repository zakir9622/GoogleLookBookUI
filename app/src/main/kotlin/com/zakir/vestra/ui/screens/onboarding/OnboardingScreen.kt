package com.zakir.vestra.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
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
import com.zakir.vestra.ui.components.PermissionChecklist
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.theme.VestraColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

private data class OnboardingPage(val title: String, val body: String, val isPermissionPage: Boolean = false)

private val pages = listOf(
    OnboardingPage(
        "Start a creative direction",
        "Describe an idea, choose a ready engine, and compare two directions before you keep what feels right.",
    ),
    OnboardingPage(
        "Shape the details",
        "Open Prompt Director only when you need it for mood, lighting, composition, finish, and reusable style choices.",
    ),
    OnboardingPage(
        "Create across mediums",
        "Move between image, motion, voice, and code while your saved directions stay ready to remix in one private library.",
    ),
    OnboardingPage(
        "Privacy stays visible",
        "Local engines work without a provider key. Cloud tools are clearly labeled and only use tokens you choose to add in Settings.",
    ),
    OnboardingPage(
        "Finish your setup",
        "Allow only the permissions you want to use for camera-based creation, voice prompts, notifications, or background model downloads.",
        isPermissionPage = true,
    ),
)

@Composable
fun OnboardingScreen(
    appSettings: AppSettings,
    onDone: () -> Unit,
    requestStartupPermissions: Boolean = true,
) {
    var page by remember { mutableIntStateOf(0) }
    val slide = pages[page]

    // Default startup permission prompt when first-time initialization screen is shown
    val startupPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> }

    LaunchedEffect(requestStartupPermissions) {
        if (!requestStartupPermissions) return@LaunchedEffect
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
        startupPermissionLauncher.launch(perms)
    }

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
                "PRIVATE AI CREATION STUDIO",
                style = MaterialTheme.typography.labelLarge,
                color = VestraColors.Accent,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "STEP ${page + 1} OF ${pages.size}",
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.InkMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )

            // Spatial direction strip — a compact sense of depth without adding content clutter.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (slide.isPermissionPage) 130.dp else 210.dp)
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
                                listOf(VestraColors.Accent.copy(alpha = 0.88f), VestraColors.AccentSoft.copy(alpha = 0.38f)),
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
                                listOf(VestraColors.SurfaceRaised, VestraColors.ModalityCode.copy(alpha = 0.72f)),
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
                                listOf(VestraColors.ModalityVideo.copy(alpha = 0.76f), VestraColors.SurfaceFloating),
                            ),
                        ),
                )
            }

            Spacer(Modifier.height(16.dp))

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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = current.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VestraColors.InkMuted,
                        textAlign = TextAlign.Start,
                    )

                    if (current.isPermissionPage) {
                        Spacer(Modifier.height(14.dp))
                        PermissionChecklist(
                            showHeader = false,
                        )
                    }
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
                    text = "Next direction",
                    onClick = { page += 1 },
                )
                TextButton(onClick = {
                    appSettings.setOnboardingComplete()
                    onDone()
                }) { Text("Explore the studio") }
            } else {
                GlassPrimaryButton(
                    text = "Open Creative Studio",
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
