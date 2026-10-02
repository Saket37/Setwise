package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberLarge
import dev.saketanand.setwise.ui.designsystem.theme.pr

/**
 * A number with a caption: "3 · workouts this week" (Home), "Volume · 8,420 kg" (Summary).
 *
 * @param unit optional suffix after the value in a smaller, muted style ("kg", "kcal").
 * @param labelFirst Summary-style: caption above the value. Default (Home): value first.
 * @param valueStyle numberLarge for compact tiles, headlineSmall for big Summary stats.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    labelFirst: Boolean = false,
    valueStyle: TextStyle = MaterialTheme.typography.numberLarge,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(
        modifier = modifier
            .background(containerColor, MaterialTheme.shapes.large)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val labelText: @Composable () -> Unit = {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (labelFirst) labelText()
        val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = buildAnnotatedString {
                append(value)
                if (unit != null) {
                    withStyle(SpanStyle(fontSize = valueStyle.fontSize * 0.55f, color = unitColor)) { append(" $unit") }
                }
            },
            style = valueStyle,
            color = valueColor,
        )
        if (!labelFirst) labelText()
    }
}

@ComponentPreviews
@Composable
private fun StatTilePreview() = SetwisePreview {
    Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("3", "workouts this week", Modifier.weight(1f).fillMaxHeight())
        StatTile("2", "new PRs", Modifier.weight(1f).fillMaxHeight(), valueColor = MaterialTheme.colorScheme.pr)
        StatTile(
            "8,420", "Volume", Modifier.weight(1f).fillMaxHeight(),
            unit = "kg", labelFirst = true, valueStyle = MaterialTheme.typography.headlineSmall,
        )
    }
}
