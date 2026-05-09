package com.patrick.lrcreader.core

import android.content.Context

enum class ManualCrossfadeDurationOption(
    val storageValue: String,
    val durationMs: Long
) {
    SECONDS_2("2s", 2_000L),
    SECONDS_3("3s", 3_000L),
    SECONDS_5("5s", 5_000L),
    SECONDS_8("8s", 8_000L),
    SECONDS_10("10s", 10_000L),
    SECONDS_20("20s", 20_000L);

    companion object {
        fun fromStorageValue(value: String?): ManualCrossfadeDurationOption {
            return entries.firstOrNull { it.storageValue == value } ?: SECONDS_5
        }
    }
}

object ManualCrossfadePrefs {
    private const val PREFS_NAME = "manual_crossfade_prefs"
    private const val KEY_DURATION = "manual_crossfade_duration"

    fun getDurationOption(context: Context): ManualCrossfadeDurationOption {
        val storedValue = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DURATION, ManualCrossfadeDurationOption.SECONDS_5.storageValue)
        return ManualCrossfadeDurationOption.fromStorageValue(storedValue)
    }

    fun getDurationMs(context: Context): Long {
        return getDurationOption(context).durationMs
    }

    fun setDurationOption(context: Context, option: ManualCrossfadeDurationOption) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DURATION, option.storageValue)
            .apply()
    }
}
