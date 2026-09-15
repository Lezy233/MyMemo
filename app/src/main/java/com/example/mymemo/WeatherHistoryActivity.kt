package com.example.mymemo

import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 查询历史界面:ListView 按查询时间倒序展示历史天气查询记录,
 * 每条包含查询时间、坐标与当日天气摘要;无记录时提示「暂无查询历史」。
 */
class WeatherHistoryActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: WeatherHistoryAdapter

    private var username: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_history)

        db = AppDatabaseHelper.getInstance(this)
        username = intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()

        progressRing = findViewById(R.id.progressRing)
        tvEmpty = findViewById(R.id.tvEmptyWeatherHistory)
        adapter = WeatherHistoryAdapter(this, emptyList())
        findViewById<ListView>(R.id.listWeatherHistory).adapter = adapter

        reload()
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
        // 从天气界面返回时历史记录保持最新
        reload()
    }

    /** 重查数据库:按时间倒序刷新列表,并切换空态提示。 */
    private fun reload() {
        val queries = db.getAllWeatherQueries()
        adapter.submit(queries)
        tvEmpty.visibility = if (queries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshProgress() {
        val user = db.findUserByUsername(username)
        if (user == null) {
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            return
        }
        progressRing.setProgress(db.getTodayLearnedCount(user.id), db.getDailyLimit(user.id))
    }
}
