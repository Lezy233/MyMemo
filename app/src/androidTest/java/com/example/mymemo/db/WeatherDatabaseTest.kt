package com.example.mymemo.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * weather-and-location 数据库测试(任务 1.1 / 1.2):
 * v4→v5 升级保留旧数据并新建 weather_queries;查询记录插入/最近一条/倒序全量;
 * 记录不绑定账号(切换账号后缓存仍可用)。
 */
@RunWith(AndroidJUnit4::class)
class WeatherDatabaseTest {

    private lateinit var helper: AppDatabaseHelper

    private val username = "weather_tester"

    @Before
    fun setUp() {
        helper = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        cleanup()
    }

    @After
    fun tearDown() {
        cleanup()
    }

    private fun cleanup() {
        val db = helper.writableDatabase
        db.delete(AppDatabaseHelper.TABLE_WEATHER_QUERIES, null, null)
        db.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    @Test
    fun upgradeFromV4ToV5KeepsOldDataAndAddsWeatherTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tmp = File(context.cacheDir, "weather_migration_v5.db")
        tmp.delete()
        val legacy = SQLiteDatabase.openOrCreateDatabase(tmp, null)

        // 最小 v4 结构:users(含 v4 的 quota_credit)
        legacy.execSQL(
            "CREATE TABLE ${AppDatabaseHelper.TABLE_USERS} (" +
                "${AppDatabaseHelper.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "${AppDatabaseHelper.COLUMN_USERNAME} TEXT UNIQUE NOT NULL, " +
                "${AppDatabaseHelper.COLUMN_PASSWORD} TEXT NOT NULL, " +
                "${AppDatabaseHelper.COLUMN_AVATAR} TEXT NOT NULL, " +
                "${AppDatabaseHelper.COLUMN_DAILY_LIMIT} INTEGER NOT NULL DEFAULT 30, " +
                "${AppDatabaseHelper.COLUMN_QUOTA_CREDIT} INTEGER NOT NULL DEFAULT 0)"
        )
        legacy.execSQL(
            "INSERT INTO ${AppDatabaseHelper.TABLE_USERS} " +
                "(${AppDatabaseHelper.COLUMN_USERNAME}, ${AppDatabaseHelper.COLUMN_PASSWORD}, " +
                "${AppDatabaseHelper.COLUMN_AVATAR}, ${AppDatabaseHelper.COLUMN_QUOTA_CREDIT}) " +
                "VALUES ('legacy_v5', 'pw', 'avatar_1', 7)"
        )

        helper.onUpgrade(legacy, 4, 5)

        // 旧账号与其额度原样保留
        legacy.rawQuery(
            "SELECT ${AppDatabaseHelper.COLUMN_PASSWORD}, " +
                "${AppDatabaseHelper.COLUMN_QUOTA_CREDIT} FROM ${AppDatabaseHelper.TABLE_USERS}",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("pw", cursor.getString(0))
            assertEquals(7, cursor.getInt(1))
        }

        // weather_queries 已建立且为空
        legacy.rawQuery(
            "SELECT COUNT(*) FROM ${AppDatabaseHelper.TABLE_WEATHER_QUERIES}",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        legacy.close()
        tmp.delete()
    }

    @Test
    fun insertLatestAndAllReturnStoredQueries() {
        assertNull(helper.getLatestWeatherQuery())

        helper.insertWeatherQuery(31.19, 121.44, RAW_JSON_TODAY, queriedAt = 1_000L)
        helper.insertWeatherQuery(1.0, 2.0, RAW_JSON_OTHER, queriedAt = 2_000L)

        // 最近一条 = 时间最大的那条
        val latest = helper.getLatestWeatherQuery()!!
        assertEquals(1.0, latest.latitude, 0.0001)
        assertEquals(2.0, latest.longitude, 0.0001)
        assertEquals(2_000L, latest.queriedAt)
        assertEquals(RAW_JSON_OTHER, latest.result)

        // 全量按时间倒序
        val all = helper.getAllWeatherQueries()
        assertEquals(2, all.size)
        assertEquals(2_000L, all[0].queriedAt)
        assertEquals(1_000L, all[1].queriedAt)
        assertEquals(31.19, all[1].latitude, 0.0001)
        assertEquals(121.44, all[1].longitude, 0.0001)
    }

    @Test
    fun sameTimestampBreakByInsertOrderDesc() {
        helper.insertWeatherQuery(1.0, 1.0, "{\"n\":1}", queriedAt = 5_000L)
        helper.insertWeatherQuery(2.0, 2.0, "{\"n\":2}", queriedAt = 5_000L)

        val all = helper.getAllWeatherQueries()
        assertEquals(2, all.size)
        // 同一毫秒内后插入的排在前面,保证历史列表顺序稳定
        assertEquals("{\"n\":2}", all[0].result)
        assertEquals("{\"n\":1}", all[1].result)
    }

    @Test
    fun queriesAreDeviceLevelAndSurviveAccountSwitch() {
        helper.insertUser(username, "pw", "avatar_1")
        helper.insertWeatherQuery(31.19, 121.44, RAW_JSON_TODAY, queriedAt = 9_000L)

        // 切换账号(新建另一账号并删除原账号)后,设备级缓存仍可用
        helper.insertUser("weather_other", "pw", "avatar_2")
        helper.writableDatabase.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
        helper.writableDatabase.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf("weather_other")
        )

        assertTrue(helper.getAllWeatherQueries().isNotEmpty())
        assertEquals(RAW_JSON_TODAY, helper.getLatestWeatherQuery()!!.result)
    }

    @Test
    fun blankResultIsRejected() {
        assertFalse(helper.insertWeatherQuery(31.19, 121.44, "   "))
        assertNull(helper.getLatestWeatherQuery())
    }

    companion object {
        const val RAW_JSON_TODAY =
            "{\"daily\":{\"time\":[\"2026-01-02\"],\"weathercode\":[2]," +
                "\"temperature_2m_max\":[8.0],\"temperature_2m_min\":[2.0]}}"
        const val RAW_JSON_OTHER =
            "{\"daily\":{\"time\":[\"2026-01-03\"],\"weathercode\":[61]," +
                "\"temperature_2m_max\":[12.5],\"temperature_2m_min\":[6.5]}}"
    }
}
