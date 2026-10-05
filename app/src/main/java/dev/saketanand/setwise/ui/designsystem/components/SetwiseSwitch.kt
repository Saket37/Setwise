package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * The design's switch: Volt track and dark thumb when on, grey track when off, no outline.
 *
 * @param onCheckedChange null when the whole row toggles (see [SetwiseSwitchRow]), so the
 *   switch doesn't take its own taps or screen-reader focus.
 */
@Composable
fun SetwiseSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@PreviewComponents
@Composable
private fun SetwiseSwitchPreview() = SetwisePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SetwiseSwitch(checked = true, onCheckedChange = {})
        SetwiseSwitch(checked = false, onCheckedChange = {})
    }
}
