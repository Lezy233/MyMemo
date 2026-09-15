package com.example.mymemo.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 设备定位封装:系统 [LocationManager] + 运行时权限 + 默认坐标兜底。
 *
 * 兜底链(规格「获取当前地理位置」):
 * - 权限被拒/未声明 → 调用方使用 [DEFAULT_LATITUDE] / [DEFAULT_LONGITUDE] 并提示;
 * - 有权限但约 [DEFAULT_TIMEOUT_MILLIS] 内无定位结果(模拟器无 GPS、无信号)→ 返回 null,
 *   调用方同样回落到默认坐标。
 *
 * [acquireLocation] 会阻塞等待,必须在子线程调用。
 */
class LocationHelper(private val context: Context) {

    /** 当前是否已获得定位权限。 */
    fun isPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * 获取当前位置:
     * 1. 权限缺失或设备无可用定位服务 → 返回 null(由调用方兜底);
     * 2. 优先复用 10 分钟内的缓存定位,避免课堂教学演示白等;
     * 3. 否则等待实时定位,最长 [timeoutMillis],超时再退回任意缓存定位。
     */
    @SuppressLint("MissingPermission")
    fun acquireLocation(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): Location? {
        if (!isPermissionGranted()) {
            Log.d(TAG, "acquireLocation: 权限未授予,回落默认坐标")
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            Log.d(TAG, "acquireLocation: 无 LocationManager,回落默认坐标")
            return null
        }
        val providers = manager.getProviders(true).filter { it in CANDIDATE_PROVIDERS }
        if (providers.isEmpty()) {
            Log.d(TAG, "acquireLocation: 没有启用的定位 provider,回落默认坐标")
            return null
        }

        val lastKnown = bestLastKnown(manager, providers)
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time <= FRESH_MILLIS) {
            Log.d(TAG, "acquireLocation: 复用缓存定位 $lastKnown")
            return lastKnown
        }

        val fresh = awaitUpdate(manager, providers, timeoutMillis)
        val result = fresh ?: lastKnown
        Log.d(TAG, "acquireLocation: fresh=$fresh, lastKnown=$lastKnown, result=$result")
        return result
    }

    /** 取各 provider 缓存定位中最新的一个。 */
    @SuppressLint("MissingPermission")
    private fun bestLastKnown(
        manager: LocationManager,
        providers: List<String>
    ): Location? = providers
        .mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        .maxByOrNull { it.time }

    /** 注册实时更新并在 [timeoutMillis] 内等待首个定位结果;超时返回 null。 */
    @SuppressLint("MissingPermission")
    private fun awaitUpdate(
        manager: LocationManager,
        providers: List<String>,
        timeoutMillis: Long
    ): Location? {
        val latch = CountDownLatch(1)
        val holder = AtomicReference<Location?>(null)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val current = holder.get()
                if (current == null || location.time >= current.time) {
                    holder.set(location)
                }
                latch.countDown()
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        try {
            providers.forEach { provider ->
                try {
                    manager.requestLocationUpdates(
                        provider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                } catch (e: SecurityException) {
                    Log.d(TAG, "requestLocationUpdates($provider) 被拒绝", e)
                } catch (e: IllegalArgumentException) {
                    Log.d(TAG, "requestLocationUpdates($provider) 参数无效", e)
                }
            }
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            try {
                manager.removeUpdates(listener)
            } catch (e: SecurityException) {
                // 权限在等待期间被撤销,忽略
            }
        }
        return holder.get()
    }

    companion object {
        private const val TAG = "LocationHelper"

        /** 运行时申请的定位权限。 */
        const val PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION

        /** 默认坐标:上海徐汇(北纬 31.19°,东经 121.44°)。 */
        const val DEFAULT_LATITUDE = 31.19
        const val DEFAULT_LONGITUDE = 121.44

        /** 定位等待上限(约 10 秒)。 */
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L

        /** 缓存定位视为「新鲜」的时限,超过则重新等待实时定位。 */
        private const val FRESH_MILLIS = 10 * 60 * 1000L

        private val CANDIDATE_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
    }
}
