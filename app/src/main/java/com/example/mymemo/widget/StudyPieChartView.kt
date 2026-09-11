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
import com.example.mymemo.db.StudyStats
import kotlin.math.min

/**
 * 自定义控件「今日背词饼图」:按 [StudyStats] 中四类数量比例绘制四色扇形,
 * 右侧配图例(色块 + 类别名 + 数量)。不引入第三方图表库,全部由 Canvas 手绘。
 *
 * 数据通过 [setStats] 注入;无数据时不绘制扇形(由界面提示替代空饼图)。
 */
class StudyPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentStats = StudyStats(green = 0, yellow = 0, red = 0, gray = 0)

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = sp(14f)
        color = ContextCompat.getColor(context, R.color.text_primary)
    }
    private val bounds = RectF()

    private val legendLabels = intArrayOf(
        R.string.stats_legend_green,
        R.string.stats_legend_yellow,
        R.string.stats_legend_red,
        R.string.stats_legend_gray
    )
    private val sliceColors = intArrayOf(
        R.color.stats_green,
        R.color.stats_yellow,
        R.color.stats_red,
        R.color.stats_gray
    )

    /** 注入今日四类统计数据并重绘。 */
    fun setStats(stats: StudyStats) {
        currentStats = stats
        invalidate()
    }

    /** 当前统计数据(供测试与调试读取)。 */
    val statsValue: StudyStats get() = currentStats

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(dp(DEFAULT_WIDTH_DP).toInt(), widthMeasureSpec)
        val height = resolveSize(dp(DEFAULT_HEIGHT_DP).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentStats.isEmpty) return

        val counts = listOf(
            currentStats.green,
            currentStats.yellow,
            currentStats.red,
            currentStats.gray
        )

        val contentHeight = (height - paddingTop - paddingBottom).toFloat()
        val contentWidth = (width - paddingLeft - paddingRight).toFloat()
        if (contentHeight <= 0f || contentWidth <= 0f) return

        // 左侧圆形区域取「可用高度」与「一半宽度」的较小值,保证右侧放得下图例
        val pieSize = min(contentHeight, contentWidth * 0.5f)
        val centerX = paddingLeft + pieSize / 2f
        val centerY = paddingTop + contentHeight / 2f
        bounds.set(
            centerX - pieSize / 2f,
            centerY - pieSize / 2f,
            centerX + pieSize / 2f,
            centerY + pieSize / 2f
        )

        val sweeps = PieChart.sweeps(counts)
        var startAngle = START_ANGLE
        counts.forEachIndexed { index, count ->
            val sweep = sweeps[index]
            if (count > 0 && sweep > 0f) {
                slicePaint.color = ContextCompat.getColor(context, sliceColors[index])
                canvas.drawArc(bounds, startAngle, sweep, true, slicePaint)
                startAngle += sweep
            }
        }

        // 右侧图例:色块 + 类别名 + 数量
        val swatchSize = legendTextPaint.textSize * 0.9f
        val legendX = paddingLeft + pieSize + dp(12f)
        val lineHeight = legendTextPaint.textSize * 1.7f
        val legendBlockHeight = lineHeight * counts.size
        var baseline = paddingTop + (contentHeight - legendBlockHeight) / 2f +
            legendTextPaint.textSize
        counts.forEachIndexed { index, count ->
            swatchPaint.color = ContextCompat.getColor(context, sliceColors[index])
            canvas.drawRect(
                legendX,
                baseline - swatchSize * 0.8f,
                legendX + swatchSize,
                baseline,
                swatchPaint
            )
            val label = context.getString(legendLabels[index])
            canvas.drawText(
                "$label $count",
                legendX + swatchSize + dp(8f),
                baseline,
                legendTextPaint
            )
            baseline += lineHeight
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        private const val DEFAULT_WIDTH_DP = 280f
        private const val DEFAULT_HEIGHT_DP = 180f
        private const val START_ANGLE = -90f
    }
}
