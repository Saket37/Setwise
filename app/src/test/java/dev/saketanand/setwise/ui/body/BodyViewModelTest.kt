package dev.saketanand.setwise.ui.body

import dev.saketanand.setwise.domain.ai.BodyReportReader
import dev.saketanand.setwise.domain.ai.TextReader
import dev.saketanand.setwise.domain.model.BmrEstimate
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.OcrLine
import dev.saketanand.setwise.domain.model.Sex
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BodyViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeUserSettingsRepository()
    private val body = FakeBody()
    private var ocr: List<OcrLine> = emptyList()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = BodyViewModel(
        body, settings, BodyReportReader(object : TextReader { override suspend fun read(uri: String) = ocr }, FakeOnDeviceModel()), Today,
    ).also { vm -> backgroundScope.launch { vm.state.collect {} } }

    @Test
    fun `a scanned report is checked, saved, and fills the empty profile`() = runTest(dispatcher) {
        ocr = listOf(
            line("InBody170 24/07/26 09:05", 0), line("Gender: Male  Age :26", 1), line("Height : 169.0 cm", 2),
            line("Weight 78.6 kg (53.4~72.2)", 3), line("PBF 31.6 %", 4), line("BMR 1531 kcal", 5),
        )
        val vm = viewModel()

        vm.onAction(BodyAction.OnReportPhoto("content://report"))
        val editor = vm.state.value.editor!!
        assertEquals(LocalDate.of(2026, 7, 24), editor.day)
        assertEquals(78.6, editor.weightKg)
        assertTrue(editor.fromReport)
        assertTrue(body.saved.isEmpty()) // nothing until checked

        vm.onAction(BodyAction.OnSave("78.6", "31.6", "", "1531", ""))

        val saved = body.saved.single()
        assertEquals(BodyMeasurement.Source.Report, saved.source)
        assertEquals(1531, saved.bmrKcal)
        assertEquals(BmrEstimate(1531, BmrEstimate.Source.Report), vm.state.value.bmr)
        assertEquals(169.0, settings.settings.value.heightCm)
        assertEquals(Sex.Male, settings.settings.value.sex)
        assertNull(vm.state.value.editor)
    }

    @Test
    fun `an unbelievable value keeps the sheet open, and a photo with no report says so`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(BodyAction.OnAddManually)
        vm.onAction(BodyAction.OnSave("78.6", "92", "", "", ""))
        assertTrue(vm.state.value.editor!!.isInvalid)
        assertTrue(body.saved.isEmpty())

        vm.onAction(BodyAction.OnDismissEditor)
        ocr = listOf(line("Grocery list: eggs, milk", 0))
        vm.onAction(BodyAction.OnReportPhoto("content://not-a-report"))
        assertTrue(vm.state.value.readFailed)
    }

    private fun line(text: String, row: Int) = OcrLine(text, 20, row * 40, 20 + text.length * 12, row * 40 + 30)

    private class FakeBody : BodyRepository {
        val saved = mutableListOf<BodyMeasurement>()
        private val all = MutableStateFlow<List<BodyMeasurement>>(emptyList())
        override fun observeMeasurements(): Flow<List<BodyMeasurement>> = all
        override suspend fun add(measurement: BodyMeasurement) {
            saved += measurement
            all.update { listOf(measurement) + it }
        }
        override suspend fun delete(id: Long) = all.update { list -> list.filter { it.id != id } }
    }

    private object Today : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 5).atTime(9, 0).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 5))
    }
}
