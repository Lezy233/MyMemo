package com.example.mymemo

import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.Word
import com.example.mymemo.study.StudySession
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 今日单词队列界面(任务 3.4):
 * - 「今日已学会」:查询当日学习记录;
 * - 「待背单词」:优先读取当前内存学习会话的队列(与背词界面共享),
 *   无活跃会话时按当日剩余额度从未学会单词中随机截取。
 *
 * 队列容量不超过当日剩余额度;上限提升后再次进入或刷新会补充新词。
 */
class WordQueueActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var learnedAdapter: WordAdapter
    private lateinit var pendingAdapter: WordAdapter

    private var userId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_queue)

        db = AppDatabaseHelper.getInstance(this)
        progressRing = findViewById(R.id.progressRing)
        userId = db.findUserByUsername(
            intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()
        )?.id ?: -1L

        learnedAdapter = WordAdapter(this, emptyList())
        pendingAdapter = WordAdapter(this, emptyList())
        findViewById<ListView>(R.id.listLearned).adapter = learnedAdapter
        findViewById<ListView>(R.id.listPending).adapter = pendingAdapter

        reload()
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
        // 从上限设置界面返回时,按新额度重新计算待背队列
        reload()
    }

    private fun reload() {
        if (userId <= 0) return
        learnedAdapter.submit(db.getTodayLearnedWords(userId))
        pendingAdapter.submit(pendingWords())
    }

    /** 待背单词:有活跃内存会话则读取并补充到剩余额度,否则重新组建。 */
    private fun pendingWords(): List<Word> {
        val remaining = remainingQuota()
        val unlearned = db.getUnlearnedWords(userId)
        val active = StudySession.current()
        if (active != null && StudySession.ownerId() == userId) {
            StudySession.supplement(unlearned, remaining)
            return active.currentWords()
        }
        return unlearned.shuffled().take(remaining)
    }

    private fun remainingQuota(): Int {
        val limit = db.getDailyLimit(userId)
        return (limit - db.getTodayLearnedCount(userId)).coerceAtLeast(0)
    }

    private fun refreshProgress() {
        if (userId <= 0) {
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            return
        }
        progressRing.setProgress(db.getTodayLearnedCount(userId), db.getDailyLimit(userId))
    }
}
