package com.example.mymemo.study

import com.example.mymemo.db.Word
import kotlin.random.Random

/**
 * 当前登录用户的学习会话(进程内内存态)。
 *
 * 会话队列不落库:应用进程存活期间,背词界面与今日队列界面共享同一个 [StudyQueue];
 * 会话正常结束(队列学完 / 达到上限)时清空,进程重启后自然丢失。
 */
object StudySession {

    private var queue: StudyQueue? = null
    private var ownerId: Long = -1L

    /**
     * 开始一次新的学习会话:按当日剩余额度从未学会单词中随机截取组建队列。
     * 每次进入背词界面都会重建会话,因此点击色块按会话清空。
     */
    fun startNew(
        userId: Long,
        unlearnedWords: List<Word>,
        remaining: Int,
        random: Random = Random.Default
    ): StudyQueue {
        val newQueue = StudyQueue(unlearnedWords, random, remaining)
        queue = newQueue
        ownerId = userId
        return newQueue
    }

    /** 返回当前活跃会话队列;无活跃会话时返回 null。 */
    fun current(): StudyQueue? = queue

    /** 当前活跃会话所属用户 id;无会话时返回 -1。 */
    fun ownerId(): Long = ownerId

    /** 指定用户是否存在活跃会话。 */
    fun isActiveFor(userId: Long): Boolean = queue != null && ownerId == userId

    /**
     * 上限提升后按新的剩余额度补充队列:仅当存在同一用户的活跃会话时,
     * 从未学会单词中补足到剩余额度,绝不超出。
     */
    fun supplement(unlearnedWords: List<Word>, remaining: Int, random: Random = Random.Default) {
        val active = queue ?: return
        var room = remaining - active.size
        if (room <= 0) return
        val present = active.wordIds()
        unlearnedWords.shuffled(random).forEach { word ->
            if (room > 0 && word.id !in present) {
                active.addWord(word)
                room -= 1
            }
        }
    }

    /** 会话结束(或测试清理)时清空内存队列。 */
    fun clear() {
        queue = null
        ownerId = -1L
    }
}
