package com.example.mymemo.db

/**
 * 收到的好友申请(待处理)。
 *
 * @param id friendships 表主键,用于同意/拒绝时定位记录。
 * @param fromUserId 发起人账号 id。
 * @param fromUsername 发起人用户名。
 * @param fromAvatar 发起人头像资源名。
 * @param createdAt 申请时间(毫秒时间戳)。
 */
data class FriendRequest(
    val id: Long,
    val fromUserId: Long,
    val fromUsername: String,
    val fromAvatar: String,
    val createdAt: Long
)
