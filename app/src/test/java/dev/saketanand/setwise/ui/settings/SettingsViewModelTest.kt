package dev.saketanand.setwise.ui.settings

import dev.saketanand.setwise.domain.ai.DownloadFailure
import dev.saketanand.setwise.domain.ai.DownloadState
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import dev.saketanand.setwise.domain.ai.ModelDownloader
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.Sex
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val model = FakeOnDeviceModel(ModelAvailability.Downloadable)

    private fun TestScope.viewModel() = SettingsViewModel(settings, model, ModelDownloader(model, backgroundScope), FixedDates, NoBody).also { vm ->
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

    @Test
    fun `a closed weekly summary can be shown again`() = runTest(dispatcher) {
        settings.settings.value = settings.settings.value.copy(weeklySummaryDismissedWeek = LocalDate.of(2026, 9, 28))
        val vm = viewModel()
        assertTrue(vm.state.value.isWeeklySummaryClosed)

        vm.onAction(SettingsAction.OnShowWeeklySummary)

        assertEquals(null, settings.settings.value.weeklySummaryDismissedWeek)
        assertEquals(false, vm.state.value.isWeeklySummaryClosed)
    }

    /** Today: Mon 5 Oct 2026, so last week starts 28 Sep. */
    @Test
    fun `profile values are saved, implausible ones keep their dialog open`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(SettingsAction.OnNameClick)
        assertEquals(SettingsEditor.Name, vm.state.value.editor)
        vm.onAction(SettingsAction.OnSaveName("  Sam "))

        vm.onAction(SettingsAction.OnAgeClick)
        vm.onAction(SettingsAction.OnSaveAge("abc"))
        assertEquals(SettingsEditor.Age(isInvalid = true), vm.state.value.editor)
        vm.onAction(SettingsAction.OnSaveAge("7"))
        assertEquals(SettingsEditor.Age(isInvalid = true), vm.state.value.editor)
        vm.onAction(SettingsAction.OnSaveAge(" 34 "))

        vm.onAction(SettingsAction.OnSexClick)
        assertEquals(SettingsEditor.SexChoice, vm.state.value.editor)
        vm.onAction(SettingsAction.OnSaveSex(Sex.Male))

        vm.onAction(SettingsAction.OnHeightClick)
        vm.onAction(SettingsAction.OnSaveHeight("20"))
        assertEquals(SettingsEditor.Height(isInvalid = true), vm.state.value.editor)
        vm.onAction(SettingsAction.OnSaveHeight("172,5"))

        val state = vm.state.value
        assertNull(state.editor)
        assertEquals("Sam", state.name)
        assertEquals(34, state.age)
        assertEquals(Sex.Male, state.sex)
        assertEquals(172.5, state.heightCm)
    }

    @Test
    fun `each profile value can be removed`() = runTest(dispatcher) {
        settings.settings.value = UserSettings(name = "Sam", birthYear = 1992, sex = Sex.Female, heightCm = 165.0)
        val vm = viewModel()

        listOf(SettingsEditor.Name, SettingsEditor.Age(), SettingsEditor.SexChoice, SettingsEditor.Height()).forEach {
            vm.onAction(SettingsAction.OnRemoveProfileValue(it))
        }
        vm.onAction(SettingsAction.OnRemoveProfileValue(SettingsEditor.DefaultRest)) // not a profile value: nothing

        val state = vm.state.value
        assertEquals(listOf(null, null, null, null), listOf(state.name, state.age, state.sex, state.heightCm))
        assertNull(state.editor)
    }

    @Test
    fun `rest timer settings are saved`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(SettingsAction.OnDefaultRestClick)
        assertEquals(SettingsEditor.DefaultRest, vm.state.value.editor)
        vm.onAction(SettingsAction.OnSaveDefaultRest(90))
        vm.onAction(SettingsAction.OnRestSoundChange(false))
        vm.onAction(SettingsAction.OnRestVibrateChange(false))
        vm.onAction(SettingsAction.OnWorkoutNotificationChange(false))

        val state = vm.state.value
        assertNull(state.editor)
        assertEquals(90, state.restSecOverride)
        assertEquals(listOf(false, false, false), listOf(state.restSound, state.restVibrate, state.workoutNotification))

        vm.onAction(SettingsAction.OnSaveDefaultRest(null)) // back to each exercise's own
        assertNull(vm.state.value.restSecOverride)
    }

    @Test
    fun `a day toggled with no days dialog open changes nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(SettingsAction.OnDayToggle(DayOfWeek.MONDAY))
        vm.onAction(SettingsAction.OnSaveTrainingDays)
        assertNull(vm.state.value.editor)
        assertTrue(settings.settings.value.trainingDays.isEmpty())
    }

    @Test
    fun `settings that fail to load show the screen, not a spinner`() = runTest(dispatcher) {
        val broken = object : UserSettingsRepository by FakeUserSettingsRepository() {
            override val settings: Flow<UserSettings> = flow { error("Unreadable DataStore") }
        }
        val vm = SettingsViewModel(broken, model, ModelDownloader(model, backgroundScope), FixedDates, NoBody)
        backgroundScope.launch { vm.state.collect {} }

        assertFalse(vm.state.value.isLoading)
    }

    private object FixedDates : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 5).atTime(9, 0).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 5))
    }

    private object NoBody : BodyRepository {
        override fun observeMeasurements(): Flow<List<BodyMeasurement>> = flowOf(emptyList())
        override suspend fun add(measurement: BodyMeasurement) = Unit
        override suspend fun delete(id: Long) = Unit
    }
}
