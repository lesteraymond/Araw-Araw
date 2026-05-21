package com.mobiledev.arawaraw

import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {
    private const val PREF_NAME = "araw_araw_prefs"
    private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_UNITS = "units"

    const val MODEL_GEMINI = "Gemini"
    const val MODEL_GROQ = "Groq"
    const val MODEL_OPENROUTER = "OpenRouter"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getAiModel(context: Context): String {
        return getPrefs(context).getString(KEY_AI_MODEL, MODEL_GEMINI) ?: MODEL_GEMINI
    }

    fun setAiModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_AI_MODEL, model).apply()
    }

    fun getUnits(context: Context): String {
        return getPrefs(context).getString(KEY_UNITS, "Celsius, km/h, mm") ?: "Celsius, km/h, mm"
    }

    fun setUnits(context: Context, units: String) {
        getPrefs(context).edit().putString(KEY_UNITS, units).apply()
    }
}
