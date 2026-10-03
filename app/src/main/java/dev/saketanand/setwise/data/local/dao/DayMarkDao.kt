package dev.saketanand.setwise.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.saketanand.setwise.data.local.entity.DayMarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayMarkDao {

    /** Every mark; a few hundred rows at most (one per answered day). */
    @Query("SELECT * FROM day_marks ORDER BY epochDay")
    fun observeAll(): Flow<List<DayMarkEntity>>

    /** Adds the day's mark, or replaces it (the user changed their answer). */
    @Upsert
    suspend fun upsert(marks: List<DayMarkEntity>)

    @Query("DELETE FROM day_marks WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)
}
