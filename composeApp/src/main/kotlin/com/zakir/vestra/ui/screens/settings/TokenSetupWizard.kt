package com.zakir.vestra.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.settings.TokenPortals

/**
 * Three-step token setup wizard (HF → Groq → OpenRouter) shown after first Save.
 */
@Composable
fun TokenSetupWizard(
    onDismiss: () -> Unit,
    onOpenPortal: (String) -> Unit,
    hfConfigured: Boolean,
    groqConfigured: Boolean,
    openRouterConfigured: Boolean,
) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        Triple("Hugging Face", TokenPortals.HF, hfConfigured),
        Triple("Groq", TokenPortals.GROQ, groqConfigured),
        Triple("OpenRouter", TokenPortals.OPENROUTER, openRouterConfigured),
    )
    val (title, portal, done) = steps[step.coerceIn(steps.indices)]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Token setup · ${step + 1}/${steps.size}") },
        text = {
            Column {
                Text(
                    when (title) {
                        "Hugging Face" ->
                            "Create a classic HF Write/Read token with Inference Providers enabled. " +
                                "This unlocks FLUX image gen, Qwen coder, and router model discovery."
                        "Groq" ->
                            "Optional: Groq free tier gives fast Llama 3.3 70B coding when HF credits are spent."
                        else ->
                            "Optional: OpenRouter openrouter/free router — no payment required."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (done) {
                    Spacer(Modifier.height(8.dp))
                    Text("✓ $title key saved", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!done) onOpenPortal(portal)
                    if (step >= steps.lastIndex) onDismiss() else step++
                },
            ) {
                Text(
                    when {
                        step >= steps.lastIndex -> "Done"
                        done -> "Next"
                        else -> "Open $title"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step > 0) step-- else onDismiss() }) {
                Text(if (step > 0) "Back" else "Skip")
            }
        },
    )
}
