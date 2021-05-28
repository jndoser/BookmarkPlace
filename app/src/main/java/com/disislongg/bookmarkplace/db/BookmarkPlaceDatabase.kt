package com.disislongg.bookmarkplace.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.disislongg.bookmarkplace.model.Bookmark

@Database(entities = arrayOf(Bookmark::class), version =3)
abstract class BookmarkPlaceDatabase: RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private var instance: BookmarkPlaceDatabase? = null
        fun getInstance(context: Context): BookmarkPlaceDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookmarkPlaceDatabase::class.java,
                    "BookmarkPlace"
                ).fallbackToDestructiveMigration().build()
            }
            return instance as BookmarkPlaceDatabase
        }
    }
}