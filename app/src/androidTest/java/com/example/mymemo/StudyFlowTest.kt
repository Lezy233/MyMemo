package com.example.mymemo

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mymemo.db.AppDatabaseHelper
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 背词学习界面端到端测试(任务 3.1 / 3.3 / 5.1):
 * 卡片翻转交互、队列学完自动结束、达到每日上限立即终止。
 */
@RunWith(AndroidJUnit4::class)
class StudyFlowTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "study_tester"

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        clearStudyData()
    }

    @After
    fun tearDown() {
        clearStudyData()
    }

    private fun clearStudyData() {
        val database = db.writableDatabase
        database.delete(AppDatabaseHelper.TABLE_STUDY_RECORDS, null, null)
        database.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    @Test
    fun cardFlipRevealsMeaningAndChoices() {
        createUser(dailyLimit = 30)
        launchStudy().use {
            // 初始只显示英文
            onView(withId(R.id.tvCardWord)).check(matches(isDisplayed()))
            onView(withId(R.id.tvCardMeaning)).check(matches(not(isDisplayed())))
            onView(withId(R.id.btnKnown)).check(matches(not(isDisplayed())))

            // 点击翻面显示释义与选择
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.tvCardMeaning)).check(matches(isDisplayed()))
            onView(withId(R.id.btnKnown)).check(matches(isDisplayed()))
            onView(withId(R.id.btnUnknown)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun unknownRequeuesWordInsteadOfLearning() {
        createUser(dailyLimit = 30)
        launchStudy().use {
            onView(withId(R.id.tvCardWord)).check(matches(isDisplayed()))
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.btnUnknown)).perform(click())

            // 仍未学会,今日已背为 0,且新卡片默认只显示英文
            assertEquals(0, db.getTodayLearnedCount(userId()!!))
            onView(withId(R.id.tvCardMeaning)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun twoConsecutiveKnownFinishSingleWordSession() {
        val userId = createUser(dailyLimit = 30)
        val words = db.getAllWords()
        val remaining = words.last()
        // 除最后一个单词外,其余均标记为"昨天已学会",从而队列只剩一张卡
        val yesterday = db.startOfTodayMillis() - 1000L
        words.dropLast(1).forEach { db.insertStudyRecord(userId, it.id, yesterday) }
        assertEquals(0, db.getTodayLearnedCount(userId))

        val scenario = launchStudy()
        onView(withId(R.id.tvCardWord)).check(matches(withText(remaining.word)))

        // 第一次认识:留在队列中等待再次出现
        onView(withId(R.id.cardWord)).perform(click())
        onView(withId(R.id.btnKnown)).perform(click())
        onView(withId(R.id.tvCardWord)).check(matches(withText(remaining.word)))
        onView(withId(R.id.btnKnown)).check(matches(not(isDisplayed())))

        // 第二次认识:写入学习记录,队列清空,会话结束
        onView(withId(R.id.cardWord)).perform(click())
        onView(withId(R.id.btnKnown)).perform(click())

        assertEquals(1, db.getTodayLearnedCount(userId))
        awaitDestroyed(scenario)
    }

    @Test
    fun reachingDailyLimitTerminatesSession() {
        val userId = createUser(dailyLimit = 1)
        // 今日已背 1 条,达到上限
        db.insertStudyRecord(userId, db.getAllWords().first().id)

        val scenario = launchStudy()
        awaitDestroyed(scenario)
        assertTrue(db.getTodayLearnedCount(userId) >= db.getDailyLimit(userId))
    }

    /** 轮询等待 Activity 完全销毁(任务 3.3:会话终止)。 */
    private fun awaitDestroyed(scenario: ActivityScenario<*>) {
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            if (scenario.state == Lifecycle.State.DESTROYED) return
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(50L)
        }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    private fun createUser(dailyLimit: Int): Long {
        db.insertUser(username, "pw", "avatar_1")
        val userId = db.findUserByUsername(username)!!.id
        db.setDailyLimit(userId, dailyLimit)
        return userId
    }

    private fun userId(): Long? = db.findUserByUsername(username)?.id

    private fun launchStudy(): ActivityScenario<StudyActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StudyActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }
}
