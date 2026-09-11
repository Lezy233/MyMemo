package com.example.mymemo.study

import com.example.mymemo.db.Word
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 内存学习会话测试(任务 3.4):队列容量不超过剩余额度、上限提升后补充新词。
 */
class StudySessionTest {

    private fun words(count: Int, offset: Long = 0): List<Word> =
        (1..count).map { Word(offset + it, "word${offset + it}", "释义", true) }

    @After
    fun tearDown() {
        StudySession.clear()
    }

    @Test
    fun `开始会话按剩余额度截取队列`() {
        val queue = StudySession.startNew(1L, words(100), remaining = 30, random = Random(1))
        assertEquals(30, queue.size)
    }

    @Test
    fun `上限提升后按新剩余额度补充新词`() {
        val allWords = words(100)
        val queue = StudySession.startNew(1L, allWords, remaining = 5, random = Random(1))
        assertEquals(5, queue.size)

        // 已背 5 个后剩余额度提高(30 → 50):补充到 45
        StudySession.supplement(allWords, remaining = 45, random = Random(2))

        assertEquals(45, queue.size)
        // 补充的单词不重复
        assertEquals(queue.size, queue.wordIds().size)
    }

    @Test
    fun `补充不会超过剩余额度`() {
        val allWords = words(100)
        val queue = StudySession.startNew(1L, allWords, remaining = 10, random = Random(1))

        StudySession.supplement(allWords, remaining = 10, random = Random(2))

        assertEquals(10, queue.size)
    }

    @Test
    fun `无活跃会话时补充不创建队列`() {
        StudySession.clear()
        StudySession.supplement(words(10), remaining = 10)
        assertTrue(StudySession.current() == null)
    }
}
