package com.example.mymemo.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 应用本地数据库(单机、无联网)。
 *
 * 版本历史:
 * - v1:users 表(account-login)
 * - v2:新增 words(词库)与 study_records(学习记录)两张表,users 增加 daily_limit 列
 * - v3:新增 friendships(好友关系)与 messages(聊天消息)两张表
 * - v4:users 增加 quota_credit(额度钱包),study_records 增加 fail_count(学会时当日失败次数),
 *      新增 word_daily_fails(单词每日失败次数)表(stats-and-game)
 *
 * 后续变更继续在 [onCreate] / [onUpgrade] 中扩展。
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
                $COLUMN_DAILY_LIMIT INTEGER NOT NULL DEFAULT $DEFAULT_DAILY_LIMIT,
                $COLUMN_QUOTA_CREDIT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        createWordsTable(db)
        createStudyRecordsTable(db)
        createFriendshipsTable(db)
        createMessagesTable(db)
        createWordDailyFailsTable(db)
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
        if (oldVersion < 3) {
            // v3 只新增两张表,既有账号、词库与学习记录原样保留
            createFriendshipsTable(db)
            createMessagesTable(db)
        }
        if (oldVersion < 4) {
            // v4:额度钱包 + 失败次数持久化,旧账号/词库/学习记录/聊天数据原样保留。
            // v1→v4 链式升级时,study_records 可能已由新版建表语句带上 fail_count,故先判列存在。
            if (!hasColumn(db, TABLE_USERS, COLUMN_QUOTA_CREDIT)) {
                db.execSQL(
                    "ALTER TABLE $TABLE_USERS ADD COLUMN $COLUMN_QUOTA_CREDIT " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
            if (!hasColumn(db, TABLE_STUDY_RECORDS, COLUMN_FAIL_COUNT)) {
                db.execSQL(
                    "ALTER TABLE $TABLE_STUDY_RECORDS ADD COLUMN $COLUMN_FAIL_COUNT " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
            createWordDailyFailsTable(db)
        }
    }

    /** 判断表中是否已存在某列(用于幂等迁移,避免重复 ALTER)。 */
    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
            false
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
                $COLUMN_LEARNED_AT INTEGER NOT NULL,
                $COLUMN_FAIL_COUNT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    /**
     * 单词每日失败次数表:按 (user_id, word_id, fail_date) 逻辑唯一。
     * fail_date 为 'yyyy-MM-dd' 本地日期字符串,跨天自然形成新记录,
     * 无需重置逻辑即实现「跨天重新计数、旧记录保留」。
     */
    private fun createWordDailyFailsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORD_DAILY_FAILS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_ID INTEGER NOT NULL,
                $COLUMN_WORD_ID INTEGER NOT NULL,
                $COLUMN_FAIL_DATE TEXT NOT NULL,
                $COLUMN_FAIL_COUNT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    /**
     * 好友关系表:申请存一条记录(user_id=发起人,friend_id=接收人,pending),
     * 同意后 status 改为 accepted,查询好友时按双向匹配,不存镜像记录。
     */
    private fun createFriendshipsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FRIENDSHIPS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USER_ID INTEGER NOT NULL,
                $COLUMN_FRIEND_ID INTEGER NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    /** 聊天消息表:单聊文字消息,按 sent_at 排序展示。 */
    private fun createMessagesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SENDER_ID INTEGER NOT NULL,
                $COLUMN_RECEIVER_ID INTEGER NOT NULL,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_SENT_AT INTEGER NOT NULL
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
                COLUMN_DAILY_LIMIT,
                COLUMN_QUOTA_CREDIT
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
                dailyLimit = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DAILY_LIMIT)),
                quotaCredit = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_QUOTA_CREDIT))
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

    /**
     * 记录某用户学会了某个单词(写入一条学习记录)。
     *
     * @param failCount 该词今日点击「不认识」的累计次数,随学习记录一并保存。
     */
    fun insertStudyRecord(
        userId: Long,
        wordId: Long,
        learnedAt: Long = System.currentTimeMillis(),
        failCount: Int = 0
    ): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_USER_ID, userId)
            put(COLUMN_WORD_ID, wordId)
            put(COLUMN_LEARNED_AT, learnedAt)
            put(COLUMN_FAIL_COUNT, failCount)
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

    /** 某用户今日已学会的单词列表(按学会时间倒序,去重)。 */
    fun getTodayLearnedWords(userId: Long): List<Word> {
        val startOfDay = startOfTodayMillis()
        val endOfDay = startOfDay + MILLIS_PER_DAY
        val words = mutableListOf<Word>()
        readableDatabase.rawQuery(
            """
            SELECT DISTINCT w.$COLUMN_ID, w.$COLUMN_WORD, w.$COLUMN_MEANING, w.$COLUMN_IS_PRESET
            FROM $TABLE_WORDS w
            JOIN $TABLE_STUDY_RECORDS r ON w.$COLUMN_ID = r.$COLUMN_WORD_ID
            WHERE r.$COLUMN_USER_ID = ? AND r.$COLUMN_LEARNED_AT >= ? AND r.$COLUMN_LEARNED_AT < ?
            ORDER BY r.$COLUMN_LEARNED_AT DESC
            """.trimIndent(),
            arrayOf(userId.toString(), startOfDay.toString(), endOfDay.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                words += readWord(cursor)
            }
        }
        return words
    }

    // ---------------------------------------------------------------------
    // 失败次数持久化(word_daily_fails)
    // ---------------------------------------------------------------------

    /**
     * 记录一次「不认识」:对 (用户, 单词, 指定日期) 执行 upsert,fail_count 加一。
     * 同一单词同日多次点击会累加;次日再次点击则从 1 重新计数(旧记录保留)。
     *
     * @return 该词当日累计失败次数。
     */
    fun incrementWordFailCount(
        userId: Long,
        wordId: Long,
        failDate: String = todayDateString()
    ): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = getWordFailCount(userId, wordId, failDate)
            val values = ContentValues().apply { put(COLUMN_FAIL_COUNT, current + 1) }
            if (current == 0) {
                values.put(COLUMN_USER_ID, userId)
                values.put(COLUMN_WORD_ID, wordId)
                values.put(COLUMN_FAIL_DATE, failDate)
                db.insert(TABLE_WORD_DAILY_FAILS, null, values)
            } else {
                db.update(
                    TABLE_WORD_DAILY_FAILS,
                    values,
                    "$COLUMN_USER_ID = ? AND $COLUMN_WORD_ID = ? AND $COLUMN_FAIL_DATE = ?",
                    arrayOf(userId.toString(), wordId.toString(), failDate)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return getWordFailCount(userId, wordId, failDate)
    }

    /** 某用户某单词指定日期的失败次数;无记录返回 0。 */
    fun getWordFailCount(userId: Long, wordId: Long, failDate: String): Int =
        readableDatabase.rawQuery(
            "SELECT $COLUMN_FAIL_COUNT FROM $TABLE_WORD_DAILY_FAILS " +
                "WHERE $COLUMN_USER_ID = ? AND $COLUMN_WORD_ID = ? AND $COLUMN_FAIL_DATE = ?",
            arrayOf(userId.toString(), wordId.toString(), failDate)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    /** 指定用户尚未学会(无任何学习记录)的单词数量。 */
    fun getUnlearnedWordCount(userId: Long): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_WORDS WHERE $COLUMN_ID NOT IN " +
                "(SELECT $COLUMN_WORD_ID FROM $TABLE_STUDY_RECORDS WHERE $COLUMN_USER_ID = ?)",
            arrayOf(userId.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    /**
     * 今日背词熟悉程度分布(饼图数据),全部由数据库计算:
     * - 绿(熟知)= 今日学会且 fail_count = 0 的条数;
     * - 黄(模糊)= 今日学会且 fail_count = 1 的条数;
     * - 红(不认识)= 今日失败次数 ≥ 2 的去重单词数(含最终学会的);
     * - 灰(待背)= max(0, min(剩余额度, 未学会总数) − 红色中仍未学会的数量)。
     */
    fun getTodayStats(userId: Long): StudyStats {
        val startOfDay = startOfTodayMillis()
        val endOfDay = startOfDay + MILLIS_PER_DAY
        val failDate = todayDateString()

        val green = countTodayLearnedByFailCount(userId, startOfDay, endOfDay, 0)
        val yellow = countTodayLearnedByFailCount(userId, startOfDay, endOfDay, 1)

        val red = readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT $COLUMN_WORD_ID) FROM $TABLE_WORD_DAILY_FAILS " +
                "WHERE $COLUMN_USER_ID = ? AND $COLUMN_FAIL_DATE = ? AND $COLUMN_FAIL_COUNT >= 2",
            arrayOf(userId.toString(), failDate)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

        // 红色中仍未学会的数量:这些词虽属红色,但会被「未学会总数」重复计入灰色,需扣除
        val redUnlearned = readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT f.$COLUMN_WORD_ID) FROM $TABLE_WORD_DAILY_FAILS f " +
                "WHERE f.$COLUMN_USER_ID = ? AND f.$COLUMN_FAIL_DATE = ? " +
                "AND f.$COLUMN_FAIL_COUNT >= 2 AND f.$COLUMN_WORD_ID NOT IN " +
                "(SELECT r.$COLUMN_WORD_ID FROM $TABLE_STUDY_RECORDS r WHERE r.$COLUMN_USER_ID = ?)",
            arrayOf(userId.toString(), failDate, userId.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

        val remaining = (getDailyLimit(userId) - getTodayLearnedCount(userId)).coerceAtLeast(0)
        val unlearned = getUnlearnedWordCount(userId)
        val gray = (minOf(remaining, unlearned) - redUnlearned).coerceAtLeast(0)

        return StudyStats(green = green, yellow = yellow, red = red, gray = gray)
    }

    private fun countTodayLearnedByFailCount(
        userId: Long,
        startOfDay: Long,
        endOfDay: Long,
        failCount: Int
    ): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_STUDY_RECORDS WHERE $COLUMN_USER_ID = ? " +
                "AND $COLUMN_LEARNED_AT >= ? AND $COLUMN_LEARNED_AT < ? AND $COLUMN_FAIL_COUNT = ?",
            arrayOf(
                userId.toString(),
                startOfDay.toString(),
                endOfDay.toString(),
                failCount.toString()
            )
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    // ---------------------------------------------------------------------
    // 额度钱包(quota_credit)
    // ---------------------------------------------------------------------

    /** 当前用户的额度余额;用户不存在返回 0。 */
    fun getQuotaCredit(userId: Long): Int =
        readableDatabase.query(
            TABLE_USERS,
            arrayOf(COLUMN_QUOTA_CREDIT),
            "$COLUMN_ID = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    /** 增加额度(游戏结算);数量必须为正数。 */
    fun addQuotaCredit(userId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        val values = ContentValues().apply { put(COLUMN_QUOTA_CREDIT, getQuotaCredit(userId) + amount) }
        return writableDatabase.update(
            TABLE_USERS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(userId.toString())
        ) > 0
    }

    /** 扣减额度(兑换);数量必须为正且不超过当前余额。 */
    fun deductQuotaCredit(userId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        val current = getQuotaCredit(userId)
        if (current < amount) return false
        val values = ContentValues().apply { put(COLUMN_QUOTA_CREDIT, current - amount) }
        return writableDatabase.update(
            TABLE_USERS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(userId.toString())
        ) > 0
    }

    /**
     * 额度兑换每日上限:1 额度 = 上限 +1。
     * 数量必须为正整数且不超过余额;余额扣减与上限提升在同一事务内完成。
     */
    fun exchangeQuotaForLimit(userId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            val credit = getQuotaCredit(userId)
            if (credit < amount) return false
            val values = ContentValues().apply {
                put(COLUMN_QUOTA_CREDIT, credit - amount)
                put(COLUMN_DAILY_LIMIT, getDailyLimit(userId) + amount)
            }
            val updated = db.update(
                TABLE_USERS,
                values,
                "$COLUMN_ID = ?",
                arrayOf(userId.toString())
            ) > 0
            db.setTransactionSuccessful()
            return updated
        } finally {
            db.endTransaction()
        }
    }

    // ---------------------------------------------------------------------
    // 好友关系(friendships)
    // ---------------------------------------------------------------------

    /**
     * 发起好友申请:校验自己、用户不存在、已是好友、已有待处理申请四种失败情形。
     *
     * @return 见 [AddFriendResult];成功时写入一条 status=pending 的记录。
     */
    fun sendFriendRequest(fromUserId: Long, targetUsername: String): AddFriendResult {
        val target = findUserByUsername(targetUsername) ?: return AddFriendResult.USER_NOT_FOUND
        if (target.id == fromUserId) return AddFriendResult.SELF
        if (areFriends(fromUserId, target.id)) return AddFriendResult.ALREADY_FRIEND
        if (hasPendingRequest(fromUserId, target.id)) return AddFriendResult.REQUEST_PENDING

        val values = ContentValues().apply {
            put(COLUMN_USER_ID, fromUserId)
            put(COLUMN_FRIEND_ID, target.id)
            put(COLUMN_STATUS, STATUS_PENDING)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        return if (writableDatabase.insert(TABLE_FRIENDSHIPS, null, values) != -1L) {
            AddFriendResult.SUCCESS
        } else {
            AddFriendResult.REQUEST_PENDING
        }
    }

    /** 双方是否已是好友(status=accepted,双向匹配)。 */
    fun areFriends(userA: Long, userB: Long): Boolean =
        friendshipsBetween(userA, userB, STATUS_ACCEPTED).isNotEmpty()

    /** 双方之间（任一方向）是否已有待处理申请。 */
    fun hasPendingRequest(userA: Long, userB: Long): Boolean =
        friendshipsBetween(userA, userB, STATUS_PENDING).isNotEmpty()

    /** 查询收到的待处理申请(申请人是 user_id,收件人是 userId)。 */
    fun getPendingRequests(userId: Long): List<FriendRequest> {
        val requests = mutableListOf<FriendRequest>()
        readableDatabase.rawQuery(
            """
            SELECT f.$COLUMN_ID, u.$COLUMN_ID, u.$COLUMN_USERNAME, u.$COLUMN_AVATAR, f.$COLUMN_CREATED_AT
            FROM $TABLE_FRIENDSHIPS f
            JOIN $TABLE_USERS u ON u.$COLUMN_ID = f.$COLUMN_USER_ID
            WHERE f.$COLUMN_FRIEND_ID = ? AND f.$COLUMN_STATUS = ?
            ORDER BY f.$COLUMN_CREATED_AT ASC, f.$COLUMN_ID ASC
            """.trimIndent(),
            arrayOf(userId.toString(), STATUS_PENDING)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                requests += FriendRequest(
                    id = cursor.getLong(0),
                    fromUserId = cursor.getLong(1),
                    fromUsername = cursor.getString(2),
                    fromAvatar = cursor.getString(3),
                    createdAt = cursor.getLong(4)
                )
            }
        }
        return requests
    }

    /** 待处理申请数量(用于主界面/好友界面的申请入口角标)。 */
    fun getPendingRequestCount(userId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_FRIENDSHIPS " +
                "WHERE $COLUMN_FRIEND_ID = ? AND $COLUMN_STATUS = ?",
            arrayOf(userId.toString(), STATUS_PENDING)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** 同意好友申请:将 pending 记录改为 accepted。 */
    fun acceptFriendRequest(requestId: Long): Boolean {
        val values = ContentValues().apply { put(COLUMN_STATUS, STATUS_ACCEPTED) }
        return writableDatabase.update(
            TABLE_FRIENDSHIPS,
            values,
            "$COLUMN_ID = ? AND $COLUMN_STATUS = ?",
            arrayOf(requestId.toString(), STATUS_PENDING)
        ) > 0
    }

    /** 拒绝好友申请:删除 pending 记录,之后双方可再次申请。 */
    fun rejectFriendRequest(requestId: Long): Boolean =
        writableDatabase.delete(
            TABLE_FRIENDSHIPS,
            "$COLUMN_ID = ? AND $COLUMN_STATUS = ?",
            arrayOf(requestId.toString(), STATUS_PENDING)
        ) > 0

    /**
     * 当前用户的好友列表,含每位好友今日已背单词数(复用 [getTodayLearnedCount])。
     * 按用户名升序展示。
     */
    fun getFriends(userId: Long): List<Friend> {
        val friends = mutableListOf<Friend>()
        readableDatabase.rawQuery(
            """
            SELECT u.$COLUMN_ID, u.$COLUMN_USERNAME, u.$COLUMN_AVATAR
            FROM $TABLE_FRIENDSHIPS f
            JOIN $TABLE_USERS u
              ON u.$COLUMN_ID = CASE WHEN f.$COLUMN_USER_ID = ? THEN f.$COLUMN_FRIEND_ID ELSE f.$COLUMN_USER_ID END
            WHERE (f.$COLUMN_USER_ID = ? OR f.$COLUMN_FRIEND_ID = ?) AND f.$COLUMN_STATUS = ?
            ORDER BY u.$COLUMN_USERNAME ASC
            """.trimIndent(),
            arrayOf(userId.toString(), userId.toString(), userId.toString(), STATUS_ACCEPTED)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val friendId = cursor.getLong(0)
                friends += Friend(
                    userId = friendId,
                    username = cursor.getString(1),
                    avatar = cursor.getString(2),
                    todayLearnedCount = getTodayLearnedCount(friendId)
                )
            }
        }
        return friends
    }

    /**
     * 删除好友:双向解除 accepted 关系;聊天记录不在本方法内处理,保持保留。
     */
    fun deleteFriend(userId: Long, friendId: Long): Boolean =
        writableDatabase.delete(
            TABLE_FRIENDSHIPS,
            "$COLUMN_STATUS = ? AND (($COLUMN_USER_ID = ? AND $COLUMN_FRIEND_ID = ?) " +
                "OR ($COLUMN_USER_ID = ? AND $COLUMN_FRIEND_ID = ?))",
            arrayOf(
                STATUS_ACCEPTED,
                userId.toString(),
                friendId.toString(),
                friendId.toString(),
                userId.toString()
            )
        ) > 0

    /** 查询两用户之间指定状态的 friendships 记录。 */
    private fun friendshipsBetween(userA: Long, userB: Long, status: String): List<Long> {
        val ids = mutableListOf<Long>()
        readableDatabase.rawQuery(
            "SELECT $COLUMN_ID FROM $TABLE_FRIENDSHIPS " +
                "WHERE $COLUMN_STATUS = ? " +
                "AND (($COLUMN_USER_ID = ? AND $COLUMN_FRIEND_ID = ?) " +
                "OR ($COLUMN_USER_ID = ? AND $COLUMN_FRIEND_ID = ?))",
            arrayOf(
                status,
                userA.toString(),
                userB.toString(),
                userB.toString(),
                userA.toString()
            )
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ids += cursor.getLong(0)
            }
        }
        return ids
    }

    // ---------------------------------------------------------------------
    // 聊天消息(messages)
    // ---------------------------------------------------------------------

    /**
     * 发送一条文字消息;内容为空或仅含空白字符时拒绝。
     *
     * @return 已写入返回 true;内容为空返回 false。
     */
    fun sendMessage(senderId: Long, receiverId: Long, content: String): Boolean {
        val text = content.trim()
        if (text.isEmpty()) return false
        val values = ContentValues().apply {
            put(COLUMN_SENDER_ID, senderId)
            put(COLUMN_RECEIVER_ID, receiverId)
            put(COLUMN_CONTENT, text)
            put(COLUMN_SENT_AT, System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE_MESSAGES, null, values) != -1L
    }

    /** 查询两用户之间的全部消息,按发送时间升序(同毫秒按主键升序)。 */
    fun getMessages(userA: Long, userB: Long): List<Message> {
        val messages = mutableListOf<Message>()
        readableDatabase.query(
            TABLE_MESSAGES,
            MESSAGE_COLUMNS,
            "(($COLUMN_SENDER_ID = ? AND $COLUMN_RECEIVER_ID = ?) " +
                "OR ($COLUMN_SENDER_ID = ? AND $COLUMN_RECEIVER_ID = ?))",
            arrayOf(
                userA.toString(),
                userB.toString(),
                userB.toString(),
                userA.toString()
            ),
            null,
            null,
            "$COLUMN_SENT_AT ASC, $COLUMN_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                messages += Message(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    senderId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SENDER_ID)),
                    receiverId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RECEIVER_ID)),
                    content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                    sentAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SENT_AT))
                )
            }
        }
        return messages
    }

    companion object {
        const val DATABASE_NAME = "mymemo.db"
        const val DATABASE_VERSION = 4

        const val DEFAULT_DAILY_LIMIT = 30
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

        const val TABLE_USERS = "users"
        const val COLUMN_ID = "id"
        const val COLUMN_USERNAME = "username"
        const val COLUMN_PASSWORD = "password"
        const val COLUMN_AVATAR = "avatar"
        const val COLUMN_DAILY_LIMIT = "daily_limit"
        const val COLUMN_QUOTA_CREDIT = "quota_credit"

        const val TABLE_WORDS = "words"
        const val COLUMN_WORD = "word"
        const val COLUMN_MEANING = "meaning"
        const val COLUMN_IS_PRESET = "is_preset"

        const val TABLE_STUDY_RECORDS = "study_records"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_WORD_ID = "word_id"
        const val COLUMN_LEARNED_AT = "learned_at"
        const val COLUMN_FAIL_COUNT = "fail_count"

        const val TABLE_WORD_DAILY_FAILS = "word_daily_fails"
        const val COLUMN_FAIL_DATE = "fail_date"

        const val TABLE_FRIENDSHIPS = "friendships"
        const val COLUMN_FRIEND_ID = "friend_id"
        const val COLUMN_STATUS = "status"
        const val COLUMN_CREATED_AT = "created_at"
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"

        const val TABLE_MESSAGES = "messages"
        const val COLUMN_SENDER_ID = "sender_id"
        const val COLUMN_RECEIVER_ID = "receiver_id"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_SENT_AT = "sent_at"

        private val WORD_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_WORD,
            COLUMN_MEANING,
            COLUMN_IS_PRESET
        )

        private val MESSAGE_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_SENDER_ID,
            COLUMN_RECEIVER_ID,
            COLUMN_CONTENT,
            COLUMN_SENT_AT
        )

        /** 本地日期字符串 'yyyy-MM-dd',用于失败记录按天分组。 */
        fun dateString(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

        /** 今日本地日期字符串。 */
        fun todayDateString(): String = dateString(System.currentTimeMillis())

        @Volatile
        private var instance: AppDatabaseHelper? = null

        /** 获取单例(应用级 Context,避免泄漏 Activity)。 */
        fun getInstance(context: Context): AppDatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context).also { instance = it }
            }
    }
}
