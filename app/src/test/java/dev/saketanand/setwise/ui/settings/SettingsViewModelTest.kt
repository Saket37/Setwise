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
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.domain.ai.DownloadFailure
import dev.saketanand.setwise.domain.ai.DownloadState
import dev.saketanand.setwise.domain.ai.ModelDownload
import dev.saketanand.setwise.domain.ai.ModelDownloader

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val model = FakeOnDeviceModel(ModelAvailability.Downloadable)

    private fun TestScope.viewModel() = SettingsViewModel(settings, model, ModelDownloader(model, backgroundScope)).also { vm ->
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
    fun `the AI row shows the model's state, and downloading it ends with Ready`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(ModelAvailability.Downloadable, vm.state.value.ai.availability)
        assertEquals(true, vm.state.value.ai.canDownload)

        model.availability = ModelAvailability.Ready // what AICore reports once it's done
        vm.onAction(SettingsAction.OnDownloadModelClick)

        assertEquals(ModelAvailability.Ready, vm.state.value.ai.availability)
        assertEquals(DownloadState.Done, vm.state.value.ai.download)
        assertEquals(false, vm.state.value.ai.canDownload)
    }

    @Test
    fun `a failed download says why and can be tried again`() = runTest(dispatcher) {
        model.downloadSteps = listOf(ModelDownload.Failed("disk full", DownloadFailure.NotEnoughSpace))
        val vm = viewModel()

        vm.onAction(SettingsAction.OnDownloadModelClick)

        assertEquals(DownloadState.Failed(DownloadFailure.NotEnoughSpace), vm.state.value.ai.download)
        assertEquals(true, vm.state.value.ai.canDownload)
    }

    @Test
    fun `coming back to the screen checks the model again`() = runTest(dispatcher) {
        val vm = viewModel()
        model.availability = ModelAvailability.Ready // finished in the background meanwhile

        vm.onAction(SettingsAction.OnScreenResumed)

        assertEquals(ModelAvailability.Ready, vm.state.value.ai.availability)
    }

    @Test
    fun `a phone without the model can't download it`() = runTest(dispatcher) {
        model.availability = ModelAvailability.Unavailable
        val vm = viewModel()

        vm.onAction(SettingsAction.OnDownloadModelClick)

        assertEquals(ModelAvailability.Unavailable, vm.state.value.ai.availability)
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
