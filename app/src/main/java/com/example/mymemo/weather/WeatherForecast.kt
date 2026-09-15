package com.example.mymemo.weather

/**
 * 一次天气查询的结构化结果。
 *
 * @param days 预报日期序列,首日为今日,其后为未来若干天(Open-Meteo 请求 8 天)。
 * @param rawJson API 返回的 JSON 原文,原样写入 weather_queries.result,展示历史时重新解析。
 */
data class WeatherForecast(
    val days: List<WeatherDay>,
    val rawJson: String
) {
    /** 今日天气;无数据返回 null。 */
    val today: WeatherDay? get() = days.firstOrNull()

    /** 今日之后的预报(最多 7 天),用于「未来 7 天」列表。 */
    val upcomingDays: List<WeatherDay> get() = days.drop(1).take(UPCOMING_DAYS)

    /** 是否包含可展示的预报数据。 */
    val hasData: Boolean get() = days.isNotEmpty()

    companion object {
        /** 未来预报展示天数。 */
        const val UPCOMING_DAYS = 7
    }
}
