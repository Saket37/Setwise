package dev.saketanand.setwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.repository.TemplateRepositoryImpl
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.model.TemplateDraftExercise
import dev.saketanand.setwise.domain.repository.TemplateRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Creating, editing and deleting templates, on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class TemplateRepositoryTest {

    private lateinit var db: SetwiseDatabase
    private lateinit var repository: TemplateRepository

    private var bench = 0L
    private var squat = 0L
    private var row = 0L

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SetwiseDatabase::class.java).build()
        repository = TemplateRepositoryImpl(db, db.templateDao(), db.workoutDao())
        bench = db.exerciseDao().insert(exercise("Bench Press"))
        squat = db.exerciseDao().insert(exercise("Back Squat"))
        row = db.exerciseDao().insert(exercise("Barbell Row"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aNewTemplateIsSavedWithItsExercisesInOrder() = runTest {
        val id = repository.saveTemplate(
            TemplateDraft(0, "  Strength A ", category = " ", exercises = listOf(TemplateDraftExercise(squat, 5), TemplateDraftExercise(bench, 3))),
            Instant.ofEpochMilli(1_000),
        )

        val template = repository.getTemplate(id)!!
        assertEquals("Strength A", template.name)
        assertNull(template.category) // blank = none
        assertEquals(listOf(squat to 5, bench to 3), template.exercises.map { it.exerciseId to it.targetSets })
        assertEquals("Chest", template.exercises.first().muscleGroup)
    }

    @Test
    fun savingAnExistingTemplateReplacesItsExercises() = runTest {
        val id = repository.saveTemplate(TemplateDraft(0, "Push", null, listOf(TemplateDraftExercise(bench, 3))), Instant.EPOCH)

        repository.saveTemplate(
            TemplateDraft(id, "Push Day", "Push", listOf(TemplateDraftExercise(row, 4), TemplateDraftExercise(bench, 2))),
            Instant.EPOCH,
        )

        val templates = repository.observeTemplates().first()
        assertEquals(1, templates.size)
        assertEquals("Push Day", templates.single().name)
        assertEquals("Push", templates.single().category)
        assertEquals(listOf(row to 4, bench to 2), templates.single().exercises.map { it.exerciseId to it.targetSets })
    }

    @Test
    fun deletingATemplateKeepsItsWorkoutsAsPlainWorkouts() = runTest {
        val id = repository.saveTemplate(TemplateDraft(0, "Legs", null, listOf(TemplateDraftExercise(squat, 3))), Instant.EPOCH)
        val workoutId = db.workoutDao().insertWorkout(WorkoutEntity(name = "Legs", templateId = id, startedAt = 1_000, endedAt = 2_000))

        repository.deleteTemplate(id)

        assertNull(repository.getTemplate(id))
        assertNull(db.workoutDao().getWorkoutWithExercises(workoutId)!!.workout.templateId)
    }

    private fun exercise(name: String) =
        ExerciseEntity(name = name, type = ExerciseType.STRENGTH, muscleGroup = "Chest", equipment = "Barbell", defaultRestSec = 90)
}
