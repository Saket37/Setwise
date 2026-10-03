package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium

/**
 * A Settings section (design): "PROFILE · USED FOR CALORIES" above a card of rows.
 * Put [SetwiseSettingsRow] / [SetwiseSwitchRow] inside; give the last one showDivider = false.
 */
@Composable
fun SetwiseSettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) SectionLabel(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            content = content,
        )
    }
}

/**
 * "Body weight          72.5 kg": a label and its value (or "Not set"). Tapping opens an editor.
 * @param supporting optional line under the label.
 */
@Composable
fun SetwiseSettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    showDivider: Boolean = true,
) {
    SettingsRowLayout(
        label = label,
        supporting = supporting,
        showDivider = showDivider,
        modifier = modifier.clickable(onClick = onClick, role = Role.Button),
    ) {
        Text(value, style = MaterialTheme.typography.numberMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Sound when rest ends   (●)": the whole row toggles, announced as a switch. */
@Composable
fun SetwiseSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    showDivider: Boolean = true,
) {
    SettingsRowLayout(
        label = label,
        supporting = supporting,
        showDivider = showDivider,
        modifier = modifier.toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
    ) {
        SetwiseSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsRowLayout(
    label: String,
    supporting: String?,
    showDivider: Boolean,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing()
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

@ComponentPreviews
@Composable
private fun SetwiseSettingsPreview() = SetwisePreview {
    SetwiseSettingsGroup(title = "Profile · used for calories") {
        SetwiseSettingsRow(label = "Body weight", value = "72.5 kg", onClick = {})
        SetwiseSettingsRow(label = "Training days", value = "Mon, Wed, Fri", onClick = {})
        SetwiseSwitchRow(
            label = "Ask about days I didn't log",
            supporting = "Once a day at most, never during a workout",
            checked = true,
            onCheckedChange = {},
            showDivider = false,
        )
    }
}
