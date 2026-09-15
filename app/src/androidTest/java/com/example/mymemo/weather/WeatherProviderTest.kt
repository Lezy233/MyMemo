package com.example.mymemo.weather

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.location.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 天气适配层测试(任务 3.1 / 3.2):
 * - 解析器对样例响应/异常响应的处理(确定性);
 * - 在真实设备/模拟器上验证 Open-Meteo 连通性并返回今日 + 7 天数据(任务 3.2 的选型依据)。
 */
@RunWith(AndroidJUnit4::class)
class WeatherProviderTest {

    @Test
    fun parseReadsDailyWeatherAndTemperatures() {
        val days = OpenMeteoParser.parse(SAMPLE_JSON)

        assertEquals(8, days.size)
        assertEquals("2026-01-02", days[0].date)
        assertEquals(2, days[0].weatherCode)
        assertEquals("多云", days[0].condition)
        assertEquals(8.0, days[0].maxTemperature, 0.0001)
        assertEquals(2.0, days[0].minTemperature, 0.0001)

        assertEquals("2026-01-03", days[1].date)
        assertEquals("小雨", days[1].condition)
    }

    @Test
    fun parseRejectsMalformedPayload() {
        assertThrowsWeatherException("")
        assertThrowsWeatherException("not json")
        assertThrowsWeatherException("{\"daily\":{}}")
        assertThrowsWeatherException(
            "{\"daily\":{\"time\":[],\"weathercode\":[]," +
                "\"temperature_2m_max\":[],\"temperature_2m_min\":[]}}"
        )
    }

    @Test
    fun buildUrlCarriesCoordinatesDailyFieldsAndTimezone() {
        val url = OpenMeteoProvider().buildUrl(31.19, 121.44)

        assertTrue(url.startsWith(OpenMeteoProvider.BASE_URL))
        assertTrue(url.contains("latitude=31.1900"))
        assertTrue(url.contains("longitude=121.4400"))
        assertTrue(url.contains("weathercode"))
        assertTrue(url.contains("temperature_2m_max"))
        assertTrue(url.contains("temperature_2m_min"))
        assertTrue(url.contains("timezone=Asia%2FShanghai"))
        assertTrue(url.contains("forecast_days=8"))
    }

    /**
     * 连通性验证:在目标演示设备/模拟器上真实请求一次 Open-Meteo。
     * 有网络时断言返回今日 + 7 天数据;API 不可达时跳过(不引入与业务无关的
     * ACCESS_NETWORK_STATE 权限),连通性结论记录在 design.md。
     */
    @Test
    fun liveFetchReturnsTodayAndSevenUpcomingDays() {
        val forecast = try {
            OpenMeteoProvider().fetch(
                LocationHelper.DEFAULT_LATITUDE,
                LocationHelper.DEFAULT_LONGITUDE
            )
        } catch (e: WeatherException) {
            org.junit.Assume.assumeNoException("Open-Meteo 不可达,跳过连通性验证", e)
            return
        }
        Log.d(TAG, "Open-Meteo 连通性验证通过:days=${forecast.days.size}, raw=${forecast.rawJson.length}B")

        assertNotNull(forecast.today)
        assertEquals(8, forecast.days.size)
        assertEquals(7, forecast.upcomingDays.size)
        assertTrue(forecast.rawJson.contains("daily"))
        assertTrue(forecast.today!!.condition.isNotEmpty())
    }

    private fun assertThrowsWeatherException(json: String) {
        try {
            OpenMeteoParser.parse(json)
            throw AssertionError("预期解析失败但成功了:$json")
        } catch (expected: WeatherException) {
            // 预期
        }
    }

    companion object {
        private const val TAG = "WeatherProviderTest"

        private const val SAMPLE_JSON =
            "{\"latitude\":31.19,\"longitude\":121.44,\"timezone\":\"Asia/Shanghai\"," +
                "\"daily\":{" +
                "\"time\":[\"2026-01-02\",\"2026-01-03\",\"2026-01-04\",\"2026-01-05\"," +
                "\"2026-01-06\",\"2026-01-07\",\"2026-01-08\",\"2026-01-09\"]," +
                "\"weathercode\":[2,61,0,3,45,71,95,1]," +
                "\"temperature_2m_max\":[8.0,10.5,12.0,9.0,7.5,3.0,6.0,11.0]," +
                "\"temperature_2m_min\":[2.0,5.5,6.0,4.0,2.5,-2.0,1.0,5.0]}}"
    }
}
