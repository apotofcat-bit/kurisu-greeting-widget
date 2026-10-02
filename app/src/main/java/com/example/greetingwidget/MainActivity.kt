package com.example.greetingwidget

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * 说明页。
 *
 * 存在的意义：
 * 1. 告诉你怎么把小组件拖到桌面；
 * 2. 提供一个「立刻换一句」按钮 —— 调试时不用等定时任务；
 * 3. **申请定位权限** —— 天气句子需要它，而权限只能从 Activity 里申请；
 * 4. 显示当前状态（定位有没有授权、拉到的是什么天气）。
 *
 * 故意用 android.app.Activity 而不是 AppCompatActivity，这样整个工程不需要额外的 UI 依赖。
 */
class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnLocation: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        btnLocation = findViewById(R.id.btn_location)

        btnLocation.setOnClickListener {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION
            )
        }

        findViewById<Button>(R.id.btn_shuffle).setOnClickListener {
            GreetingWidgetProvider.refreshAll(this)
            Toast.makeText(this, R.string.toast_shuffled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_LOCATION) return

        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED

        if (granted) {
            // 立刻取一次系统里已有的定位，再拉一次天气，不用等下一个整点
            WeatherRepository.refreshCoordinatesFromSystem(this)
            GreetingWidgetProvider.requestWeatherIfStale(this)
            Toast.makeText(this, R.string.toast_weather_fetching, Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    private fun updateStatus() {
        val hasLocation = WeatherRepository.hasLocationPermission(this)
        btnLocation.visibility = if (hasLocation) View.GONE else View.VISIBLE

        val weather = WeatherRepository.cached(this)
        val weatherLine = when {
            weather == null -> getString(R.string.main_status_weather_none)
            weather.isStale() -> getString(
                R.string.main_status_weather_stale,
                weather.label,
                weather.temperature
            )
            else -> getString(
                R.string.main_status_weather,
                weather.label,
                weather.temperature,
                getString(if (weather.isDay) R.string.main_day else R.string.main_night)
            )
        }

        tvStatus.text = listOf(
            getString(
                if (hasLocation) R.string.main_status_location_ok
                else R.string.main_status_location_no
            ),
            weatherLine,
            getString(R.string.main_attribution)
        ).joinToString("\n")

        // 有权限但还没有天气数据，顺手补一次（KEEP 策略，不会重复排队）
        if (hasLocation && (weather == null || weather.isStale())) {
            GreetingWidgetProvider.requestWeatherIfStale(this)
        }
    }

    companion object {
        private const val REQUEST_LOCATION = 1001
    }
}
