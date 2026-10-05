package dev.saketanand.setwise.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.buttonLarge

/**
 * The app's button. Pill-shaped; grows with the text (minimum height per [size], never fixed),
 * so large system fonts don't clip.
 *
 * - Filled + Large: primary full-width action ("Start workout") → pass Modifier.fillMaxWidth().
 * - Outlined + Medium: secondary actions ("Add exercise", "Save as template").
 * - Outlined + Small: compact header actions ("+ New").
 * - Tonal: quiet in-card actions on a grey fill ("Add set"); often with shape = shapes.medium.
 * - Text: low-emphasis actions ("Change", dialog buttons).
 */
@Composable
fun SetwiseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SetwiseButtonStyle = SetwiseButtonStyle.Filled,
    size: SetwiseButtonSize = SetwiseButtonSize.Large,
    @DrawableRes startIcon: Int? = null,
    enabled: Boolean = true,
    colors: SetwiseButtonColors = SetwiseButtonDefaults.colors(style),
    /** Overrides the size's text style (rarely needed). */
    textStyle: TextStyle? = null,
    contentPadding: PaddingValues = SetwiseButtonDefaults.contentPadding(style, size),
    shape: Shape = CircleShape,
) {
    Button(
        onClick = onClick,
        // heightIn(min) instead of a fixed height: the design size is the minimum.
        modifier = modifier.heightIn(min = size.minHeight),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
        ),
        border = colors.borderColor?.let { BorderStroke(1.dp, it) },
        contentPadding = contentPadding,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (startIcon != null) {
                Icon(painterResource(startIcon), contentDescription = null, modifier = Modifier.size(size.iconSize))
                HorizontalGap(size.iconGap)
            }
            Text(text = text, style = textStyle ?: size.textStyle())
        }
    }
}

enum class SetwiseButtonStyle { Filled, Outlined, Tonal, Text }

/** Size presets from the design. Heights are minimums. */
enum class SetwiseButtonSize(
    val minHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Dp,
    val iconGap: Dp,
) {
    /** 56dp, DM Sans Bold 17: primary actions. */
    Large(minHeight = 56.dp, horizontalPadding = 24.dp, verticalPadding = 16.dp, iconSize = 20.dp, iconGap = 10.dp),

    /** 44dp, SemiBold 15: secondary actions, dialog buttons. */
    Medium(minHeight = 44.dp, horizontalPadding = 16.dp, verticalPadding = 10.dp, iconSize = 18.dp, iconGap = 8.dp),

    /** 36dp, SemiBold 12: compact actions in headers ("+ New"). */
    Small(minHeight = 36.dp, horizontalPadding = 14.dp, verticalPadding = 0.dp, iconSize = 16.dp, iconGap = 6.dp);

    @Composable
    fun textStyle(): TextStyle = when (this) {
        Large -> MaterialTheme.typography.buttonLarge
        Medium -> MaterialTheme.typography.labelLarge
        Small -> MaterialTheme.typography.labelMedium
    }
}

@Immutable
data class SetwiseButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color?,
)

object SetwiseButtonDefaults {

    /**
     * Padding from the [size]. Text buttons have no container to fill, so they use M3's tighter
     * 12dp sides (the text lines up the way dialog buttons usually do).
     */
    fun contentPadding(style: SetwiseButtonStyle, size: SetwiseButtonSize) = PaddingValues(
        horizontal = if (style == SetwiseButtonStyle.Text) minOf(size.horizontalPadding, 12.dp) else size.horizontalPadding,
        vertical = size.verticalPadding,
    )

    @Composable
    fun colors(style: SetwiseButtonStyle): SetwiseButtonColors = when (style) {
        SetwiseButtonStyle.Filled -> SetwiseButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            borderColor = null,
        )
        SetwiseButtonStyle.Outlined -> SetwiseButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            borderColor = MaterialTheme.colorScheme.outline,
        )
        SetwiseButtonStyle.Tonal -> SetwiseButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            borderColor = null,
        )
        SetwiseButtonStyle.Text -> SetwiseButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            borderColor = null,
        )
    }

    /** For actions that delete something ("Discard and start", "Delete set"). */
    @Composable
    fun destructiveColors(style: SetwiseButtonStyle): SetwiseButtonColors = when (style) {
        SetwiseButtonStyle.Filled -> SetwiseButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            borderColor = null,
        )
        SetwiseButtonStyle.Outlined -> SetwiseButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.error,
            borderColor = MaterialTheme.colorScheme.error,
        )
        SetwiseButtonStyle.Tonal -> SetwiseButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.error,
            borderColor = null,
        )
        SetwiseButtonStyle.Text -> SetwiseButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.error,
            borderColor = null,
        )
    }
}

@PreviewComponents
@Composable
private fun SetwiseButtonPreview() = SetwisePreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetwiseButton("Start workout", onClick = {}, startIcon = R.drawable.ic_play, modifier = Modifier.fillMaxWidth())
        SetwiseButton("Start workout", onClick = {}, startIcon = R.drawable.ic_play, enabled = false, modifier = Modifier.fillMaxWidth())
        SetwiseButton(
            "Add exercise", onClick = {}, startIcon = R.drawable.ic_add,
            style = SetwiseButtonStyle.Outlined, size = SetwiseButtonSize.Medium, modifier = Modifier.fillMaxWidth(),
        )
        SetwiseButton(
            "Add set", onClick = {}, startIcon = R.drawable.ic_add, style = SetwiseButtonStyle.Tonal,
            size = SetwiseButtonSize.Medium, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SetwiseButton("New", onClick = {}, startIcon = R.drawable.ic_add, style = SetwiseButtonStyle.Outlined, size = SetwiseButtonSize.Small)
            SetwiseButton("Change", onClick = {}, style = SetwiseButtonStyle.Text, size = SetwiseButtonSize.Medium)
            SetwiseButton(
                "Discard", onClick = {}, style = SetwiseButtonStyle.Text, size = SetwiseButtonSize.Medium,
                colors = SetwiseButtonDefaults.destructiveColors(SetwiseButtonStyle.Text),
            )
        }
    }
}
