package com.zakir.vestra.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.cloud.PromptExpander
import com.zakir.vestra.shared.cloud.PromptRecipe
import com.zakir.vestra.shared.cloud.StyleModifier
import com.zakir.vestra.ui.theme.VestraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptDirectorSheet(
    recipe: PromptRecipe,
    modifiers: List<StyleModifier>,
    onRecipeChange: (PromptRecipe) -> Unit,
    onToggleModifier: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VestraColors.Canvas,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            Text(
                "Recipe helper",
                color = VestraColors.Ink,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Use this only when you want help shaping a brief. Nothing changes until you apply it to the prompt.",
                color = VestraColors.InkMuted,
            )
            Spacer(Modifier.height(14.dp))

            RecipeField("Subject", recipe.subject, "Who or what is the focus?", { onRecipeChange(recipe.copy(subject = it)) })
            RecipeField("Setting", recipe.setting, "Where does it live?", { onRecipeChange(recipe.copy(setting = it)) })
            RecipeField("Mood", recipe.mood, "What should it feel like?", { onRecipeChange(recipe.copy(mood = it)) })
            RecipeField("Lighting", recipe.lighting, "How should light behave?", { onRecipeChange(recipe.copy(lighting = it)) })
            RecipeField("Composition", recipe.composition, "Framing, lens, or spatial direction", { onRecipeChange(recipe.copy(composition = it)) })
            RecipeField("Finish", recipe.finish, "Texture, color, and final treatment", { onRecipeChange(recipe.copy(finish = it)) })

            Spacer(Modifier.height(14.dp))
            Text("STYLE MODIFIERS", color = VestraColors.InkMuted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modifiers.forEach { modifier ->
                    AtelierFilterChip(
                        selected = modifier.id in recipe.styleModifierIds,
                        onClick = { onToggleModifier(modifier.id) },
                        label = { Text(modifier.label) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("EXPANDED PROMPT", color = VestraColors.InkMuted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                PromptExpander.expand(recipe).ifBlank { "Add a creative detail or choose a style modifier to preview the final direction." },
                color = VestraColors.Ink,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VestraColors.Accent, contentColor = VestraColors.Canvas),
                ) { Text("Apply to prompt") }
            }
        }
    }
}

@Composable
private fun RecipeField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        singleLine = true,
    )
}
