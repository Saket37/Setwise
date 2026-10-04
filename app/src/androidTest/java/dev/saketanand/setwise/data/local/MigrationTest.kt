package dev.saketanand.setwise.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.saketanand.setwise.data.repository.TemplateRepositoryImpl
import dev.saketanand.setwise.data.repository.WorkoutRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each schema version migrates to the next with what's already logged kept: a database as an
 * older app left it (or as Android restores it from a backup) opens in the current app.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SetwiseDatabase::class.java)

    @Test
    fun version1ToVersion2KeepsWorkoutsAndTemplates() = runTest {
        helper.createDatabase(NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO exercises (id, name, type, muscleGroup, equipment, defaultRestSec, isTimed, isCustom) " +
                    "VALUES (1, 'Back Squat (Barbell)', 'STRENGTH', 'Quads', 'Barbell', 120, 0, 0)",
            )
            db.execSQL("INSERT INTO templates (id, name, category, createdAt) VALUES (1, 'Legs', 'Legs', 1000)")
            db.execSQL("INSERT INTO template_exercises (id, templateId, exerciseId, position, targetSets) VALUES (1, 1, 1, 0, 4)")
            db.execSQL("INSERT INTO workouts (id, name, templateId, startedAt, endedAt) VALUES (1, 'Legs', 1, 2000, 5000)")
            db.execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position) VALUES (1, 1, 1, 0)")
            db.execSQL(
                "INSERT INTO sets (id, workoutExerciseId, setNumber, weightKg, reps, isCompleted, isPr) VALUES (1, 1, 1, 100.0, 5, 1, 0)",
            )
        }

        // Migrates, and checks the result matches the version 2 schema exactly.
        helper.runMigrationsAndValidate(NAME, 2, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, SetwiseDatabase::class.java, NAME).build()
        try {
            val template = TemplateRepositoryImpl(database, database.templateDao(), database.workoutDao()).getTemplate(1)!!
            assertEquals(4, template.exercises.single().targetSets)
            assertNull(template.exercises.single().targetReps) // new column, empty

            val session = WorkoutRepositoryImpl(database, database.workoutDao(), database.templateDao()).observeSession(1).first()!!
            assertEquals("Legs", session.name)
            val set = session.exercises.single().sets.single()
            assertEquals(100.0, set.weightKg!!, 0.0)
            assertEquals(5, set.reps)
            assertNull(session.exercises.single().targetReps)
        } finally {
            database.close()
            context.deleteDatabase(NAME)
        }
    }

    private companion object {
        const val NAME = "migration-test"
    }
}
