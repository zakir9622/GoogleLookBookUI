package com.zakir.vestra.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zakir.vestra.BuildConfig
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassSectionLabel

/** Help · diagnostics · about — shown only for [SettingsSection.ALL]. */
internal fun LazyListScope.settingsGeneralSection(
    onOpenHelp: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: (() -> Unit)?,
) {
    item(key = "help") {
        GlassCard(onClick = onOpenHelp) {
            GlassSectionLabel("HELP")
            Text(LookbookCopy.STUDIO_HELP, style = MaterialTheme.typography.titleMedium)
            Text(
                "Searchable FAQ for packs, API keys, permissions, queues, and recovery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
    }

    item(key = "diagnostics") {
        GlassCard(onClick = { onOpenDiagnostics?.invoke() }) {
            GlassSectionLabel("DIAGNOSTICS")
            Text("Run history", style = MaterialTheme.typography.titleMedium)
            Text(
                "Export JSON of local + cloud generations when reporting issues.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
    }

    item(key = "about") {
        GlassCard {
            GlassSectionLabel("ABOUT")
            Text(LookbookCopy.PRODUCT_NAME, style = MaterialTheme.typography.titleMedium)
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                LookbookCopy.PRODUCT_BLURB,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) {
                Text(LookbookCopy.ACTION_OPEN_HELP)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(LookbookCopy.ACTION_OPEN_PRIVACY)
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}
