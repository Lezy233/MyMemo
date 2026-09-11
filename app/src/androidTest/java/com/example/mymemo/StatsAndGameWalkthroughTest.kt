package com.example.mymemo

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.widget.StudyPieChartView
import com.example.mymemo.widget.StudyProgressRingView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 全流程走查(任务 6.1):背词制造绿/黄/红/灰 → 统计饼图一致 → 玩游戏赢额度 →
 * 兑换提升上限 → 继续背词 → 饼图与进度环同步更新。
 */
@RunWith(AndroidJUnit4::class)
class StatsAndGameWalkthroughTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "walkthrough_ui"

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
    fun fullLoopKeepsStatsAndProgressConsistent() {
        val userId = userId()
        db.setDailyLimit(userId, 5)

        val words = db.getAllWords()
        val yesterday = db.startOfTodayMillis() - 1000L
        // 保留最后 6 个单词为未学会,其余标记为昨天已学会
        words.dropLast(6).forEach { db.insertStudyRecord(userId, it.id, yesterday) }
        val pending = words.takeLast(6)

        // 背词:绿(0 失败)、黄(1 失败)、红(2 失败后仍学会)
        db.insertStudyRecord(userId, pending[0].id, failCount = 0)
        db.insertStudyRecord(userId, pending[1].id, failCount = 1)
        repeat(2) { db.incrementWordFailCount(userId, pending[2].id) }
        db.insertStudyRecord(userId, pending[2].id, failCount = 2)

        // 今日已背 3,上限 5,未学会 3 → 待背灰 = min(2, 3) = 2
        var stats = db.getTodayStats(userId)
        assertEquals(1, stats.green)
        assertEquals(1, stats.yellow)
        assertEquals(1, stats.red)
        assertEquals(2, stats.gray)

        launchStats().use {
            onView(withId(R.id.pieChart)).check(matches(pieStats(1, 1, 1, 2)))
            onView(withId(R.id.progressRing)).check(matches(ringProgress(3, 5)))
        }

        // 玩游戏赢额度 7,兑换 2 提额
        assertTrue(db.addQuotaCredit(userId, 7))
        assertTrue(db.exchangeQuotaForLimit(userId, 2))
        assertEquals(5, db.getQuotaCredit(userId))
        assertEquals(7, db.getDailyLimit(userId))

        // 兑换后继续背词:再学会一个
        db.insertStudyRecord(userId, pending[3].id, failCount = 0)

        // 玩贪吃蛇:确认三角形方向键的十字布局,游戏界面进度环与其余界面一致(4/7)
        launchSnake().use {
            onView(withId(R.id.dirPad)).check(matches(isDisplayed()))
            onView(withId(R.id.btnUp)).check(matches(isDisplayed()))
            onView(withId(R.id.btnLeft)).check(matches(isDisplayed()))
            onView(withId(R.id.btnRight)).check(matches(isDisplayed()))
            onView(withId(R.id.btnDown)).check(matches(isDisplayed()))
            onView(withId(R.id.progressRing)).check(matches(ringProgress(4, 7)))
        }

        // 今日已背 4,上限 7,未学会 2 → 灰 = min(3, 2) = 2,绿 +1
        stats = db.getTodayStats(userId)
        assertEquals(2, stats.green)
        assertEquals(1, stats.yellow)
        assertEquals(1, stats.red)
        assertEquals(2, stats.gray)

        // 主界面:额度余额与进度环同步为 4/7
        launchMain().use {
            onView(withId(R.id.tvQuota)).check(
                matches(withText(context().getString(R.string.quota_balance_format, 5)))
            )
            onView(withId(R.id.progressRing)).check(matches(ringProgress(4, 7)))
        }

        // 统计界面:饼图与数据库同步,进度环与主界面/游戏界面一致(4/7)
        launchStats().use {
            onView(withId(R.id.pieChart)).check(matches(pieStats(2, 1, 1, 2)))
            onView(withId(R.id.tvStatsTotal)).check(
                matches(withText(context().getString(R.string.stats_total_format, 6)))
            )
            onView(withId(R.id.progressRing)).check(matches(ringProgress(4, 7)))
        }
    }

    private fun launchSnake(): ActivityScenario<SnakeGameActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            SnakeGameActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }

    private fun launchStats(): ActivityScenario<StatsActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StatsActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }

    private fun launchMain(): ActivityScenario<MainActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            MainActivity::class.java
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

    private fun ringProgress(current: Int, max: Int): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("进度环显示 $current/$max")
            }

            override fun matchesSafely(item: View): Boolean {
                val ring = item as StudyProgressRingView
                return ring.currentValue == current && ring.maxValue == max
            }
        }
}
