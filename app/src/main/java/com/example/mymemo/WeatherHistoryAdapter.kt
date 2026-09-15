package com.example.mymemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.mymemo.db.WeatherQuery
import com.example.mymemo.weather.OpenMeteoParser
import com.example.mymemo.weather.WeatherDay
import com.example.mymemo.weather.WeatherException
import com.example.mymemo.weather.WeatherFormat

/**
 * 查询历史适配器:每行展示查询时间、坐标与当日天气摘要。
 *
 * 记录里只存了预报 JSON 原文,因此在 [submit] 时统一重新解析出当日摘要,
 * 解析失败的行显示「该记录无法解析」而不是崩溃。
 */
class WeatherHistoryAdapter(
    context: Context,
    queries: List<WeatherQuery>
) : BaseAdapter() {

    /** 一行历史:原始记录 + 解析出的当日摘要(可能为 null)。 */
    private data class Row(val query: WeatherQuery, val today: WeatherDay?)

    private val appContext = context.applicationContext
    private val inflater = LayoutInflater.from(context)
    private var rows: List<Row> = queries.map { Row(it, parseToday(it.result)) }

    override fun getCount(): Int = rows.size

    override fun getItem(position: Int): WeatherQuery = rows[position].query

    override fun getItemId(position: Int): Long = rows[position].query.id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_weather_history, parent, false)
        val row = rows[position]

        view.findViewById<TextView>(R.id.tvHistoryTime).text = appContext.getString(
            R.string.weather_history_time_format,
            WeatherFormat.time(row.query.queriedAt)
        )
        view.findViewById<TextView>(R.id.tvHistoryLocation).text = appContext.getString(
            R.string.weather_history_location_format,
            row.query.longitude,
            row.query.latitude
        )
        view.findViewById<TextView>(R.id.tvHistoryToday).text = row.today?.let { day ->
            appContext.getString(
                R.string.weather_history_today_format,
                day.condition,
                day.maxTemperature,
                day.minTemperature
            )
        } ?: appContext.getString(R.string.weather_history_parse_failed)
        return view
    }

    /** 替换数据集(重新解析摘要)并刷新列表。 */
    fun submit(newQueries: List<WeatherQuery>) {
        rows = newQueries.map { Row(it, parseToday(it.result)) }
        notifyDataSetChanged()
    }

    companion object {
        /** 解析记录 JSON 原文中的今日天气;无法解析返回 null。 */
        fun parseToday(rawJson: String): WeatherDay? =
            try {
                OpenMeteoParser.parse(rawJson).firstOrNull()
            } catch (e: WeatherException) {
                null
            }
    }
}
