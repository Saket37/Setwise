package dev.saketanand.setwise.ui.history

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        HistoryViewModel(StubWorkoutRepository(), FixedDateProvider, handle).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
        }

    @Test
    fun `tapping a day selects it, tapping it again clears it`() = runTest(dispatcher) {
        val vm = viewModel()
        val day = LocalDate.of(2026, 9, 30)

        vm.onAction(HistoryAction.OnDayClick(day))
        assertEquals(day, vm.state.value.selectedDate)

        vm.onAction(HistoryAction.OnDayClick(LocalDate.of(2026, 9, 29)))
        assertEquals(LocalDate.of(2026, 9, 29), vm.state.value.selectedDate)

        vm.onAction(HistoryAction.OnDayClick(LocalDate.of(2026, 9, 29)))
        assertNull(vm.state.value.selectedDate)
    }

    @Test
    fun `the selected day survives the app being killed`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        viewModel(handle).onAction(HistoryAction.OnDayClick(LocalDate.of(2026, 9, 30)))

        assertEquals(LocalDate.of(2026, 9, 30), viewModel(handle).state.value.selectedDate)
    }

    /** Saturday 3 Oct 2026. */
    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 3).atTime(18, 30).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }
}
