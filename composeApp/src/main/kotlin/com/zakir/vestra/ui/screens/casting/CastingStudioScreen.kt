package com.zakir.vestra.ui.screens.casting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.domain.BodyType
import com.zakir.vestra.shared.domain.CastingPresets
import com.zakir.vestra.shared.domain.Ethnicity
import com.zakir.vestra.shared.domain.GarmentColor
import com.zakir.vestra.shared.domain.HairCoverage
import com.zakir.vestra.shared.domain.Scenario
import com.zakir.vestra.shared.domain.SkinTone
import com.zakir.vestra.ui.TryOnViewModel
import com.zakir.vestra.ui.components.AtelierFilterChip
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassScreen
import com.zakir.vestra.ui.components.GlassSectionLabel

/** Spatial casting studio — presets and parameter cards for ethnicity, body, scene, and color. */
@Composable
fun CastingStudioScreen(
    viewModel: TryOnViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val casting by viewModel.casting.collectAsState()

    GlassScreen(
        title = "Casting studio",
        subtitle = "Step 2 · Scene",
        onBack = onBack,
    ) {
        GlassCard {
            GlassSectionLabel("QUICK PRESETS")
            Text(
                "One-tap looks for abaya, bazaar, and traditional wear scenes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CastingPresets.all) { (name, preset) ->
                    val selected = casting == preset
                    AtelierFilterChip(
                        selected = selected,
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text(name) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            GlassSectionLabel("ETHNICITY")
            ChipRow(Ethnicity.entries, casting.ethnicity) { viewModel.setEthnicity(it) }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            GlassSectionLabel("SKIN TONE")
            ChipRow(SkinTone.entries, casting.skinTone) { viewModel.setSkinTone(it) }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            GlassSectionLabel("BODY TYPE")
            ChipRow(BodyType.entries, casting.bodyType) { viewModel.setBodyType(it) }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            GlassSectionLabel("HAIR COVERAGE")
            ChipRow(HairCoverage.entries, casting.hairCoverage) { viewModel.setHairCoverage(it) }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            GlassSectionLabel("GARMENT COLOR")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AtelierFilterChip(
                        selected = casting.garmentColor == null,
                        onClick = { viewModel.setGarmentColor(null) },
                        label = { Text("From photo") },
                    )
                }
                items(GarmentColor.entries) { color ->
                    AtelierFilterChip(
                        selected = casting.garmentColor == color,
                        onClick = { viewModel.setGarmentColor(color) },
                        label = { Text(color.displayName) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard {
            GlassSectionLabel("SCENARIO")
            ChipRow(Scenario.entries, casting.scenario) { viewModel.setScenario(it) }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text("Choose model & pose")
        }
    }
}

@Composable
private inline fun <T> ChipRow(
    items: List<T>,
    selected: T,
    crossinline label: (T) -> String = { item ->
        when (item) {
            is Ethnicity -> item.displayName
            is SkinTone -> item.displayName
            is BodyType -> item.displayName
            is HairCoverage -> item.displayName
            is Scenario -> item.displayName
            else -> item.toString()
        }
    },
    crossinline onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            AtelierFilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
            )
        }
    }
}
