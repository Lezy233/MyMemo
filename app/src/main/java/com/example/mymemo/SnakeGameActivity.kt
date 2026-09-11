package com.example.mymemo

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.game.SnakeGame
import com.example.mymemo.widget.SnakeBoardView
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 贪吃蛇游戏界面(任务 4.1~4.4)。
 *
 * - [Handler] 每约 300ms 调用一次 [SnakeGame.step] 推进对局,[onPause] 停表、[onDestroy] 移除回调;
 * - 四个方向按键更新待生效方向;只负责结算与界面,规则在 [SnakeGame] 中;
 * - 对局结束(失败/胜利)时一次性结算额度并弹结果对话框;中途退出不结算。
 */
class SnakeGameActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var boardView: SnakeBoardView
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var tvScore: TextView

    private var userId: Long = -1L
    private var game: SnakeGame? = null
    private var settled = false

    private val handler = Handler(Looper.getMainLooper())
    private val stepRunnable = Runnable { stepOnce() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snake)

        db = AppDatabaseHelper.getInstance(this)
        boardView = findViewById(R.id.snakeBoard)
        progressRing = findViewById(R.id.progressRing)
        tvScore = findViewById(R.id.tvSnakeScore)

        userId = db.findUserByUsername(
            intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()
        )?.id ?: -1L
        if (userId <= 0) {
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btnUp).setOnClickListener { turn(SnakeGame.Direction.UP) }
        findViewById<ImageButton>(R.id.btnDown).setOnClickListener { turn(SnakeGame.Direction.DOWN) }
        findViewById<ImageButton>(R.id.btnLeft).setOnClickListener { turn(SnakeGame.Direction.LEFT) }
        findViewById<ImageButton>(R.id.btnRight).setOnClickListener { turn(SnakeGame.Direction.RIGHT) }

        startNewGame()
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
        // 从后台返回:移除可能残留的回调后重新计时,避免重复步进
        handler.removeCallbacks(stepRunnable)
        if (game?.status == SnakeGame.Status.RUNNING) {
            handler.postDelayed(stepRunnable, STEP_INTERVAL_MS)
        }
    }

    override fun onPause() {
        super.onPause()
        // 停表:后台不空跑
        handler.removeCallbacks(stepRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(stepRunnable)
    }

    /** 开始新的一局:重置棋盘、得分与结算标记。 */
    private fun startNewGame() {
        val newGame = SnakeGame()
        game = newGame
        settled = false
        boardView.setGame(newGame)
        updateScore()
        handler.removeCallbacks(stepRunnable)
        handler.postDelayed(stepRunnable, STEP_INTERVAL_MS)
    }

    private fun turn(direction: SnakeGame.Direction) {
        game?.turn(direction)
    }

    /** 推进一步并处理后续计时或结算。 */
    private fun stepOnce() {
        val current = game ?: return
        if (current.status != SnakeGame.Status.RUNNING) return

        val status = current.step()
        boardView.invalidate()
        updateScore()

        if (status == SnakeGame.Status.RUNNING) {
            handler.postDelayed(stepRunnable, STEP_INTERVAL_MS)
        } else {
            onGameOver(status)
        }
    }

    private fun onGameOver(status: SnakeGame.Status) {
        handler.removeCallbacks(stepRunnable)
        settleQuota()
        showResultDialog(status)
    }

    /** 对局结束时一次性结算:果子数 + 胜利 100,写入额度余额。 */
    private fun settleQuota() {
        if (settled) return
        settled = true
        val reward = game?.reward ?: 0
        if (reward > 0) {
            db.addQuotaCredit(userId, reward)
        }
        refreshProgress()
    }

    private fun showResultDialog(status: SnakeGame.Status) {
        val score = game?.score ?: 0
        val reward = game?.reward ?: 0
        val titleRes = if (status == SnakeGame.Status.WON) {
            R.string.snake_result_win
        } else {
            R.string.snake_result_lose
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(getString(R.string.snake_result_format, score, reward))
            .setCancelable(false)
            .setPositiveButton(R.string.btn_restart) { _, _ -> startNewGame() }
            .setNegativeButton(R.string.btn_exit_game) { _, _ -> finish() }
            .show()
    }

    private fun updateScore() {
        tvScore.text = getString(R.string.snake_score_format, game?.score ?: 0)
    }

    private fun refreshProgress() {
        if (userId <= 0) return
        progressRing.setProgress(db.getTodayLearnedCount(userId), db.getDailyLimit(userId))
    }

    companion object {
        /** 步进间隔(毫秒)。 */
        const val STEP_INTERVAL_MS = 300L
    }
}
