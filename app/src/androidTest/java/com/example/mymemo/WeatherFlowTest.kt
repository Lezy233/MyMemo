package com.example.mymemo

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.weather.WeatherFormat
import com.example.mymemo.widget.StudyProgressRingView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 查询历史界面测试(任务 5.1):倒序展示、坐标与当日摘要、无记录提示、进度环与数据库一致。
 */
@RunWith(AndroidJUnit4::class)
class WeatherFlowTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "weather_ui"

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
        database.delete(AppDatabaseHelper.TABLE_WEATHER_QUERIES, null, null)
        database.delete(AppDatabaseHelper.TABLE_STUDY_RECORDS, null, null)
        database.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    @Test
    fun emptyHistoryShowsHint() {
        launchHistory().use {
            onView(withId(R.id.tvEmptyWeatherHistory)).check(matches(isDisplayed()))
            onView(withId(R.id.listWeatherHistory)).check(matches(listCount(0)))
        }
    }

    @Test
    fun historyIsOrderedNewestFirstWithLocationAndSummary() {
        db.insertWeatherQuery(31.19, 121.44, RAW_JSON, queriedAt = 1_000L)
        db.insertWeatherQuery(40.0, 116.0, RAW_JSON, queriedAt = 9_000L)

        launchHistory().use {
            onView(withId(R.id.tvEmptyWeatherHistory)).check(matches(not(isDisplayed())))
            onView(withId(R.id.listWeatherHistory)).check(matches(listCount(2)))

            // 最新一条(时间 9000)排在第 0 位,展示时间、坐标与当日摘要
            onData(anything())
                .inAdapterView(withId(R.id.listWeatherHistory))
                .atPosition(0)
                .onChildView(withId(R.id.tvHistoryTime))
                .check(
                    matches(
                        withText(
                            context().getString(
                                R.string.weather_history_time_format,
                                WeatherFormat.time(9_000L)
                            )
                        )
                    )
                )
            onData(anything())
                .inAdapterView(withId(R.id.listWeatherHistory))
                .atPosition(0)
                .onChildView(withId(R.id.tvHistoryLocation))
                .check(
                    matches(
                        withText(
                            context().getString(
                                R.string.weather_history_location_format,
                                116.0,
                                40.0
                            )
                        )
                    )
                )
            onData(anything())
                .inAdapterView(withId(R.id.listWeatherHistory))
                .atPosition(0)
                .onChildView(withId(R.id.tvHistoryToday))
                .check(
                    matches(
                        withText(
                            context().getString(
                                R.string.weather_history_today_format,
                                "多云",
                                8.0,
                                2.0
                            )
                        )
                    )
                )
        }
    }

    @Test
    fun progressRingReflectsDatabase() {
        val userId = db.findUserByUsername(username)!!.id
        db.setDailyLimit(userId, 20)
        db.insertStudyRecord(userId, db.getAllWords().first().id)

        launchHistory().use {
            onView(withId(R.id.progressRing)).check(matches(ringProgress(1, 20)))
        }
    }

    @Test
    fun unparsableRecordIsShownWithoutCrash() {
        db.insertWeatherQuery(31.19, 121.44, "not-a-json", queriedAt = 1_000L)

        launchHistory().use {
            onView(withId(R.id.listWeatherHistory)).check(matches(listCount(1)))
            onData(anything())
                .inAdapterView(withId(R.id.listWeatherHistory))
                .atPosition(0)
                .onChildView(withId(R.id.tvHistoryToday))
                .check(
                    matches(
                        withText(context().getString(R.string.weather_history_parse_failed))
                    )
                )
        }
    }

    private fun launchHistory(): ActivityScenario<WeatherHistoryActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            WeatherHistoryActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun listCount(expected: Int): Matcher<View> = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("列表有 $expected 条记录")
        }

        override fun matchesSafely(item: View): Boolean =
            (item as ListView).adapter.count == expected
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

    companion object {
        private const val RAW_JSON =
            "{\"daily\":{\"time\":[\"2026-01-02\"],\"weathercode\":[2]," +
                "\"temperature_2m_max\":[8.0],\"temperature_2m_min\":[2.0]}}"
    }
}
