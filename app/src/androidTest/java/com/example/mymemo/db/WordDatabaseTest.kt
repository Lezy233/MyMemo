package com.example.mymemo.db

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 词库与学习记录数据访问测试(任务 1.2 / 1.3):
 * 预制词表播种幂等、单词增删改查、学习记录与今日已背数、未学会单词查询、每日上限存取。
 */
@RunWith(AndroidJUnit4::class)
class WordDatabaseTest {

    private lateinit var helper: AppDatabaseHelper

    private val testWord = "zzz_test_word"
    private val testWord2 = "zzz_test_word_2"

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
        db.delete(AppDatabaseHelper.TABLE_WORDS, "${AppDatabaseHelper.COLUMN_WORD} LIKE 'zzz_%'", null)
        db.delete(AppDatabaseHelper.TABLE_STUDY_RECORDS, null, null)
        db.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} LIKE '%tester'",
            null
        )
    }

    @Test
    fun upgradeFromV1KeepsUsersAndAddsTables() {
        // 在临时数据库上重现 v1 结构并写入一个老账号
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tmp = File(context.cacheDir, "migration_test.db")
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
                "${AppDatabaseHelper.COLUMN_AVATAR}) VALUES ('legacy', 'pw123', 'avatar_1')"
        )

        // 执行真实的 v1 → v2 迁移
        helper.onUpgrade(legacy, 1, 2)

        // 老账号保留,并获得默认每日上限
        legacy.rawQuery(
            "SELECT ${AppDatabaseHelper.COLUMN_PASSWORD}, ${AppDatabaseHelper.COLUMN_DAILY_LIMIT} " +
                "FROM ${AppDatabaseHelper.TABLE_USERS} WHERE " +
                "${AppDatabaseHelper.COLUMN_USERNAME} = 'legacy'",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("pw123", cursor.getString(0))
            assertEquals(AppDatabaseHelper.DEFAULT_DAILY_LIMIT, cursor.getInt(1))
        }

        // 新表存在且预制词表已播种
        legacy.rawQuery("SELECT COUNT(*) FROM ${AppDatabaseHelper.TABLE_STUDY_RECORDS}", null)
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        legacy.rawQuery(
            "SELECT COUNT(*) FROM ${AppDatabaseHelper.TABLE_WORDS} " +
                "WHERE ${AppDatabaseHelper.COLUMN_IS_PRESET} = 1",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getInt(0) in 100..200)
        }

        legacy.close()
        tmp.delete()
    }

    @Test
    fun presetWordsAreSeededWithinRange() {
        val count = presetCount()
        assertTrue("预制词表应包含 100~200 个单词,实际 $count", count in 100..200)
    }

    private fun presetCount(): Int =
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${AppDatabaseHelper.TABLE_WORDS} " +
                "WHERE ${AppDatabaseHelper.COLUMN_IS_PRESET} = 1",
            null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    @Test
    fun seedingIsIdempotent() {
        val before = helper.getAllWords().size
        helper.seedPresetWordsIfEmpty(helper.writableDatabase)
        helper.seedPresetWordsIfEmpty(helper.writableDatabase)
        assertEquals(before, helper.getAllWords().size)
    }

    @Test
    fun addUpdateDeleteWord() {
        assertTrue(helper.insertWord(testWord, "测试释义"))
        assertFalse("重复单词应被拒绝", helper.insertWord(testWord, "另一个释义"))

        val inserted = helper.findWordByText(testWord)
        assertNotNull(inserted)
        assertEquals("测试释义", inserted!!.meaning)

        assertTrue(helper.updateWordMeaning(inserted.id, "新释义"))
        assertEquals("新释义", helper.findWordByText(testWord)!!.meaning)

        assertTrue(helper.deleteWord(inserted.id))
        assertNull(helper.findWordByText(testWord))
    }

    @Test
    fun studyRecordUpdatesTodayCountAndUnlearnedList() {
        helper.insertUser("word_tester", "pw", "avatar_1")
        val userId = helper.findUserByUsername("word_tester")!!.id

        // 初始:今日已背 0,所有词均未学会
        assertEquals(0, helper.getTodayLearnedCount(userId))
        val allCount = helper.getAllWords().size
        assertEquals(allCount, helper.getUnlearnedWords(userId).size)

        val word = helper.getAllWords().first()
        assertTrue(helper.insertStudyRecord(userId, word.id))

        // 学会后:今日已背 +1,未学会列表不再包含该词
        assertEquals(1, helper.getTodayLearnedCount(userId))
        assertEquals(allCount - 1, helper.getUnlearnedWords(userId).size)
        assertFalse(helper.getUnlearnedWords(userId).any { it.id == word.id })

        // 昨天的记录不计入今日
        val yesterday = helper.startOfTodayMillis() - 1000L
        helper.insertStudyRecord(userId, helper.getAllWords()[1].id, yesterday)
        assertEquals(1, helper.getTodayLearnedCount(userId))
    }

    @Test
    fun dailyLimitDefaultsAndPersists() {
        helper.insertUser("limit_tester", "pw", "avatar_2")
        val userId = helper.findUserByUsername("limit_tester")!!.id

        assertEquals(AppDatabaseHelper.DEFAULT_DAILY_LIMIT, helper.getDailyLimit(userId))
        assertEquals(30, helper.getDailyLimit(userId))

        assertTrue(helper.setDailyLimit(userId, 50))
        assertEquals(50, helper.getDailyLimit(userId))
        assertEquals(50, helper.findUserByUsername("limit_tester")!!.dailyLimit)

        assertFalse("非正整数上限应被拒绝", helper.setDailyLimit(userId, 0))
        assertFalse("非正整数上限应被拒绝", helper.setDailyLimit(userId, -5))
        assertEquals(50, helper.getDailyLimit(userId))
    }
}
