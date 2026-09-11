package com.example.mymemo.db

/**
 * 今日背词熟悉程度分布(饼图四类数量)。
 *
 * 分类规则见 [AppDatabaseHelper.getTodayStats]:
 * 绿 = 熟知,黄 = 模糊,红 = 不认识,灰 = 待背。
 */
data class StudyStats(
    val green: Int,
    val yellow: Int,
    val red: Int,
    val gray: Int
) {
    /** 四类合计。 */
    val total: Int get() = green + yellow + red + gray

    /** 四类是否全为 0(用于「今日暂无学习数据」提示)。 */
    val isEmpty: Boolean get() = total == 0
}
