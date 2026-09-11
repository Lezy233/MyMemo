package com.example.mymemo.db

/**
 * 一条聊天消息(单聊、仅文字)。
 *
 * @param sentAt 发送时间(毫秒时间戳),历史消息按此升序排列。
 */
data class Message(
    val id: Long,
    val senderId: Long,
    val receiverId: Long,
    val content: String,
    val sentAt: Long
)
