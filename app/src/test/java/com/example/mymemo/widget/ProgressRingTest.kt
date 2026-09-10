package com.example.mymemo.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证进度环的填充比例与文本计算(含 0 与超出上限两种边界)。
 */
class ProgressRingTest {

    @Test
    fun `部分完成 12 slash 30 填充 40 percent`() {
        assertEquals(0.4f, ProgressRing.sweepRatio(12, 30), 0.0001f)
        assertEquals("12/30", ProgressRing.label(12, 30))
    }

    @Test
    fun `进度为零无填充`() {
        assertEquals(0f, ProgressRing.sweepRatio(0, 30), 0.0001f)
        assertEquals("0/30", ProgressRing.label(0, 30))
    }

    @Test
    fun `超出上限按满环显示且数值如实`() {
        assertEquals(1f, ProgressRing.sweepRatio(35, 30), 0.0001f)
        assertEquals("35/30", ProgressRing.label(35, 30))
    }

    @Test
    fun `非法上限不崩溃且无填充`() {
        assertEquals(0f, ProgressRing.sweepRatio(5, 0), 0.0001f)
        assertEquals(0f, ProgressRing.sweepRatio(5, -1), 0.0001f)
    }

    @Test
    fun `恰好完成填充满环`() {
        assertEquals(1f, ProgressRing.sweepRatio(30, 30), 0.0001f)
    }
}
