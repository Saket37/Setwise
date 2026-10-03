package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Top bar of pushed screens (exercise picker, create exercise, template editor…): back arrow,
 * title, and optional [actions] on the right ("Create new", "Save").
 *
 * Draws no status-bar inset: the app Scaffold already pads every screen for it.
 *
 * @param onBack null hides the back arrow; the title then lines up with the 20dp screen margin.
 */
@Composable
fun SetwiseTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            SetwiseIconButton(
                icon = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                size = 48.dp,
                iconSize = 22.dp,
                colors = SetwiseIconButtonDefaults.plainColors(),
            )
        } else {
            HorizontalGap(8.dp) // 8 + 4 + 8 = the 20dp screen margin
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Lets screen-reader users jump straight to the screen title.
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
    }
}

@ComponentPreviews
@Composable
private fun SetwiseTopAppBarPreview() = SetwisePreview(padding = 0.dp) {
    Column {
        SetwiseTopAppBar(title = "Add exercise", onBack = {}) {
            SetwiseButton(
                text = "Create new",
                onClick = {},
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                textStyle = MaterialTheme.typography.titleSmall,
            )
        }
        SetwiseTopAppBar(title = "Settings")
    }
}
