package com.example.mymemo.weather

/**
 * 天气数据源接口:输入经纬度,输出今日 + 未来若干天的结构化预报。
 *
 * 规格不锁定具体 API;实现类可替换([OpenMeteoProvider] 为默认实现),
 * 界面层与持久化层只依赖本接口与 [WeatherForecast]。
 */
interface WeatherProvider {

    /**
     * 阻塞式拉取预报,**必须在子线程调用**(网络请求不得在主线程执行)。
     *
     * @throws WeatherException 网络不可用、超时、HTTP 错误或响应解析失败。
     */
    @Throws(WeatherException::class)
    fun fetch(latitude: Double, longitude: Double): WeatherForecast
}

/** 天气查询失败(网络、超时、HTTP 状态或解析错误)。 */
class WeatherException(message: String, cause: Throwable? = null) : Exception(message, cause)
