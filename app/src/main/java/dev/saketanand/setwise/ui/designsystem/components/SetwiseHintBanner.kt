package dev.saketanand.setwise.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall

/**
 * A tinted one-line suggestion with a leading icon and, when [onClick] is set, a chevron: e.g.
 * the workout card's "✦ Try 62.5 kg × 8 today: you hit all reps at 60 twice ›".
 * [highlight] is drawn in the number style ([SetwiseHintBannerDefaults.highlighted] builds the text).
 */
@Composable
fun SetwiseHintBanner(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_ai_sparkle,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

object SetwiseHintBannerDefaults {
    /** [text] with its first [highlight] in the number style ("Try **62.5 kg × 8** today…"). */
    @Composable
    fun highlighted(text: String, highlight: String): AnnotatedString {
        val numberStyle = MaterialTheme.typography.numberSmall.toSpanStyle()
        return buildAnnotatedString {
            val start = text.indexOf(highlight)
            if (start < 0) {
                append(text)
            } else {
                append(text.substring(0, start))
                withStyle(SpanStyle(fontFamily = numberStyle.fontFamily, fontWeight = numberStyle.fontWeight, fontSize = numberStyle.fontSize)) {
                    append(highlight)
                }
                append(text.substring(start + highlight.length))
            }
        }
    }
}

@PreviewComponents
@Composable
private fun SetwiseHintBannerPreview() = SetwisePreview {
    SetwiseHintBanner(
        text = SetwiseHintBannerDefaults.highlighted("Try 62.5 kg × 8 today: you hit all reps at 60 kg twice", "62.5 kg × 8"),
        onClick = {},
    )
}
