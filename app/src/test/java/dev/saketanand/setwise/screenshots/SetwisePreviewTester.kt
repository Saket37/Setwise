package dev.saketanand.setwise.screenshots

import com.github.takahirom.roborazzi.AndroidComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboComposePreviewOptionVariation
import com.github.takahirom.roborazzi.annotations.ManualClockOptions

/**
 * The screenshot tests' previews (#50): every @Preview in the app, light and dark, but not the
 * Pixel 10 copies of each screen (the design size and 130% font are enough to catch a change).
 * Roborazzi generates a test per preview from this; the goldens are in src/test/screenshots.
 */
@OptIn(ExperimentalRoborazziApi::class)
class SetwisePreviewTester : ComposePreviewTester<AndroidPreviewJUnit4TestParameter> {

    private val tester = AndroidComposePreviewTester()

    override fun options(): ComposePreviewTester.Options = tester.options()

    override fun testParameters(): List<AndroidPreviewJUnit4TestParameter> =
        tester.testParameters()
            .filterNot { it.preview.previewInfo.name.startsWith("Pixel 10") }
            .map { parameter ->
                // A focused text field's blinking cursor never lets Compose go idle: a fixed clock, half a second in.
                if (parameter.preview.methodName in NEVER_IDLE) {
                    parameter.copy(composeRoboComposePreviewOptionVariation = RoboComposePreviewOptionVariation(ManualClockOptions(FROZEN_AT_MS)))
                } else {
                    parameter
                }
            }

    override fun test(testParameter: AndroidPreviewJUnit4TestParameter) = tester.test(testParameter)

    private companion object {
        val NEVER_IDLE = setOf("SetwiseTextInputDialogPreview")
        const val FROZEN_AT_MS = 500L
    }
}
