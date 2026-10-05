package dev.saketanand.setwise.ui.designsystem.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Preview

/**
 * Light + dark for a single component (button, set row, chip...).
 * Wraps content, so the preview is only as big as the component.
 *
 * ```
 * @PreviewComponents
 * @Composable
 * private fun SetRowPreview() = SetwisePreview { SetRow(...) }
 * ```
 */
@Preview(name = "Light", group = "Component", uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
@Preview(name = "Dark", group = "Component", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
annotation class PreviewComponents

/**
 * Full screens at the design canvas size (390×844 dp, to compare against the mockups), at
 * Pixel 10 width (411 dp, to check the layout stretches sensibly), and at the design size with
 * 130% font. Light + dark for each. The screenshot tests (#50) use the design and font ones.
 */
@Preview(name = "Design dark", group = "Screen", widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Preview(name = "Design light", group = "Screen", widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
@Preview(name = "Pixel 10 dark", group = "Screen", widthDp = 411, heightDp = 923, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Preview(name = "Pixel 10 light", group = "Screen", widthDp = 411, heightDp = 923, uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
@Preview(name = "Large font dark", group = "Screen", widthDp = 390, heightDp = 844, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Preview(name = "Large font light", group = "Screen", widthDp = 390, heightDp = 844, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
annotation class PreviewScreens

/**
 * Accessibility check: a narrow phone (130% font is in [PreviewScreens]). Use alongside
 * [PreviewScreens] on screens with dense rows (active workout, history).
 */
@Preview(name = "Small phone dark", group = "Accessibility", widthDp = 360, heightDp = 740, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
annotation class PreviewAccessibility
