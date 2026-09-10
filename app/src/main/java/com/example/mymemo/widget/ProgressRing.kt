package com.example.mymemo.widget

/**
 * 背词进度环的纯计算逻辑,与 [StudyProgressRingView] 的绘制解耦,便于单元测试。
 */
object ProgressRing {

    /** 默认每日背词上限(占位值,真实数据将在 word-library 变更中接入)。 */
    const val DEFAULT_MAX = 30

    /** 已背数占上限的比例,已裁剪到 0..1;上限非法(<=0)时返回 0。 */
    fun sweepRatio(current: Int, max: Int): Float {
        if (max <= 0) return 0f
        return (current.toFloat() / max).coerceIn(0f, 1f)
    }

    /** 环中央显示的 "已背/上限" 文本。 */
    fun label(current: Int, max: Int): String = "$current/$max"
}
