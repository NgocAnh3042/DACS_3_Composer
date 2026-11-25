package com.example.dacs_3_composer.data.cache

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.dacs_3_composer.MainApplication

/** Small durable JSON cache using Android's built-in SQLite API (no extra dependency). */
class LocalJsonCache(context: Context) : SQLiteOpenHelper(context, "foodhub_cache.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cached_json(cache_key TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL, saved_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun read(key: String): Entry? = readableDatabase.rawQuery(
        "SELECT payload, saved_at FROM cached_json WHERE cache_key = ?", arrayOf(key)
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else Entry(cursor.getString(0), cursor.getLong(1))
    }

    fun write(key: String, payload: String, savedAt: Long) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO cached_json(cache_key, payload, saved_at) VALUES(?, ?, ?)",
            arrayOf<Any?>(key, payload, savedAt)
        )
    }

    data class Entry(val payload: String, val savedAt: Long)

    companion object {
        @Volatile private var instance: LocalJsonCache? = null
        fun get(): LocalJsonCache = instance ?: synchronized(this) {
            instance ?: LocalJsonCache(MainApplication.instance).also { instance = it }
        }
    }
}
