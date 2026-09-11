package com.example.mymemo

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.Friend
import com.example.mymemo.db.FriendRequest
import com.example.mymemo.db.Message
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 好友系统与聊天界面端到端测试(任务 2.1 / 2.2 / 3.1 / 3.2 / 4.1 / 4.2):
 * 添加好友校验、申请同意、好友列表展示、长按删除、聊天气泡与发送持久化。
 */
@RunWith(AndroidJUnit4::class)
class FriendsAndChatFlowTest {

    private lateinit var db: AppDatabaseHelper

    private val alice = "ui_friends_alice"
    private val bob = "ui_friends_bob"
    private var aliceId = -1L
    private var bobId = -1L

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        cleanUp()
        db.insertUser(alice, "pw", "avatar_1")
        db.insertUser(bob, "pw", "avatar_2")
        aliceId = db.findUserByUsername(alice)!!.id
        bobId = db.findUserByUsername(bob)!!.id
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
            arrayOf(alice, bob)
        )
    }

    @Test
    fun addFriendRejectsInvalidAndRequestCanBeAccepted() {
        launchFriendList(alice).use {
            onView(withId(R.id.progressRing)).check(matches(isDisplayed()))
            onView(withId(R.id.tvEmptyFriends)).check(matches(isDisplayed()))

            // 添加自己被拒绝:对话框保持打开
            onView(withId(R.id.btnAddFriend)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).check(matches(isDisplayed()))
            inputIntoDialog(alice)
            onView(isAssignableFrom(EditText::class.java)).check(matches(isDisplayed()))
            onView(withText(R.string.btn_cancel)).perform(click())

            // 用户不存在被拒绝:对话框保持打开
            onView(withId(R.id.btnAddFriend)).perform(click())
            inputIntoDialog("ui_friends_ghost")
            onView(isAssignableFrom(EditText::class.java)).check(matches(isDisplayed()))
            onView(withText(R.string.btn_cancel)).perform(click())

            // 向 bob 发起申请成功:对话框关闭
            onView(withId(R.id.btnAddFriend)).perform(click())
            inputIntoDialog(bob)
            onView(isAssignableFrom(EditText::class.java)).check(doesNotExist())
            assertEquals(1, db.getPendingRequestCount(bobId))
        }

        // bob 登录后看到申请并同意
        launchFriendRequest(bob).use {
            onView(withId(R.id.progressRing)).check(matches(isDisplayed()))
            onView(withId(R.id.tvEmptyRequests)).check(matches(not(isDisplayed())))
            onData(requestWithName(alice))
                .inAdapterView(withId(R.id.listRequests))
                .onChildView(withId(R.id.btnAccept))
                .perform(click())
            onView(withId(R.id.tvEmptyRequests)).check(matches(isDisplayed()))
        }
        assertTrue(db.areFriends(aliceId, bobId))

        // alice 的好友列表出现 bob
        launchFriendList(alice).use {
            onData(friendWithName(bob))
                .inAdapterView(withId(R.id.listFriends))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun longPressDeletesFriendMutuallyAndAllowsReapply() {
        makeFriends()

        launchFriendList(alice).use {
            onData(friendWithName(bob))
                .inAdapterView(withId(R.id.listFriends))
                .perform(longClick())
            onView(withText(R.string.btn_confirm_delete_friend)).perform(click())
            onView(withId(R.id.tvEmptyFriends)).check(matches(isDisplayed()))
        }

        assertFalse(db.areFriends(aliceId, bobId))
        assertTrue(db.getFriends(bobId).isEmpty())
        // 删除后可再次申请
        assertEquals(
            com.example.mymemo.db.AddFriendResult.SUCCESS,
            db.sendFriendRequest(aliceId, bob)
        )
    }

    @Test
    fun chatSendPersistsAndEmptyIsRejected() {
        makeFriends()

        launchChat(alice, bob).use {
            onView(withId(R.id.tvEmptyChat)).check(matches(isDisplayed()))

            onView(withId(R.id.etMessage)).perform(typeText("hello bob"))
            closeSoftKeyboard()
            onView(withId(R.id.btnSend)).perform(click())

            assertEquals(1, db.getMessages(aliceId, bobId).size)
            onView(withId(R.id.tvEmptyChat)).check(matches(not(isDisplayed())))
            onData(messageWithText("hello bob"))
                .inAdapterView(withId(R.id.listMessages))
                .check(matches(isDisplayed()))

            // 空消息不产生新记录
            onView(withId(R.id.btnSend)).perform(click())
            assertEquals(1, db.getMessages(aliceId, bobId).size)
        }

        // 收到方(bob)登录后能看到 alice 的消息
        launchChat(bob, alice).use {
            onData(messageWithText("hello bob"))
                .inAdapterView(withId(R.id.listMessages))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun chatAdapterAlignsOwnMessagesToTheRight() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = ChatAdapter(
            context,
            aliceId,
            listOf(
                Message(1L, aliceId, bobId, "我发的", 1L),
                Message(2L, bobId, aliceId, "对方发的", 2L)
            )
        )
        val parent = FrameLayout(context)

        // 本人消息:第 0 种视图类型,靠右
        assertEquals(0, adapter.getItemViewType(0))
        val mine = adapter.getView(0, null, parent) as LinearLayout
        assertEquals(Gravity.RIGHT, horizontalAlignment(mine))

        // 对方消息:第 1 种视图类型,靠左
        assertEquals(1, adapter.getItemViewType(1))
        val other = adapter.getView(1, null, parent) as LinearLayout
        assertEquals(Gravity.LEFT, horizontalAlignment(other))
    }

    /** 将布局方向解析后的水平对齐分量提取出来(END→RIGHT、START→LEFT)。 */
    private fun horizontalAlignment(view: LinearLayout): Int =
        Gravity.getAbsoluteGravity(view.gravity, View.LAYOUT_DIRECTION_LTR) and
            Gravity.HORIZONTAL_GRAVITY_MASK

    private fun inputIntoDialog(text: String) {
        // 对话框内只有一个用户名输入框
        onView(isAssignableFrom(EditText::class.java)).perform(typeText(text))
        closeSoftKeyboard()
        onView(withText(R.string.btn_save)).perform(click())
    }

    private fun makeFriends() {
        db.sendFriendRequest(aliceId, bob)
        db.acceptFriendRequest(db.getPendingRequests(bobId).single().id)
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

    private fun messageWithText(text: String): Matcher<Message> = object : TypeSafeMatcher<Message>() {
        override fun describeTo(description: Description) {
            description.appendText("消息内容为 $text")
        }

        override fun matchesSafely(item: Message): Boolean = item.content == text
    }
}
