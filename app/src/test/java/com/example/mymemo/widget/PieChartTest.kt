package com.example.mymemo.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 饼图扇形角度计算单元测试(任务 3.2):角度与数量比例一致,无数据时全为 0。
 */
class PieChartTest {

    @Test
    fun sweepsAreProportionalToCounts() {
        val sweeps = PieChart.sweeps(listOf(1, 1, 2, 0))
        assertEquals(4, sweeps.size)
        assertEquals(90f, sweeps[0], 0.001f)
        assertEquals(90f, sweeps[1], 0.001f)
        assertEquals(180f, sweeps[2], 0.001f)
        assertEquals(0f, sweeps[3], 0.001f)
        assertEquals(360f, sweeps.sum(), 0.001f)
    }

    @Test
    fun emptyCountsProduceNoSlices() {
        val sweeps = PieChart.sweeps(listOf(0, 0, 0, 0))
        assertEquals(listOf(0f, 0f, 0f, 0f), sweeps)
    }

    @Test
    fun singleCategoryTakesWholeCircle() {
        val sweeps = PieChart.sweeps(listOf(0, 7, 0, 0))
        assertEquals(360f, sweeps[1], 0.001f)
    }
}
