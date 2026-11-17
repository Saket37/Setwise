package dev.saketanand.setwise.ui.body

import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.ProgressMeasure
import dev.saketanand.setwise.domain.model.ProgressRange
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Made-up weights. */
class BodyProgressViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate.of(2026, 10, 6)
    private val checks = listOf(
        BodyMeasurement(4, today, weightKg = 78.4),
        BodyMeasurement(3, today.minusDays(40), weightKg = 79.6),
        BodyMeasurement(2, today.minusDays(150), weightKg = 80.6),
        BodyMeasurement(1, today.minusDays(300), weightKg = 82.1),
    )

    @Test
    fun `the range picks which checks count`() = runTest(dispatcher) {
        val vm = BodyProgressViewModel(Body(checks), Today(today))
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        fun weight() = vm.state.value.measures.single { it.measure == ProgressMeasure.Weight }
        assertEquals(ProgressRange.ThreeMonths, vm.state.value.range)
        assertEquals(-1.2, weight().change!!, 1e-9) // 79.6 → 78.4
        assertEquals(2, weight().points.size)

        vm.onRangeChange(ProgressRange.All)
        advanceUntilIdle()
        assertEquals(-3.7, weight().change!!, 1e-9) // 82.1 → 78.4
        assertEquals(4, weight().points.size)
    }

    private class Body(private val checks: List<BodyMeasurement>) : BodyRepository {
        override fun observeMeasurements(): Flow<List<BodyMeasurement>> = flowOf(checks)
        override suspend fun add(measurement: BodyMeasurement) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    private class Today(private val day: LocalDate) : DateProvider {
        override val zone: ZoneId = ZoneId.of("UTC")
        override fun now(): Instant = day.atTime(9, 0).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(day)
    }
}
