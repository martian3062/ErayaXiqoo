package com.evolet.tachyon.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Session::class, Commitment::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun commitmentDao(): CommitmentDao

    companion object {
        fun build(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "tachyon.db")
                // Hackathon build: schema changes wipe local test data instead of migrating.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
