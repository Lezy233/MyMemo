package com.example.mymemo.db

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 好友关系与聊天消息数据访问测试(任务 1.2 / 1.3):
 * 用 alice、bob 两个账号交叉验证申请校验、同意/拒绝、双向好友查询、删除、
 * 消息存取与排序(空内容拒绝)。
 */
@RunWith(AndroidJUnit4::class)
class FriendsDatabaseTest {

    private lateinit var helper: AppDatabaseHelper

    private val alice = "friends_alice"
    private val bob = "friends_bob"
    private val word = "friends_test_word"

    private var aliceId = -1L
    private var bobId = -1L

    @Before
    fun setUp() {
        helper = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        cleanUp()
        helper.insertUser(alice, "pw", "avatar_1")
        helper.insertUser(bob, "pw", "avatar_2")
        aliceId = helper.findUserByUsername(alice)!!.id
        bobId = helper.findUserByUsername(bob)!!.id
    }

    @After
    fun tearDown() {
        cleanUp()
    }

    private fun cleanUp() {
        val db = helper.writableDatabase
        db.delete(AppDatabaseHelper.TABLE_MESSAGES, null, null)
        db.delete(AppDatabaseHelper.TABLE_FRIENDSHIPS, null, null)
        db.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} IN (?, ?)",
            arrayOf(alice, bob)
        )
        db.delete(
            AppDatabaseHelper.TABLE_WORDS,
            "${AppDatabaseHelper.COLUMN_WORD} = ?",
            arrayOf(word)
        )
    }

    @Test
    fun sendRequestValidatesSelfDuplicateAndUnknownUser() {
        assertEquals(
            AddFriendResult.USER_NOT_FOUND,
            helper.sendFriendRequest(aliceId, "friends_ghost")
        )
        assertEquals(AddFriendResult.SELF, helper.sendFriendRequest(aliceId, alice))
        assertEquals(AddFriendResult.SUCCESS, helper.sendFriendRequest(aliceId, bob))

        // 自己重复申请、对方反方向申请,均视为已有待处理申请
        assertEquals(AddFriendResult.REQUEST_PENDING, helper.sendFriendRequest(aliceId, bob))
        assertEquals(AddFriendResult.REQUEST_PENDING, helper.sendFriendRequest(bobId, alice))

        assertEquals(1, helper.getPendingRequestCount(bobId))
        val requests = helper.getPendingRequests(bobId)
        assertEquals(1, requests.size)
        assertEquals(aliceId, requests[0].fromUserId)
        assertEquals(alice, requests[0].fromUsername)
    }

    @Test
    fun acceptBuildsMutualFriendshipWithTodayLearnedCount() {
        helper.sendFriendRequest(aliceId, bob)
        val requestId = helper.getPendingRequests(bobId).single().id
        assertTrue(helper.acceptFriendRequest(requestId))

        assertTrue(helper.areFriends(aliceId, bobId))
        assertTrue(helper.areFriends(bobId, aliceId))
        // 已是好友后不能重复申请
        assertEquals(AddFriendResult.ALREADY_FRIEND, helper.sendFriendRequest(aliceId, bob))

        helper.insertWord(word, "好友测试词")
        val wordId = helper.findWordByText(word)!!.id
        helper.insertStudyRecord(bobId, wordId)

        val aliceFriends = helper.getFriends(aliceId)
        assertEquals(1, aliceFriends.size)
        assertEquals(bobId, aliceFriends[0].userId)
        assertEquals(bob, aliceFriends[0].username)
        assertEquals(1, aliceFriends[0].todayLearnedCount)

        val bobFriends = helper.getFriends(bobId)
        assertEquals(1, bobFriends.size)
        assertEquals(aliceId, bobFriends[0].userId)
    }

    @Test
    fun rejectDeletesRequestAndAllowsReapply() {
        helper.sendFriendRequest(aliceId, bob)
        val requestId = helper.getPendingRequests(bobId).single().id
        assertTrue(helper.rejectFriendRequest(requestId))

        assertFalse(helper.areFriends(aliceId, bobId))
        assertEquals(0, helper.getPendingRequestCount(bobId))
        // 拒绝后可再次申请
        assertEquals(AddFriendResult.SUCCESS, helper.sendFriendRequest(aliceId, bob))
    }

    @Test
    fun deleteFriendIsMutualAndAllowsReapply() {
        helper.sendFriendRequest(aliceId, bob)
        helper.acceptFriendRequest(helper.getPendingRequests(bobId).single().id)

        assertTrue(helper.deleteFriend(aliceId, bobId))
        assertFalse(helper.areFriends(aliceId, bobId))
        assertTrue(helper.getFriends(aliceId).isEmpty())
        assertTrue(helper.getFriends(bobId).isEmpty())
        // 删除后可重新申请
        assertEquals(AddFriendResult.SUCCESS, helper.sendFriendRequest(bobId, alice))
    }

    @Test
    fun messageStorageRejectsEmptyAndOrdersByTime() {
        assertFalse(helper.sendMessage(aliceId, bobId, "   "))
        assertTrue(helper.getMessages(aliceId, bobId).isEmpty())

        assertTrue(helper.sendMessage(aliceId, bobId, "hello bob"))
        assertTrue(helper.sendMessage(bobId, aliceId, "hi alice"))

        val messages = helper.getMessages(aliceId, bobId)
        assertEquals(2, messages.size)
        assertEquals("hello bob", messages[0].content)
        assertEquals(aliceId, messages[0].senderId)
        assertEquals(bobId, messages[0].receiverId)
        assertEquals("hi alice", messages[1].content)
        assertEquals(bobId, messages[1].senderId)
        // 反方向查询结果一致
        assertEquals(messages, helper.getMessages(bobId, aliceId))
    }
}
