package com.example.mymemo.weather

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 天气界面的公共格式化工具,保证各界面时间展示一致。 */
object WeatherFormat {

    /** 把毫秒时间戳格式化为 'yyyy-MM-dd HH:mm' 本地时间。 */
    fun time(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}
