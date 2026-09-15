package com.example.mymemo.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预报结构切分(任务 3.1):今日取首日,「未来 7 天」列表取其后 7 天且不含今日。
 */
class WeatherForecastTest {

    private fun forecastOf(days: Int): WeatherForecast = WeatherForecast(
        days = (0 until days).map { WeatherDay("2026-01-%02d".format(it + 1), 0, 10.0, 0.0) },
        rawJson = "{}"
    )

    @Test
    fun `今日取首日未来列表取其后七天`() {
        val forecast = forecastOf(8)
        assertEquals("2026-01-01", forecast.today?.date)
        assertEquals(7, forecast.upcomingDays.size)
        assertEquals("2026-01-02", forecast.upcomingDays.first().date)
        assertEquals("2026-01-08", forecast.upcomingDays.last().date)
        assertTrue(forecast.hasData)
    }

    @Test
    fun `预报不足八天时列表按实际天数返回且不含今日`() {
        val forecast = forecastOf(3)
        assertEquals(2, forecast.upcomingDays.size)
        assertFalse(forecast.upcomingDays.any { it.date == forecast.today?.date })
    }

    @Test
    fun `空预报不崩溃`() {
        val forecast = WeatherForecast(emptyList(), "{}")
        assertEquals(null, forecast.today)
        assertTrue(forecast.upcomingDays.isEmpty())
        assertFalse(forecast.hasData)
    }
}
