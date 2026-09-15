package com.example.mymemo.weather

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 天气状况码中文映射(任务 3.1):常见码有对应文案,未登记的码统一兜底为「未知」。
 */
class WeatherCodesTest {

    @Test
    fun `常见天气码映射为中文`() {
        assertEquals("晴", WeatherCodes.describe(0))
        assertEquals("多云", WeatherCodes.describe(2))
        assertEquals("阴", WeatherCodes.describe(3))
        assertEquals("小雨", WeatherCodes.describe(61))
        assertEquals("大雪", WeatherCodes.describe(75))
        assertEquals("雷阵雨", WeatherCodes.describe(95))
    }

    @Test
    fun `未登记的天气码兜底为未知`() {
        assertEquals(WeatherCodes.UNKNOWN, WeatherCodes.describe(-1))
        assertEquals(WeatherCodes.UNKNOWN, WeatherCodes.describe(1000))
        assertEquals("未知", WeatherCodes.describe(42))
    }

    @Test
    fun `天气日复用映射表且不抛异常`() {
        val day = WeatherDay("2026-01-02", 2, 8.0, 2.0)
        assertEquals("多云", day.condition)
        assertEquals("未知", day.copy(weatherCode = 12345).condition)
    }
}
