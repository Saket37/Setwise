package dev.saketanand.setwise.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp grid. Read it with `MaterialTheme.spacing.md`. */
@Immutable
data class Spacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val screenHorizontal: Dp = 20.dp,
    val minTouchTarget: Dp = 44.dp,
    val buttonHeight: Dp = 52.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
