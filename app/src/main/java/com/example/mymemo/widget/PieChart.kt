package com.example.mymemo.widget

/**
 * 饼图扇形角度的纯计算逻辑,与 [StudyPieChartView] 的绘制解耦,便于单元测试。
 */
object PieChart {

    /**
     * 按各类数量计算扇形角度,总和为 360°。
     * 总数 <= 0 时返回全 0(表示无数据,不应绘制空饼图)。
     */
    fun sweeps(counts: List<Int>): List<Float> {
        val total = counts.sum()
        if (total <= 0) return List(counts.size) { 0f }
        return counts.map { it.toFloat() / total * 360f }
    }
}
