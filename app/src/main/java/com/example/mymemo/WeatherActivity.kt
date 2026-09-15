package com.example.mymemo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.WeatherQuery
import com.example.mymemo.location.LocationHelper
import com.example.mymemo.weather.OpenMeteoParser
import com.example.mymemo.weather.OpenMeteoProvider
import com.example.mymemo.weather.WeatherDay
import com.example.mymemo.weather.WeatherException
import com.example.mymemo.weather.WeatherFormat
import com.example.mymemo.weather.WeatherProvider
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView
import java.util.concurrent.Executors

/**
 * 天气界面:进入即自动执行「定位 → 查询」,顶部展示当前坐标(或默认位置提示),
 * 中部为今日天气卡片,下部为未来 7 天预报列表,并可跳转查询历史。
 *
 * 失败兜底(规格「查询结果持久化」):
 * - 请求失败且有缓存 → 展示最近一次结果并标注其查询时间;
 * - 请求失败且无缓存 → 提示检查网络。
 */
class WeatherActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var locationHelper: LocationHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var tvLocation: TextView
    private lateinit var tvWeatherStatus: TextView
    private lateinit var cardToday: View
    private lateinit var tvTodayCondition: TextView
    private lateinit var tvTodayTemp: TextView
    private lateinit var tvForecastTitle: TextView
    private lateinit var listForecast: ListView
    private lateinit var btnRefreshWeather: Button
    private lateinit var adapter: WeatherForecastAdapter

    private val provider: WeatherProvider = OpenMeteoProvider()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var username: String = ""
    private var loading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        db = AppDatabaseHelper.getInstance(this)
        locationHelper = LocationHelper(this)
        username = intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()

        progressRing = findViewById(R.id.progressRing)
        tvLocation = findViewById(R.id.tvLocation)
        tvWeatherStatus = findViewById(R.id.tvWeatherStatus)
        cardToday = findViewById(R.id.cardToday)
        tvTodayCondition = findViewById(R.id.tvTodayCondition)
        tvTodayTemp = findViewById(R.id.tvTodayTemp)
        tvForecastTitle = findViewById(R.id.tvForecastTitle)
        listForecast = findViewById(R.id.listForecast)
        btnRefreshWeather = findViewById(R.id.btnRefreshWeather)

        adapter = WeatherForecastAdapter(this, emptyList())
        listForecast.adapter = adapter

        btnRefreshWeather.setOnClickListener {
            if (!loading) {
                if (locationHelper.isPermissionGranted()) {
                    startLoad()
                } else {
                    requestLocationPermission()
                }
            }
        }
        findViewById<Button>(R.id.btnWeatherHistory).setOnClickListener {
            startActivity(
                Intent(this, WeatherHistoryActivity::class.java)
                    .putExtra(MainActivity.EXTRA_USERNAME, username)
            )
        }

        // 进入界面自动执行「定位 → 查询」
        if (locationHelper.isPermissionGranted()) {
            startLoad()
        } else {
            requestLocationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            // 拒绝时不再纠缠用户,直接以默认坐标继续查询(兜底链见 LocationHelper)
            startLoad(forceDefault = !locationHelper.isPermissionGranted())
        }
    }

    /** 运行时申请定位权限。 */
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(LocationHelper.PERMISSION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    /** 进度环展示当前用户今日背词进度,与其他界面一致。 */
    private fun refreshProgress() {
        val user = db.findUserByUsername(username)
        if (user == null) {
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            return
        }
        progressRing.setProgress(db.getTodayLearnedCount(user.id), db.getDailyLimit(user.id))
    }

    /**
     * 子线程完成"定位 → 联网查询 → 入库/读缓存",主线程一次性渲染结果。
     * 网络请求与定位等待都不得占用主线程。
     */
    private fun startLoad(forceDefault: Boolean = false) {
        if (loading) return
        loading = true
        btnRefreshWeather.isEnabled = false
        tvWeatherStatus.visibility = View.VISIBLE
        tvWeatherStatus.text = getString(R.string.weather_loading)

        executor.execute {
            val location = resolveLocation(forceDefault)
            val fresh = try {
                provider.fetch(location.latitude, location.longitude)
                    .takeIf { it.hasData }
                    ?.also { db.insertWeatherQuery(location.latitude, location.longitude, it.rawJson) }
            } catch (e: WeatherException) {
                null
            }

            if (fresh != null) {
                mainHandler.post {
                    finishLoading()
                    render(location, fresh.today, fresh.upcomingDays, cachedQuery = null)
                }
            } else {
                // 失败:回落到最近一次缓存,并带上其查询时间
                val cached = db.getLatestWeatherQuery()
                val cachedDays = cached?.let { query ->
                    try {
                        OpenMeteoParser.parse(query.result)
                    } catch (e: WeatherException) {
                        null
                    }
                }.orEmpty()
                mainHandler.post {
                    finishLoading()
                    render(
                        location,
                        cachedDays.firstOrNull(),
                        cachedDays.drop(1).take(7),
                        cachedQuery = if (cachedDays.isEmpty()) null else cached
                    )
                }
            }
        }
    }

    private fun finishLoading() {
        loading = false
        btnRefreshWeather.isEnabled = true
    }

    /** 解析本次查询使用的坐标;权限缺失、超时或无信号统一回落默认坐标。 */
    private fun resolveLocation(forceDefault: Boolean): ResolvedLocation {
        if (!forceDefault && locationHelper.isPermissionGranted()) {
            val location = locationHelper.acquireLocation()
            if (location != null) {
                return ResolvedLocation(location.latitude, location.longitude, usedDefault = false)
            }
        }
        return ResolvedLocation(
            LocationHelper.DEFAULT_LATITUDE,
            LocationHelper.DEFAULT_LONGITUDE,
            usedDefault = true
        )
    }

    /** 统一渲染:成功、缓存回退、彻底失败三种情况共用一套视图切换逻辑。 */
    private fun render(
        location: ResolvedLocation,
        today: WeatherDay?,
        upcoming: List<WeatherDay>,
        cachedQuery: WeatherQuery?
    ) {
        tvLocation.text = if (location.usedDefault) {
            getString(
                R.string.weather_default_location_format,
                location.longitude,
                location.latitude
            )
        } else {
            getString(R.string.weather_location_format, location.longitude, location.latitude)
        }
        if (location.usedDefault) {
            Toast.makeText(this, R.string.toast_default_location, Toast.LENGTH_SHORT).show()
        }

        val hasData = today != null
        cardToday.visibility = if (hasData) View.VISIBLE else View.GONE
        tvForecastTitle.visibility = if (hasData) View.VISIBLE else View.GONE
        listForecast.visibility = if (hasData) View.VISIBLE else View.GONE
        if (today != null) {
            tvTodayCondition.text = today.condition
            tvTodayTemp.text = getString(
                R.string.weather_today_temp_format,
                today.maxTemperature,
                today.minTemperature
            )
        }
        adapter.submit(upcoming)

        when {
            cachedQuery != null && hasData -> {
                tvWeatherStatus.visibility = View.VISIBLE
                tvWeatherStatus.text = getString(
                    R.string.weather_cache_notice_format,
                    WeatherFormat.time(cachedQuery.queriedAt)
                )
            }

            hasData -> tvWeatherStatus.visibility = View.GONE

            else -> {
                tvWeatherStatus.visibility = View.VISIBLE
                tvWeatherStatus.text = getString(R.string.weather_failed)
            }
        }
    }

    /** 本次查询实际使用的坐标,以及是否回落到默认坐标。 */
    private data class ResolvedLocation(
        val latitude: Double,
        val longitude: Double,
        val usedDefault: Boolean
    )

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
    }
}
