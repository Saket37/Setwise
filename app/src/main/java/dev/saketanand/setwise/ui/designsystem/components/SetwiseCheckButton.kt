package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Square ✓ toggle: "set done". Unchecked: outlined; checked: Volt fill.
 * Screen readers hear a checkbox with [contentDescription] ("Set 3 done").
 */
@Composable
fun SetwiseCheckButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (checked) colors.primary else Color.Transparent, label = "checkContainer")
    val icon by animateColorAsState(
        when {
            checked -> colors.onPrimary
            enabled -> colors.onSurfaceVariant
            else -> colors.outline
        },
        label = "checkIcon",
    )

    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(10.dp),
        color = container,
        border = if (checked) null else BorderStroke(2.dp, colors.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = icon, modifier = Modifier.size(20.dp))
        }
    }
}

@ComponentPreviews
@Composable
private fun SetwiseCheckButtonPreview() = SetwisePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SetwiseCheckButton(checked = false, onCheckedChange = {}, contentDescription = "Set 1 done")
        SetwiseCheckButton(checked = true, onCheckedChange = {}, contentDescription = "Set 2 done")
        SetwiseCheckButton(checked = false, onCheckedChange = {}, contentDescription = "Set 3 done", enabled = false)
    }
}
