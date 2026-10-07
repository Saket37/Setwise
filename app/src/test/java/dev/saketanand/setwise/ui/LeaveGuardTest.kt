package dev.saketanand.setwise.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.saketanand.setwise.ui.designsystem.theme.SetwiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LeaveGuardTest {

    @get:Rule val compose = createComposeRule()

    private var left = 0

    private fun show(hasChanges: Boolean) = compose.setContent {
        SetwiseTheme {
            val state = rememberLeaveGuardState()
            LeaveGuard(state, hasChanges = hasChanges, onLeave = { left++ })
            Button(onClick = { state.leave(hasChanges) { left++ } }) { Text("Back") }
        }
    }

    @Test
    fun withoutChangesBackJustLeaves() {
        show(hasChanges = false)
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, left)
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun withChangesBackAsksFirst() {
        show(hasChanges = true)
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, left)

        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(0, left)

        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Discard").performClick()
        assertEquals(1, left)
    }
}
