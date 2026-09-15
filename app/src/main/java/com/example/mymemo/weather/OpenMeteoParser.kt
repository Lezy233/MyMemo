package com.example.mymemo.weather

import org.json.JSONException
import org.json.JSONObject

/**
 * Open-Meteo 预报响应解析器。
 *
 * 与请求逻辑分离,便于两处复用:
 * - [OpenMeteoProvider] 解析实时响应;
 * - 天气界面/历史界面解析 weather_queries.result 中保存的 JSON 原文。
 */
object OpenMeteoParser {

    /**
     * 解析 Open-Meteo `/v1/forecast` 的 daily 段。
     *
     * @return 按日期升序的预报列表;数组缺失或长度不一时按最短长度截断。
     * @throws WeatherException JSON 结构不符合预期或没有任何可用数据。
     */
    @Throws(WeatherException::class)
    fun parse(rawJson: String): List<WeatherDay> {
        if (rawJson.isBlank()) throw WeatherException("天气数据为空")
        try {
            val daily = JSONObject(rawJson).optJSONObject("daily")
                ?: throw WeatherException("天气数据缺少 daily 字段")
            val times = daily.optJSONArray("time")
            val codes = daily.optJSONArray("weathercode")
            val maxTemps = daily.optJSONArray("temperature_2m_max")
            val minTemps = daily.optJSONArray("temperature_2m_min")
            if (times == null || codes == null || maxTemps == null || minTemps == null) {
                throw WeatherException("天气数据缺少预报字段")
            }
            val count = minOf(
                times.length(),
                codes.length(),
                maxTemps.length(),
                minTemps.length()
            )
            if (count == 0) throw WeatherException("天气数据不含任何预报日")

            val days = ArrayList<WeatherDay>(count)
            for (i in 0 until count) {
                days += WeatherDay(
                    date = times.getString(i),
                    weatherCode = codes.getInt(i),
                    maxTemperature = maxTemps.getDouble(i),
                    minTemperature = minTemps.getDouble(i)
                )
            }
            return days
        } catch (e: JSONException) {
            throw WeatherException("天气数据解析失败:${e.message}", e)
        }
    }
}
