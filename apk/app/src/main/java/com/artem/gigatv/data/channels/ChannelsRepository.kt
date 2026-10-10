package com.artem.gigatv.data.channels

import android.content.Context
import android.content.ContentValues
import android.media.tv.TvContract
import android.media.tv.TvContract.WatchNextPrograms
import android.os.Build
import android.provider.BaseColumns
import android.util.Log
import androidx.annotation.RequiresApi
import com.artem.gigatv.data.history.WatchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Synchronize our Watch Next rows without resetting IDs or user dismissals. */
class ChannelsRepository(private val context: Context) {
    private val mutex = Mutex()
    private val preferences = context.getSharedPreferences("watch_next_hidden", Context.MODE_PRIVATE)

    suspend fun sync(items: List<WatchItem>, encode: (WatchItem) -> String) =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext
            mutex.withLock {
                runCatching {
                    val rows = readOwn() ?: return@runCatching
                    val hidden = preferences.getStringSet("urls", emptySet()).orEmpty()
                    val plan = planWatchNext(rows, items, hidden)
                    if (plan.hiddenUrls != hidden) {
                        // Persist before deleting hidden rows, so the next sync cannot resurrect them.
                        check(preferences.edit().putStringSet("urls", plan.hiddenUrls).commit())
                    }
                    plan.deleteIds.forEach { id ->
                        context.contentResolver.delete(
                            TvContract.buildWatchNextProgramUri(id), null, null
                        )
                    }
                    plan.upserts.forEach { (id, item) -> upsert(id, item, encode(item)) }
                }.onFailure { Log.w("GigaTvChannels", "Не удалось обновить «Смотреть далее»", it) }
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun readOwn(): List<WatchNextRow>? {
        val projection = arrayOf(
            BaseColumns._ID,
            TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
            WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
            WatchNextPrograms.COLUMN_BROWSABLE
        )
        val cursor = context.contentResolver.query(
            WatchNextPrograms.CONTENT_URI, projection, null, null, null
        ) ?: return null
        return cursor.use { c ->
            val id = c.getColumnIndex(BaseColumns._ID)
            val pkg = c.getColumnIndex(TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME)
            val url = c.getColumnIndex(WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            val browsable = c.getColumnIndex(WatchNextPrograms.COLUMN_BROWSABLE)
            // Never guess ownership from an HTTP URL on an incompatible provider.
            if (id < 0 || pkg < 0 || url < 0 || browsable < 0) return@use null
            buildList {
                while (c.moveToNext()) {
                    if (c.getString(pkg) != context.packageName || c.isNull(url)) continue
                    add(WatchNextRow(c.getLong(id), c.getString(url),
                        !c.isNull(browsable) && c.getInt(browsable) == 0))
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun upsert(id: Long?, item: WatchItem, payload: String) {
        val values = ContentValues().apply {
            put(WatchNextPrograms.COLUMN_TYPE, WatchNextPrograms.TYPE_MOVIE)
            put(WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            put(WatchNextPrograms.COLUMN_TITLE, item.title)
            put(WatchNextPrograms.COLUMN_INTENT_URI, "gigatv://play/$payload")
            put(WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, item.url)
            put(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, item.updatedAt)
            put(WatchNextPrograms.COLUMN_POSTER_ART_URI, item.poster?.takeIf { it.isNotBlank() })
            put(WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, WatchNextPrograms.ASPECT_RATIO_2_3)
            put(WatchNextPrograms.COLUMN_DURATION_MILLIS, item.duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            put(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                item.position.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        }
        if (id == null) {
            context.contentResolver.insert(WatchNextPrograms.CONTENT_URI, values)
        } else {
            context.contentResolver.update(TvContract.buildWatchNextProgramUri(id), values, null, null)
        }
    }
}
