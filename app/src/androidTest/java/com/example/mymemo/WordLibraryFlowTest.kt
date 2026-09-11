package com.example.mymemo

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.Word
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词库管理界面端到端测试(任务 2.2 / 2.3):
 * 添加(含空值、重复被拒)、编辑释义、删除确认并持久化。
 */
@RunWith(AndroidJUnit4::class)
class WordLibraryFlowTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "lib_tester"
    private val testWord = "zzz_flow_word"

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        db.writableDatabase.delete(
            AppDatabaseHelper.TABLE_WORDS,
            "${AppDatabaseHelper.COLUMN_WORD} = ?",
            arrayOf(testWord)
        )
        db.writableDatabase.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
        db.insertUser(username, "pw", "avatar_1")
    }

    @After
    fun tearDown() {
        db.writableDatabase.delete(
            AppDatabaseHelper.TABLE_WORDS,
            "${AppDatabaseHelper.COLUMN_WORD} = ?",
            arrayOf(testWord)
        )
        db.writableDatabase.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    @Test
    fun addEditDeleteWord() {
        launchLibrary().use {
            onView(withId(R.id.progressRing)).check(matches(isDisplayed()))

            // 添加成功
            onView(withId(R.id.btnAddWord)).perform(click())
            onView(withId(R.id.etWord)).perform(typeText(testWord))
            onView(withId(R.id.etMeaning)).perform(replaceText("流程测试释义"))
            closeSoftKeyboard()
            onView(withText(R.string.btn_save)).perform(click())

            onData(wordWithText(testWord)).inAdapterView(withId(R.id.listWords))
                .check(matches(isDisplayed()))
            assertNotNull(db.findWordByText(testWord))

            // 编辑释义
            onData(wordWithText(testWord)).inAdapterView(withId(R.id.listWords)).perform(click())
            onView(withId(R.id.etMeaning)).perform(replaceText("修改后的释义"))
            closeSoftKeyboard()
            onView(withText(R.string.btn_save)).perform(click())
            assertEquals("修改后的释义", db.findWordByText(testWord)!!.meaning)

            // 删除(二次确认)
            onData(wordWithText(testWord)).inAdapterView(withId(R.id.listWords)).perform(click())
            onView(withText(R.string.btn_delete)).perform(click())
            onView(withText(R.string.btn_confirm_delete)).perform(click())
            assertNull(db.findWordByText(testWord))
        }
    }

    @Test
    fun rejectsEmptyAndDuplicateWord() {
        launchLibrary().use {
            // 空值被拒绝:对话框保持打开
            onView(withId(R.id.btnAddWord)).perform(click())
            onView(withText(R.string.btn_save)).perform(click())
            onView(withId(R.id.etWord)).check(matches(isDisplayed()))
            onView(withText(R.string.btn_cancel)).perform(click())

            // 重复单词被拒绝:对话框保持打开,数据库词条数不变
            val before = db.getAllWords().size
            onView(withId(R.id.btnAddWord)).perform(click())
            onView(withId(R.id.etWord)).perform(typeText("abandon"))
            onView(withId(R.id.etMeaning)).perform(replaceText("重复释义"))
            closeSoftKeyboard()
            onView(withText(R.string.btn_save)).perform(click())
            onView(withId(R.id.etWord)).check(matches(isDisplayed()))
            onView(withText(R.string.btn_cancel)).perform(click())
            assertEquals(before, db.getAllWords().size)
        }
    }

    private fun launchLibrary(): ActivityScenario<WordLibraryActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            WordLibraryActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }

    private fun wordWithText(text: String): Matcher<Word> = object : TypeSafeMatcher<Word>() {
        override fun describeTo(description: Description) {
            description.appendText("单词为 $text")
        }

        override fun matchesSafely(item: Word): Boolean = item.word == text
    }
}
