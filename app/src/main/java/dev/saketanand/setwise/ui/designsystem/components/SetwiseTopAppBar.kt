package dev.saketanand.setwise.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Top bar of pushed screens (exercise picker, create exercise, template editor…): back arrow,
 * title, and optional [actions] on the right ("Create new", "Save").
 *
 * Draws no status-bar inset: the app Scaffold already pads every screen for it.
 *
 * @param onBack null hides the navigation button; the title then lines up with the 20dp screen margin.
 * @param navigationIcon back arrow by default; the active workout uses a "minimise" chevron.
 * @param subtitle optional line under the title ("Started 6:42 PM").
 */
@Composable
fun SetwiseTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    @DrawableRes navigationIcon: Int = R.drawable.ic_arrow_back,
    navigationContentDescription: String = stringResource(R.string.back),
    subtitle: (@Composable () -> Unit)? = null,
    /** Makes the title tappable (e.g. rename), labelled [titleClickLabel] for screen readers. */
    onTitleClick: (() -> Unit)? = null,
    titleClickLabel: String? = null,
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
                icon = navigationIcon,
                contentDescription = navigationContentDescription,
                onClick = onBack,
                size = 48.dp,
                iconSize = 22.dp,
                colors = SetwiseIconButtonDefaults.plainColors(),
            )
        } else {
            HorizontalGap(8.dp) // 8 + 4 + 8 = the 20dp screen margin
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Lets screen-reader users jump straight to the screen title.
                modifier = Modifier
                    .then(if (onTitleClick != null) Modifier.clickable(onClickLabel = titleClickLabel, onClick = onTitleClick) else Modifier)
                    .semantics { heading() },
            )
            if (subtitle != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, subtitle)
                }
            }
        }
        actions()
    }
}

@PreviewComponents
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
        SetwiseTopAppBar(
            title = "Push Day",
            onBack = {},
            navigationIcon = R.drawable.ic_chevron_down,
            navigationContentDescription = "Minimise workout",
            subtitle = { Text("Started 6:42 PM") },
        )
        SetwiseTopAppBar(title = "Settings")
    }
}
