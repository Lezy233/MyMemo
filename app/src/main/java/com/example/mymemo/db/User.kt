package com.example.mymemo.db

/** 数据库中的用户记录。 */
data class User(
    val id: Long,
    val username: String,
    val password: String,
    val avatar: String,
    val dailyLimit: Int
)
