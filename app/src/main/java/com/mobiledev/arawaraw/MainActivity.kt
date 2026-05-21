package com.mobiledev.arawaraw

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton
import androidx.drawerlayout.widget.DrawerLayout
import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator

import androidx.core.view.GravityCompat

import android.content.Intent
import android.widget.LinearLayout

import android.view.View
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
import android.location.Geocoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var temperatureTextv: TextView
    private lateinit var locationNameTextv: TextView
    private lateinit var currentWeatherIc: ImageView
    private lateinit var weatherSuggestionTextv: TextView
    private lateinit var humidityTextv: TextView
    private lateinit var uvIndexTextv: TextView
    private lateinit var greetingTextv: TextView
    private lateinit var proTipTextv: TextView
    private lateinit var loadingOverlay: View

    private val aiService by lazy {
        val modelPref = PreferenceManager.getAiModel(this)
        when (modelPref) {
            PreferenceManager.MODEL_GROQ -> GroqService(BuildConfig.GROQ_API_KEY)
            PreferenceManager.MODEL_OPENROUTER -> OpenRouterService(BuildConfig.OPENROUTER_API_KEY)
            else -> GeminiService(BuildConfig.GEMINI_API_KEY)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navMenuBtn = findViewById<ImageButton>(R.id.nav_menu_btn)
        val navInsights = findViewById<LinearLayout>(R.id.nav_insights)
        val navSettings = findViewById<LinearLayout>(R.id.nav_settings)
        val refreshBtn = findViewById<ImageButton>(R.id.refresh_btn)
        
        val aiStarBtn = findViewById<ImageButton>(R.id.ai_star_btn)
        loadingOverlay = findViewById<View>(R.id.loading_overlay)

        // Initialize UI elements for weather
        temperatureTextv = findViewById<TextView>(R.id.temperature_textv)
        locationNameTextv = findViewById<TextView>(R.id.location_name_textv)
        currentWeatherIc = findViewById<ImageView>(R.id.current_weather_ic)
        weatherSuggestionTextv = findViewById<TextView>(R.id.id_weather_suggestion_textv)
        humidityTextv = findViewById<TextView>(R.id.humidity_textv)
        uvIndexTextv = findViewById<TextView>(R.id.uv_index_textv)
        greetingTextv = findViewById<TextView>(R.id.greeting_textv)
        proTipTextv = findViewById<TextView>(R.id.pro_tip_textv)

        loadingOverlay.visibility = View.VISIBLE
        fetchLocationAndWeather()

        // Handle system back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            }
        })

        navMenuBtn.setOnClickListener {
            // Open drawer
            drawerLayout.openDrawer(GravityCompat.START)
        }

        refreshBtn.setOnClickListener {
            // Spin animation for the button
            val rotate = ObjectAnimator.ofFloat(refreshBtn, "rotation", 0f, 360f)
            rotate.duration = 1000
            rotate.repeatCount = ObjectAnimator.INFINITE
            rotate.interpolator = LinearInterpolator()
            rotate.start()
            refreshBtn.tag = rotate

            loadingOverlay.visibility = View.VISIBLE
            Toast.makeText(this, "Refreshing weather data...", Toast.LENGTH_SHORT).show()
            fetchLocationAndWeather(forceRefresh = true)
        }

        aiStarBtn.setOnClickListener {
            val intent = Intent(this, AIChatActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        navInsights.setOnClickListener {
            val intent = Intent(this, InsightsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        navSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        // Highlight current menu item
        findViewById<View>(R.id.menu_home).setBackgroundResource(R.drawable.bg_nav_menu_selected)

        // Drawer Menu Click Listeners
        findViewById<View>(R.id.menu_home).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.menu_daily_summary).setOnClickListener {
            val intent = Intent(this, InsightsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.menu_app_preferences).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.menu_about_us).setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.btn_laundry).setOnClickListener {
            startAiChatWithQuery("Pwede ba maglaba ngayon?")
        }
        findViewById<View>(R.id.btn_umbrella).setOnClickListener {
            startAiChatWithQuery("Need ko ba magdala ng umbrella today?")
        }
        findViewById<View>(R.id.btn_sampay).setOnClickListener {
            startAiChatWithQuery("Maganda ba magsampay ng damit ngayon?")
        }
        findViewById<View>(R.id.btn_outdoor).setOnClickListener {
            startAiChatWithQuery("Safe ba mag-outdoor activities ngayon?")
        }

        // Auto-refresh every 15 minutes
        lifecycleScope.launch {
            while (true) {
                delay(15 * 60 * 1000)
                fetchLocationAndWeather(forceRefresh = true)
            }
        }
    }

    private fun startAiChatWithQuery(query: String) {
        val intent = Intent(this, AIChatActivity::class.java)
        intent.putExtra("EXTRA_QUERY", query)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun fetchLocationAndWeather(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            WeatherRepository.cachedAiGreeting = null
            WeatherRepository.cachedAiSuggestion = null
            WeatherRepository.cachedAiProTip = null
        }

        if (!forceRefresh && WeatherRepository.isCacheFresh()) {
            locationNameTextv.text = WeatherRepository.cachedCityName ?: "Quezon City"
            WeatherRepository.cachedWeather?.let { updateUI(it) }
            return
        }


        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    updateLocationName(lat, lon)
                    fetchWeather(lat, lon)
                } else {
                    // Default to Quezon City if location is null
                    updateLocationName(14.6760, 121.0437)
                    fetchWeather(14.6760, 121.0437)
                }
            }
        } catch (e: SecurityException) {
            // Default to Quezon City if permission not granted
            updateLocationName(14.6760, 121.0437)
            fetchWeather(14.6760, 121.0437)
        }
    }

    private fun updateLocationName(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val cityName = addresses[0].locality ?: addresses[0].subAdminArea ?: "Unknown Location"
                    WeatherRepository.cachedCityName = cityName
                    WeatherRepository.saveToDisk(this@MainActivity)
                    withContext(Dispatchers.Main) {
                        locationNameTextv.text = cityName
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchWeather(lat: Double, lon: Double) {
        val units = PreferenceManager.getUnits(this)
        val isImperial = units.contains("Fahrenheit")
        val tempUnit = if (isImperial) "fahrenheit" else "celsius"
        val windUnit = if (isImperial) "mph" else "kmh"
        val precipUnit = if (isImperial) "inch" else "mm"

        val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max" +
                "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit&precipitation_unit=$precipUnit&timezone=auto"
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = URL(urlString).readText()
                val weatherData = Gson().fromJson(response, WeatherResponse::class.java)
                
                WeatherRepository.cachedWeather = weatherData
                WeatherRepository.lastFetchTime = System.currentTimeMillis()
                WeatherRepository.saveToDisk(this@MainActivity)

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
        
        temperatureTextv.text = data.current.temperature.toInt().toString()
        val units = PreferenceManager.getUnits(this)
        findViewById<TextView>(R.id.unit_symbol_textv).text = if (units.contains("Fahrenheit")) "F" else "C"

        humidityTextv.text = "${data.current.humidity}%"
        
        val uv = data.daily.uvIndex[0]
        uvIndexTextv.text = "${uv.toInt()}"
        
        val code = data.current.weatherCode
        currentWeatherIc.setImageResource(getWeatherIcon(code))

        generateAiContent(data)
    }

    private fun generateAiContent(data: WeatherResponse) {
        // Check cache first
        if (WeatherRepository.cachedAiGreeting != null &&
            WeatherRepository.cachedAiSuggestion != null &&
            WeatherRepository.cachedAiProTip != null
        ) {
            greetingTextv.text = WeatherRepository.cachedAiGreeting
            weatherSuggestionTextv.text = WeatherRepository.cachedAiSuggestion
            proTipTextv.text = WeatherRepository.cachedAiProTip
            return
        }

        val city = WeatherRepository.cachedCityName ?: "your location"
        val desc = getWeatherDescription(data.current.weatherCode)
        val temp = data.current.temperature.toInt()
        val humidity = data.current.humidity
        val uv = data.daily.uvIndex[0]

        // Set thinking state
        proTipTextv.text = "Araw-Araw AI is thinking..."

        val forecastSb = StringBuilder()
        if (data.daily.time.isNotEmpty()) {
            for (i in 0 until minOf(7, data.daily.time.size)) {
                forecastSb.append("${data.daily.time[i]}: ${data.daily.tempMax[i]}°C, ${getWeatherDescription(data.daily.weatherCode[i])}; ")
            }
        }

        val currentTime = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm a", Locale.getDefault()).format(Date())

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Toast.makeText(applicationContext, currentTime.toString(), Toast.LENGTH_LONG).show()
                val prompt = "You are 'Araw-Araw AI', an expert weather analyst. Based on this weather in $city: $temp°C, $desc, $humidity% humidity, UV index $uv. " +
                        "7-Day Forecast: $forecastSb" +
                        "Provide 3 short outputs: " +
                        "1. A warm greeting in English (max 30 words). Start with 'Good morning', 'Good afternoon', or 'Good evening' based on the current time (" + currentTime +"), followed by a short, casual message to start their day right." +
                        "2. A clever weather suggestion in english (max 15 words). " +
                        "3. A 'Pro-Tip' in English: Give either a fun weather fact, a quick note if it's a good day for outdoor activities, or a laundry tip (like if clothes will dry fast). Pick only ONE that matches today's weather best (RANDOM). Keep it simple, friendly, and easy to understand with no technical words." +
                        "Format exactly like this: Greeting: [text] | Suggestion: [text] | ProTip: [text]"

                val response = aiService.generateContent(prompt)
                val result = response ?: ""

                withContext(Dispatchers.Main) {
                    if (result.contains("|")) {
                        val parts = result.split("|")
                        val greeting = parts.getOrNull(0)?.substringAfter("Greeting:")?.trim() ?: "Good day!"
                        val suggestion = parts.getOrNull(1)?.substringAfter("Suggestion:")?.trim() ?: "Stay safe!"
                        val proTip = parts.getOrNull(2)?.substringAfter("ProTip:")?.trim() ?: "Check the sky for a great day."
                        
                        greetingTextv.text = greeting
                        weatherSuggestionTextv.text = suggestion
                        proTipTextv.text = proTip

                        // Cache the results
                        WeatherRepository.cachedAiGreeting = greeting
                        WeatherRepository.cachedAiSuggestion = suggestion
                        WeatherRepository.cachedAiProTip = proTip
                        WeatherRepository.saveToDisk(this@MainActivity)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    greetingTextv.text = "Good day! Hope you're doing well."
                    weatherSuggestionTextv.text = "Check the sky and plan your day accordingly."
                    proTipTextv.text = "Did you know? Humidity makes the air feel warmer than it actually is."
                }
            }
        }
    }
}