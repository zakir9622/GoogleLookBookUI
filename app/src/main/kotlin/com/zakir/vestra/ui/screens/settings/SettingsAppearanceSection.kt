package com.zakir.vestra.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zakir.vestra.media.CacheCleanup
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.settings.AppearanceMode
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.storage.DurableStorage
import com.zakir.vestra.storage.TokenSidecar
import com.zakir.vestra.ui.components.GlassCard
import com.zakir.vestra.ui.components.GlassSectionLabel
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.hasCameraPermission
import com.zakir.vestra.ui.util.hasPostNotificationsPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Durable storage status. Primary enable CTA lives on pack download
 * ([com.zakir.vestra.ui.util.rememberPackDownloadStarter]), not here.
 */
internal fun LazyListScope.settingsDurableStatusSection(
    appSettings: AppSettings,
    durableReady: Boolean,
) {
    item(key = "durable") {
        val context = LocalContext.current
        GlassCard {
            GlassSectionLabel("SURVIVES REINSTALL")
            Text(
                if (durableReady) {
                    "Packs & tokens use Documents/TheLookbook. After uninstall/reinstall they are detected automatically — no re-download or re-paste needed."
                } else {
                    "Model packs and tokens.json can live in Documents/TheLookbook so they survive uninstall. You’ll be prompted when you download a pack."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Path: Documents/${DurableStorage.ROOT_FOLDER}/",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (durableReady) {
                Text(
                    "Durable storage on · packs root remounted",
                    style = MaterialTheme.typography.labelMedium,
                    color = VestraColors.Accent,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val ok = TokenSidecar.persist(context, appSettings)
                        Toast.makeText(
                            context,
                            if (ok) "Wrote ${DurableStorage.TOKENS_FILE}" else "Could not write sidecar",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export tokens file now")
                }
            } else {
                Text(
                    "Tip: open Model packs and tap Download — durable storage is requested there.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

internal fun LazyListScope.settingsThemeSection(
    appSettings: AppSettings,
    appearance: AppearanceMode,
) {
    item(key = "appearance") {
        GlassCard {
            GlassSectionLabel("APPEARANCE")
            Text(
                "Pearl day / graphite night. System follows your phone setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AppearanceDropdown(
                selected = appearance,
                onSelect = appSettings::setAppearanceMode,
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

internal fun LazyListScope.settingsStoragePermissionsSection(
    clearingCache: Boolean,
    onClearingCache: (Boolean) -> Unit,
    usageLedger: UsageLedger,
    permissionEpoch: Int,
    onConfirmClearTokens: () -> Unit,
) {
    item(key = "storage") {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val reportStore = remember { com.zakir.vestra.data.LocalReportStore(context) }
        GlassCard {
            GlassSectionLabel("STORAGE & PRIVACY")
            Text(
                "Clears regenerable caches only. Installed model packs and wardrobe index stay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    if (clearingCache) return@OutlinedButton
                    onClearingCache(true)
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            CacheCleanup.clearAppCaches(context)
                        }
                        onClearingCache(false)
                        Toast.makeText(
                            context,
                            "Cleared ${result.deletedFiles} files · ${CacheCleanup.formatBytes(result.freedBytes)}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                enabled = !clearingCache,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (clearingCache) "Clearing…" else "Clear cache")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    usageLedger.clear()
                    Toast.makeText(context, "Usage ledger cleared", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear usage ledger")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onConfirmClearTokens,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear API keys")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Content reports on device: ${reportStore.count()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (reportStore.count() == 0) {
                        Toast.makeText(context, "No reports yet", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "The Lookbook content reports")
                        putExtra(Intent.EXTRA_TEXT, reportStore.exportJson())
                    }
                    context.startActivity(Intent.createChooser(send, LookbookCopy.ACTION_EXPORT_REPORTS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(LookbookCopy.ACTION_EXPORT_REPORTS)
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    item(key = "permissions-$permissionEpoch") {
        val context = LocalContext.current
        GlassCard {
            GlassSectionLabel("PERMISSIONS")
            Text(
                LookbookCopy.PERM_NOTIFICATIONS_TITLE + ": " +
                    if (context.hasPostNotificationsPermission()) "Allowed" else "Not allowed",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                LookbookCopy.PERM_CAMERA_TITLE + ": " +
                    if (context.hasCameraPermission()) "Allowed" else "Not allowed",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                LookbookCopy.PERM_STORAGE_TITLE + ": " +
                    if (DurableStorage.hasAllFilesAccess()) "Allowed" else "Not allowed",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Permissions are requested in context — when you use the camera, download packs, or enable durable storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}
