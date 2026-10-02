package com.example.greetingwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 小组件本体。
 *
 * 关键认知：这个类是一个 BroadcastReceiver，跑在**你自己的 App 进程**里。
 * 它不能直接操作桌面上的 View —— 只能通过 RemoteViews 交一份"布局说明书"给桌面进程去渲染。
 *
 * 另一条铁律：**这里绝不联网。** 广播回调超过约 10 秒不返回就 ANR，
 * 所以天气全由 GreetingRefreshWorker 在后台拉好、写进 SharedPreferences，
 * 这里只是读缓存来挑句子。
 */
class GreetingWidgetProvider : AppWidgetProvider() {

    /**
     * 系统会把所有发给这个组件的广播都交给这里。
     * 必须调用 super，父类才会把 APPWIDGET_UPDATE 等标准动作分发到 onUpdate 等方法；
     * 自定义的 ACTION_SHUFFLE（点击触发）则由我们自己处理。
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SHUFFLE) {
            Log.d(TAG, "widget clicked -> shuffle")
            refreshAll(context)
            // 点一下顺手把过期的天气补上（只是排个后台任务，不会阻塞这里）
            requestWeatherIfStale(context)
        }
    }

    /** 系统认为"该更新了"时调用：添加小组件、重启桌面、变更尺寸等。 */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate ids=${appWidgetIds.joinToString()}")
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
        // 刚被拖到桌面上时很可能还没有天气数据，补一次
        requestWeatherIfStale(context)
    }

    /** 桌面上出现第一个本小组件时调用。 */
    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled -> schedule periodic refresh")
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<GreetingRefreshWorker>(1, TimeUnit.HOURS).build()
        )
    }

    /** 桌面上最后一个本小组件被移除时调用，顺手把定时任务停掉，别白耗电。 */
    override fun onDisabled(context: Context) {
        Log.d(TAG, "onDisabled -> cancel periodic refresh")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
    }

    companion object {
        private const val TAG = "GreetingWidget"
        const val ACTION_SHUFFLE = "com.example.greetingwidget.action.SHUFFLE"
        const val WORK_PERIODIC = "greeting_refresh"
        const val WORK_WEATHER_ONCE = "greeting_weather_once"

        /** 刷新桌面上所有本小组件实例。MainActivity 的测试按钮也调用它。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GreetingWidgetProvider::class.java)
            )
            Log.d(TAG, "refreshAll ids=${ids.joinToString()}")
            if (ids.isEmpty()) {
                Log.d(TAG, "refreshAll: 桌面上还没有本小组件")
                return
            }
            ids.forEach { render(context, manager, it) }
        }

        /**
         * 天气缺失或过期时，排一次后台刷新。
         *
         * 用 ExistingWorkPolicy.KEEP：任务还在排队/执行中时不会重复排队，
         * 所以连点几下小组件也只会跑一个任务。
         */
        fun requestWeatherIfStale(context: Context) {
            if (!WeatherRepository.hasLocationPermission(context)) {
                Log.d(TAG, "没有定位权限，不排天气任务")
                return
            }
            val weather = WeatherRepository.cached(context)
            if (weather != null && !weather.isStale()) return

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_WEATHER_ONCE,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GreetingRefreshWorker>().build()
            )
            Log.d(TAG, "已排队一次天气刷新")
        }

        /** 给单个小组件实例渲染一句新的问候语。 */
        fun render(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val greeting = GreetingRepository.next(context)
            Log.d(TAG, "render id=$appWidgetId -> $greeting")

            val views = RemoteViews(context.packageName, R.layout.widget_greeting).apply {
                setTextViewText(R.id.tv_greeting, greeting)
                setOnClickPendingIntent(R.id.widget_root, shufflePendingIntent(context, appWidgetId))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * 构造"点击换一句"的 PendingIntent。
         *
         * 两个坑：
         * 1. targetSdk 31+ 必须显式指定 FLAG_IMMUTABLE，否则直接抛异常；
         * 2. 每个小组件实例的 requestCode 和 data 必须不同，否则多个实例会共用同一个
         *    PendingIntent，点其中一个只有它自己变。
         */
        private fun shufflePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, GreetingWidgetProvider::class.java).apply {
                action = ACTION_SHUFFLE
                data = Uri.parse("greeting://widget/$appWidgetId")
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
