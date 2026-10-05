package dev.saketanand.setwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.saketanand.setwise.data.repository.DayMarkRepositoryImpl
import dev.saketanand.setwise.domain.model.DayStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayMarkRepositoryTest {

    private lateinit var db: SetwiseDatabase
    private lateinit var repository: DayMarkRepositoryImpl
    private val friday = LocalDate.of(2026, 10, 2)
    private val thursday = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SetwiseDatabase::class.java).build()
        repository = DayMarkRepositoryImpl(db.dayMarkDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun marksAreSavedChangedAndRemoved() = runTest {
        repository.mark(listOf(friday, thursday), DayStatus.Rest, Instant.EPOCH)
        assertEquals(mapOf(friday to DayStatus.Rest, thursday to DayStatus.Rest), repository.observeMarks().first())

        repository.mark(listOf(friday), DayStatus.Missed, Instant.EPOCH)
        assertEquals(DayStatus.Missed, repository.observeMarks().first()[friday])

        repository.mark(listOf(friday), null, Instant.EPOCH)
        assertEquals(mapOf(thursday to DayStatus.Rest), repository.observeMarks().first())
    }
}
