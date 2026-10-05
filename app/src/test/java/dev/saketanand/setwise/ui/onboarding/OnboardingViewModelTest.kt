package dev.saketanand.setwise.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.MainViewModel
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.ui.navigation.Route
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeUserSettingsRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** Default: notifications already allowed (or Android 12-), so no notifications step. */
    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(), needsNotificationPermission: Boolean = false) =
        OnboardingViewModel(settings, { needsNotificationPermission }, handle)

    @Test
    fun `the notifications step comes last when the permission still has to be asked`() = runTest(dispatcher) {
        val vm = viewModel(SavedStateHandle(mapOf("step" to OnboardingStep.CheckIns.name)), needsNotificationPermission = true)
        assertEquals(5, vm.state.value.stepCount)

        vm.onAction(OnboardingAction.OnContinue())
        assertEquals(OnboardingStep.Notifications, vm.state.value.step)
        assertTrue(vm.state.value.isLastStep)
        assertFalse(settings.settings.value.onboardingDone)

        // Allowed or not, the system prompt's answer ends onboarding.
        vm.onAction(OnboardingAction.OnNotificationsAnswered)
        vm.onFinished.first()
        assertTrue(settings.settings.value.onboardingDone)
    }

    @Test
    fun `without a permission to ask, check-ins is the last step`() = runTest(dispatcher) {
        val vm = viewModel(SavedStateHandle(mapOf("step" to OnboardingStep.CheckIns.name)))
        assertEquals(4, vm.state.value.stepCount)
        assertTrue(vm.state.value.isLastStep)
    }

    @Test
    fun `not now on notifications finishes without asking`() = runTest(dispatcher) {
        val vm = viewModel(SavedStateHandle(mapOf("step" to OnboardingStep.Notifications.name)), needsNotificationPermission = true)

        vm.onAction(OnboardingAction.OnSkipStep)

        vm.onFinished.first()
        assertTrue(settings.settings.value.onboardingDone)
    }

    @Test
    fun `answering every step saves the answers and finishes`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(OnboardingAction.OnContinue()) // Welcome
        vm.onAction(OnboardingAction.OnDayToggle(DayOfWeek.MONDAY))
        vm.onAction(OnboardingAction.OnDayToggle(DayOfWeek.THURSDAY))
        vm.onAction(OnboardingAction.OnContinue())
        vm.onAction(OnboardingAction.OnContinue(bodyWeightText = "72,5"))
        vm.onAction(OnboardingAction.OnAskToggle(false))
        vm.onAction(OnboardingAction.OnContinue())

        assertEquals(
            UserSettings(72.5, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), askAboutUnloggedDays = false, onboardingDone = true),
            settings.settings.value,
        )
        assertEquals(Unit, vm.onFinished.first())
    }

    @Test
    fun `skipping a step saves nothing for it`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(OnboardingAction.OnContinue())
        vm.onAction(OnboardingAction.OnDayToggle(DayOfWeek.MONDAY))

        vm.onAction(OnboardingAction.OnSkipStep)

        assertEquals(OnboardingStep.BodyWeight, vm.state.value.step)
        assertTrue(settings.settings.value.trainingDays.isEmpty())
    }

    @Test
    fun `an implausible weight stays on the step with a message, an empty one skips`() = runTest(dispatcher) {
        val vm = viewModel(SavedStateHandle(mapOf("step" to OnboardingStep.BodyWeight.name)))

        vm.onAction(OnboardingAction.OnContinue(bodyWeightText = "7"))
        assertEquals(OnboardingStep.BodyWeight, vm.state.value.step)
        assertTrue(vm.state.value.isBodyWeightInvalid)
        assertNull(settings.settings.value.bodyWeightKg)

        vm.onAction(OnboardingAction.OnBodyWeightEdited)
        assertFalse(vm.state.value.isBodyWeightInvalid)

        vm.onAction(OnboardingAction.OnContinue(bodyWeightText = ""))
        assertEquals(OnboardingStep.CheckIns, vm.state.value.step)
    }

    @Test
    fun `skip setup finishes right away`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(OnboardingAction.OnSkipAll)

        assertTrue(settings.settings.value.onboardingDone)
        assertEquals(Unit, vm.onFinished.first())
    }

    @Test
    fun `back goes to the previous step, and the step survives the app being killed`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.onAction(OnboardingAction.OnContinue())
        vm.onAction(OnboardingAction.OnContinue())
        assertEquals(OnboardingStep.BodyWeight, viewModel(handle).state.value.step)

        vm.onAction(OnboardingAction.OnBack)
        assertEquals(OnboardingStep.TrainingDays, vm.state.value.step)
    }

    @Test
    fun `the app starts on onboarding until it's done, then on Home`() = runTest(dispatcher) {
        assertEquals(Route.Onboarding, MainViewModel(FakeUserSettingsRepository()).startDestination.value)
        assertEquals(Route.Home, MainViewModel(FakeUserSettingsRepository(UserSettings(onboardingDone = true))).startDestination.value)
    }
}
