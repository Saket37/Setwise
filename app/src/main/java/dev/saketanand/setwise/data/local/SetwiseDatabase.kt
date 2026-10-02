package dev.saketanand.setwise.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.local.dao.TemplateDao
import dev.saketanand.setwise.data.local.dao.WorkoutDao
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.TemplateEntity
import dev.saketanand.setwise.data.local.entity.TemplateExerciseEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity

/**
 * The single instance is created by Koin (Room.databaseBuilder in AppModule), so there is
 * no companion-object singleton here.
 *
 * Schema changes: until the first release the version stays at 1. Edit the entities, then
 * uninstall the app (or clear its data) and rebuild; app/schemas/.../1.json is regenerated.
 * After release: bump [version] and add a Migration, checked against the exported schemas.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        SetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        TemplateEntity::class,
        TemplateExerciseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SetwiseDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun templateDao(): TemplateDao
}
