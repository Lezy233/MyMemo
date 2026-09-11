package com.example.mymemo

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
 * 主界面每日上限设置测试(任务 4.1):
 * 非法值被拒绝、合法值保存后立即生效并刷新进度环。
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLimitTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "limit_ui"

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
        db.writableDatabase.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    @Test
    fun invalidValueRejectedThenValidValueAppliesImmediately() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            MainActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)

        ActivityScenario.launch<MainActivity>(intent).use {
            onView(withId(R.id.progressRing)).check(matches(ringProgress(0, 30)))

            // 非法值(0)被拒绝:对话框保持打开,上限不变
            onView(withId(R.id.btnDailyLimit)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).perform(clearText(), typeText("0"))
            onView(withText(R.string.btn_save)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).check(matches(isDisplayed()))
            assertEquals(30, db.getDailyLimit(db.findUserByUsername(username)!!.id))

            // 合法值 50 保存后立即生效
            onView(isAssignableFrom(EditText::class.java)).perform(clearText(), typeText("50"))
            onView(withText(R.string.btn_save)).perform(click())
            onView(withId(R.id.progressRing)).check(matches(ringProgress(0, 50)))
            assertEquals(50, db.getDailyLimit(db.findUserByUsername(username)!!.id))
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
