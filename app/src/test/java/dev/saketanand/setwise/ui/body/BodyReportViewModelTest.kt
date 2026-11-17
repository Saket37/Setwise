package dev.saketanand.setwise.ui.body

import dev.saketanand.setwise.domain.ai.BodyTipsWriter
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.BodyTip
import dev.saketanand.setwise.domain.model.Rating
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentValues
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Made-up reports. */
class BodyReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun report(id: Long, day: LocalDate, fatKg: Double, leftLeg: Rating = Rating.Normal) = BodyMeasurement(
        id, day, weightKg = 78.0, muscleMassKg = 35.0, source = BodyMeasurement.Source.Report,
        details = ReportDetails(
            fatMassKg = fatKg,
            segments = listOf(SegmentValues(BodySegment.RightLeg, 9.4, Rating.Normal), SegmentValues(BodySegment.LeftLeg, 9.3, leftLeg)),
        ),
    )

    // Newest first, as the repository gives them: a typed-in weight sits between the two reports.
    private val body = FakeBody(
        listOf(
            report(3, LocalDate.of(2026, 10, 6), fatKg = 14.5, leftLeg = Rating.Under),
            BodyMeasurement(2, LocalDate.of(2026, 9, 29), weightKg = 78.8),
            report(1, LocalDate.of(2026, 9, 22), fatKg = 14.9),
        ),
    )
    private val model = FakeOnDeviceModel()

    @Test
    fun `tips come from the report and the report before it, worded by the screen without a model`() = runTest(dispatcher) {
        val vm = BodyReportViewModel(3, body, BodyTipsWriter(model))
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(BodyTip.Uneven(BodySegment.LeftLeg, BodySegment.RightLeg), state.tips.first())
        assertEquals(BodyTip.SinceLast(LocalDate.of(2026, 9, 22), fatKg = -0.4, muscleKg = 0.0), state.tips[1]) // not the typed-in weight
        assertNull(state.modelTips)
    }

    @Test
    fun `with the model, its sentences are used when every number is from the facts`() = runTest(dispatcher) {
        model.availability = ModelAvailability.Ready
        model.answer = { "Your left leg is rated Under, so add split squats or step-ups. Since 22 Sep your fat mass is down 0.4 kg, so keep going." }
        val vm = BodyReportViewModel(3, body, BodyTipsWriter(model))
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.state.value.modelTips?.size)
        assertEquals(1, model.requests.size) // asked once, not on every update

        vm.onDeleteClick()
        advanceUntilIdle()
        assertTrue(vm.state.value.isConfirmingDelete)
        assertEquals(1, model.requests.size)
    }

    @Test
    fun `a wording with a made-up number, or the wrong number of sentences, isn't used`() = runTest(dispatcher) {
        model.availability = ModelAvailability.Ready
        model.answer = { "Your left leg is 12% weaker. Since 22 Sep fat mass is down 0.4 kg." }
        val vm = BodyReportViewModel(3, body, BodyTipsWriter(model))
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertNull(vm.state.value.modelTips)
    }

    private class FakeBody(checks: List<BodyMeasurement>) : BodyRepository {
        private val all = MutableStateFlow(checks)
        override fun observeMeasurements(): Flow<List<BodyMeasurement>> = all
        override suspend fun add(measurement: BodyMeasurement) = all.update { listOf(measurement) + it }
        override suspend fun delete(id: Long) = all.update { list -> list.filter { it.id != id } }
    }
}
