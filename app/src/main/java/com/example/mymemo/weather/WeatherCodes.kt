package com.example.mymemo.weather

/**
 * 天气状况码 → 中文文案映射表。
 *
 * 采用 WMO Weather interpretation codes(Open-Meteo 的 weathercode 即此码表);
 * 未登记的码统一回退为 [UNKNOWN],保证界面上不出现空白、不崩溃。
 */
object WeatherCodes {

    /** 未知码兜底文案。 */
    const val UNKNOWN = "未知"

    private val TABLE: Map<Int, String> = mapOf(
        0 to "晴",
        1 to "大部晴朗",
        2 to "多云",
        3 to "阴",
        45 to "雾",
        48 to "雾凇",
        51 to "小毛毛雨",
        53 to "毛毛雨",
        55 to "大毛毛雨",
        56 to "冻毛毛雨",
        57 to "强冻毛毛雨",
        61 to "小雨",
        63 to "中雨",
        65 to "大雨",
        66 to "冻雨",
        67 to "强冻雨",
        71 to "小雪",
        73 to "中雪",
        75 to "大雪",
        77 to "雪粒",
        80 to "小阵雨",
        81 to "阵雨",
        82 to "强阵雨",
        85 to "小阵雪",
        86 to "大阵雪",
        95 to "雷阵雨",
        96 to "雷阵雨伴小冰雹",
        99 to "雷阵雨伴大冰雹"
    )

    /** 把天气状况码翻译为中文;未登记的码返回 [UNKNOWN]。 */
    fun describe(code: Int): String = TABLE[code] ?: UNKNOWN
}
