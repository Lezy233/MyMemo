package com.example.mymemo

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.study.StudySession
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 背词学习界面端到端测试(任务 3.1 / 3.2 / 3.3 / 3.5 / 5.1):
 * 卡片翻转交互、初见认识即学会、不认识插回后连续两次认识、点击色块、会话终止。
 */
@RunWith(AndroidJUnit4::class)
class StudyFlowTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "study_tester"

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        clearStudyData()
        StudySession.clear()
    }

    @After
    fun tearDown() {
        clearStudyData()
        StudySession.clear()
    }

    private fun clearStudyData() {
        val database = db.writableDatabase
        database.delete(AppDatabaseHelper.TABLE_WORD_DAILY_FAILS, null, null)
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
    fun firstSightKnownLearnsImmediately() {
        val userId = createUser(dailyLimit = 30)
        val remaining = singleWordQueue(userId)

        val scenario = launchStudy()
        onView(withId(R.id.tvCardWord)).check(matches(withText(remaining.word)))

        // 初见点「认识」:直接写入学习记录并结束会话
        onView(withId(R.id.cardWord)).perform(click())
        onView(withId(R.id.btnKnown)).perform(click())

        assertEquals(1, db.getTodayLearnedCount(userId))
        awaitDestroyed(scenario)
    }

    @Test
    fun unknownThenTwoKnownsFinishSingleWordSession() {
        val userId = createUser(dailyLimit = 30)
        val remaining = singleWordQueue(userId)

        val scenario = launchStudy()
        onView(withId(R.id.tvCardWord)).check(matches(withText(remaining.word)))

        // 不认识:留在队列中
        onView(withId(R.id.cardWord)).perform(click())
        onView(withId(R.id.btnUnknown)).perform(click())
        assertEquals(0, db.getTodayLearnedCount(userId))
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
    fun unknownThenKnownAppendsColorSquares() {
        val userId = createUser(dailyLimit = 30)
        singleWordQueue(userId)

        launchStudy().use {
            // 第一次不认识:1 个红色方格
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.btnUnknown)).perform(click())
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.tapHistory)).check(matches(hasChildCount(1)))

            // 再次认识:追加 1 个蓝色方格,累计 2 个
            onView(withId(R.id.btnKnown)).perform(click())
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.tapHistory)).check(matches(hasChildCount(2)))
        }
    }

    @Test
    fun tapHistoryClearsWhenSessionRestarts() {
        val userId = createUser(dailyLimit = 30)
        singleWordQueue(userId)

        launchStudy().use {
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.btnUnknown)).perform(click())
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.tapHistory)).check(matches(hasChildCount(1)))
        }

        // 退出背词界面后重进:会话重建,色块按会话清空
        launchStudy().use {
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.tapHistory)).check(matches(hasChildCount(0)))
        }
    }

    @Test
    fun unknownClickPersistsTodayFailCount() {
        val userId = createUser(dailyLimit = 30)
        val remaining = singleWordQueue(userId)

        launchStudy().use {
            onView(withId(R.id.tvCardWord)).check(matches(withText(remaining.word)))
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.btnUnknown)).perform(click())

            // 点「不认识」即时入库,且尚未学会
            assertEquals(
                1,
                db.getWordFailCount(userId, remaining.id, AppDatabaseHelper.todayDateString())
            )
            assertEquals(0, db.getTodayLearnedCount(userId))
        }
    }

    @Test
    fun learningWritesTodayFailCountIntoStudyRecord() {
        val userId = createUser(dailyLimit = 30)
        val remaining = singleWordQueue(userId)

        launchStudy().use {
            // 不认识 1 次 → 连续认识 2 次学会
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.btnUnknown)).perform(click())
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.btnKnown)).perform(click())
            onView(withId(R.id.cardWord)).perform(click())
            onView(withId(R.id.btnKnown)).perform(click())
            assertEquals(1, db.getTodayLearnedCount(userId))
        }

        // 学习记录携带当日失败次数 1 → 统计归「黄」
        assertEquals(1, db.getWordFailCount(userId, remaining.id, AppDatabaseHelper.todayDateString()))
        val stats = db.getTodayStats(userId)
        assertEquals(1, stats.yellow)
        assertEquals(0, stats.green)
        assertEquals(0, stats.red)
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

    /**
     * 除最后一个单词外,其余均标记为「昨天已学会」,使队列只剩最后一张卡;
     * 同时返回该卡单词,便于断言。
     */
    private fun singleWordQueue(userId: Long) = db.getAllWords().last().also { remaining ->
        val yesterday = db.startOfTodayMillis() - 1000L
        db.getAllWords().dropLast(1).forEach { db.insertStudyRecord(userId, it.id, yesterday) }
        assertEquals(0, db.getTodayLearnedCount(userId))
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
