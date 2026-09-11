package com.example.mymemo.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.mymemo.R
import com.example.mymemo.game.SnakeGame
import kotlin.math.min

/**
 * 自定义控件「贪吃蛇棋盘」:绘制 [SnakeGame] 的 21×21 网格、蛇身与果子。
 *
 * 只负责渲染,不接受输入、不推进游戏;Activity 通过 [setGame] 注入状态并在每步后
 * 调用 [invalidate] 触发重绘。
 */
class SnakeBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var game: SnakeGame? = null

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.snake_board_bg)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.snake_grid)
        style = Paint.Style.STROKE
        strokeWidth = dp(0.5f)
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.snake_body)
        style = Paint.Style.FILL
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.snake_head)
        style = Paint.Style.FILL
    }
    private val fruitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.snake_fruit)
        style = Paint.Style.FILL
    }
    private val cellRect = RectF()

    /** 注入当前对局状态并重绘;传 null 表示清空棋盘。 */
    fun setGame(game: SnakeGame?) {
        this.game = game
        invalidate()
    }

    /** 当前注入的对局状态(供测试与调试读取)。 */
    val gameValue: SnakeGame? get() = game

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(DEFAULT_SIZE_DP).toInt()
        val width = resolveSize(desired, widthMeasureSpec)
        val height = resolveSize(desired, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val current = game ?: return
        val size = min(width, height).toFloat()
        if (size <= 0f) return

        val cellSize = size / current.columns
        val boardSize = cellSize * current.rows

        // 棋盘背景
        canvas.drawRect(0f, 0f, boardSize, boardSize, backgroundPaint)

        // 网格线
        for (i in 0..current.columns) {
            val x = i * cellSize
            canvas.drawLine(x, 0f, x, boardSize, gridPaint)
        }
        for (i in 0..current.rows) {
            val y = i * cellSize
            canvas.drawLine(0f, y, boardSize, y, gridPaint)
        }

        // 果子
        val fruit = current.fruit
        canvas.drawCircle(
            (fruit.col + 0.5f) * cellSize,
            (fruit.row + 0.5f) * cellSize,
            cellSize * 0.35f,
            fruitPaint
        )

        // 蛇身(蛇头颜色加深)
        val body = current.body()
        body.forEachIndexed { index, cell ->
            cellRect.set(
                cell.col * cellSize + INSET_RATIO * cellSize,
                cell.row * cellSize + INSET_RATIO * cellSize,
                (cell.col + 1) * cellSize - INSET_RATIO * cellSize,
                (cell.row + 1) * cellSize - INSET_RATIO * cellSize
            )
            val radius = cellSize * 0.2f
            canvas.drawRoundRect(cellRect, radius, radius, if (index == 0) headPaint else bodyPaint)
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    companion object {
        private const val DEFAULT_SIZE_DP = 320f
        private const val INSET_RATIO = 0.08f
    }
}
