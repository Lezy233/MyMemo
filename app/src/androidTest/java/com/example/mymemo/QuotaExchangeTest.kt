package com.example.mymemo

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.widget.StudyProgressRingView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 额度兑换端到端测试(任务 5.1):
 * 非法数量与余额不足被拒绝、合法兑换立即扣余额并提升上限。
 */
@RunWith(AndroidJUnit4::class)
class QuotaExchangeTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "quota_ui"

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        cleanup()
        db.insertUser(username, "pw", "avatar_1")
        db.addQuotaCredit(db.findUserByUsername(username)!!.id, 20)
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
    fun invalidAndInsufficientAreRejectedThenValidApplies() {
        val userId = userId()

        launchMain().use {
            // 初始余额 20,进度环 0/30
            onView(withId(R.id.tvQuota)).check(
                matches(withText(context().getString(R.string.quota_balance_format, 20)))
            )
            onView(withId(R.id.progressRing)).check(matches(ringProgress(0, 30)))

            // 非法数量 0 → 拒绝,余额与上限不变,对话框保持打开
            onView(withId(R.id.btnExchangeLimit)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).perform(clearText(), typeText("0"))
            onView(withText(R.string.btn_save)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).check(matches(isDisplayed()))
            assertEquals(20, db.getQuotaCredit(userId))
            assertEquals(30, db.getDailyLimit(userId))

            // 余额不足(999 > 20)→ 拒绝
            onView(isAssignableFrom(EditText::class.java)).perform(clearText(), typeText("999"))
            onView(withText(R.string.btn_save)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).check(matches(isDisplayed()))
            assertEquals(20, db.getQuotaCredit(userId))
            assertEquals(30, db.getDailyLimit(userId))

            // 合法 5 → 余额 15,上限 35,界面即时刷新
            onView(isAssignableFrom(EditText::class.java)).perform(clearText(), typeText("5"))
            onView(withText(R.string.btn_save)).perform(click())
            onView(withId(R.id.tvQuota)).check(
                matches(withText(context().getString(R.string.quota_balance_format, 15)))
            )
            onView(withId(R.id.progressRing)).check(matches(ringProgress(0, 35)))
            assertEquals(15, db.getQuotaCredit(userId))
            assertEquals(35, db.getDailyLimit(userId))
        }
    }

    private fun launchMain(): ActivityScenario<MainActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            MainActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

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
