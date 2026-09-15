package com.example.mymemo.db

/**
 * 一条天气查询记录(weather_queries 表)。
 *
 * @param result 天气 API 返回的预报 JSON 原文,展示历史时重新解析。
 * @param queriedAt 查询发生时的本地毫秒时间戳。
 */
data class WeatherQuery(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val queriedAt: Long,
    val result: String
)
