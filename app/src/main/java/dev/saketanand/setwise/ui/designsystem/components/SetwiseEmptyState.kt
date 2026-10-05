package dev.saketanand.setwise.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Centered "nothing here" message: no search results, empty history, no PRs yet.
 * Optional [icon] tile above, and an [action] below (usually a [SetwiseButton]).
 */
@Composable
fun SetwiseEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes icon: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            IconTile(
                icon = icon,
                size = 48.dp,
                iconSize = 22.dp,
                cornerRadius = 14.dp,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VerticalGap(4.dp)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            VerticalGap(8.dp)
            action()
        }
    }
}

@PreviewComponents
@Composable
private fun SetwiseEmptyStatePreview() = SetwisePreview {
    SetwiseEmptyState(
        title = "No matches for “zercher”",
        message = "Check the spelling, or add it as your own exercise.",
        icon = R.drawable.ic_search,
        action = {
            SetwiseButton(
                text = "Create “zercher”",
                onClick = {},
                style = SetwiseButtonStyle.Outlined,
                size = SetwiseButtonSize.Medium,
                startIcon = R.drawable.ic_add,
            )
        },
    )
}
