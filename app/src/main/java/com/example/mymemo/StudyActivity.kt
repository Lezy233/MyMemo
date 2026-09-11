package com.example.mymemo

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.Word
import com.example.mymemo.study.StudyQueue
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 背词学习界面:卡片翻转 + 「认识/不认识」判定。
 *
 * 会话队列为内存态:中途退出会丢失队列中间进度,已学会单词(学习记录)不受影响。
 */
class StudyActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var cardWord: LinearLayout
    private lateinit var tvCardWord: TextView
    private lateinit var tvCardMeaning: TextView
    private lateinit var tvFlipHint: TextView
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

    /** 组建学习队列;已达上限或无未学单词时给出提示并结束。 */
    private fun startSession() {
        if (todayLearned >= dailyLimit) {
            finishWithMessage(getString(R.string.study_limit_reached))
            return
        }
        val words: List<Word> = db.getUnlearnedWords(userId)
        val queue = StudyQueue(words)
        if (queue.isEmpty) {
            finishWithMessage(getString(R.string.study_no_words))
            return
        }
        session = queue
        showCurrentCard()
    }

    /** 展示队首单词:只显示英文,释义与按钮隐藏。 */
    private fun showCurrentCard() {
        val item = session?.current ?: return
        tvCardWord.text = item.word.word
        tvCardMeaning.text = item.word.meaning
        tvCardMeaning.visibility = View.GONE
        tvFlipHint.visibility = View.VISIBLE
        btnKnown.visibility = View.GONE
        btnUnknown.visibility = View.GONE
    }

    /** 点击卡片翻转:显示释义与「认识/不认识」按钮。 */
    private fun flipCard() {
        if (tvCardMeaning.visibility == View.VISIBLE) return
        tvCardMeaning.visibility = View.VISIBLE
        tvFlipHint.visibility = View.GONE
        btnKnown.visibility = View.VISIBLE
        btnUnknown.visibility = View.VISIBLE
    }

    /** 对队首单词作答,并处理学会入账与会话结束条件。 */
    private fun answer(known: Boolean) {
        if (finished) return
        val queue = session ?: return
        val answered = queue.current?.word ?: return

        val learned = queue.answer(known)
        Log.d(
            TAG,
            "answer known=$known word=${answered.word} learned=$learned " +
                "today=$todayLearned queue=${queue.snapshot()}"
        )

        if (learned) {
            db.insertStudyRecord(userId, answered.id)
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val TAG = "StudyActivity"
    }
}
