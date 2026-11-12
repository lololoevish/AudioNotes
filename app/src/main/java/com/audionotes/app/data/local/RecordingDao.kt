package com.audionotes.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeById(id: String): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity)

    @Update
    suspend fun update(recording: RecordingEntity)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("UPDATE recordings SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)
}
