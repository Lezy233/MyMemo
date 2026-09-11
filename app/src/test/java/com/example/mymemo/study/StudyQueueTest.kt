package com.example.mymemo.study

import com.example.mymemo.db.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 学习队列判定逻辑测试(任务 3.2):
 * 不认识插回位置、连续两次认识才学会、认识被打断后需重新连续两次。
 */
class StudyQueueTest {

    private fun words(count: Int): List<Word> =
        (1..count).map { Word(it.toLong(), "word$it", "释义$it", true) }

    @Test
    fun `不认识插回距队首第 3 位`() {
        val queue = StudyQueue(words(6), Random(1))
        val firstId = queue.current!!.word.id

        queue.answer(known = false)

        assertEquals(3, queue.snapshot().indexOf(firstId))
        assertEquals(6, queue.size)
    }

    @Test
    fun `队列不足 3 张时插到队尾`() {
        val queue = StudyQueue(words(2), Random(1))
        val firstId = queue.current!!.word.id

        queue.answer(known = false)

        assertEquals(1, queue.snapshot().indexOf(firstId))
        assertEquals(2, queue.size)
    }

    @Test
    fun `认识未达标放回队尾`() {
        val queue = StudyQueue(words(3), Random(1))
        val firstId = queue.current!!.word.id

        queue.answer(known = true)

        assertEquals(2, queue.snapshot().indexOf(firstId))
        assertEquals(3, queue.size)
    }

    @Test
    fun `连续两次认识才计入已背并出队`() {
        val queue = StudyQueue(words(1), Random(1))

        assertFalse(queue.answer(known = true))
        assertFalse(queue.isEmpty)
        assertTrue(queue.answer(known = true))
        assertTrue(queue.isEmpty)
    }

    @Test
    fun `认识被打断后需重新连续两次`() {
        val queue = StudyQueue(words(1), Random(1))

        assertFalse(queue.answer(known = true))   // 连续认识 1 次
        assertFalse(queue.answer(known = false))  // 清零
        assertFalse(queue.answer(known = true))   // 重新连续认识 1 次
        assertTrue(queue.answer(known = true))    // 连续认识到 2 次 → 学会
        assertTrue(queue.isEmpty)
    }

    @Test
    fun `空队列作答不崩溃`() {
        val queue = StudyQueue(emptyList(), Random(1))
        assertTrue(queue.isEmpty)
        assertFalse(queue.answer(known = true))
        assertFalse(queue.answer(known = false))
    }
}
