package com.example.mymemo

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AddFriendResult
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.Friend
import com.example.mymemo.db.FriendRequest
import com.example.mymemo.db.Message
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 双账号全流程走查(任务 5.1):
 * 注册 A、B → A 搜索 B 发起申请(含失败场景) → 换 B 登录同意 →
 * 双方好友列表互见且今日已背数正确 → A、B 互发消息并持久化 →
 * 删除好友后聊天记录保留、可重新申请;各界面进度环均显示。
 */
@RunWith(AndroidJUnit4::class)
class FriendsAndChatWalkthroughTest {

    private lateinit var db: AppDatabaseHelper

    private val userA = "walk_a"
    private val userB = "walk_b"
    private val word = "walk_test_word"
    private var idA = -1L
    private var idB = -1L

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        cleanUp()
        db.insertUser(userA, "pw", "avatar_1")
        db.insertUser(userB, "pw", "avatar_2")
        idA = db.findUserByUsername(userA)!!.id
        idB = db.findUserByUsername(userB)!!.id
    }

    @After
    fun tearDown() {
        cleanUp()
    }

    private fun cleanUp() {
        val sql = db.writableDatabase
        sql.delete(AppDatabaseHelper.TABLE_MESSAGES, null, null)
        sql.delete(AppDatabaseHelper.TABLE_FRIENDSHIPS, null, null)
        sql.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} IN (?, ?)",
            arrayOf(userA, userB)
        )
        sql.delete(
            AppDatabaseHelper.TABLE_WORDS,
            "${AppDatabaseHelper.COLUMN_WORD} = ?",
            arrayOf(word)
        )
    }

    @Test
    fun dualAccountWalkthrough() {
        // 1) A 发起申请的各失败场景
        assertEquals(AddFriendResult.SELF, db.sendFriendRequest(idA, userA))
        assertEquals(AddFriendResult.USER_NOT_FOUND, db.sendFriendRequest(idA, "walk_ghost"))
        assertEquals(AddFriendResult.SUCCESS, db.sendFriendRequest(idA, userB))
        assertEquals(AddFriendResult.REQUEST_PENDING, db.sendFriendRequest(idA, userB))

        // 2) A 进入好友列表:申请入口数量为 0(申请是 A 发出的),进度环显示
        launchFriendList(userA).use {
            onView(withId(R.id.progressRing)).check(matches(isDisplayed()))
            onView(withId(R.id.tvEmptyFriends)).check(matches(isDisplayed()))
        }

        // 3) 退出登录换 B:申请列表中看到 A 并同意
        launchFriendRequest(userB).use {
            onView(withId(R.id.progressRing)).check(matches(isDisplayed()))
            onData(requestWithName(userA))
                .inAdapterView(withId(R.id.listRequests))
                .onChildView(withId(R.id.btnAccept))
                .perform(click())
            onView(withId(R.id.tvEmptyRequests)).check(matches(isDisplayed()))
        }
        assertTrue(db.areFriends(idA, idB))

        // 4) B 今日背词后,A 的好友列表互见且今日已背数正确
        db.insertWord(word, "走查测试词")
        db.insertStudyRecord(idB, db.findWordByText(word)!!.id)

        launchFriendList(userA).use {
            onData(friendWithName(userB))
                .inAdapterView(withId(R.id.listFriends))
                .onChildView(withId(R.id.tvFriendLearned))
                .check(matches(withText("今日已背 1 词")))
            onView(withId(R.id.btnFriendRequests))
                .check(matches(withText("好友申请 (0)")))

            // 5) 点击好友项进入聊天界面
            onData(friendWithName(userB))
                .inAdapterView(withId(R.id.listFriends))
                .perform(click())
            onView(withId(R.id.tvChatTitle))
                .check(matches(withText("与 $userB 的聊天")))
            pressBack()
        }
        assertTrue(db.getFriends(idB).any { it.userId == idA })

        // 6) A、B 互发消息,双方都能读到(持久化)
        launchChat(userA, userB).use {
            onView(withId(R.id.progressRing)).check(matches(isDisplayed()))
            sendMessage("A 说你好")
        }
        launchChat(userB, userA).use {
            onData(messageWithText("A 说你好"))
                .inAdapterView(withId(R.id.listMessages))
                .check(matches(isDisplayed()))
            sendMessage("B 说你也好")
        }
        launchChat(userA, userB).use {
            onData(messageWithText("B 说你也好"))
                .inAdapterView(withId(R.id.listMessages))
                .check(matches(isDisplayed()))
        }
        assertEquals(2, db.getMessages(idA, idB).size)

        // 7) 删除好友:关系双向解除,聊天记录保留,可重新申请
        assertTrue(db.deleteFriend(idA, idB))
        assertFalse(db.areFriends(idA, idB))
        assertTrue(db.getFriends(idA).isEmpty())
        assertTrue(db.getFriends(idB).isEmpty())
        assertEquals(2, db.getMessages(idA, idB).size)
        assertEquals(AddFriendResult.SUCCESS, db.sendFriendRequest(idB, userA))
    }

    private fun sendMessage(text: String) {
        onView(withId(R.id.etMessage)).perform(replaceText(text))
        closeSoftKeyboard()
        onView(withId(R.id.btnSend)).perform(click())
    }

    private fun launchFriendList(current: String): ActivityScenario<FriendListActivity> =
        ActivityScenario.launch(
            Intent(ApplicationProvider.getApplicationContext(), FriendListActivity::class.java)
                .putExtra(MainActivity.EXTRA_USERNAME, current)
        )

    private fun launchFriendRequest(current: String): ActivityScenario<FriendRequestActivity> =
        ActivityScenario.launch(
            Intent(
                ApplicationProvider.getApplicationContext(),
                FriendRequestActivity::class.java
            ).putExtra(MainActivity.EXTRA_USERNAME, current)
        )

    private fun launchChat(current: String, friend: String): ActivityScenario<ChatActivity> {
        val friendUser = db.findUserByUsername(friend)!!
        return ActivityScenario.launch(
            Intent(ApplicationProvider.getApplicationContext(), ChatActivity::class.java)
                .putExtra(MainActivity.EXTRA_USERNAME, current)
                .putExtra(ChatActivity.EXTRA_FRIEND_ID, friendUser.id)
                .putExtra(ChatActivity.EXTRA_FRIEND_USERNAME, friendUser.username)
                .putExtra(ChatActivity.EXTRA_FRIEND_AVATAR, friendUser.avatar)
        )
    }

    private fun friendWithName(name: String): Matcher<Friend> = object : TypeSafeMatcher<Friend>() {
        override fun describeTo(description: Description) {
            description.appendText("好友为 $name")
        }

        override fun matchesSafely(item: Friend): Boolean = item.username == name
    }

    private fun requestWithName(name: String): Matcher<FriendRequest> =
        object : TypeSafeMatcher<FriendRequest>() {
            override fun describeTo(description: Description) {
                description.appendText("申请人为 $name")
            }

            override fun matchesSafely(item: FriendRequest): Boolean =
                item.fromUsername == name
        }

    private fun messageWithText(text: String): Matcher<Message> =
        object : TypeSafeMatcher<Message>() {
            override fun describeTo(description: Description) {
                description.appendText("消息内容为 $text")
            }

            override fun matchesSafely(item: Message): Boolean = item.content == text
        }
}
