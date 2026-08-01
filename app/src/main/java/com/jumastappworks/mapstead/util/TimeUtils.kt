package com.jumastappworks.mapstead.util

import android.content.Context
import com.jumastappworks.mapstead.R

enum class RelativeAge {
    JUST_NOW,
    ONE_MINUTE_AGO,
    MINUTES_AGO,
    ONE_HOUR_AGO,
    HOURS_AGO,
    ONE_DAY_AGO,
    DAYS_AGO
}

data class RelativeAgeResult(
    val age: RelativeAge,
    val value: Int = 0
)

object TimeUtils {

    fun resolveRelativeAge(timestampMillis: Long, nowMillis: Long): RelativeAgeResult {
        val diffMillis = nowMillis - timestampMillis
        if (diffMillis < 0) return RelativeAgeResult(RelativeAge.JUST_NOW)
        
        val diffSeconds = diffMillis / 1000
        val diffMinutes = diffSeconds / 60
        val diffHours = diffMinutes / 60
        val diffDays = diffHours / 24
        
        return when {
            diffSeconds < 60 -> RelativeAgeResult(RelativeAge.JUST_NOW)
            diffMinutes < 60 -> if (diffMinutes == 1L) RelativeAgeResult(RelativeAge.ONE_MINUTE_AGO) else RelativeAgeResult(RelativeAge.MINUTES_AGO, diffMinutes.toInt())
            diffHours < 24 -> if (diffHours == 1L) RelativeAgeResult(RelativeAge.ONE_HOUR_AGO) else RelativeAgeResult(RelativeAge.HOURS_AGO, diffHours.toInt())
            else -> if (diffDays == 1L) RelativeAgeResult(RelativeAge.ONE_DAY_AGO) else RelativeAgeResult(RelativeAge.DAYS_AGO, diffDays.toInt())
        }
    }

    fun formatRelativeTime(context: Context, timestampMillis: Long): String {
        val result = resolveRelativeAge(timestampMillis, System.currentTimeMillis())
        return when (result.age) {
            RelativeAge.JUST_NOW -> context.getString(R.string.just_now)
            RelativeAge.ONE_MINUTE_AGO -> context.getString(R.string.one_minute_ago)
            RelativeAge.MINUTES_AGO -> context.getString(R.string.minutes_ago, result.value)
            RelativeAge.ONE_HOUR_AGO -> context.getString(R.string.one_hour_ago)
            RelativeAge.HOURS_AGO -> context.getString(R.string.hours_ago, result.value)
            RelativeAge.ONE_DAY_AGO -> context.getString(R.string.one_day_ago)
            RelativeAge.DAYS_AGO -> context.getString(R.string.days_ago, result.value)
        }
    }
}
