package com.zakir.vestra.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelContracts
import com.zakir.vestra.shared.cloud.CloudPlatform
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.cloud.ModelSupportLevel
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.engine.Availability
import com.zakir.vestra.shared.engine.UnavailableReason
import com.zakir.vestra.shared.settings.AppearanceMode
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassFormDefaults
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.theme.VestraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceDropdown(
    selected: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = GlassFormDefaults.outlinedFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = GlassFormDefaults.menuContainerColor(),
            shadowElevation = GlassFormDefaults.MenuShadow,
        ) {
            AppearanceMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label(), color = VestraColors.Ink) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                    colors = GlassFormDefaults.menuItemColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EngineDropdown(
    selected: EngineTier,
    availability: (EngineTier) -> Availability,
    onSelect: (EngineTier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember { EngineTier.entries }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Engine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = {
                Text(selected.description(availability(selected)), color = VestraColors.InkMuted)
            },
            colors = GlassFormDefaults.outlinedFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = GlassFormDefaults.menuContainerColor(),
            shadowElevation = GlassFormDefaults.MenuShadow,
        ) {
            options.forEach { tier ->
                val avail = availability(tier)
                val enabled = true // Always selectable; pack download is the recovery path for Lite/Pro.
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(tier.label(), color = VestraColors.Ink)
                            Text(
                                tier.description(avail),
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.InkMuted,
                            )
                        }
                    },
                    onClick = {
                        if (enabled) {
                            onSelect(tier)
                            expanded = false
                        }
                    },
                    enabled = enabled,
                    colors = GlassFormDefaults.menuItemColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PackDropdown(
    choices: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = choices.firstOrNull { it.first == selectedId }?.second ?: "Select a pack"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model pack") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = GlassFormDefaults.outlinedFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = GlassFormDefaults.menuContainerColor(),
            shadowElevation = GlassFormDefaults.MenuShadow,
        ) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, color = VestraColors.Ink) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                    colors = GlassFormDefaults.menuItemColors(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CloudCapabilityDropdown(
    title: String,
    capability: AiCapability,
    selectedId: String,
    appSettings: AppSettings,
    discovery: FreeCloudDiscovery,
    onSelect: (String) -> Unit,
) {
    val hfToken by appSettings.hfToken.collectAsState()
    val groqKey by appSettings.groqApiKey.collectAsState()
    val openRouterKey by appSettings.openRouterApiKey.collectAsState()
    val discovered by appSettings.discoveredProviders.collectAsState()
    val tokenEpoch = "${hfToken.orEmpty()}|${groqKey.orEmpty()}|${openRouterKey.orEmpty()}|${discovered.size}"

    val usable = remember(tokenEpoch, capability) { discovery.selectable(appSettings, capability) }
    val locked = remember(tokenEpoch, capability) { discovery.curatedLocked(appSettings, capability) }
    val unsupported = remember(capability) { discovery.curatedUnsupported(capability) }
    val options = usable

    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
        ?: locked.firstOrNull { it.id == selectedId }
        ?: options.firstOrNull()
    val selectedNeedsToken = selected != null && locked.any { it.id == selected.id }
    val lockedOthers = locked.filter { it.id != selected?.id }
    val selectedContractNote = selected?.let { CloudModelContracts.settingsSupportingText(it) }
    val selectedSupport = selected?.let { appSettings.modelHealth.effectiveSupport(it) }

    // Edge case: prefs still point at a locked model while usable options exist (e.g. HF
    // discovery unlocked models but Groq selection lacks a Groq key). Auto-switch.
    LaunchedEffect(tokenEpoch, selectedId, options.map { it.id }) {
        if (options.isEmpty()) return@LaunchedEffect
        val selectedUsable = options.any { it.id == selectedId }
        if (!selectedUsable) {
            onSelect(options.first().id)
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard {
        GlassSectionLabel(title)
        Text(
            when {
                options.isNotEmpty() -> "${options.size} free models ready with your tokens"
                locked.isNotEmpty() -> "Add the required token above to unlock ${locked.size} models"
                else -> "No free models for this capability yet"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.displayName ?: "Select model",
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                supportingText = {
                    Text(
                        when {
                            selectedNeedsToken ->
                                "Save a ${selected?.platform.toTokenLabel()} key above to use this model"
                            selectedSupport == ModelSupportLevel.DEGRADED ->
                                selectedContractNote ?: (selected?.usageNote ?: "")
                            else -> selectedContractNote
                                ?: selected?.usageNote?.ifBlank { selected.description }
                                ?: ""
                        },
                        color = when {
                            selectedNeedsToken -> MaterialTheme.colorScheme.error
                            selectedSupport == ModelSupportLevel.DEGRADED -> MaterialTheme.colorScheme.tertiary
                            else -> VestraColors.InkMuted
                        },
                    )
                },
                colors = GlassFormDefaults.outlinedFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                enabled = options.isNotEmpty(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = GlassFormDefaults.menuContainerColor(),
                shadowElevation = GlassFormDefaults.MenuShadow,
            ) {
                options.forEach { provider ->
                    val support = appSettings.modelHealth.effectiveSupport(provider)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    "${provider.displayName} · ${CloudModelContracts.liveStatusLabel(provider, appSettings.modelHealth)}",
                                    color = VestraColors.Ink,
                                )
                                Text(
                                    "${provider.platform.name} · ${CloudModelContracts.forProvider(provider).schemaNote}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (support == ModelSupportLevel.DEGRADED) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        VestraColors.InkMuted
                                    },
                                )
                            }
                        },
                        onClick = {
                            onSelect(provider.id)
                            expanded = false
                        },
                        colors = GlassFormDefaults.menuItemColors(),
                    )
                }
            }
        }
        if (lockedOthers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Also needs a token: " + lockedOthers.take(3).joinToString { it.displayName },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unsupported.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            // Nothing is wrong with the user's setup here — these are models the app cannot
            // drive yet — so this reads as a footnote rather than an error.
            Text(
                "Not selectable yet: " + unsupported.joinToString { it.displayName },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        // Warm HF Inference auto-listing is disabled: image/video need Gradio Spaces, and
        // CODE uses curated Groq/HF/OpenRouter chat routes only.
        Text(
            "Uses curated free HF Spaces / APIs — live Inference discovery isn’t needed here.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        colors = GlassFormDefaults.outlinedFieldColors(),
    )
}

internal fun CloudPlatform?.toTokenLabel(): String = when (this) {
    CloudPlatform.GROQ -> "Groq"
    CloudPlatform.OPENROUTER -> "OpenRouter"
    CloudPlatform.HF_INFERENCE, CloudPlatform.HF_SPACE -> "Hugging Face"
    null -> "API"
}

internal fun AppearanceMode.label(): String = when (this) {
    AppearanceMode.SYSTEM -> "System"
    AppearanceMode.LIGHT -> "Light"
    AppearanceMode.DARK -> "Dark"
}

internal fun EngineTier.label(): String = when (this) {
    EngineTier.AUTO -> "Auto (on-device)"
    EngineTier.LITE -> "Lite — on device"
    EngineTier.PRO -> "Pro — on device"
    EngineTier.CLOUD -> "Cloud — free HF Spaces"
}

internal fun EngineTier.description(availability: Availability): String {
    val base = when (this) {
        EngineTier.AUTO -> "Best on-device engine. Never uses cloud automatically."
        EngineTier.LITE -> "Fast compositor. Works offline on every phone."
        EngineTier.PRO -> "SD1.5 diffusion on-device. Needs Pro pack."
        EngineTier.CLOUD -> "Free Hugging Face Spaces only. Select model below."
    }
    return when (availability) {
        Availability.Ready -> base
        is Availability.Unavailable -> when (availability.reason) {
            UnavailableReason.PACK_NOT_INSTALLED -> "$base Model pack not installed."
            UnavailableReason.PACK_VERIFY_FAILED ->
                "$base Model pack failed verification — re-download in Model packs."
            UnavailableReason.PACK_VERIFY_PENDING ->
                "$base Model pack verifying — wait a moment."
            UnavailableReason.COMPANION_PACK_MISSING ->
                "$base Pro also needs the Lite pack — install Lite to enable it."
            UnavailableReason.DEVICE_NOT_CAPABLE -> "$base Device doesn’t meet RAM requirements."
            UnavailableReason.OFFLINE -> "$base No internet connection."
            UnavailableReason.NOT_CONFIGURED -> "$base Add the required free API key above."
        }
    }
}
