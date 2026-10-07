package dev.saketanand.setwise.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.saketanand.setwise.domain.model.Sex
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WeeklyRecap
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs against a real DataStore file in a temp folder (plain JVM, no Android needed). */
class DataStoreUserSettingsRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    private fun newRepository() = DataStoreUserSettingsRepository(
        PreferenceDataStoreFactory.create(produceFile = { File(folder.root, "test.preferences_pb") }),
    )

    @Test
    fun `defaults before anything is set`() = runTest {
        assertEquals(UserSettings(), newRepository().settings.first())
    }

    @Test
    fun `None is kept apart from not set, and days given turn it off`() = runTest {
        val repository = newRepository()
        assertEquals(false, repository.settings.first().noFixedTrainingDays) // not set

        repository.setTrainingDays(emptySet(), noFixedDays = true)
        assertTrue(repository.settings.first().noFixedTrainingDays)

        repository.setTrainingDays(setOf(DayOfWeek.MONDAY))
        assertEquals(false, repository.settings.first().noFixedTrainingDays)
        assertEquals(setOf(DayOfWeek.MONDAY), repository.settings.first().trainingDays)
    }

    @Test
    fun `settings are saved and read back`() = runTest {
        val repository = newRepository()

        assertTrue(repository.setBodyWeightKg(72.5))
        repository.setTrainingDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        repository.setAskAboutUnloggedDays(false)
        repository.setOnboardingDone()

        assertEquals(
            UserSettings(72.5, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), askAboutUnloggedDays = false, onboardingDone = true),
            repository.settings.first(),
        )
    }

    @Test
    fun `an implausible body weight is rejected, null clears it`() = runTest {
        val repository = newRepository()
        repository.setBodyWeightKg(70.0)

        assertFalse(repository.setBodyWeightKg(7.0))
        assertEquals(70.0, repository.settings.first().bodyWeightKg)

        repository.setBodyWeightKg(null)
        assertNull(repository.settings.first().bodyWeightKg)
    }

    @Test
    fun `the profile is saved, with implausible values rejected`() = runTest {
        val repository = newRepository()
        val today = LocalDate.of(2026, 10, 5)

        repository.setName("  Sam  ")
        assertTrue(repository.setAge(34, today))
        assertFalse(repository.setAge(7, today))
        repository.setSex(Sex.Female)
        assertTrue(repository.setHeightCm(172.0))
        assertFalse(repository.setHeightCm(20.0))

        val saved = repository.settings.first()
        assertEquals("Sam", saved.name)
        assertEquals(1992, saved.birthYear)
        assertEquals(Sex.Female, saved.sex)
        assertEquals(172.0, saved.heightCm)

        repository.setName("x".repeat(60))
        assertEquals(40, repository.settings.first().name?.length)

        // Blank or null clears each.
        repository.setName("   ")
        repository.setAge(null, today)
        repository.setSex(null)
        repository.setHeightCm(null)
        val cleared = repository.settings.first()
        assertEquals(listOf(null, null, null, null), listOf(cleared.name, cleared.birthYear, cleared.sex, cleared.heightCm))
    }

    @Test
    fun `rest timer settings default on, and are saved`() = runTest {
        val repository = newRepository()
        val defaults = repository.settings.first()
        assertEquals(listOf(true, true, true), listOf(defaults.restSound, defaults.restVibrate, defaults.workoutNotification))
        assertNull(defaults.restSecOverride)

        repository.setRestSecOverride(90)
        repository.setRestSound(false)
        repository.setRestVibrate(false)
        repository.setWorkoutNotification(false)

        val saved = repository.settings.first()
        assertEquals(90, saved.restSecOverride)
        assertEquals(listOf(false, false, false), listOf(saved.restSound, saved.restVibrate, saved.workoutNotification))

        repository.setRestSecOverride(null)
        assertNull(repository.settings.first().restSecOverride)
    }

    @Test
    fun `check-in and weekly summary days are saved`() = runTest {
        val repository = newRepository()
        val monday = LocalDate.of(2026, 9, 28)

        repository.setCheckInLastAskedOn(monday.plusDays(6))
        repository.setWeeklySummaryDismissed(monday)
        repository.setWeeklyRecap(WeeklyRecap(monday, "3 workouts, 12,400 kg."))

        val saved = repository.settings.first()
        assertEquals(monday.plusDays(6), saved.checkInLastAskedOn)
        assertEquals(monday, saved.weeklySummaryDismissedWeek)
        assertEquals(WeeklyRecap(monday, "3 workouts, 12,400 kg."), saved.weeklyRecap)

        repository.setWeeklySummaryDismissed(null)
        assertNull(repository.settings.first().weeklySummaryDismissedWeek)
    }

    @Test
    fun `stored names this build doesn't know are ignored`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(folder.root, "raw.preferences_pb") })
        dataStore.edit {
            it[stringSetPreferencesKey("training_days")] = setOf("MONDAY", "FUNDAY")
            it[stringPreferencesKey("sex")] = "Unknown"
        }

        val saved = DataStoreUserSettingsRepository(dataStore).settings.first()
        assertEquals(setOf(DayOfWeek.MONDAY), saved.trainingDays)
        assertNull(saved.sex)
    }
}
