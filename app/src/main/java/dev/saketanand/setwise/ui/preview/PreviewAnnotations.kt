package dev.saketanand.setwise.ui.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Preview

/**
 * Light + dark for a single component (button, set row, chip...).
 * Wraps content, so the preview is only as big as the component.
 *
 * ```
 * @ComponentPreviews
 * @Composable
 * private fun SetRowPreview() = SetwisePreview { SetRow(...) }
 * ```
 */
@Preview(name = "Light", group = "Component", uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
@Preview(name = "Dark", group = "Component", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
annotation class ComponentPreviews

/**
 * Full screens at the design canvas size (390×844 dp, to compare against the mockups)
 * and at Pixel 10 width (411 dp, to check the layout stretches sensibly). Light + dark for each.
 */
@Preview(name = "Design · Dark", group = "Screen", widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Preview(name = "Design · Light", group = "Screen", widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
@Preview(name = "Pixel 10 · Dark", group = "Screen", widthDp = 411, heightDp = 923, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Preview(name = "Pixel 10 · Light", group = "Screen", widthDp = 411, heightDp = 923, uiMode = UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL)
annotation class ScreenPreviews

/**
 * Accessibility check: large system font and a narrow phone. Use alongside
 * [ScreenPreviews] on screens with dense rows (active workout, history).
 */
@Preview(name = "Font 130% · Dark", group = "Accessibility", widthDp = 390, heightDp = 844, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Preview(name = "Small phone 360dp · Dark", group = "Accessibility", widthDp = 360, heightDp = 740, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
annotation class AccessibilityPreviews
