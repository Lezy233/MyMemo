package com.example.mymemo.db

/**
 * 数据库中的单词记录。
 *
 * @param isPreset 是否来自内置预制词表(true),用户手动添加为 false。
 */
data class Word(
    val id: Long,
    val word: String,
    val meaning: String,
    val isPreset: Boolean
)
