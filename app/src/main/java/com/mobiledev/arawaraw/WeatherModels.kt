package com.mobiledev.arawaraw

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

data class WeatherResponse(
    val current: CurrentWeather,
    val hourly: HourlyWeather? = null,
    val daily: DailyWeather
)

data class CurrentWeather(
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("relative_humidity_2m") val humidity: Int,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("wind_speed_10m") val windSpeed: Double
)

data class HourlyWeather(
    val time: List<String>,
    @SerializedName("temperature_2m") val temperature2m: List<Double>
)

data class DailyWeather(
    val time: List<String>,
    @SerializedName("weather_code") val weatherCode: List<Int>,
    @SerializedName("temperature_2m_max") val tempMax: List<Double>,
    @SerializedName("temperature_2m_min") val tempMin: List<Double>,
    @SerializedName("uv_index_max") val uvIndex: List<Double>
)

data class CachedWeatherData(
    val weather: WeatherResponse,
    val cityName: String,
    val timestamp: Long,
    val aiGreeting: String? = null,
    val aiSuggestion: String? = null,
    val aiProTip: String? = null
)


fun getWeatherDescription(code: Int): String {
    return when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Mainly clear, partly cloudy, and overcast"
        45, 48 -> "Fog and depositing rime fog"
        51, 53, 55 -> "Drizzle: Light, moderate, and dense intensity"
        56, 57 -> "Freezing Drizzle: Light and dense intensity"
        61, 63, 65 -> "Rain: Slight, moderate and heavy intensity"
        66, 67 -> "Freezing Rain: Light and heavy intensity"
        71, 73, 75 -> "Snow fall: Slight, moderate, and heavy intensity"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers: Slight, moderate, and violent"
        85, 86 -> "Snow showers slight and heavy"
        95 -> "Thunderstorm: Slight or moderate"
        96, 99 -> "Thunderstorm with slight and heavy hail"
        else -> "Unknown"
    }
}

fun getWeatherIcon(code: Int): Int {
    return when (code) {
        0 -> R.drawable.ic_weather_sunny
        1, 2 -> R.drawable.ic_weather_partly_cloudy
        3 -> R.drawable.ic_weather_cloudy
        45, 48 -> R.drawable.ic_weather_cloudy // Fog
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> R.drawable.ic_weather_rainy
        95, 96, 99 -> R.drawable.ic_weather_thunderstorm
        else -> R.drawable.ic_weather_cloudy
    }
}

object WeatherRepository {
    var cachedWeather: WeatherResponse? = null
    var cachedCityName: String? = null
    var lastFetchTime: Long = 0
    var cachedAiGreeting: String? = null
    var cachedAiSuggestion: String? = null
    var cachedAiProTip: String? = null

    private const val CACHE_FILE_NAME = "weather_cache.json"
    private const val CACHE_DURATION = 15 * 60 * 1000 // 15 minutes in milliseconds

    fun isCacheFresh(): Boolean {
        return cachedWeather != null && (System.currentTimeMillis() - lastFetchTime < CACHE_DURATION)
    }

    fun saveToDisk(context: Context) {
        val weather = cachedWeather ?: return
        val cityName = cachedCityName ?: ""
        val data = CachedWeatherData(
            weather, 
            cityName, 
            lastFetchTime,
            cachedAiGreeting,
            cachedAiSuggestion,
            cachedAiProTip
        )
        
        try {
            val json = Gson().toJson(data)
            val file = File(context.getExternalFilesDir(null), CACHE_FILE_NAME)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFromDisk(context: Context) {
        try {
            val file = File(context.getExternalFilesDir(null), CACHE_FILE_NAME)
            if (file.exists()) {
                val json = file.readText()
                val data = Gson().fromJson(json, CachedWeatherData::class.java)
                cachedWeather = data.weather
                cachedCityName = data.cityName
                lastFetchTime = data.timestamp
                cachedAiGreeting = data.aiGreeting
                cachedAiSuggestion = data.aiSuggestion
                cachedAiProTip = data.aiProTip
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
