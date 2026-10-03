package dev.saketanand.setwise.ui.settings

import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = SettingsViewModel(settings).also { vm ->
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `body weight is saved, an implausible one keeps the dialog open with a message`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SettingsAction.OnBodyWeightClick)

        vm.onAction(SettingsAction.OnSaveBodyWeight("7"))
        assertEquals(SettingsEditor.BodyWeight(isInvalid = true), vm.state.value.editor)
        assertEquals(70.0, settings.settings.value.bodyWeightKg)

        vm.onAction(SettingsAction.OnSaveBodyWeight("72,5"))
        assertNull(vm.state.value.editor)
        assertEquals(72.5, vm.state.value.bodyWeightKg)
    }

    @Test
    fun `body weight can be removed`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SettingsAction.OnBodyWeightClick)

        vm.onAction(SettingsAction.OnRemoveBodyWeight)

        assertNull(vm.state.value.bodyWeightKg)
    }

    @Test
    fun `training days are picked in the dialog and saved only on Save`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SettingsAction.OnTrainingDaysClick)
        vm.onAction(SettingsAction.OnDayToggle(DayOfWeek.TUESDAY))
        vm.onAction(SettingsAction.OnDayToggle(DayOfWeek.SATURDAY))
        assertEquals(emptySet<DayOfWeek>(), settings.settings.value.trainingDays)

        vm.onAction(SettingsAction.OnSaveTrainingDays)

        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), vm.state.value.trainingDays)
        assertNull(vm.state.value.editor)
    }

    @Test
    fun `cancel keeps the saved days`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SettingsAction.OnTrainingDaysClick)
        vm.onAction(SettingsAction.OnDayToggle(DayOfWeek.MONDAY))

        vm.onAction(SettingsAction.OnDismissEditor)

        assertEquals(emptySet<DayOfWeek>(), vm.state.value.trainingDays)
    }

    @Test
    fun `the check-in switch is saved`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(SettingsAction.OnAskAboutUnloggedDaysChange(false))

        assertFalse(vm.state.value.askAboutUnloggedDays)
    }
}
