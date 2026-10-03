package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * "−  3 sets  +": a small count with buttons, in a pill. − / + disable at the ends of [range].
 * [label] is the value as read and shown ("3 sets"); the buttons are named by
 * [decreaseDescription] / [increaseDescription].
 */
@Composable
fun SetwiseStepper(
    value: Int,
    label: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
    range: IntRange = 1..Int.MAX_VALUE,
) {
    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(R.drawable.ic_remove, decreaseDescription, enabled = value > range.first, onClick = onDecrease)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 52.dp)
                // Read out when − / + change it.
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepButton(R.drawable.ic_add, increaseDescription, enabled = value < range.last, onClick = onIncrease)
    }
}

@Composable
private fun StepButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    SetwiseIconButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        enabled = enabled,
        size = 44.dp,
        iconSize = 16.dp,
        colors = SetwiseIconButtonDefaults.plainColors(),
    )
}

@ComponentPreviews
@Composable
private fun SetwiseStepperPreview() = SetwisePreview {
    SetwiseStepper(value = 3, label = "3 sets", onDecrease = {}, onIncrease = {}, decreaseDescription = "", increaseDescription = "", range = 1..10)
}
