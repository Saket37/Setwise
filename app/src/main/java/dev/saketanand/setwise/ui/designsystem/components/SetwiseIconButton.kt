package dev.saketanand.setwise.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Round icon-only button: ▶ on template cards, back arrows, "more" menus, steppers.
 * [contentDescription] is required: icon-only buttons need a label for screen readers.
 */
@Composable
fun SetwiseIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 18.dp,
    colors: SetwiseIconButtonColors = SetwiseIconButtonDefaults.tonalColors(),
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}

@Immutable
data class SetwiseIconButtonColors(val containerColor: Color, val contentColor: Color)

object SetwiseIconButtonDefaults {

    /** Dark tile with a Volt icon (▶ on template cards). */
    @Composable
    fun tonalColors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor: Color = MaterialTheme.colorScheme.primary,
    ) = SetwiseIconButtonColors(containerColor, contentColor)

    /** Volt fill (the most important action on a card). */
    @Composable
    fun filledColors() = SetwiseIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )

    /** No background (back arrow, overflow menu). */
    @Composable
    fun plainColors(contentColor: Color = MaterialTheme.colorScheme.onSurface) =
        SetwiseIconButtonColors(containerColor = Color.Transparent, contentColor = contentColor)
}

@PreviewComponents
@Composable
private fun SetwiseIconButtonPreview() = SetwisePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SetwiseIconButton(R.drawable.ic_play, contentDescription = "Start", onClick = {})
        SetwiseIconButton(R.drawable.ic_play, "Resume", onClick = {}, colors = SetwiseIconButtonDefaults.filledColors())
        SetwiseIconButton(
            R.drawable.ic_arrow_back, "Back", onClick = {},
            size = 48.dp, iconSize = 22.dp, colors = SetwiseIconButtonDefaults.plainColors(),
        )
    }
}
