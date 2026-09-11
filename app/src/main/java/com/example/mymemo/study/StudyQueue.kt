package com.example.mymemo.study

import com.example.mymemo.db.Word
import kotlin.random.Random

/**
 * 内存态学习队列,实现简化版间隔重复机制。
 *
 * 规则:
 * - 队列由词库中尚未学会的单词随机组成;每个单词记录「连续认识次数」streak。
 * - 点「认识」:streak 加一;达到 [REQUIRED_STREAK] 时出队(视为学会),
 *   否则放回队尾等待再次出现。
 * - 点「不认识」:streak 清零,插回距队首 [INSERT_OFFSET] 张卡片之后的位置;
 *   队列剩余不足 [INSERT_OFFSET] 张时插到队尾。
 *
 * 队首即为当前展示的单词。该类不持有数据库,作答结果由调用方写入。
 */
class StudyQueue(words: List<Word>, random: Random = Random.Default) {

    /** 队列元素:单词 + 连续认识次数。 */
    data class Item(val word: Word, var streak: Int = 0)

    private val items = ArrayDeque<Item>()

    init {
        items.addAll(words.map { Item(it) })
        items.shuffle(random)
    }

    /** 当前待背单词(队首);队列为空时返回 null。 */
    val current: Item? get() = items.firstOrNull()

    /** 队列中剩余卡片数。 */
    val size: Int get() = items.size

    /** 队列是否已空。 */
    val isEmpty: Boolean get() = items.isEmpty()

    /** 当前队列的单词 id 快照(供日志与测试观察插回位置)。 */
    fun snapshot(): List<Long> = items.map { it.word.id }

    /**
     * 对队首单词作答,并按规则调整队列。
     *
     * @param known true 表示「认识」,false 表示「不认识」。
     * @return true 表示该单词本次达到连续认识 2 次并被移出队列(调用方应写入学习记录);
     *         false 表示仍留在队列中。队列为空时返回 false 且不改动状态。
     */
    fun answer(known: Boolean): Boolean {
        val item = items.removeFirstOrNull() ?: return false
        if (known) {
            item.streak += 1
            if (item.streak >= REQUIRED_STREAK) {
                // 连续认识达标:出队
                return true
            }
            items.addLast(item)
        } else {
            item.streak = 0
            // 插回 3 张卡片之后;剩余不足 3 张则插到队尾
            items.add(minOf(INSERT_OFFSET, items.size), item)
        }
        return false
    }

    companion object {
        /** 连续认识多少次算学会。 */
        const val REQUIRED_STREAK = 2

        /** 「不认识」时插回的位置:距队首第几张之后。 */
        const val INSERT_OFFSET = 3
    }
}
