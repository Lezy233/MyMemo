package com.example.mymemo.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 应用本地数据库(单机、无联网)。
 *
 * 本变更建立 users 表;后续变更(word-library、friends-and-chat、stats-and-game)
 * 在同一 Helper 的 [onCreate] / [onUpgrade] 中扩展新表并递增 [DATABASE_VERSION]。
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
                $COLUMN_AVATAR TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 尚无历史版本;后续变更在此按版本号迁移表结构。
    }

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
            arrayOf(COLUMN_ID, COLUMN_USERNAME, COLUMN_PASSWORD, COLUMN_AVATAR),
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
                avatar = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AVATAR))
            )
        }
    }

    /** 校验用户名与密码是否匹配。 */
    fun checkCredentials(username: String, password: String): Boolean =
        findUserByUsername(username)?.password == password

    companion object {
        const val DATABASE_NAME = "mymemo.db"
        const val DATABASE_VERSION = 1

        const val TABLE_USERS = "users"
        const val COLUMN_ID = "id"
        const val COLUMN_USERNAME = "username"
        const val COLUMN_PASSWORD = "password"
        const val COLUMN_AVATAR = "avatar"

        @Volatile
        private var instance: AppDatabaseHelper? = null

        /** 获取单例(应用级 Context,避免泄漏 Activity)。 */
        fun getInstance(context: Context): AppDatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context).also { instance = it }
            }
    }
}
