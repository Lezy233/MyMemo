package com.example.mymemo.study

import com.example.mymemo.db.Word
import kotlin.random.Random

/**
 * 内存态学习队列,实现简化版间隔重复机制(修订版)。
 *
 * 规则:
 * - 会话开始时由词库中尚未学会的单词随机打乱,并按当日剩余额度截取(见 [limit])组成队列。
 * - 每个单词记录:是否今日点过「不认识」[Item.failedToday]、连续认识次数 [Item.streak],
 *   以及本次会话的点击历史 [Item.tapHistory](true=认识/false=不认识)。
 * - 点「认识」:
 *   - 初见(failedToday == false)→ 直接视为学会、出队;
 *   - 已点过「不认识」→ 连续认识次数加一,达到 [REQUIRED_STREAK] 才出队,否则放回队尾。
 * - 点「不认识」:标记 failedToday、连续认识次数清零,并插回距队首 [INSERT_OFFSET] 张卡片
 *   之后的位置;队列剩余不足 [INSERT_OFFSET] 张时插到队尾。
 *
 * 队首即为当前展示的单词。该类不持有数据库,作答结果由调用方写入。
 */
class StudyQueue(
    words: List<Word>,
    random: Random = Random.Default,
    limit: Int = Int.MAX_VALUE
) {

    /** 队列元素:单词 + 今日作答状态 + 本次会话点击历史。 */
    data class Item(
        val word: Word,
        var failedToday: Boolean = false,
        var streak: Int = 0,
        val tapHistory: MutableList<Boolean> = mutableListOf()
    )

    private val items = ArrayDeque<Item>()

    init {
        val capacity = if (limit < 0) 0 else limit
        items.addAll(words.shuffled(random).take(capacity).map { Item(it) })
    }

    /** 当前待背单词(队首);队列为空时返回 null。 */
    val current: Item? get() = items.firstOrNull()

    /** 队列中剩余卡片数。 */
    val size: Int get() = items.size

    /** 队列是否已空。 */
    val isEmpty: Boolean get() = items.isEmpty()

    /** 当前队列的单词 id 快照(供日志与测试观察插回位置)。 */
    fun snapshot(): List<Long> = items.map { it.word.id }

    /** 当前队列的单词快照(供今日队列界面展示待背单词)。 */
    fun currentWords(): List<Word> = items.map { it.word }

    /** 队列中已包含的单词 id 集合。 */
    fun wordIds(): Set<Long> = items.mapTo(mutableSetOf()) { it.word.id }

    /** 向队尾补充一个新单词(用于上限提升后的队列补充)。 */
    fun addWord(word: Word) {
        items.addLast(Item(word))
    }

    /**
     * 对队首单词作答,并按规则调整队列。
     *
     * @param known true 表示「认识」,false 表示「不认识」。
     * @return true 表示该单词被移出队列、视为学会(调用方应写入学习记录);
     *         false 表示仍留在队列中。队列为空时返回 false 且不改动状态。
     */
    fun answer(known: Boolean): Boolean {
        val item = items.removeFirstOrNull() ?: return false
        item.tapHistory += known

        if (known) {
            // 初见认识:直接计为学会
            if (!item.failedToday) return true

            item.streak += 1
            // 连续认识达标:出队
            if (item.streak >= REQUIRED_STREAK) return true
            // 未达标:放回队尾等待再次出现
            items.addLast(item)
        } else {
            item.failedToday = true
            item.streak = 0
            // 插回 3 张卡片之后;剩余不足 3 张则插到队尾
            items.add(minOf(INSERT_OFFSET, items.size), item)
        }
        return false
    }

    companion object {
        /** 点过「不认识」后,需要连续认识多少次才算学会。 */
        const val REQUIRED_STREAK = 2

        /** 「不认识」时插回的位置:距队首第几张之后。 */
        const val INSERT_OFFSET = 3
    }
}
