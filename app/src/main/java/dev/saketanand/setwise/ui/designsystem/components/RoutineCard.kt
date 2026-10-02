package dev.saketanand.setwise.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview

/**
 * Tappable card with an icon tile, a title and a short description (design artboard 1b,
 * "Create a template" / "Build from a goal").
 *
 * The look comes from [colors]: use [RoutineCardDefaults.colors] for the standard card and
 * [RoutineCardDefaults.highlightedColors] for the AI one. A new variant = a new colours preset.
 *
 * Grows with its text (no fixed height) and never gets shorter than the design's 112dp.
 */
@Composable
fun RoutineCard(
    title: String,
    description: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: RoutineCardColors = RoutineCardDefaults.colors(),
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = RoutineCardDefaults.MinHeight),
        shape = MaterialTheme.shapes.large,
        color = colors.containerColor,
        contentColor = colors.titleColor,
        border = BorderStroke(1.dp, colors.borderColor),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Decorative: the title describes the card.
            IconTile(
                icon = icon,
                containerColor = colors.iconContainerColor,
                contentColor = colors.iconColor,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.titleColor,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.descriptionColor,
            )
        }
    }
}

/** Every colour a [RoutineCard] uses. Build one with [RoutineCardDefaults]. */
@Immutable
data class RoutineCardColors(
    val containerColor: Color,
    val borderColor: Color,
    val iconContainerColor: Color,
    val iconColor: Color,
    val titleColor: Color,
    val descriptionColor: Color,
)

object RoutineCardDefaults {

    val MinHeight = 112.dp

    /** Standard card ("Create a template"): neutral border and icon tile. */
    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        borderColor: Color = MaterialTheme.colorScheme.outline,
        iconContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        iconColor: Color = MaterialTheme.colorScheme.onSurface,
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ) = RoutineCardColors(
        containerColor = containerColor,
        borderColor = borderColor,
        iconContainerColor = iconContainerColor,
        iconColor = iconColor,
        titleColor = titleColor,
        descriptionColor = descriptionColor,
    )

    /** Highlighted card ("✦ Build from a goal", on-device AI): Volt-tinted border and icon tile. */
    @Composable
    fun highlightedColors() = colors(
        borderColor = MaterialTheme.colorScheme.primaryContainer,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconColor = MaterialTheme.colorScheme.primary,
    )
}

@ComponentPreviews
@Composable
private fun RoutineCardPreview() = SetwisePreview {
    // Same arrangement as on the Workout tab: two cards side by side, equal height.
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoutineCard(
            title = "Create a template",
            description = "Save a routine like Push Day",
            icon = R.drawable.ic_add,
            onClick = {},
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        RoutineCard(
            title = "Build from a goal",
            description = "Describe it; drafted on-device",
            icon = R.drawable.ic_ai_sparkle,
            onClick = {},
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = RoutineCardDefaults.highlightedColors(),
        )
    }
}

@ComponentPreviews
@Composable
private fun RoutineCardFullWidthPreview() = SetwisePreview {
    RoutineCard(
        title = "Save Pull Day as a template",
        description = "Turn your last workout into a reusable routine",
        icon = R.drawable.ic_add,
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
    )
}
