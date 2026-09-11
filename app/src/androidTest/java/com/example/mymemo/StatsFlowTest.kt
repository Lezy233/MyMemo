package com.example.mymemo

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.widget.StudyPieChartView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 统计界面端到端测试(任务 3.1 / 3.2):
 * 无数据时提示替代空饼图、饼图四类数量与数据库一致、数据更新后 onResume 重绘。
 */
@RunWith(AndroidJUnit4::class)
class StatsFlowTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "stats_ui"

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        cleanup()
        db.insertUser(username, "pw", "avatar_1")
    }

    @After
    fun tearDown() {
        cleanup()
    }

    private fun cleanup() {
        val database = db.writableDatabase
        database.delete(AppDatabaseHelper.TABLE_WORD_DAILY_FAILS, null, null)
        database.delete(AppDatabaseHelper.TABLE_STUDY_RECORDS, null, null)
        database.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    private fun userId(): Long = db.findUserByUsername(username)!!.id

    @Test
    fun noDataShowsHintInsteadOfEmptyPie() {
        // 所有单词标记为「昨天已学会」→ 今日无记录且无待背词
        val userId = userId()
        val yesterday = db.startOfTodayMillis() - 1000L
        db.getAllWords().forEach { db.insertStudyRecord(userId, it.id, yesterday) }

        launchStats().use {
            onView(withId(R.id.tvStatsEmpty)).check(matches(isDisplayed()))
            onView(withId(R.id.pieChart)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun pieChartMatchesDatabaseFourCategories() {
        val userId = userId()
        db.setDailyLimit(userId, 30)
        val words = db.getAllWords()

        db.insertStudyRecord(userId, words[0].id, failCount = 0) // 绿
        db.insertStudyRecord(userId, words[1].id, failCount = 1) // 黄
        repeat(2) { db.incrementWordFailCount(userId, words[2].id) }
        db.insertStudyRecord(userId, words[2].id, failCount = 2) // 红(已学会)
        repeat(2) { db.incrementWordFailCount(userId, words[3].id) } // 红(未学会)

        val expected = db.getTodayStats(userId)
        assertEquals(1, expected.green)
        assertEquals(1, expected.yellow)
        assertEquals(2, expected.red)

        launchStats().use {
            onView(withId(R.id.tvStatsEmpty)).check(matches(not(isDisplayed())))
            onView(withId(R.id.pieChart)).check(matches(isDisplayed()))
            onView(withId(R.id.pieChart)).check(
                matches(
                    pieStats(
                        expected.green,
                        expected.yellow,
                        expected.red,
                        expected.gray
                    )
                )
            )
            onView(withId(R.id.tvStatsTotal)).check(
                matches(withText(context().getString(R.string.stats_total_format, expected.total)))
            )
        }
    }

    @Test
    fun pieReloadsWhenNewDataAppears() {
        val userId = userId()
        val yesterday = db.startOfTodayMillis() - 1000L
        val words = db.getAllWords()
        words.forEach { db.insertStudyRecord(userId, it.id, yesterday) }

        val scenario = launchStats()
        scenario.use {
            onView(withId(R.id.tvStatsEmpty)).check(matches(isDisplayed()))

            // 产生今日新数据后重新进入(onResume 重查重绘)
            db.insertStudyRecord(userId, words.first().id, failCount = 0)
            scenario.recreate()

            onView(withId(R.id.tvStatsEmpty)).check(matches(not(isDisplayed())))
            onView(withId(R.id.pieChart)).check(matches(isDisplayed()))
            onView(withId(R.id.pieChart)).check(matches(pieStats(1, 0, 0, 0)))
        }
    }

    private fun launchStats(): ActivityScenario<StatsActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StatsActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun pieStats(green: Int, yellow: Int, red: Int, gray: Int): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("饼图显示 绿$green 黄$yellow 红$red 灰$gray")
            }

            override fun matchesSafely(item: View): Boolean {
                val stats = (item as StudyPieChartView).statsValue
                return stats.green == green && stats.yellow == yellow &&
                    stats.red == red && stats.gray == gray
            }
        }
}
