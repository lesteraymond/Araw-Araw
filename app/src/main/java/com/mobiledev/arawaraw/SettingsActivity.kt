package com.mobiledev.arawaraw

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class SettingsActivity : AppCompatActivity() {
    private lateinit var loadingOverlay: View
    private lateinit var aiPreferenceText: TextView
    private lateinit var unitsPreferenceText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_act)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navMenuBtn = findViewById<ImageButton>(R.id.nav_menu_btn)
        val refreshBtn = findViewById<ImageButton>(R.id.refresh_btn)
        val homeBtn = findViewById<View>(R.id.nav_home)
        val insightsBtn = findViewById<View>(R.id.nav_insights)
        val aiStarBtn = findViewById<ImageButton>(R.id.ai_star_btn)
        loadingOverlay = findViewById<View>(R.id.loading_overlay)

        aiPreferenceText = findViewById(R.id.ai_preference_text)
        unitsPreferenceText = findViewById(R.id.units_preference_text)

        updatePreferenceUI()

        findViewById<View>(R.id.ai_preference).setOnClickListener {
            showAiModelDialog()
        }

        findViewById<View>(R.id.units_preference).setOnClickListener {
            showUnitsDialog()
        }

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

            Toast.makeText(this, "Fetching latest weather updates...", Toast.LENGTH_SHORT).show()
            loadingOverlay.visibility = View.VISIBLE
            fetchWeatherForCache()
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

        insightsBtn.setOnClickListener {
            val intent = Intent(this, InsightsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }

        // Highlight current menu item
        findViewById<View>(R.id.menu_app_preferences).setBackgroundResource(R.drawable.bg_nav_menu_selected)

        // Drawer Menu Click Listeners
        findViewById<View>(R.id.menu_home).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            drawerLayout.closeDrawer(GravityCompat.START)
            finish()
        }

        findViewById<View>(R.id.menu_daily_summary).setOnClickListener {
            val intent = Intent(this, InsightsActivity::class.java)
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

        findViewById<View>(R.id.menu_app_preferences).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun updatePreferenceUI() {
        aiPreferenceText.text = PreferenceManager.getAiModel(this)
        unitsPreferenceText.text = PreferenceManager.getUnits(this)
    }

    private fun showAiModelDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_model, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val optionGemini = dialogView.findViewById<View>(R.id.option_gemini)
        val optionGroq = dialogView.findViewById<View>(R.id.option_groq)
        val optionOpenRouter = dialogView.findViewById<View>(R.id.option_openrouter)
        val radioGemini = dialogView.findViewById<RadioButton>(R.id.radio_gemini)
        val radioGroq = dialogView.findViewById<RadioButton>(R.id.radio_groq)
        val radioOpenRouter = dialogView.findViewById<RadioButton>(R.id.radio_openrouter)

        val currentModel = PreferenceManager.getAiModel(this)
        radioGemini.isChecked = currentModel == PreferenceManager.MODEL_GEMINI
        radioGroq.isChecked = currentModel == PreferenceManager.MODEL_GROQ
        radioOpenRouter.isChecked = currentModel == PreferenceManager.MODEL_OPENROUTER

        optionGemini.setOnClickListener {
            PreferenceManager.setAiModel(this, PreferenceManager.MODEL_GEMINI)
            updatePreferenceUI()
            dialog.dismiss()
            Toast.makeText(this, "AI Model updated to Gemini", Toast.LENGTH_SHORT).show()
        }

        optionGroq.setOnClickListener {
            PreferenceManager.setAiModel(this, PreferenceManager.MODEL_GROQ)
            updatePreferenceUI()
            dialog.dismiss()
            Toast.makeText(this, "AI Model updated to Groq", Toast.LENGTH_SHORT).show()
        }

        optionOpenRouter.setOnClickListener {
            PreferenceManager.setAiModel(this, PreferenceManager.MODEL_OPENROUTER)
            updatePreferenceUI()
            dialog.dismiss()
            Toast.makeText(this, "AI Model updated to OpenRouter", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showUnitsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_units, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val optionMetric = dialogView.findViewById<View>(R.id.option_metric)
        val optionImperial = dialogView.findViewById<View>(R.id.option_imperial)
        val radioMetric = dialogView.findViewById<RadioButton>(R.id.radio_metric)
        val radioImperial = dialogView.findViewById<RadioButton>(R.id.radio_imperial)

        val currentUnits = PreferenceManager.getUnits(this)
        radioMetric.isChecked = currentUnits == "Celsius, km/h, mm"
        radioImperial.isChecked = currentUnits == "Fahrenheit, mph, in"

        optionMetric.setOnClickListener {
            PreferenceManager.setUnits(this, "Celsius, km/h, mm")
            WeatherRepository.lastFetchTime = 0 // Invalidate cache
            updatePreferenceUI()
            dialog.dismiss()
            Toast.makeText(this, "Units updated to Metric", Toast.LENGTH_SHORT).show()
        }

        optionImperial.setOnClickListener {
            PreferenceManager.setUnits(this, "Fahrenheit, mph, in")
            WeatherRepository.lastFetchTime = 0 // Invalidate cache
            updatePreferenceUI()
            dialog.dismiss()
            Toast.makeText(this, "Units updated to Imperial", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun fetchWeatherForCache() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude

                    val units = PreferenceManager.getUnits(this@SettingsActivity)
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
                            WeatherRepository.saveToDisk(this@SettingsActivity)
                            
                            withContext(Dispatchers.Main) {
                                loadingOverlay.visibility = View.GONE
                                val refreshBtnView = findViewById<ImageButton>(R.id.refresh_btn)
                                (refreshBtnView.tag as? ObjectAnimator)?.cancel()
                                refreshBtnView.rotation = 0f
                            }
                        } catch (e: Exception) { 
                            e.printStackTrace() 
                            withContext(Dispatchers.Main) {
                                loadingOverlay.visibility = View.GONE
                                val refreshBtnView = findViewById<ImageButton>(R.id.refresh_btn)
                                (refreshBtnView.tag as? ObjectAnimator)?.cancel()
                                refreshBtnView.rotation = 0f
                            }
                        }
                    }
                } else {
                    loadingOverlay.visibility = View.GONE
                    val refreshBtnView = findViewById<ImageButton>(R.id.refresh_btn)
                    (refreshBtnView.tag as? ObjectAnimator)?.cancel()
                    refreshBtnView.rotation = 0f
                }
            }
        } catch (e: SecurityException) { 
            loadingOverlay.visibility = View.GONE
            val refreshBtnView = findViewById<ImageButton>(R.id.refresh_btn)
            (refreshBtnView.tag as? ObjectAnimator)?.cancel()
            refreshBtnView.rotation = 0f
        }
    }
}
