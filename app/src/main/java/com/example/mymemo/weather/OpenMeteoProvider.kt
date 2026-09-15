package com.example.mymemo.weather

import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 默认天气数据源:Open-Meteo(https://open-meteo.com)。
 *
 * 选型理由:免注册、免 API key,课堂演示无需额外准备;HTTP 用 [HttpURLConnection],
 * JSON 用内置 org.json,不引入任何第三方依赖。
 *
 * 请求参数:
 * - daily=weathercode,temperature_2m_max,temperature_2m_min —— 每日天气状况与最高/最低气温
 * - timezone=Asia/Shanghai —— 按本地时区返回日期
 * - forecast_days=8 —— 今日 + 未来 7 天
 */
class OpenMeteoProvider(
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
) : WeatherProvider {

    @Throws(WeatherException::class)
    override fun fetch(latitude: Double, longitude: Double): WeatherForecast {
        val url = buildUrl(latitude, longitude)
        val rawJson = get(url)
        val days = OpenMeteoParser.parse(rawJson)
        return WeatherForecast(days = days, rawJson = rawJson)
    }

    /** 拼接请求 URL(纬度/经度保留 4 位小数,足够城市级精度)。 */
    internal fun buildUrl(latitude: Double, longitude: Double): String = String.format(
        Locale.US,
        "%s?latitude=%.4f&longitude=%.4f&daily=weathercode,temperature_2m_max," +
            "temperature_2m_min&timezone=Asia%%2FShanghai&forecast_days=8",
        BASE_URL,
        latitude,
        longitude
    )

    /** 在子线程发起 GET 并读回响应体。 */
    @Throws(WeatherException::class)
    private fun get(url: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
            }
            val status = connection.responseCode
            if (status != HTTP_OK) {
                throw WeatherException("天气服务返回 HTTP $status")
            }
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            if (body.isBlank()) throw WeatherException("天气服务返回空响应")
            return body
        } catch (e: WeatherException) {
            throw e
        } catch (e: IOException) {
            throw WeatherException("网络请求失败:${e.message}", e)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

        /** 连接与读取超时(毫秒)。 */
        const val DEFAULT_TIMEOUT_MILLIS = 10_000

        private const val HTTP_OK = 200
    }
}
