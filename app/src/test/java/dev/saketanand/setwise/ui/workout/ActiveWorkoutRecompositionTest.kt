package dev.saketanand.setwise.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import dev.saketanand.setwise.testing.RecompositionCounter
import dev.saketanand.setwise.ui.designsystem.theme.SetwiseTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How much of the workout screen recomposes when one thing changes: the screen stays open for a
 * whole workout, with a clock and a rest timer ticking. Ceilings are what was measured plus
 * headroom; a change that blows one (e.g. set rows recomposing on every tick) fails here. To see
 * the numbers, print [RecompositionCounter.scopes].
 */
@RunWith(RobolectricTestRunner::class)
class ActiveWorkoutRecompositionTest {

    @get:Rule
    val compose = createComposeRule()

    private val counter = RecompositionCounter()
    private var uiState by mutableStateOf(state)

    @Before
    fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            counter.Observe { SetwiseTheme { ActiveWorkoutScreen(uiState = uiState, onAction = {}) } }
        }
        compose.mainClock.advanceTimeBy(100)
    }

    @Test
    fun `the workout clock ticking recomposes only the clock`() {
        counter.reset()
        compose.mainClock.advanceTimeBy(3_000)
        // Measured: 5 over 3 seconds.
        assertAtMost(8, "3 s of the clock")
    }

    @Test
    fun `the rest timer ticking recomposes only the rest bar`() {
        change { it.copy(rest = RestUi(endsAtElapsed = 60_000, totalMillis = 90_000, nextSetNumber = 3, nextExerciseName = null)) }
        counter.reset()
        compose.mainClock.advanceTimeBy(1_000)
        // Measured: 9 for a second of rest bar (ticking 10× a second) and clock.
        assertAtMost(15, "1 s of rest")
    }

    @Test
    fun `editing one set leaves the other rows alone`() {
        change { editSets(it, exercise = 0) { index, set -> if (index == 2) set.copy(reps = "9") else set } }
        val oneSet = counter.scopes
        change { editSets(it, exercise = 0) { _, set -> set.copy(reps = "7") } }
        val allSets = counter.scopes

        // Measured: 74 for one set, 131 for all four (≈ 55 for the card, ≈ 19 a row).
        assertTrue("One set recomposed $oneSet scopes", oneSet <= 95)
        assertTrue("One set ($oneSet) should cost well under all four ($allSets)", oneSet + 2 * ROW_SCOPES <= allSets)
    }

    @Test
    fun `a set in a collapsed card recomposes only that card`() {
        change { editSets(it, exercise = 1) { index, set -> if (index == 0) set.copy(reps = "5") else set } }
        // Measured: 26.
        assertAtMost(35, "a collapsed card's set")
    }

    @Test
    fun `an equal state recomposes nothing`() {
        change { s -> s.copy(exercises = s.exercises.map { e -> e.copy(sets = e.sets.map { it.copy() }) }) }
        assertEquals(0, counter.scopes)
    }

    /** Resets the count, changes the state (as the ViewModel would) and runs the frames it needs. */
    private fun change(update: (ActiveWorkoutUiState) -> ActiveWorkoutUiState) {
        counter.reset()
        uiState = update(uiState)
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun assertAtMost(ceiling: Int, what: String) =
        assertTrue("$what recomposed ${counter.scopes} scopes (at most $ceiling)", counter.scopes <= ceiling)

    private fun editSets(state: ActiveWorkoutUiState, exercise: Int, edit: (Int, SetUi) -> SetUi) =
        state.copy(
            exercises = state.exercises.mapIndexed { i, e -> if (i == exercise) e.copy(sets = e.sets.mapIndexed(edit)) else e },
        )

    private companion object {
        /** Roughly what one set row costs to recompose (measured ≈ 19). */
        const val ROW_SCOPES = 12

        val state = ActiveWorkoutUiState(
            isLoading = false,
            name = "Push Day",
            startedAtMillis = System.currentTimeMillis() - 38 * 60_000,
            startTime = LocalTime.of(18, 42),
            expandedExerciseId = 1,
            exercises = listOf(
                WorkoutExerciseUi(
                    id = 1, exerciseId = 10, name = "Bench Press (Barbell)", kind = SetKind.WeightReps, restSec = 120,
                    lastTime = "60 × 8 · 60 × 8 · 60 × 7 · 57.5 × 9",
                    sets = listOf(
                        SetUi(1, 1, "60 × 8", "60", "8", "60", "8", isCompleted = true, isPr = false),
                        SetUi(2, 2, "60 × 8", "62.5", "8", "60", "8", isCompleted = true, isPr = true),
                        SetUi(3, 3, "60 × 7", "", "", "62.5", "8", isCompleted = false, isPr = false),
                        SetUi(4, 4, "57.5 × 9", "", "", "57.5", "9", isCompleted = false, isPr = false),
                    ),
                ),
                WorkoutExerciseUi(
                    id = 2, exerciseId = 11, name = "Overhead Press", kind = SetKind.WeightReps, restSec = 90,
                    lastTime = "40 × 6 · 40 × 6 · 37.5 × 8",
                    sets = List(3) { SetUi(10L + it, it + 1, null, "", "", "40", "6", isCompleted = false, isPr = false) },
                ),
            ),
        )
    }
}
