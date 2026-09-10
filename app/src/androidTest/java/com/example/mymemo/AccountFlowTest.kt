package com.example.mymemo

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 账号流程端到端走查(任务 7.1):
 * 启动 → 注册(含失败场景) → 登录(含失败场景) → 主界面展示 → 退出登录 → 重新登录。
 *
 * 运行方式(需已连接真机或启动模拟器):
 * `./gradlew connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class AccountFlowTest {

    private val username = "walker"
    private val password = "pw12345"
    private val avatar = "avatar_3"

    @Before
    fun clearUsers() {
        AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
            .writableDatabase.delete(AppDatabaseHelper.TABLE_USERS, null, null)
    }

    @After
    fun tearDown() {
        AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
            .writableDatabase.delete(AppDatabaseHelper.TABLE_USERS, null, null)
    }

    @Test
    fun fullAccountWalkthrough() {
        ActivityScenario.launch(LoginActivity::class.java).use {

            // 1) 冷启动显示登录界面
            onView(withId(R.id.login_title)).check(matches(isDisplayed()))

            // 2) 注册:必填项缺失场景(未选头像)被拒绝
            onView(withId(R.id.tvGoRegister)).perform(click())
            onView(withId(R.id.register_title)).check(matches(isDisplayed()))
            onView(withId(R.id.etUsername)).perform(typeText(username))
            onView(withId(R.id.etPassword)).perform(typeText(password))
            closeSoftKeyboard()
            onView(withId(R.id.btnRegister)).perform(click())
            onView(withId(R.id.register_title)).check(matches(isDisplayed()))

            // 3) 注册成功
            onView(withContentDescription(avatar)).perform(click())
            onView(withId(R.id.btnRegister)).perform(click())
            onView(withId(R.id.login_title)).check(matches(isDisplayed()))

            // 4) 注册后重复用户名被拒绝
            onView(withId(R.id.tvGoRegister)).perform(click())
            onView(withId(R.id.etUsername)).perform(typeText(username))
            onView(withId(R.id.etPassword)).perform(typeText("another"))
            closeSoftKeyboard()
            onView(withContentDescription(avatar)).perform(click())
            onView(withId(R.id.btnRegister)).perform(click())
            onView(withId(R.id.register_title)).check(matches(isDisplayed()))
            onView(withId(R.id.tvGoLogin)).perform(click())
            onView(withId(R.id.login_title)).check(matches(isDisplayed()))

            // 5) 登录失败:密码错误
            onView(withId(R.id.etUsername)).perform(typeText(username))
            onView(withId(R.id.etPassword)).perform(typeText("wrong"))
            closeSoftKeyboard()
            onView(withId(R.id.btnLogin)).perform(click())
            onView(withId(R.id.login_title)).check(matches(isDisplayed()))

            // 6) 登录失败:用户名不存在
            clearAndType(R.id.etUsername, "nobody")
            clearAndType(R.id.etPassword, password)
            closeSoftKeyboard()
            onView(withId(R.id.btnLogin)).perform(click())
            onView(withId(R.id.login_title)).check(matches(isDisplayed()))

            // 7) 登录成功 → 主界面显示用户名、头像与进度环
            clearAndType(R.id.etUsername, username)
            clearAndType(R.id.etPassword, password)
            closeSoftKeyboard()
            onView(withId(R.id.btnLogin)).perform(click())
            onView(withId(R.id.progressRing)).check(matches(isDisplayed()))
            onView(withId(R.id.ivAvatar)).check(matches(isDisplayed()))
            onView(withId(R.id.tvUsername))
                .check(matches(withText("你好，$username")))
            val mainActivity = currentResumedActivity()
            assertTrue("登录成功后应处于主界面", mainActivity is MainActivity)

            // 8) 退出登录 → 回到登录界面,且无法通过返回键回到主界面
            onView(withId(R.id.btnLogout)).perform(click())
            onView(withId(R.id.login_title)).check(matches(isDisplayed()))
            assertTrue("退出后应回到登录界面", currentResumedActivity() is LoginActivity)

            // 退出后返回键只会关闭登录界面,主界面已被销毁,无法再返回
            pressBackUnconditionally()
            assertEquals(
                "退出后主界面应已被销毁",
                Stage.DESTROYED,
                stageOf(mainActivity!!)
            )
        }
    }

    private fun currentResumedActivity(): Activity? {
        var activity: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull()
        }
        return activity
    }

    private fun stageOf(activity: Activity): Stage? {
        var stage: Stage? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            stage = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity)
        }
        return stage
    }

    private fun clearAndType(viewId: Int, text: String) {
        onView(withId(viewId)).perform(
            androidx.test.espresso.action.ViewActions.clearText(),
            typeText(text)
        )
    }

    /**
     * 主界面在活动重建(等同旋转屏幕/返回重进)后仍展示同一份用户名、头像与进度数据。
     */
    @Test
    fun mainScreenKeepsUserInfoAndProgressAfterRecreation() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_USERNAME, username)
            .putExtra(MainActivity.EXTRA_AVATAR, avatar)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            onView(withId(R.id.tvUsername)).check(matches(withText("你好，$username")))
            onView(withId(R.id.ivAvatar)).check(matches(isDisplayed()))
            onView(withId(R.id.progressRing)).check(
                matches(ringProgress(0, ProgressRing.DEFAULT_MAX))
            )

            scenario.recreate()

            onView(withId(R.id.tvUsername)).check(matches(withText("你好，$username")))
            onView(withId(R.id.ivAvatar)).check(matches(isDisplayed()))
            onView(withId(R.id.progressRing)).check(
                matches(ringProgress(0, ProgressRing.DEFAULT_MAX))
            )
        }
    }

    private fun ringProgress(current: Int, max: Int): Matcher<android.view.View> =
        object : org.hamcrest.TypeSafeMatcher<android.view.View>() {
            override fun describeTo(description: org.hamcrest.Description) {
                description.appendText("进度环显示 $current/$max")
            }

            override fun matchesSafely(item: android.view.View): Boolean {
                val ring = item as StudyProgressRingView
                return ring.currentValue == current && ring.maxValue == max
            }
        }
}
