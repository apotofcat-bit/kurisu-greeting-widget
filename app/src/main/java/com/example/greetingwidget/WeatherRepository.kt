package com.example.greetingwidget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 一次天气观测。
 *
 * 数据来自 Open-Meteo（免密钥、免注册）。它的 weather_code 是 WMO 标准码。
 */
data class Weather(
    val code: Int,
    val temperature: Double,
    val isDay: Boolean,
    val fetchedAt: Long
) {

    /** 超过 6 小时就算旧数据，宁可不用也不要显示错的。 */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - fetchedAt > STALE_MS

    /** 中文描述，只用在说明页上。 */
    val label: String
        get() = when (code) {
            0 -> "晴"
            1 -> "少云"
            2 -> "多云"
            3 -> "阴"
            45, 48 -> "雾"
            in 51..57 -> "毛毛雨"
            in 61..67 -> "雨"
            in 71..77 -> "雪"
            in 80..82 -> "阵雨"
            in 85..86 -> "阵雪"
            in 95..99 -> "雷暴"
            else -> "未知"
        }

    /**
     * 把 WMO 代码 + 气温归成一个"句子类别"的 key，
     * 用来去 greetings_<key> 这个字符串数组里挑句子。
     *
     * 雨/雪/雷暴这类"需要提醒"的天气优先按天气代码走；
     * 晴和多云则再看气温，热/冷比"晴"更值得说一句。
     */
    val key: String
        get() = when (code) {
            0 -> when {
                temperature >= 30.0 -> "hot"
                temperature <= 3.0 -> "cold"
                else -> "sunny"
            }
            1, 2, 3 -> when {
                temperature >= 32.0 -> "hot"
                temperature <= 0.0 -> "cold"
                else -> "cloudy"
            }
            45, 48 -> "foggy"
            in 51..57 -> "rainy"
            in 61..67 -> "rainy"
            in 71..77 -> "snowy"
            in 80..82 -> "rainy"
            in 85..86 -> "snowy"
            in 95..99 -> "stormy"
            else -> "cloudy"
        }

    companion object {
        const val STALE_MS = 6 * 60 * 60 * 1000L
    }
}

/**
 * 天气的获取与缓存。
 *
 * 设计上最关键的一条：**只有 [refresh] 会联网，而它只在 WorkManager 的后台线程里跑。**
 * 小组件渲染时只读 [cached]，绝不联网 —— AppWidgetProvider.onUpdate 是广播回调，
 * 超过约 10 秒不返回就会 ANR。
 */
object WeatherRepository {

    private const val TAG = "GreetingWidget"
    private const val PREFS = "greeting_widget"

    private const val KEY_CODE = "weather_code"
    private const val KEY_TEMP = "weather_temp"
    private const val KEY_IS_DAY = "weather_is_day"
    private const val KEY_AT = "weather_at"
    private const val KEY_LAT = "loc_lat"
    private const val KEY_LON = "loc_lon"

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读缓存的天气。从没成功拉过就返回 null。 */
    fun cached(context: Context): Weather? {
        val p = prefs(context)
        val at = p.getLong(KEY_AT, 0L)
        if (at == 0L) return null
        return Weather(
            code = p.getInt(KEY_CODE, -1),
            temperature = p.getString(KEY_TEMP, null)?.toDoubleOrNull() ?: 0.0,
            isDay = p.getBoolean(KEY_IS_DAY, true),
            fetchedAt = at
        )
    }

    private fun saveCoordinates(context: Context, latitude: Double, longitude: Double) {
        prefs(context).edit {
            putString(KEY_LAT, latitude.toString())
            putString(KEY_LON, longitude.toString())
        }
        Log.d(TAG, "已保存坐标: $latitude, $longitude")
    }

    fun cachedCoordinates(context: Context): Pair<Double, Double>? {
        val p = prefs(context)
        val lat = p.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = p.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    /**
     * 从系统已有的定位里挑一个最新的。
     *
     * getLastKnownLocation 是同步调用、不需要 Looper，所以后台线程也能用。
     * 它拿的是"系统最近一次定位的结果"——通常来自别的 App 或系统服务，
     * 所以不需要我们自己去等 GPS 冷启动，立刻就有得用。
     */
    fun refreshCoordinatesFromSystem(context: Context): Boolean {
        if (!hasLocationPermission(context)) return false
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false

        var best: Location? = null
        for (provider in listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            val loc = try {
                lm.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            }
            if (loc != null && (best == null || loc.time > best.time)) best = loc
        }

        if (best == null) {
            Log.d(TAG, "系统里暂时没有可用的最近定位")
            return false
        }
        saveCoordinates(context, best.latitude, best.longitude)
        return true
    }

    /**
     * 完整流程：刷新坐标 → 请求 Open-Meteo → 写缓存。
     * **只能在后台线程调用**（GreetingRefreshWorker.doWork 就是后台线程）。
     *
     * @return 是否成功拿到并保存了新的天气
     */
    fun refresh(context: Context): Boolean {
        if (!hasLocationPermission(context)) {
            Log.d(TAG, "没有定位权限，跳过天气")
            return false
        }
        if (!refreshCoordinatesFromSystem(context)) {
            // 拿不到新定位不算失败：可能只是系统里暂时没有缓存，沿用上次的坐标即可
            Log.d(TAG, "拿不到新定位，沿用上次缓存的坐标")
        }

        val coords = cachedCoordinates(context)
        if (coords == null) {
            Log.d(TAG, "一个坐标都没有，跳过天气")
            return false
        }

        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=" + String.format(Locale.US, "%.4f", coords.first) +
            "&longitude=" + String.format(Locale.US, "%.4f", coords.second) +
            "&current=temperature_2m,is_day,weather_code" +
            "&timezone=auto"

        var conn: HttpURLConnection? = null
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            conn = c

            val status = c.responseCode
            if (status != 200) {
                Log.w(TAG, "天气接口返回 HTTP $status")
                return false
            }

            val body = c.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            val weather = Weather(
                code = current.getInt("weather_code"),
                temperature = current.getDouble("temperature_2m"),
                isDay = current.optInt("is_day", 1) == 1,
                fetchedAt = System.currentTimeMillis()
            )

            prefs(context).edit {
                putInt(KEY_CODE, weather.code)
                putString(KEY_TEMP, weather.temperature.toString())
                putBoolean(KEY_IS_DAY, weather.isDay)
                putLong(KEY_AT, weather.fetchedAt)
            }
            Log.d(TAG, "天气已更新: ${weather.label} ${weather.temperature}℃ -> 句子类别 ${weather.key}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "天气请求失败: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }
}
