package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Empty space between items in a Row.
 *
 * ```
 * Row { Icon(...); HorizontalGap(MaterialTheme.spacing.xs); Text(...) }
 * ```
 */
@Composable
fun HorizontalGap(width: Dp, modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.width(width))
}

/**
 * Empty space between items in a Column.
 *
 * ```
 * Column { Text(...); VerticalGap(MaterialTheme.spacing.md); Button(...) }
 * ```
 */
@Composable
fun VerticalGap(height: Dp, modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(height))
}
