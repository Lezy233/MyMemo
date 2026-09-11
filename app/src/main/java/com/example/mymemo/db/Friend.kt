package com.example.mymemo.db

/**
 * 好友列表中的一项:好友账号信息 + 其今日已背单词数。
 *
 * @param todayLearnedCount 好友当日学习记录条数(跨天自动归零)。
 */
data class Friend(
    val userId: Long,
    val username: String,
    val avatar: String,
    val todayLearnedCount: Int
)

/** 发起好友申请的结果,用于界面给出准确提示。 */
enum class AddFriendResult {
    /** 申请已写入,等待对方同意。 */
    SUCCESS,

    /** 目标用户名未注册。 */
    USER_NOT_FOUND,

    /** 不能添加自己。 */
    SELF,

    /** 双方已是好友。 */
    ALREADY_FRIEND,

    /** 已存在待处理的申请(自己发出的或对方发来的)。 */
    REQUEST_PENDING
}
