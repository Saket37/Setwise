package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Round check mark for multi-select rows (exercise picker): an empty ring, or a Volt circle
 * with ✓ when [selected].
 *
 * Display only: make the whole row tappable and give it the checked state for screen readers
 * (Role.Checkbox + toggleableState), so this needs no semantics of its own.
 */
@Composable
fun SetwiseSelectionIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val colors = MaterialTheme.colorScheme
    val fillColor by animateColorAsState(if (selected) colors.primary else Color.Transparent, label = "selectionFill")
    val ringColor by animateColorAsState(if (selected) colors.primary else colors.outline, label = "selectionRing")

    Box(
        modifier = modifier
            .size(size)
            .border(2.dp, ringColor, CircleShape)
            .background(fillColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

@PreviewComponents
@Composable
private fun SetwiseSelectionIndicatorPreview() = SetwisePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SetwiseSelectionIndicator(selected = false)
        SetwiseSelectionIndicator(selected = true)
    }
}
