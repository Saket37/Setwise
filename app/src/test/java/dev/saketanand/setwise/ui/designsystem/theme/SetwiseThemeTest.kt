package dev.saketanand.setwise.ui.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetwiseThemeTest {

    @get:Rule val compose = createComposeRule()

    /** Screens draw on a plain background (no Surface): their text was Material's black (#125, #127). */
    @Test
    fun `text outside a Surface uses the theme's on-background colour, dark and light`() {
        val seen = mutableMapOf<Boolean, Pair<Color, Color>>()
        compose.setContent {
            listOf(true, false).forEach { dark ->
                SetwiseTheme(darkTheme = dark) { seen[dark] = LocalContentColor.current to MaterialTheme.colorScheme.onBackground }
            }
        }
        compose.waitForIdle()

        listOf(true, false).forEach { dark ->
            val (content, onBackground) = seen.getValue(dark)
            assertEquals(onBackground, content)
        }
        assertNotEquals(Color.Black, seen.getValue(true).first) // unreadable on the dark background
    }
}
