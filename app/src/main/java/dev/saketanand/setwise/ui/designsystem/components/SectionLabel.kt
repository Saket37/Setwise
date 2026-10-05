package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * UPPERCASE caption above a group: "FROM A TEMPLATE", "PREVIOUS", "RECENT", month headers.
 * Pass normal-case text (from strings.xml); it's uppercased here, so screen readers and
 * translations get natural text.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

@ComponentPreviews
@Composable
private fun SectionLabelPreview() = SetwisePreview {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("From a template")
        SectionLabel("Workout in progress", color = MaterialTheme.colorScheme.primary)
    }
}
