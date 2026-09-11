package com.example.mymemo.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * stats-and-game 数据库测试(任务 1.1 / 1.2 / 3.1):
 * v1→v4 全链路升级、不认识次数 upsert、学会时失败次数入库、四类统计分类边界、额度钱包存取。
 */
@RunWith(AndroidJUnit4::class)
class StatsDatabaseTest {

    private lateinit var helper: AppDatabaseHelper

    private val username = "stats_tester"

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
        db.delete(AppDatabaseHelper.TABLE_WORD_DAILY_FAILS, null, null)
        db.delete(AppDatabaseHelper.TABLE_STUDY_RECORDS, null, null)
        db.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    @Test
    fun upgradeFromV1ToV4KeepsOldDataAndAddsQuotaAndFailTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tmp = File(context.cacheDir, "stats_migration_v4.db")
        tmp.delete()
        val legacy = SQLiteDatabase.openOrCreateDatabase(tmp, null)
        legacy.execSQL(
            "CREATE TABLE ${AppDatabaseHelper.TABLE_USERS} (" +
                "${AppDatabaseHelper.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "${AppDatabaseHelper.COLUMN_USERNAME} TEXT UNIQUE NOT NULL, " +
                "${AppDatabaseHelper.COLUMN_PASSWORD} TEXT NOT NULL, " +
                "${AppDatabaseHelper.COLUMN_AVATAR} TEXT NOT NULL)"
        )
        legacy.execSQL(
            "INSERT INTO ${AppDatabaseHelper.TABLE_USERS} " +
                "(${AppDatabaseHelper.COLUMN_USERNAME}, ${AppDatabaseHelper.COLUMN_PASSWORD}, " +
                "${AppDatabaseHelper.COLUMN_AVATAR}) VALUES ('legacy_v4', 'pw', 'avatar_1')"
        )

        helper.onUpgrade(legacy, 1, 4)

