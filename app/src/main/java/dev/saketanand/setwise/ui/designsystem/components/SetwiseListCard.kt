package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * The card-shaped list row used across the app: template cards, sheet rows, the Resume card,
 * exercise rows, settings rows, PR rows.
 *
 * Slot API like Material's ListItem: [overlineContent] (small label above), [headlineContent]
 * (title), [supportingContent] (line below), [leadingContent] / [trailingContent]. Slots get
 * the right text style and colour automatically, so pass a plain `Text(...)`.
 *
 * @param onClick null = not clickable (e.g. a static info row).
 * @param outline none, solid or dashed border (dashed = "add something" placeholders).
 * @param shape cards use shapes.large; denser list rows (exercise picker) use shapes.medium.
 *   The dashed outline is drawn for shapes.large's 16dp corners.
 */
@Composable
fun SetwiseListCard(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: SetwiseListCardColors = SetwiseListCardDefaults.colors(),
    outline: SetwiseListCardOutline = SetwiseListCardOutline.None,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = SetwiseListCardDefaults.ContentPadding,
    horizontalSpacing: Dp = 12.dp,
    textSpacing: Dp = 2.dp,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .then(if (outline is SetwiseListCardOutline.Dashed) Modifier.dashedBorder(outline.color, cornerRadius = 16.dp) else Modifier)
    val border = (outline as? SetwiseListCardOutline.Solid)?.let { BorderStroke(1.dp, it.color) }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        ) {
            leadingContent?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(textSpacing)) {
                overlineContent?.let {
                    ProvideTextStyle(MaterialTheme.typography.labelSmall, it)
                }
                ProvideTextStyle(MaterialTheme.typography.titleMedium, headlineContent)
                supportingContent?.let {
                    CompositionLocalProvider(LocalContentColor provides colors.supportingColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, it)
                    }
                }
            }
            trailingContent?.invoke()
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = cardModifier,
            shape = shape,
            color = colors.containerColor,
            contentColor = colors.contentColor,
            border = border,
            content = content,
        )
    } else {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = colors.containerColor,
            contentColor = colors.contentColor,
            border = border,
            content = content,
        )
    }
}

@Immutable
data class SetwiseListCardColors(
    val containerColor: Color,
    val contentColor: Color,
    val supportingColor: Color,
)

/** Border around a [SetwiseListCard]. */
@Immutable
sealed interface SetwiseListCardOutline {
    data object None : SetwiseListCardOutline
    data class Solid(val color: Color) : SetwiseListCardOutline
    data class Dashed(val color: Color) : SetwiseListCardOutline
}

object SetwiseListCardDefaults {

    val ContentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

    /** Standard card on the screen background. */
    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ) = SetwiseListCardColors(containerColor, contentColor, supportingColor)

    /** Rows inside a sheet or card, one step lighter than their container. */
    @Composable
    fun raisedColors() = colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

    /** Highlighted (Volt-tinted): "Workout in progress". */
    @Composable
    fun highlightedColors() = colors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        supportingColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    /** No fill; use with an outline. */
    @Composable
    fun transparentColors() = colors(containerColor = Color.Transparent)
}

@PreviewComponents
@Composable
private fun SetwiseListCardPreview() = SetwisePreview {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SetwiseListCard(
            headlineContent = { Text("Push Day") },
            supportingContent = { Text("Bench Press · Incline Dumbbell Press · +2 · 4 days ago") },
            trailingContent = { SetwiseIconButton(R.drawable.ic_play, "Start Push Day", onClick = {}) },
            onClick = {},
        )
        SetwiseListCard(
            overlineContent = { SectionLabel("Workout in progress", color = LocalContentColor.current) },
            headlineContent = { Text("Pull Day") },
            supportingContent = { Text("Running 12:34 · 6 sets done") },
            colors = SetwiseListCardDefaults.highlightedColors(),
            onClick = {},
        )
        SetwiseListCard(
            leadingContent = { IconTile(R.drawable.ic_add, size = 44.dp, iconSize = 22.dp, cornerRadius = 12.dp, contentColor = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text("Empty workout") },
            supportingContent = { Text("Add exercises as you go") },
            colors = SetwiseListCardDefaults.transparentColors(),
            outline = SetwiseListCardOutline.Dashed(MaterialTheme.colorScheme.outline),
            onClick = {},
        )
        SetwiseListCard(
            headlineContent = { Text("Leg Day") },
            supportingContent = { Text("5 exercises · 16 sets · ~44 min") },
            trailingContent = {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
            },
            colors = SetwiseListCardDefaults.raisedColors(),
            onClick = {},
        )
    }
}
