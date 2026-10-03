package dev.saketanand.setwise.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.saketanand.setwise.domain.model.UserSettings
import java.io.File
import java.time.DayOfWeek
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
}
