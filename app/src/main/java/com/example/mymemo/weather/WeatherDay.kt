package com.example.mymemo.weather

/**
 * 某一天的天气预报(今日或未来某天)。
 *
 * @param date 'yyyy-MM-dd' 本地日期字符串(由 API 按时区返回)。
 * @param weatherCode 天气状况码(Open-Meteo 用 WMO 码),展示时经 [WeatherCodes.describe] 映射为中文。
 * @param maxTemperature 当日最高气温(摄氏度)。
 * @param minTemperature 当日最低气温(摄氏度)。
 */
data class WeatherDay(
    val date: String,
    val weatherCode: Int,
    val maxTemperature: Double,
    val minTemperature: Double
) {
    /** 天气状况中文文案(未知码回退为「未知」,不抛异常)。 */
    val condition: String get() = WeatherCodes.describe(weatherCode)
}
