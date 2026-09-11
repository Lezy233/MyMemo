package com.example.mymemo.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

/**
 * 应用本地数据库(单机、无联网)。
 *
 * 版本历史:
 * - v1:users 表(account-login)
 * - v2:新增 words(词库)与 study_records(学习记录)两张表,users 增加 daily_limit 列
 *
 * 后续变更(friends-and-chat、stats-and-game)继续在 [onCreate] / [onUpgrade] 中扩展。
 */
class AppDatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USERNAME TEXT UNIQUE NOT NULL,
                $COLUMN_PASSWORD TEXT NOT NULL,
                $COLUMN_AVATAR TEXT NOT NULL,
                $COLUMN_DAILY_LIMIT INTEGER NOT NULL DEFAULT $DEFAULT_DAILY_LIMIT
            )
            """.trimIndent()
        )
        createWordsTable(db)
        createStudyRecordsTable(db)
        seedPresetWordsIfEmpty(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createWordsTable(db)
            createStudyRecordsTable(db)
            // 迁移只加表加列,不动既有账号数据
            db.execSQL(
                "ALTER TABLE $TABLE_USERS ADD COLUMN $COLUMN_DAILY_LIMIT " +
                    "INTEGER NOT NULL DEFAULT $DEFAULT_DAILY_LIMIT"
            )
            seedPresetWordsIfEmpty(db)
        }
    }

    private fun createWordsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WORD TEXT UNIQUE NOT NULL,
                $COLUMN_MEANING TEXT NOT NULL,
                $COLUMN_IS_PRESET INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    private fun createStudyRecordsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_STUDY_RECORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_ID INTEGER NOT NULL,
                $COLUMN_WORD_ID INTEGER NOT NULL,
                $COLUMN_LEARNED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    /**
     * 预制词表播种:仅当 words 表为空时,在单事务内批量插入 [PresetWords.ENTRIES]。
     * 空表判断保证幂等,重复启动不会产生重复词条。
     */
    internal fun seedPresetWordsIfEmpty(db: SQLiteDatabase) {
        if (countWords(db) > 0) return
        db.beginTransaction()
        try {
            PresetWords.ENTRIES.forEach { (word, meaning) ->
                val values = ContentValues().apply {
                    put(COLUMN_WORD, word)
                    put(COLUMN_MEANING, meaning)
                    put(COLUMN_IS_PRESET, 1)
                }
                db.insertWithOnConflict(
                    TABLE_WORDS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun countWords(db: SQLiteDatabase): Long =
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_WORDS", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    // ---------------------------------------------------------------------
    // 用户
    // ---------------------------------------------------------------------

    /**
     * 插入新用户。
     *
     * @return 成功返回 true;用户名已存在返回 false。
     */
    fun insertUser(username: String, password: String, avatar: String): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_USERNAME, username)
            put(COLUMN_PASSWORD, password)
            put(COLUMN_AVATAR, avatar)
            put(COLUMN_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)
        }
        return try {
            writableDatabase.insertOrThrow(TABLE_USERS, null, values) != -1L
        } catch (e: SQLiteConstraintException) {
            // 用户名 UNIQUE 冲突
            false
        }
    }

    /** 按用户名查询用户;不存在返回 null。 */
    fun findUserByUsername(username: String): User? {
        readableDatabase.query(
            TABLE_USERS,
            arrayOf(
                COLUMN_ID,
                COLUMN_USERNAME,
                COLUMN_PASSWORD,
                COLUMN_AVATAR,
                COLUMN_DAILY_LIMIT
            ),
            "$COLUMN_USERNAME = ?",
            arrayOf(username),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return User(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
                avatar = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AVATAR)),
                dailyLimit = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DAILY_LIMIT))
            )
        }
    }

    /** 校验用户名与密码是否匹配。 */
    fun checkCredentials(username: String, password: String): Boolean =
        findUserByUsername(username)?.password == password

    /** 读取指定用户的每日背词上限;用户不存在时返回默认值。 */
    fun getDailyLimit(userId: Long): Int {
        readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_DAILY_LIMIT),
            "$COLUMN_ID = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return DEFAULT_DAILY_LIMIT
            return cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DAILY_LIMIT))
        }
    }

    /**
     * 更新指定用户的每日背词上限。
     *
     * @return 成功更新返回 true;用户不存在返回 false。
     */
    fun setDailyLimit(userId: Long, limit: Int): Boolean {
        if (limit <= 0) return false
        val values = ContentValues().apply { put(COLUMN_DAILY_LIMIT, limit) }
        return writableDatabase.update(
            TABLE_USERS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(userId.toString())
        ) > 0
    }

    // ---------------------------------------------------------------------
    // 词库(words)
    // ---------------------------------------------------------------------

    /**
     * 新增单词。
     *
     * @return 成功返回 true;单词已存在返回 false。
     */
    fun insertWord(word: String, meaning: String, isPreset: Boolean = false): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_WORD, word)
            put(COLUMN_MEANING, meaning)
            put(COLUMN_IS_PRESET, if (isPreset) 1 else 0)
        }
        return try {
            writableDatabase.insertOrThrow(TABLE_WORDS, null, values) != -1L
        } catch (e: SQLiteConstraintException) {
            // 单词 UNIQUE 冲突
            false
        }
    }

    /** 按单词文本查询;不存在返回 null。 */
    fun findWordByText(word: String): Word? {
        readableDatabase.query(
            TABLE_WORDS,
            WORD_COLUMNS,
            "$COLUMN_WORD = ?",
            arrayOf(word),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return readWord(cursor)
        }
    }

    /** 词库全量列表(按主键升序)。 */
    fun getAllWords(): List<Word> =
        queryWords(null, null)

    /** 指定用户尚未学会(无学习记录)的单词列表。 */
    fun getUnlearnedWords(userId: Long): List<Word> = queryWords(
        "$COLUMN_ID NOT IN (SELECT $COLUMN_WORD_ID FROM $TABLE_STUDY_RECORDS WHERE $COLUMN_USER_ID = ?)",
        arrayOf(userId.toString())
    )

    /** 修改单词释义。 */
    fun updateWordMeaning(wordId: Long, meaning: String): Boolean {
        val values = ContentValues().apply { put(COLUMN_MEANING, meaning) }
        return writableDatabase.update(
            TABLE_WORDS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(wordId.toString())
        ) > 0
    }

    /** 删除单词(同时清理其学习记录,避免遗留悬挂引用)。 */
    fun deleteWord(wordId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                TABLE_STUDY_RECORDS,
                "$COLUMN_WORD_ID = ?",
                arrayOf(wordId.toString())
            )
            val deleted = db.delete(
                TABLE_WORDS,
                "$COLUMN_ID = ?",
                arrayOf(wordId.toString())
            ) > 0
            db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    private fun queryWords(selection: String?, selectionArgs: Array<String>?): List<Word> {
        val words = mutableListOf<Word>()
        readableDatabase.query(
            TABLE_WORDS,
            WORD_COLUMNS,
            selection,
            selectionArgs,
            null,
            null,
            "$COLUMN_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                words += readWord(cursor)
            }
        }
        return words
    }

    private fun readWord(cursor: Cursor): Word = Word(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
        word = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORD)),
        meaning = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MEANING)),
        isPreset = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_PRESET)) == 1
    )

    // ---------------------------------------------------------------------
    // 学习记录(study_records)
    // ---------------------------------------------------------------------

    /** 记录某用户学会了某个单词(写入一条学习记录)。 */
    fun insertStudyRecord(userId: Long, wordId: Long, learnedAt: Long = System.currentTimeMillis()): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_USER_ID, userId)
            put(COLUMN_WORD_ID, wordId)
            put(COLUMN_LEARNED_AT, learnedAt)
        }
        return writableDatabase.insert(TABLE_STUDY_RECORDS, null, values) != -1L
    }

    /**
     * 查询某用户今日已背单词数(按 learned_at 落在今天的记录条数统计)。
     * 跨天自动归零,无需重置逻辑。
     */
    fun getTodayLearnedCount(userId: Long): Int {
        val startOfDay = startOfTodayMillis()
        val endOfDay = startOfDay + MILLIS_PER_DAY
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*) FROM $TABLE_STUDY_RECORDS
            WHERE $COLUMN_USER_ID = ? AND $COLUMN_LEARNED_AT >= ? AND $COLUMN_LEARNED_AT < ?
            """.trimIndent(),
            arrayOf(userId.toString(), startOfDay.toString(), endOfDay.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** 今日 00:00:00.000 的本地时区毫秒时间戳。 */
    internal fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val DATABASE_NAME = "mymemo.db"
        const val DATABASE_VERSION = 2

        const val DEFAULT_DAILY_LIMIT = 30
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

        const val TABLE_USERS = "users"
        const val COLUMN_ID = "id"
        const val COLUMN_USERNAME = "username"
        const val COLUMN_PASSWORD = "password"
        const val COLUMN_AVATAR = "avatar"
        const val COLUMN_DAILY_LIMIT = "daily_limit"

        const val TABLE_WORDS = "words"
        const val COLUMN_WORD = "word"
        const val COLUMN_MEANING = "meaning"
        const val COLUMN_IS_PRESET = "is_preset"

        const val TABLE_STUDY_RECORDS = "study_records"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_WORD_ID = "word_id"
        const val COLUMN_LEARNED_AT = "learned_at"

        private val WORD_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_WORD,
            COLUMN_MEANING,
            COLUMN_IS_PRESET
        )

        @Volatile
        private var instance: AppDatabaseHelper? = null

        /** 获取单例(应用级 Context,避免泄漏 Activity)。 */
        fun getInstance(context: Context): AppDatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context).also { instance = it }
            }
    }
}
