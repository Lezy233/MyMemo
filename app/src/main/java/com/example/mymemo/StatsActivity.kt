package com.example.mymemo

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyPieChartView
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 今日背词统计界面(任务 3.2):饼图展示四类熟悉程度分布,数据全部来自数据库。
 *
 * 每次 [onResume] 都重新查询并重绘,保证从背词/兑换界面返回后饼图与进度环同步更新。
 * 今日无任何数据时提示文案替代空饼图。
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var pieChart: StudyPieChartView
    private lateinit var tvStatsTotal: TextView
    private lateinit var tvStatsEmpty: TextView

    private var userId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        db = AppDatabaseHelper.getInstance(this)
        progressRing = findViewById(R.id.progressRing)
        pieChart = findViewById(R.id.pieChart)
        tvStatsTotal = findViewById(R.id.tvStatsTotal)
        tvStatsEmpty = findViewById(R.id.tvStatsEmpty)

        userId = db.findUserByUsername(
            intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()
        )?.id ?: -1L
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /** 重查数据库:刷新进度环、饼图与总数/空态提示。 */
    private fun reload() {
        if (userId <= 0) {
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            pieChart.visibility = View.GONE
            tvStatsTotal.visibility = View.GONE
            tvStatsEmpty.visibility = View.VISIBLE
            return
        }

        val stats = db.getTodayStats(userId)
        progressRing.setProgress(db.getTodayLearnedCount(userId), db.getDailyLimit(userId))
        pieChart.setStats(stats)

        if (stats.isEmpty) {
            // 无数据时提示而非空饼图
            pieChart.visibility = View.GONE
            tvStatsTotal.visibility = View.GONE
            tvStatsEmpty.visibility = View.VISIBLE
        } else {
            pieChart.visibility = View.VISIBLE
            tvStatsTotal.visibility = View.VISIBLE
            tvStatsEmpty.visibility = View.GONE
            tvStatsTotal.text = getString(R.string.stats_total_format, stats.total)
        }
    }
}
