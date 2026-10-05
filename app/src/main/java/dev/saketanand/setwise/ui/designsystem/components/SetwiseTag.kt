package dev.saketanand.setwise.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.saketanand.setwise.ui.designsystem.theme.onPr
import dev.saketanand.setwise.ui.designsystem.theme.pr

/**
 * Small non-interactive label: template category ("PUSH"), PR chips ("🏆 PR · Weight").
 * Look comes from [style]: [SetwiseTagDefaults.outlined] or [SetwiseTagDefaults.pr].
 */
@Composable
fun SetwiseTag(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    style: SetwiseTagStyle = SetwiseTagDefaults.outlined(),
) {
    Row(
        modifier = modifier
            .then(if (style.borderColor != null) Modifier.border(1.dp, style.borderColor, style.shape) else Modifier)
            .background(style.containerColor, style.shape)
            .padding(style.contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = style.contentColor, modifier = Modifier.size(style.iconSize))
        }
        Text(text = if (style.uppercase) text.uppercase() else text, style = style.textStyle, color = style.contentColor)
    }
}

/** Everything that differs between tag looks. Build with [SetwiseTagDefaults]. */
@Immutable
data class SetwiseTagStyle(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color?,
    val shape: Shape,
    val contentPadding: PaddingValues,
    val textStyle: TextStyle,
    val iconSize: Dp,
    /** Tags read as labels in capitals; a sentence-like one (e.g. "3 days / week") keeps its case. */
    val uppercase: Boolean = true,
)

object SetwiseTagDefaults {

    /** Template category on cards: thin outline, muted caps text. */
    @Composable
    fun outlined() = SetwiseTagStyle(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        borderColor = MaterialTheme.colorScheme.outline,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp),
        textStyle = MaterialTheme.typography.labelSmall,
        iconSize = 12.dp,
    )

    /** What something was understood as (e.g. a goal's "45 min"): the accent's container. */
    @Composable
    fun accent() = SetwiseTagStyle(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        borderColor = null,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        textStyle = MaterialTheme.typography.labelMedium,
        iconSize = 14.dp,
        uppercase = false,
    )

    /** Personal record: Ember fill. Only for PRs, so the colour keeps its meaning. */
    @Composable
    fun pr() = SetwiseTagStyle(
        containerColor = MaterialTheme.colorScheme.pr,
        contentColor = MaterialTheme.colorScheme.onPr,
        borderColor = null,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        textStyle = MaterialTheme.typography.labelMedium,
        iconSize = 14.dp,
    )
}

@PreviewComponents
@Composable
private fun SetwiseTagPreview() = SetwisePreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SetwiseTag("Push")
        SetwiseTag("PR · Weight", icon = R.drawable.ic_trophy, style = SetwiseTagDefaults.pr())
    }
}
