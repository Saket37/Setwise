package dev.saketanand.setwise.data.seed

import android.content.Context
import android.util.Log
import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.mapper.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads the built-in exercise library from assets/exercises.json.
 *
 * Runs on first launch, and again whenever the file's "version" is higher than the version
 * last seeded (e.g. an app update added exercises). When you add or rename exercises in the
 * JSON, bump its "version", otherwise existing users won't get them. Renames run first, so a
 * renamed exercise keeps its id and history instead of being added again under the new name.
 */
class ExerciseSeeder(
    private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val seedPreferences: SeedPreferences,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun seedIfNeeded() {
        try {
            val seededVersion = seedPreferences.exerciseSeedVersion()

            val seedFile = withContext(ioDispatcher) {
                val text = context.assets.open(SEED_FILE).bufferedReader().use { it.readText() }
                json.decodeFromString<ExerciseSeedFile>(text)
            }
            if (seedFile.version <= seededVersion) return

            val renamed = seedFile.renames.sumOf { exerciseDao.renameBuiltIn(it.from, it.to) }
            // IGNORE + unique name index: existing exercises are skipped, only new ones inserted.
            val ids = exerciseDao.insertAll(seedFile.exercises.map { it.toEntity() })
            // Saved only after a successful insert, so a failed seed is retried next launch.
            seedPreferences.setExerciseSeedVersion(seedFile.version)

            val added = ids.count { it != -1L }
            Log.i(TAG, "Seed v$seededVersion -> v${seedFile.version}: renamed $renamed, added $added new exercises")
        } catch (e: Exception) {
            // Don't crash the app over the seed; the list stays empty and Logcat says why.
            Log.e(TAG, "Exercise seeding failed", e)
        }
    }

    private companion object {
        const val TAG = "ExerciseSeeder"
        const val SEED_FILE = "exercises.json"
    }
}
