package com.audionotes.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecordingEntity::class], version = 1, exportSchema = true)
abstract class AudioNotesDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var instance: AudioNotesDatabase? = null

        fun get(context: Context): AudioNotesDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AudioNotesDatabase::class.java,
                    "audionotes.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
