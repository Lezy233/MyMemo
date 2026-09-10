package com.example.mymemo.db

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

/**
 * AppDatabaseHelper 的增查与凭据校验测试。
 *
 * 运行方式(需已连接真机或启动模拟器):
 * `./gradlew connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseHelperTest {

    private lateinit var helper: AppDatabaseHelper

    @Before
    fun setUp() {
        helper = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        helper.writableDatabase.delete(AppDatabaseHelper.TABLE_USERS, null, null)
    }

    @After
    fun tearDown() {
        helper.writableDatabase.delete(AppDatabaseHelper.TABLE_USERS, null, null)
    }

    @Test
    fun insertThenFindByUsername() {
        assertTrue(helper.insertUser("alice", "pw123", "avatar_3"))

        val user = helper.findUserByUsername("alice")
        assertEquals("alice", user?.username)
        assertEquals("pw123", user?.password)
        assertEquals("avatar_3", user?.avatar)
    }

    @Test
    fun duplicateUsernameIsRejected() {
        assertTrue(helper.insertUser("alice", "pw123", "avatar_3"))
        assertFalse(helper.insertUser("alice", "other", "avatar_5"))
        assertTrue(helper.checkCredentials("alice", "pw123"))
    }

    @Test
    fun checkCredentialsMatchesPassword() {
        helper.insertUser("bob", "secret", "avatar_1")
        assertTrue(helper.checkCredentials("bob", "secret"))
        assertFalse(helper.checkCredentials("bob", "wrong"))
        assertFalse(helper.checkCredentials("nobody", "secret"))
    }

    @Test
    fun findUnknownUserReturnsNull() {
        assertNull(helper.findUserByUsername("ghost"))
    }
}
