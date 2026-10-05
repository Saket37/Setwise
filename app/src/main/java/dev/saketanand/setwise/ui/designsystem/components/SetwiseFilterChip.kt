package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Pill that filters a list ("All", "Chest", "Back"…). Selected: Volt-tinted fill; otherwise
 * outlined. Screen readers announce it as selected / not selected.
 *
 * Visually 36dp tall; the touch target is 48dp (Material minimum), which adds 6dp of
 * invisible space above and below. Account for it in the spacing around a row of chips.
 */
@Composable
fun SetwiseFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(if (selected) colors.primaryContainer else Color.Transparent, label = "chipContainer")
    val contentColor by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurface, label = "chipContent")
    val borderColor by animateColorAsState(if (selected) colors.primaryContainer else colors.outline, label = "chipBorder")

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@PreviewComponents
@Composable
private fun SetwiseFilterChipPreview() = SetwisePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SetwiseFilterChip("All", selected = true, onClick = {})
        SetwiseFilterChip("Chest", selected = false, onClick = {})
        SetwiseFilterChip("Back", selected = false, onClick = {})
    }
}
