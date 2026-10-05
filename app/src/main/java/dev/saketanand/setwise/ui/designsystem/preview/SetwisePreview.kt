package dev.saketanand.setwise.ui.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.theme.SetwiseTheme

/**
 * Wraps preview content in [SetwiseTheme] with the theme's background, picking light or dark
 * from the preview's uiMode. Pair it with [PreviewComponents], [PreviewScreens] or
 * [PreviewAccessibility].
 *
 * @param padding space around a component so it isn't flush to the preview edge; use 0.dp for screens.
 */
@Composable
fun SetwisePreview(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    SetwiseTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(padding)) {
                content()
            }
        }
    }
}

/** Shorthand for full-screen previews: no padding. */
@Composable
fun SetwiseScreenPreview(content: @Composable () -> Unit) = SetwisePreview(padding = 0.dp, content = content)
