package com.example.campushub.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Lightweight SQLite-backed repository for calendar events, using Android's built-in
 * SQLiteOpenHelper to avoid KSP/kapt plugin overhead while keeping ACID persistence.
 */
class CalendarRepository(context: Context) {

  private val dbHelper = CalendarDatabaseHelper(context.applicationContext)
  private val writeLock = Any()

  private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
  val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

  init {
    _events.value = queryAllEventsSync()
  }

  suspend fun addEvent(event: CalendarEvent) =
    withContext(Dispatchers.IO) {
      synchronized(writeLock) {
        val db = dbHelper.writableDatabase
        db.insert(TABLE_EVENTS, null, event.toContentValues())
        _events.value = queryAllEventsSync()
      }
    }

  suspend fun updateEvent(event: CalendarEvent) =
    withContext(Dispatchers.IO) {
      synchronized(writeLock) {
        val db = dbHelper.writableDatabase
        db.update(TABLE_EVENTS, event.toContentValues(), "$COL_ID = ?", arrayOf(event.id.toString()))
        _events.value = queryAllEventsSync()
      }
    }

  suspend fun toggleCompleted(eventId: Long) =
    withContext(Dispatchers.IO) {
      synchronized(writeLock) {
        val current = _events.value.firstOrNull { it.id == eventId } ?: return@withContext
        val db = dbHelper.writableDatabase
        val values =
          ContentValues().apply { put(COL_IS_COMPLETED, if (current.isCompleted) 0 else 1) }
        db.update(TABLE_EVENTS, values, "$COL_ID = ?", arrayOf(eventId.toString()))
        _events.value = queryAllEventsSync()
      }
    }

  suspend fun deleteEvent(eventId: Long) =
    withContext(Dispatchers.IO) {
      synchronized(writeLock) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_EVENTS, "$COL_ID = ?", arrayOf(eventId.toString()))
        _events.value = queryAllEventsSync()
      }
    }

  /**
   * Inserts or updates Canvas DDL items matched by externalId, preserving local completion state
   * if the user already checked the task off manually.
   */
  suspend fun upsertCanvasEvents(items: List<CalendarEvent>): Int =
    withContext(Dispatchers.IO) {
      if (items.isEmpty()) return@withContext 0
      synchronized(writeLock) {
        val db = dbHelper.writableDatabase
        var syncedCount = 0
        db.beginTransaction()
        try {
          for (item in items) {
            val extId = item.externalId ?: continue
            val existingCompleted = queryCompletedByExternalId(db, extId)
            if (existingCompleted == null) {
              db.insert(TABLE_EVENTS, null, item.toContentValues())
            } else {
              val merged = item.copy(isCompleted = existingCompleted || item.isCompleted)
              db.update(TABLE_EVENTS, merged.toContentValues(), "$COL_EXTERNAL_ID = ?", arrayOf(extId))
            }
            syncedCount++
          }
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
        _events.value = queryAllEventsSync()
        syncedCount
      }
    }

  private fun queryCompletedByExternalId(db: SQLiteDatabase, externalId: String): Boolean? {
    db.query(
      TABLE_EVENTS,
      arrayOf(COL_IS_COMPLETED),
      "$COL_EXTERNAL_ID = ?",
      arrayOf(externalId),
      null,
      null,
      null,
    ).use { cursor ->
      return if (cursor.moveToFirst()) cursor.getInt(0) == 1 else null
    }
  }

  private fun queryAllEventsSync(): List<CalendarEvent> {
    val db = dbHelper.readableDatabase
    val list = mutableListOf<CalendarEvent>()
    db.query(
      TABLE_EVENTS,
      null,
      null,
      null,
      null,
      null,
      "$COL_DATE_EPOCH_DAY ASC, $COL_IS_COMPLETED ASC, $COL_ID ASC",
    ).use { cursor ->
      val idxId = cursor.getColumnIndexOrThrow(COL_ID)
      val idxTitle = cursor.getColumnIndexOrThrow(COL_TITLE)
      val idxEpoch = cursor.getColumnIndexOrThrow(COL_DATE_EPOCH_DAY)
      val idxTime = cursor.getColumnIndexOrThrow(COL_TIME_RANGE)
      val idxNote = cursor.getColumnIndexOrThrow(COL_NOTE)
      val idxCat = cursor.getColumnIndexOrThrow(COL_CATEGORY)
      val idxDone = cursor.getColumnIndexOrThrow(COL_IS_COMPLETED)
      val idxSrc = cursor.getColumnIndexOrThrow(COL_SOURCE)
      val idxExt = cursor.getColumnIndexOrThrow(COL_EXTERNAL_ID)

      while (cursor.moveToNext()) {
        list.add(
          CalendarEvent(
            id = cursor.getLong(idxId),
            title = cursor.getString(idxTitle),
            dateEpochDay = cursor.getLong(idxEpoch),
            timeRange = cursor.getString(idxTime),
            note = cursor.getString(idxNote),
            category = EventCategory.fromNameOrDefault(cursor.getString(idxCat)),
            isCompleted = cursor.getInt(idxDone) == 1,
            source = EventSource.fromNameOrDefault(cursor.getString(idxSrc)),
            externalId = if (cursor.isNull(idxExt)) null else cursor.getString(idxExt),
          )
        )
      }
    }
    return list
  }

  private fun CalendarEvent.toContentValues(): ContentValues =
    ContentValues().apply {
      put(COL_TITLE, title)
      put(COL_DATE_EPOCH_DAY, dateEpochDay)
      put(COL_TIME_RANGE, timeRange)
      put(COL_NOTE, note)
      put(COL_CATEGORY, category.name)
      put(COL_IS_COMPLETED, if (isCompleted) 1 else 0)
      put(COL_SOURCE, source.name)
      if (externalId != null) {
        put(COL_EXTERNAL_ID, externalId)
      } else {
        putNull(COL_EXTERNAL_ID)
      }
    }

  private class CalendarDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE $TABLE_EVENTS (
          $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $COL_TITLE TEXT NOT NULL,
          $COL_DATE_EPOCH_DAY INTEGER NOT NULL,
          $COL_TIME_RANGE TEXT NOT NULL DEFAULT '',
          $COL_NOTE TEXT NOT NULL DEFAULT '',
          $COL_CATEGORY TEXT NOT NULL,
          $COL_IS_COMPLETED INTEGER NOT NULL DEFAULT 0,
          $COL_SOURCE TEXT NOT NULL DEFAULT 'LOCAL',
          $COL_EXTERNAL_ID TEXT UNIQUE
        )
        """.trimIndent()
      )
      db.execSQL("CREATE INDEX idx_events_epoch ON $TABLE_EVENTS ($COL_DATE_EPOCH_DAY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
  }

  private companion object {
    const val DB_NAME = "campushub.db"
    const val DB_VERSION = 1
    const val TABLE_EVENTS = "calendar_events"
    const val COL_ID = "id"
    const val COL_TITLE = "title"
    const val COL_DATE_EPOCH_DAY = "date_epoch_day"
    const val COL_TIME_RANGE = "time_range"
    const val COL_NOTE = "note"
    const val COL_CATEGORY = "category"
    const val COL_IS_COMPLETED = "is_completed"
    const val COL_SOURCE = "source"
    const val COL_EXTERNAL_ID = "external_id"
  }
}
