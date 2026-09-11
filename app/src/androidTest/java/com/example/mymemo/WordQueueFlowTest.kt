package com.example.mymemo

import android.content.Intent
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.study.StudySession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 今日单词队列界面测试(任务 3.4):
 * 分区展示今日已学会与待背单词、队列容量不超过剩余额度。
 */
@RunWith(AndroidJUnit4::class)
class WordQueueFlowTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "queue_tester"

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        StudySession.clear()
        clearData()
        db.insertUser(username, "pw", "avatar_1")
    }

    @After
    fun tearDown() {
        StudySession.clear()
        clearData()
    }

    private fun clearData() {
        db.writableDatabase.delete(AppDatabaseHelper.TABLE_STUDY_RECORDS, null, null)
        db.writableDatabase.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    @Test
    fun showsLearnedAndPendingSections() {
        val userId = userId()
        db.setDailyLimit(userId, 10)
        // 今日已学会 2 个单词
        db.getAllWords().take(2).forEach { db.insertStudyRecord(userId, it.id) }

        launchQueue().use { scenario ->
            scenario.onActivity { activity ->
                val learned = activity.findViewById<ListView>(R.id.listLearned)
                val pending = activity.findViewById<ListView>(R.id.listPending)
                assertEquals(2, learned.adapter.count)
                // 剩余额度 = 10 - 2 = 8
                assertEquals(8, pending.adapter.count)
            }
        }
    }

    @Test
    fun pendingCapacityDoesNotExceedRemainingQuota() {
        val userId = userId()
        db.setDailyLimit(userId, 3)
        db.insertStudyRecord(userId, db.getAllWords().first().id)

        launchQueue().use { scenario ->
            scenario.onActivity { activity ->
                val pending = activity.findViewById<ListView>(R.id.listPending)
                assertEquals(2, pending.adapter.count)
            }
        }
    }

    @Test
    fun limitIncreaseSupplementsPendingQueue() {
        val userId = userId()
        db.setDailyLimit(userId, 3)
        db.insertStudyRecord(userId, db.getAllWords().first().id)

        launchQueue().use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(2, activity.findViewById<ListView>(R.id.listPending).adapter.count)
            }

            // 上限提升后重新进入界面:按新剩余额度补充新词
            db.setDailyLimit(userId, 5)
            scenario.recreate()

            scenario.onActivity { activity ->
                assertEquals(4, activity.findViewById<ListView>(R.id.listPending).adapter.count)
            }
        }
    }

    @Test
    fun progressRingReflectsRealData() {
        val userId = userId()
        db.setDailyLimit(userId, 30)
        db.getAllWords().take(5).forEach { db.insertStudyRecord(userId, it.id) }

        launchQueue().use { scenario ->
            scenario.onActivity { activity ->
                val ring = activity.findViewById<com.example.mymemo.widget.StudyProgressRingView>(
                    R.id.progressRing
                )
                assertEquals(5, ring.currentValue)
                assertEquals(30, ring.maxValue)
                assertTrue(ring.currentValue <= ring.maxValue)
            }
        }
    }

    private fun userId(): Long = db.findUserByUsername(username)!!.id

    private fun launchQueue(): ActivityScenario<WordQueueActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            WordQueueActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }
}
