package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent

data class OpenBetaFeedbackRequest(
    val action: String,
    val url: String
)

object OpenBetaWelcomePrefs {
    private const val PREFS_NAME = "open_beta_welcome_prefs"
    private const val KEY_OPEN_BETA_INTRO_V1_SEEN = "open_beta_intro_v1_seen"
    const val FEEDBACK_URL = "https://www.musimio.com/feedback"

    fun shouldShow(context: Context): Boolean =
        !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OPEN_BETA_INTRO_V1_SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OPEN_BETA_INTRO_V1_SEEN, true)
            .apply()
    }

    fun feedbackRequest(): OpenBetaFeedbackRequest = OpenBetaFeedbackRequest(
        action = Intent.ACTION_VIEW,
        url = FEEDBACK_URL
    )
}
