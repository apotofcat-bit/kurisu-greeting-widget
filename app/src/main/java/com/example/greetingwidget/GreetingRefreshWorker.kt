package com.example.greetingwidget

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 后台刷新：拉一次天气，然后让桌面上的小组件重画。
 *
 * 两个触发来源：
 *   - 周期性任务（每小时一次，在 GreetingWidgetProvider.onEnabled 里排的）
 *   - 一次性任务（刚授权定位、刚把小组件拖上桌面、或者点小组件时发现天气过期了）
 *
 * WorkManager 的最小周期是 15 分钟，而且省电模式下会被推迟 —— 这是"尽力而为"，
 * 所以真正让用户有感知的机制还是「点一下换一句」。
 *
 * Worker.doWork() 跑在 WorkManager 的后台线程上，所以这里可以放心做阻塞的网络请求。
 * 这正是"小组件渲染不联网"的代价转移：联网全在这里，渲染只读缓存。
 */
class GreetingRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val updated = try {
            WeatherRepository.refresh(applicationContext)
        } catch (e: Exception) {
            // 哪怕天气这一步出意外，也要保证小组件照常刷新（会降级成时段/季节句子）
            Log.w("GreetingWidget", "刷新天气时异常: ${e.message}")
            false
        }

        GreetingWidgetProvider.refreshAll(applicationContext)
        Log.d("GreetingWidget", "后台刷新完成，天气更新=$updated")

        // 永远返回 success：没网、没定位都是正常情况，下一个小时会再试，
        // 没必要用 retry 制造退避风暴。
        return Result.success()
    }
}
