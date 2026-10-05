package dev.saketanand.setwise.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall

/**
 * Rounded square holding an icon (or any small content, e.g. exercise initials "BP").
 * Decorative: not clickable. Pass cornerRadius = size / 2 for a circle.
 */
@Composable
fun IconTile(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    cornerRadius: Dp = 10.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/** [IconTile] with a drawable icon (the common case). */
@Composable
fun IconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    cornerRadius: Dp = 10.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconTile(
        modifier = modifier,
        size = size,
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize))
    }
}

@PreviewComponents
@Composable
private fun IconTilePreview() = SetwisePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon = R.drawable.ic_add)
        IconTile(
            icon = R.drawable.ic_add,
            size = 44.dp,
            iconSize = 22.dp,
            cornerRadius = 12.dp,
            contentColor = MaterialTheme.colorScheme.primary,
        )
        IconTile(size = 40.dp, cornerRadius = 12.dp, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
            Text("BP", style = MaterialTheme.typography.numberSmall)
        }
        IconTile(
            icon = R.drawable.ic_play,
            size = 44.dp,
            cornerRadius = 22.dp,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
