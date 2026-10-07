package dev.saketanand.setwise.ui.designsystem

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTimePickerDialog
import dev.saketanand.setwise.ui.designsystem.theme.SetwiseTheme
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetwiseTimePickerDialogTest {

    @get:Rule val compose = createComposeRule()

    private val message = "That's later than now. Pick an earlier time."

    @Test
    fun aTimeThatIsNotAllowedSaysWhyAndCantBeConfirmed() {
        compose.setContent {
            SetwiseTheme {
                SetwiseTimePickerDialog(
                    title = "Start time",
                    initial = LocalTime.of(23, 0),
                    onConfirm = {},
                    onDismiss = {},
                    isAllowed = { it.isBefore(LocalTime.of(18, 30)) },
                    notAllowedMessage = message,
                )
            }
        }
        compose.onNodeWithText(message).assertExists()
        compose.onNodeWithText("OK").assertIsNotEnabled()
    }

    @Test
    fun anAllowedTimeCanBeConfirmed() {
        compose.setContent {
            SetwiseTheme {
                SetwiseTimePickerDialog(
                    title = "Start time",
                    initial = LocalTime.of(17, 45),
                    onConfirm = {},
                    onDismiss = {},
                    isAllowed = { it.isBefore(LocalTime.of(18, 30)) },
                    notAllowedMessage = message,
                )
            }
        }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithText("OK").assertIsEnabled()
    }
}
