package com.example.mymemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.mymemo.weather.WeatherDay

/** 未来 7 天预报列表适配器:每行展示日期、天气状况与最高/最低气温。 */
class WeatherForecastAdapter(
    context: Context,
    days: List<WeatherDay>
) : BaseAdapter() {

    private val appContext = context.applicationContext
    private val inflater = LayoutInflater.from(context)
    private var days: List<WeatherDay> = days

    override fun getCount(): Int = days.size

    override fun getItem(position: Int): WeatherDay = days[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_weather_day, parent, false)
        val day = days[position]
        view.findViewById<TextView>(R.id.tvForecastDate).text = day.date
        view.findViewById<TextView>(R.id.tvForecastCondition).text = day.condition
        view.findViewById<TextView>(R.id.tvForecastTemp).text = appContext.getString(
            R.string.weather_day_temp_format,
            day.maxTemperature,
            day.minTemperature
        )
        return view
    }

    /** 替换数据集并刷新列表。 */
    fun submit(newDays: List<WeatherDay>) {
        days = newDays
        notifyDataSetChanged()
    }
}
