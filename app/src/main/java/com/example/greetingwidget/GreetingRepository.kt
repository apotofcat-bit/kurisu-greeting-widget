package com.example.greetingwidget

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * 从分好组的问候语池里挑一句。
 *
 * 挑选分两步：
 *   1. 先按权重随机选一个"类别"：
 *      时段 25% / 实验室日常 25% / 天气 20% / 通用 20% / 季节 10%
 *   2. 再从该类别对应的字符串数组里随机抽一句（避开上一次说过的那句）
 *
 * "通用"和"实验室日常"是主力：前者任何场景都能用，后者是同一个 lab 的日常。
 * 句子池共 165 条，源头是仓库根目录的 kurisu_greetings.txt，
 * 用 tools/sync_strings.py 生成 strings.xml（别手改 XML）。
 * 天气拿不到时，"天气"这个类别会自动从候选里消失，权重让给其它类别。
 * 所以断网、没授权定位、API 挂了，都只是"少一类句子"，不会白屏也不会崩。
 */
object GreetingRepository {

    private const val PREFS = "greeting_widget"
    private const val KEY_LAST = "last_greeting"

    // 权重之和不必等于 100，写成百分比只是为了好读。
    private const val WEIGHT_TIME = 25
    private const val WEIGHT_LAB = 25
    private const val WEIGHT_WEATHER = 20
    private const val WEIGHT_ANYTIME = 20
    private const val WEIGHT_SEASON = 10

    fun next(context: Context): String {
        // 1) 组装候选类别
        val weighted = ArrayList<Pair<String, Int>>(5)
        val weather = WeatherRepository.cached(context)
        if (weather != null && !weather.isStale()) {
            weighted += weather.key to WEIGHT_WEATHER
        }
        weighted += timeBucket() to WEIGHT_TIME
        weighted += season() to WEIGHT_SEASON
        weighted += "anytime" to WEIGHT_ANYTIME
        weighted += "lab" to WEIGHT_LAB

        // 2) 抽类别，再从类别里抽句子；类别为空就退到 anytime
        var pool = pool(context, pickWeighted(weighted))
        if (pool.isEmpty()) pool = pool(context, "anytime")
        if (pool.isEmpty()) return ""

        // 3) 避开上一句，否则连点两下看到同一句会觉得"这随机是假的"
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST, null)
        val candidates = if (pool.size > 1) pool.filter { it != last } else pool
        val text = candidates.random()

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_LAST, text) }

        return text
    }

    /** 现在是哪个时段。 */
    private fun timeBucket(): String = when (LocalTime.now().hour) {
        in 5..10 -> "morning"
        in 11..17 -> "afternoon"
        in 18..22 -> "evening"
        else -> "night"
    }

    /** 现在是什么季节（北半球，按气象学常用的月份划分）。 */
    private fun season(): String = when (LocalDate.now().monthValue) {
        3, 4, 5 -> "spring"
        6, 7, 8 -> "summer"
        9, 10, 11 -> "autumn"
        else -> "winter"
    }

    private fun pool(context: Context, key: String): List<String> =
        context.resources.getStringArray(arrayRes(key)).toList()

    private fun arrayRes(key: String): Int = when (key) {
        "morning" -> R.array.greetings_morning
        "afternoon" -> R.array.greetings_afternoon
        "evening" -> R.array.greetings_evening
        "night" -> R.array.greetings_night
        "spring" -> R.array.greetings_spring
        "summer" -> R.array.greetings_summer
        "autumn" -> R.array.greetings_autumn
        "winter" -> R.array.greetings_winter
        "sunny" -> R.array.greetings_sunny
        "cloudy" -> R.array.greetings_cloudy
        "foggy" -> R.array.greetings_foggy
        "rainy" -> R.array.greetings_rainy
        "snowy" -> R.array.greetings_snowy
        "stormy" -> R.array.greetings_stormy
        "hot" -> R.array.greetings_hot
        "cold" -> R.array.greetings_cold
        "lab" -> R.array.greetings_lab
        else -> R.array.greetings_anytime
    }

    /** 按权重随机取一个 key。 */
    private fun pickWeighted(items: List<Pair<String, Int>>): String {
        var r = Random.nextInt(items.sumOf { it.second })
        for ((key, weight) in items) {
            r -= weight
            if (r < 0) return key
        }
        return items.last().first
    }
}
