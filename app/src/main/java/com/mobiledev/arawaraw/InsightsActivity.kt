package com.mobiledev.arawaraw

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

import android.view.View
import android.view.Window
import android.transition.Slide
import android.view.Gravity
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class InsightsActivity : AppCompatActivity() {

    private lateinit var todayDayTextv: TextView
    private lateinit var todayTempTextv: TextView
    private lateinit var todayDescriptionTextv: TextView
    private lateinit var todayWeatherIc: ImageView
    private lateinit var todayHumidityTextv: TextView
    private lateinit var todayWindTextv: TextView
    private lateinit var todayUvTextv: TextView
    private lateinit var todaySuggestionPill: TextView
    private lateinit var todaySubDescriptionTextv: TextView
    private lateinit var loadingOverlay: View

    private lateinit var bestLaundryDay: TextView
    private lateinit var bestLaundryReason: TextView
    private lateinit var bestOutdoorDay: TextView
    private lateinit var bestOutdoorReason: TextView

    private val aiService by lazy {
        val modelPref = PreferenceManager.getAiModel(this)
        when (modelPref) {
            PreferenceManager.MODEL_GROQ -> GroqService(BuildConfig.GROQ_API_KEY)
            PreferenceManager.MODEL_OPENROUTER -> OpenRouterService(BuildConfig.OPENROUTER_API_KEY)
            else -> GeminiService(BuildConfig.GEMINI_API_KEY)
        }
    }

    private val dayNames = mutableListOf<TextView?>()
    private val dayTemps = mutableListOf<TextView?>()
    private val dayIcons = mutableListOf<ImageView?>()
    private val daySuggestions = mutableListOf<TextView?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.insights_activity)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navMenuBtn = findViewById<ImageButton>(R.id.nav_menu_btn)
        val refreshBtn = findViewById<ImageButton>(R.id.refresh_btn)
        val homeBtn = findViewById<LinearLayout>(R.id.nav_home)
        val settingsBtn = findViewById<LinearLayout>(R.id.nav_settings)
        
        val aiStarBtn = findViewById<ImageButton>(R.id.ai_star_btn)
        loadingOverlay = findViewById<View>(R.id.loading_overlay)

        // Initialize UI elements safely
        todayDayTextv = findViewById<TextView>(R.id.today_day_textv)
        todayTempTextv = findViewById<TextView>(R.id.today_temp_textv)
        todayDescriptionTextv = findViewById<TextView>(R.id.today_description_textv)
        todayWeatherIc = findViewById<ImageView>(R.id.today_weather_ic)
        todayHumidityTextv = findViewById<TextView>(R.id.today_humidity_textv)
        todayWindTextv = findViewById<TextView>(R.id.today_wind_textv)
        todayUvTextv = findViewById<TextView>(R.id.today_uv_textv)
        todaySuggestionPill = findViewById<TextView>(R.id.today_suggestion_pill)
        todaySubDescriptionTextv = findViewById<TextView>(R.id.today_sub_description_textv)

        bestLaundryDay = findViewById(R.id.best_laundry_day)
        bestLaundryReason = findViewById(R.id.best_laundry_reason)
        bestOutdoorDay = findViewById(R.id.best_outdoor_day)
        bestOutdoorReason = findViewById(R.id.best_outdoor_reason)

        // Clear existing lists to avoid duplications on config changes
        dayNames.clear()
        dayTemps.clear()
        dayIcons.clear()
        daySuggestions.clear()

        loadingOverlay.visibility = View.VISIBLE
        // Bind 7 days dynamically
        for (i in 1..7) {
            val nameId = resources.getIdentifier("day${i}_name_textv", "id", packageName)
            val tempId = resources.getIdentifier("day${i}_temp_textv", "id", packageName)
            val iconId = resources.getIdentifier("day${i}_weather_ic", "id", packageName)
            val suggestId = resources.getIdentifier("day${i}_suggestion_textv", "id", packageName)

            dayNames.add(if (nameId != 0) findViewById<TextView>(nameId) else null)
            dayTemps.add(if (tempId != 0) findViewById<TextView>(tempId) else null)
            dayIcons.add(if (iconId != 0) findViewById<ImageView>(iconId) else null)
            daySuggestions.add(if (suggestId != 0) findViewById<TextView>(suggestId) else null)
        }

        fetchLocationAndWeather()

        // Handle system back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }
        })

        navMenuBtn.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        refreshBtn.setOnClickListener {
            val rotate = ObjectAnimator.ofFloat(refreshBtn, "rotation", 0f, 360f)
            rotate.duration = 1000
            rotate.repeatCount = ObjectAnimator.INFINITE
            rotate.interpolator = LinearInterpolator()
            rotate.start()
            refreshBtn.tag = rotate

            Toast.makeText(this, "Refreshing weather data...", Toast.LENGTH_SHORT).show()
            fetchLocationAndWeather(forceRefresh = true)
        }

        aiStarBtn.setOnClickListener {
            val intent = Intent(this, AIChatActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        homeBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }

        settingsBtn.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }

        // Highlight current menu item
        findViewById<View>(R.id.menu_daily_summary).setBackgroundResource(R.drawable.bg_nav_menu_selected)

        // Drawer Menu Click Listeners
        findViewById<View>(R.id.menu_home).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            drawerLayout.closeDrawer(GravityCompat.START)
            finish()
        }

        findViewById<View>(R.id.menu_daily_summary).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.menu_app_preferences).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            drawerLayout.closeDrawer(GravityCompat.START)
            finish()
        }

        findViewById<View>(R.id.menu_about_us).setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            drawerLayout.closeDrawer(GravityCompat.START)
            finish()
        }

        // Auto-refresh every 15 minutes
        lifecycleScope.launch {
            while (true) {
                delay(15 * 60 * 1000)
                fetchLocationAndWeather(forceRefresh = true)
            }
        }
    }

    private fun fetchLocationAndWeather(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            loadingOverlay.visibility = View.VISIBLE
        }

        if (!forceRefresh && WeatherRepository.isCacheFresh()) {
            WeatherRepository.cachedWeather?.let { updateUI(it) }
            return
        }


        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    fetchWeather(location.latitude, location.longitude)
                } else {
                    fetchWeather(14.6760, 121.0437)
                }
            }
        } catch (e: SecurityException) {
            fetchWeather(14.6760, 121.0437)
        }
    }

    private fun fetchWeather(lat: Double, lon: Double) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val target = Calendar.getInstance()
        target.firstDayOfWeek = Calendar.MONDAY
        while (target.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            target.add(Calendar.DATE, -1)
        }
        
        val startDate = sdf.format(target.time)
        val sundayCal = target.clone() as Calendar
        sundayCal.add(Calendar.DATE, 6)
        val endDate = sdf.format(sundayCal.time)

        val units = PreferenceManager.getUnits(this)
        val isImperial = units.contains("Fahrenheit")
        val tempUnit = if (isImperial) "fahrenheit" else "celsius"
        val windUnit = if (isImperial) "mph" else "kmh"
        val precipUnit = if (isImperial) "inch" else "mm"

        val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max" +
                "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit&precipitation_unit=$precipUnit" +
                "&timezone=auto&start_date=$startDate&end_date=$endDate"
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = URL(urlString).readText()
                val weatherData = Gson().fromJson(response, WeatherResponse::class.java)
                
                withContext(Dispatchers.Main) {
                    updateUI(weatherData)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    val refreshBtn = findViewById<ImageButton>(R.id.refresh_btn)
                    (refreshBtn.tag as? ObjectAnimator)?.cancel()
                    refreshBtn.rotation = 0f
                }
            }
        }
    }

    private fun updateUI(data: WeatherResponse) {
        loadingOverlay.visibility = View.GONE
        
        // Stop refresh button rotation
        val refreshBtn = findViewById<ImageButton>(R.id.refresh_btn)
        (refreshBtn.tag as? ObjectAnimator)?.cancel()
        refreshBtn.rotation = 0f
        val sdfDay = SimpleDateFormat("EEEE", Locale.getDefault())
        val currentDayName = sdfDay.format(Date()).uppercase()
        todayDayTextv.text = "TODAY •\n$currentDayName"
        
        val units = PreferenceManager.getUnits(this)
        val isImperial = units.contains("Fahrenheit")
        val tempSymbol = if (isImperial) "°F" else "°C"
        val windSymbol = if (isImperial) "mph" else "kmh"

        todayTempTextv.text = "${data.current.temperature.toInt()}°"
        // If you want to show C/F in the main temp here too, but it seems there's only one TextView for temp
        
        todayDescriptionTextv.text = getWeatherDescription(data.current.weatherCode)
        todayWeatherIc.setImageResource(getWeatherIcon(data.current.weatherCode))
        
        val todayUv = if (data.daily.uvIndex.isNotEmpty()) data.daily.uvIndex[0] else 0.0
        val todayTemp = data.current.temperature
        val todayCode = data.current.weatherCode
        
        // Convert back to Celsius for logic if needed, but the logic seems to use absolute values which might be wrong for F
        val tempForLogic = if (isImperial) (todayTemp - 32) * 5 / 9 else todayTemp

        val todaySug = getDetailedSuggestion(todayCode, tempForLogic, todayUv)
        todaySuggestionPill.apply {
            text = todaySug.text
            setBackgroundResource(todaySug.bgRes)
            setTextColor(ContextCompat.getColor(context, todaySug.textColorRes))
            visibility = View.VISIBLE
        }
        
        todaySubDescriptionTextv.text = when {
            todayCode >= 51 -> "Better stay cozy indoors"
            todayUv >= 8 -> "Wear sunscreen if going out"
            tempForLogic >= 34 -> "Stay cool and hydrated"
            else -> "Perfect for your daily plans"
        }

        todayHumidityTextv.text = "Humidity\n${data.current.humidity}%"
        todayWindTextv.text = "Wind\n${data.current.windSpeed.toInt()}$windSymbol"
        
        if (data.daily.uvIndex.isNotEmpty()) {
            val uv = data.daily.uvIndex[0]
            val uvLevel = when {
                uv < 3 -> "Low"
                uv < 6 -> "Mod"
                uv < 8 -> "High"
                uv < 11 -> "V.High"
                else -> "Ext"
            }
            todayUvTextv.text = "UV\n$uvLevel"
        }

        // Update 7-Day Forecast (Monday to Sunday)
        val daily = data.daily
        val size = daily.time.size
        for (i in 0 until minOf(7, size, dayNames.size)) {
            dayNames[i]?.text = getShortDayName(daily.time[i])
            
            val tempMax = if (i < daily.tempMax.size) daily.tempMax[i] else 0.0
            val uvIndex = if (i < daily.uvIndex.size) daily.uvIndex[i] else 0.0
            val code = if (i < daily.weatherCode.size) daily.weatherCode[i] else 0
            
            dayTemps[i]?.text = "${tempMax.toInt()}°"
            dayIcons[i]?.setImageResource(getWeatherIcon(code))
            
            val tempMaxForLogic = if (isImperial) (tempMax - 32) * 5 / 9 else tempMax
            val sug = getDetailedSuggestion(code, tempMaxForLogic, uvIndex)
            daySuggestions[i]?.apply {
                text = sug.text
                setBackgroundResource(sug.bgRes)
                setTextColor(ContextCompat.getColor(context, sug.textColorRes))
            }
        }

        generateAiRecommendations(data)
    }

    private fun generateAiRecommendations(data: WeatherResponse) {
        val forecastSb = StringBuilder()
        if (data.daily.time.isNotEmpty()) {
            for (i in 0 until minOf(7, data.daily.time.size)) {
                val day = getShortDayName(data.daily.time[i])
                forecastSb.append("$day: ${data.daily.tempMax[i]}°C, ${getWeatherDescription(data.daily.weatherCode[i])}; ")
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prompt = "Based on this 7-day weather forecast: $forecastSb\n" +
                        "Find the single best day/time for: 1. Doing Laundry, 2. Outdoor Activities.\n" +
                        "Format your response exactly like this:\n" +
                        "LaundryDay: [Day/Time] | LaundryReason: [Short reason why] | OutdoorDay: [Day/Time] | OutdoorReason: [Short reason why]\n" +
                        "Keep reasons under 8 words. Use Taglish."

                val response = aiService.generateContent(prompt) ?: ""
                
                withContext(Dispatchers.Main) {
                    if (response.contains("|")) {
                        val parts = response.split("|")
                        bestLaundryDay.text = parts.getOrNull(0)?.substringAfter("LaundryDay:")?.trim() ?: "TBD"
                        bestLaundryReason.text = parts.getOrNull(1)?.substringAfter("LaundryReason:")?.trim() ?: "Check forecast"
                        bestOutdoorDay.text = parts.getOrNull(2)?.substringAfter("OutdoorDay:")?.trim() ?: "TBD"
                        bestOutdoorReason.text = parts.getOrNull(3)?.substringAfter("OutdoorReason:")?.trim() ?: "Check forecast"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    bestLaundryDay.text = "TBD"
                    bestLaundryReason.text = "Error fetching AI"
                }
            }
        }
    }

    private fun getShortDayName(dateString: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString)
            SimpleDateFormat("EEE", Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            "???"
        }
    }

    private data class SuggestionData(val text: String, val bgRes: Int, val textColorRes: Int)

    private fun getDetailedSuggestion(code: Int, temp: Double, uv: Double): SuggestionData {
        return when {
            code >= 80 -> SuggestionData("Bring Umbrella", R.drawable.bg_pill_umbrella, R.color.umbrella_red_text)
            code >= 51 -> SuggestionData("Expect Rain", R.drawable.bg_pill_umbrella, R.color.umbrella_red_text)
            uv >= 8 -> SuggestionData("Extreme UV", R.drawable.bg_pill_laundry, R.color.font_color_dark)
            temp >= 34 -> SuggestionData("Stay Hydrated", R.drawable.bg_pill_picnic, R.color.font_color_blue)
            code <= 1 && uv >= 6 -> SuggestionData("Laundry Gold", R.drawable.bg_pill_laundry, R.color.font_color_dark)
            code <= 2 -> SuggestionData("Outdoor Day", R.drawable.bg_pill_picnic, R.color.font_color_blue)
            else -> SuggestionData("Normal Day", R.drawable.bg_pill_picnic, R.color.font_color_blue)
        }
    }
}