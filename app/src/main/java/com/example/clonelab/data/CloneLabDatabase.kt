package com.example.clonelab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ClonedApp::class], version = 1, exportSchema = false)
abstract class CloneLabDatabase : RoomDatabase() {

    abstract fun clonedAppDao(): ClonedAppDao

    companion object {
        @Volatile
        private var INSTANCE: CloneLabDatabase? = null

        fun getInstance(context: Context): CloneLabDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CloneLabDatabase::class.java,
                    "clonelab_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