        // 老账号保留,并获得默认上限与零额度
        legacy.rawQuery(
            "SELECT ${AppDatabaseHelper.COLUMN_PASSWORD}, " +
                "${AppDatabaseHelper.COLUMN_DAILY_LIMIT}, " +
                "${AppDatabaseHelper.COLUMN_QUOTA_CREDIT} FROM ${AppDatabaseHelper.TABLE_USERS}",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("pw", cursor.getString(0))
            assertEquals(AppDatabaseHelper.DEFAULT_DAILY_LIMIT, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }

        // study_records 新增 fail_count 列,可写入并读回
        legacy.execSQL(
            "INSERT INTO ${AppDatabaseHelper.TABLE_STUDY_RECORDS} " +
                "(${AppDatabaseHelper.COLUMN_USER_ID}, ${AppDatabaseHelper.COLUMN_WORD_ID}, " +
                "${AppDatabaseHelper.COLUMN_LEARNED_AT}, ${AppDatabaseHelper.COLUMN_FAIL_COUNT}) " +
                "VALUES (1, 1, 123, 2)"
        )
        legacy.rawQuery(
            "SELECT ${AppDatabaseHelper.COLUMN_FAIL_COUNT} FROM " +
                AppDatabaseHelper.TABLE_STUDY_RECORDS,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }

        // word_daily_fails 表已建立且为空
        legacy.rawQuery(
            "SELECT COUNT(*) FROM ${AppDatabaseHelper.TABLE_WORD_DAILY_FAILS}",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        legacy.close()
        tmp.delete()
    }

    @Test
    fun incrementWordFailCountUpsertsPerDayAndKeepsHistory() {
        helper.insertUser(username, "pw", "avatar_1")
        val userId = helper.findUserByUsername(username)!!.id
        val wordId = helper.getAllWords().first().id

        assertEquals(0, helper.getWordFailCount(userId, wordId, "2026-01-01"))
        assertEquals(1, helper.incrementWordFailCount(userId, wordId, "2026-01-01"))
        assertEquals(2, helper.incrementWordFailCount(userId, wordId, "2026-01-01"))
        assertEquals(2, helper.getWordFailCount(userId, wordId, "2026-01-01"))

        // 跨天从 1 重新计数,昨日记录保留
        assertEquals(1, helper.incrementWordFailCount(userId, wordId, "2026-01-02"))
        assertEquals(2, helper.getWordFailCount(userId, wordId, "2026-01-01"))
        assertEquals(1, helper.getWordFailCount(userId, wordId, "2026-01-02"))
    }

    @Test
    fun studyRecordStoresFailCountAndStatsClassifyFourWays() {
        helper.insertUser(username, "pw", "avatar_1")
        val userId = helper.findUserByUsername(username)!!.id
        helper.setDailyLimit(userId, 30)
        val words = helper.getAllWords()
        val totalWords = words.size

        // 绿:0 次失败即学会
        helper.insertStudyRecord(userId, words[0].id, failCount = 0)
        // 黄:恰好 1 次失败后学会
        helper.insertStudyRecord(userId, words[1].id, failCount = 1)
        // 红(最终学会):失败 2 次后学会
        helper.incrementWordFailCount(userId, words[2].id)
        helper.incrementWordFailCount(userId, words[2].id)
        helper.insertStudyRecord(userId, words[2].id, failCount = 2)
        // 红(未学会):失败 3 次,无学习记录
        repeat(3) { helper.incrementWordFailCount(userId, words[3].id) }

        val stats = helper.getTodayStats(userId)
        assertEquals(1, stats.green)
        assertEquals(1, stats.yellow)
        assertEquals("失败≥2 但最终学会也应归红", 2, stats.red)

        // 灰 = min(剩余额度, 未学会总数) - 红色中仍未学会的数量
        val remaining = 30 - 3
        val unlearned = totalWords - 3
        val expectedGray = minOf(remaining, unlearned) - 1
        assertEquals(expectedGray, stats.gray)
        assertEquals(1 + 1 + 2 + expectedGray, stats.total)
        assertFalse(stats.isEmpty)
    }

    @Test
    fun quotaCreditAddsDeductsAndExchangesAtomically() {
        helper.insertUser(username, "pw", "avatar_1")
        val userId = helper.findUserByUsername(username)!!.id

        assertEquals(0, helper.getQuotaCredit(userId))
        assertFalse("零额度不可加", helper.addQuotaCredit(userId, 0))
        assertTrue(helper.addQuotaCredit(userId, 5))
        assertEquals(5, helper.getQuotaCredit(userId))

        assertFalse("超出余额不可扣", helper.deductQuotaCredit(userId, 10))
        assertTrue(helper.deductQuotaCredit(userId, 2))
        assertEquals(3, helper.getQuotaCredit(userId))

        // 兑换:1 额度 = 上限 +1,同一事务内完成
        val limitBefore = helper.getDailyLimit(userId)
        assertFalse("非法数量不可兑换", helper.exchangeQuotaForLimit(userId, 0))
        assertFalse("超出余额不可兑换", helper.exchangeQuotaForLimit(userId, 5))
        assertTrue(helper.exchangeQuotaForLimit(userId, 2))
        assertEquals(1, helper.getQuotaCredit(userId))
        assertEquals(limitBefore + 2, helper.getDailyLimit(userId))

        // 余额持久化:重新查询用户仍是结算后的数值
        val user = helper.findUserByUsername(username)!!
        assertEquals(1, user.quotaCredit)
        assertEquals(limitBefore + 2, user.dailyLimit)
    }

    @Test
    fun todayStatsEmptyWhenNothingLearnedAndNoPendingWords() {
        helper.insertUser(username, "pw", "avatar_1")
        val userId = helper.findUserByUsername(username)!!.id
        val yesterday = helper.startOfTodayMillis() - 1000L
        helper.getAllWords().forEach { helper.insertStudyRecord(userId, it.id, yesterday) }

        val stats = helper.getTodayStats(userId)
        assertTrue("无今日记录且无待背词应为空", stats.isEmpty)
        assertEquals(0, stats.total)
    }
}
