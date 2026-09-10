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
import kotlin.math.min

/**
 * 自定义控件「背词进度环」:以圆环显示今日已背单词数 / 当日上限。
 *
 * 环的填充比例 = 已背数 / 上限(超出上限按满环显示),中央显示 "已背/上限" 文本。
 * 数据通过 [setProgress] 注入,与绘制逻辑解耦,后续可接入真实背词数据。
 */
class StudyProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentProgress: Int = 0
    private var maxProgress: Int = ProgressRing.DEFAULT_MAX

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val arcBounds = RectF()

    private var ringWidth: Float = dp(10f)
    private var ringTextSize: Float = sp(16f)

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.StudyProgressRingView)
        ringWidth = a.getDimension(R.styleable.StudyProgressRingView_ringWidth, dp(10f))
        ringTextSize = a.getDimension(R.styleable.StudyProgressRingView_ringTextSize, sp(16f))
        trackPaint.color = a.getColor(
            R.styleable.StudyProgressRingView_ringTrackColor,
            ContextCompat.getColor(context, R.color.ring_track)
        )
        progressPaint.color = a.getColor(
            R.styleable.StudyProgressRingView_ringProgressColor,
            ContextCompat.getColor(context, R.color.ring_progress)
        )
        textPaint.color = a.getColor(
            R.styleable.StudyProgressRingView_ringTextColor,
            ContextCompat.getColor(context, R.color.ring_text)
        )
        a.recycle()

        trackPaint.strokeWidth = ringWidth
        progressPaint.strokeWidth = ringWidth
        textPaint.textSize = ringTextSize
    }

    /** 设置今日已背数与当日上限并重绘。 */
    fun setProgress(current: Int, max: Int) {
        currentProgress = current
        maxProgress = max
        invalidate()
    }

    /** 当前已背数(供测试与调试读取)。 */
    val currentValue: Int get() = currentProgress

    /** 当日上限(供测试与调试读取)。 */
    val maxValue: Int get() = maxProgress

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (DEFAULT_SIZE_DP + ringWidth).toInt()
        val width = resolveSize(desired, widthMeasureSpec)
        val height = resolveSize(desired, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(
            (width - paddingLeft - paddingRight).toFloat(),
            (height - paddingTop - paddingBottom).toFloat()
        ) / 2f - ringWidth / 2f

        if (radius <= 0f) return

        arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        // 底环
        canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint)

        // 进度弧(自顶部顺时针)
        val sweep = ProgressRing.sweepRatio(currentProgress, maxProgress) * 360f
        if (sweep > 0f) {
            canvas.drawArc(arcBounds, START_ANGLE, sweep, false, progressPaint)
        }

        // 中央文本 "已背/上限"
        val label = ProgressRing.label(currentProgress, maxProgress)
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, centerX, textY, textPaint)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        /** wrap_content 时的默认边长(dp,不含环宽)。 */
        private const val DEFAULT_SIZE_DP = 96f
        private const val START_ANGLE = -90f
    }
}
