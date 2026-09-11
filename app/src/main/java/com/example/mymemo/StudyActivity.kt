package com.example.mymemo

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.study.StudyQueue
import com.example.mymemo.study.StudySession
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 背词学习界面:卡片翻转 + 「认识/不认识」判定。
 *
 * 会话队列为内存态(见 [StudySession]):每次进入本界面都会重建队列,
 * 因此中途退出会丢失队列中间进度与点击色块,已学会单词(学习记录)不受影响。
 */
class StudyActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var cardWord: LinearLayout
    private lateinit var tvCardWord: TextView
    private lateinit var tvCardMeaning: TextView
    private lateinit var tvFlipHint: TextView
    private lateinit var tapHistory: LinearLayout
    private lateinit var btnKnown: Button
    private lateinit var btnUnknown: Button

    private var userId: Long = -1L
    private var dailyLimit: Int = AppDatabaseHelper.DEFAULT_DAILY_LIMIT
    private var todayLearned: Int = 0

    private var session: StudyQueue? = null
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study)

        db = AppDatabaseHelper.getInstance(this)
        progressRing = findViewById(R.id.progressRing)
        cardWord = findViewById(R.id.cardWord)
        tvCardWord = findViewById(R.id.tvCardWord)
        tvCardMeaning = findViewById(R.id.tvCardMeaning)
        tvFlipHint = findViewById(R.id.tvFlipHint)
        tapHistory = findViewById(R.id.tapHistory)
        btnKnown = findViewById(R.id.btnKnown)
        btnUnknown = findViewById(R.id.btnUnknown)

        val user = db.findUserByUsername(
            intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()
        )
        if (user == null) {
            finish()
            return
        }
        userId = user.id
        dailyLimit = user.dailyLimit
        todayLearned = db.getTodayLearnedCount(userId)

        cardWord.setOnClickListener { flipCard() }
        btnKnown.setOnClickListener { answer(true) }
        btnUnknown.setOnClickListener { answer(false) }

        startSession()
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
    }

    /**
     * 组建学习队列:查询未学会单词,按当日剩余额度(上限 - 已背)随机截取。
     * 已达上限或无未学单词时给出提示并结束。
     */
    private fun startSession() {
        val remaining = todayLearned.let { (dailyLimit - it).coerceAtLeast(0) }
        if (remaining <= 0) {
            finishWithMessage(getString(R.string.study_limit_reached))
            return
        }
        val words = db.getUnlearnedWords(userId)
        val queue = StudySession.startNew(userId, words, remaining)
        if (queue.isEmpty) {
            finishWithMessage(getString(R.string.study_no_words))
            return
        }
        session = queue
        Log.d(TAG, "session start remaining=$remaining queue=${queue.snapshot()}")
        showCurrentCard()
    }

    /** 展示队首单词:只显示英文,释义、点击色块与按钮隐藏。 */
    private fun showCurrentCard() {
        val item = session?.current ?: return
        tvCardWord.text = item.word.word
        tvCardMeaning.text = item.word.meaning
        tvCardMeaning.visibility = View.GONE
        tvFlipHint.visibility = View.VISIBLE
        tapHistory.visibility = View.GONE
        btnKnown.visibility = View.GONE
        btnUnknown.visibility = View.GONE
    }

    /** 点击卡片翻转:显示释义、本次会话点击色块与「认识/不认识」按钮。 */
    private fun flipCard() {
        if (tvCardMeaning.visibility == View.VISIBLE) return
        tvCardMeaning.visibility = View.VISIBLE
        tvFlipHint.visibility = View.GONE
        tapHistory.visibility = View.VISIBLE
        renderTapHistory()
        btnKnown.visibility = View.VISIBLE
        btnUnknown.visibility = View.VISIBLE
    }

    /**
     * 按点击顺序渲染本次会话的色块:蓝色 = 认识,红色 = 不认识。
     * 单词被插回队列后再次出现时,历史色块依然保留。
     */
    private fun renderTapHistory() {
        tapHistory.removeAllViews()
        val item = session?.current ?: return
        item.tapHistory.forEach { known ->
            val colorRes = if (known) R.color.tap_known else R.color.tap_unknown
            val square = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(SQUARE_SIZE_DP), dp(SQUARE_SIZE_DP))
                    .apply { marginEnd = dp(SQUARE_MARGIN_DP) }
                setBackgroundColor(ContextCompat.getColor(this@StudyActivity, colorRes))
            }
            tapHistory.addView(square)
        }
    }

    /** 对队首单词作答,并处理学会入账与会话结束条件。 */
    private fun answer(known: Boolean) {
        if (finished) return
        val queue = session ?: return
        val item = queue.current ?: return

        val learned = queue.answer(known)
        Log.d(
            TAG,
            "answer known=$known word=${item.word.word} learned=$learned " +
                "failedToday=${item.failedToday} streak=${item.streak} " +
                "taps=${item.tapHistory} today=$todayLearned queue=${queue.snapshot()}"
        )

        if (learned) {
            db.insertStudyRecord(userId, item.word.id)
            todayLearned += 1
            refreshProgress()
            if (todayLearned >= dailyLimit) {
                finishWithMessage(getString(R.string.study_limit_reached))
                return
            }
        }

        if (queue.isEmpty) {
            finishWithMessage(getString(R.string.study_finished))
            return
        }
        showCurrentCard()
    }

    private fun refreshProgress() {
        if (userId <= 0) {
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            return
        }
        progressRing.setProgress(todayLearned, dailyLimit)
    }

    private fun finishWithMessage(message: String) {
        finished = true
        // 会话结束:清空内存队列(点击色块随之丢弃)
        StudySession.clear()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "StudyActivity"
        private const val SQUARE_SIZE_DP = 16
        private const val SQUARE_MARGIN_DP = 4
    }
}
