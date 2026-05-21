package com.mobiledev.arawaraw

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AIChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

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
        setContentView(R.layout.ai_chat_act)

        recyclerView = findViewById(R.id.chat_recycler_view)
        inputEditText = findViewById(R.id.chat_input)
        sendButton = findViewById(R.id.send_btn)
        loadingProgressBar = findViewById(R.id.chat_loading)
        val backBtn = findViewById<ImageButton>(R.id.back_btn)
        val deleteBtn = findViewById<ImageButton>(R.id.delete_chat_btn)

        adapter = ChatAdapter(messages) { suggestion ->
            inputEditText.setText(suggestion)
            sendMessage(suggestion)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        backBtn.setOnClickListener { 
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        deleteBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Conversation")
                .setMessage("Are you sure you want to clear this conversation?")
                .setPositiveButton("Delete") { _, _ ->
                    messages.clear()
                    adapter.notifyDataSetChanged()
                    addMessage(ChatMessage("Hello! I'm your Araw-Araw assistant. How can I help you with your weather-related plans today?", false))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Handle system back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })

        sendButton.setOnClickListener {
            val query = inputEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                sendMessage(query)
            }
        }
        
        // Initial message
        addMessage(ChatMessage("Hello! I'm your Araw-Araw assistant. How can I help you with your weather-related plans today?", false))

        // Check for automatic query from Intent
        val initialQuery = intent.getStringExtra("EXTRA_QUERY")
        if (!initialQuery.isNullOrEmpty()) {
            sendMessage(initialQuery)
        }
    }

    private fun sendMessage(query: String) {
        val history = buildHistoryString()
        addMessage(ChatMessage(query, true))
        inputEditText.text.clear()
        
        loadingProgressBar.visibility = View.VISIBLE
        sendButton.isEnabled = false

        val weatherContext = prepareWeatherContext()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prompt = "You are 'Araw-Araw AI', a helpful weather assistant. use taglish." +
                        "Base your response on this context and history:\n\n" +
                        "$weatherContext\n\n" +
                        "Conversation History:\n$history\n" +
                        "User: $query\n\n" +
                        "If the user asks about laundry, drying, rain, or UV, include technical data in your response. " +
                        "Format your response as a JSON object with these fields:\n" +
                        "- 'text': Your conversational response but not too formal.\n" +
                        "- 'dryingTime': (optional object with 'value' (Double) and 'condition' (String))\n" +
                        "- 'rainRisk': (optional object with 'value' (String e.g. '10%') and 'label' (String e.g. 'Very Low'))\n" +
                        "- 'uvIndex': (optional object with 'value' (String e.g. '8.2') and 'label' (String e.g. 'High'))\n" +
                        "- 'suggestions': (optional list of 2-3 short follow-up questions written from the USER'S perspective. \n\n" +
                        "Return ONLY the JSON object. Don't repeat the user's name or the date unless asked."

                val response = aiService.generateContent(prompt)
                
                withContext(Dispatchers.Main) {
                    loadingProgressBar.visibility = View.GONE
                    sendButton.isEnabled = true
                    if (response != null) {
                        parseAndAddAiMessage(response)
                    } else {
                        addMessage(ChatMessage("Sorry, I'm having trouble connecting right now.", false))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingProgressBar.visibility = View.GONE
                    sendButton.isEnabled = true
                    addMessage(ChatMessage("Sorry, I'm having trouble connecting. Error: ${e.message}", false))
                }
            }
        }
    }

    private fun buildHistoryString(): String {
        val sb = StringBuilder()
        // Last 6 messages for context to stay within token limits
        val historyLimit = 6
        val chatHistory = if (messages.size > historyLimit) messages.takeLast(historyLimit) else messages
        for (msg in chatHistory) {
            val role = if (msg.isUser) "User" else "AI"
            sb.append("$role: ${msg.text}\n")
        }
        return sb.toString()
    }

    private fun parseAndAddAiMessage(rawText: String) {
        try {
            val jsonStart = rawText.indexOf("{")
            val jsonEnd = rawText.lastIndexOf("}")
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                val jsonStr = rawText.substring(jsonStart, jsonEnd + 1)
                val aiResponse = Gson().fromJson(jsonStr, AiResponse::class.java)
                addMessage(ChatMessage(
                    text = aiResponse.text,
                    isUser = false,
                    dryingTime = aiResponse.dryingTime,
                    rainRisk = aiResponse.rainRisk,
                    uvIndex = aiResponse.uvIndex,
                    suggestions = aiResponse.suggestions
                ))
            } else {
                addMessage(ChatMessage(rawText, false))
            }
        } catch (e: Exception) {
            addMessage(ChatMessage(rawText, false))
        }
    }

    private fun prepareWeatherContext(): String {
        val weather = WeatherRepository.cachedWeather ?: return "Weather data is currently unavailable."
        val city = WeatherRepository.cachedCityName ?: "Unknown Location"
        
        val current = weather.current
        val daily = weather.daily
        
        val now = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())
        
        val sb = StringBuilder()
        sb.append("Current Date/Time: $now\n")
        sb.append("Location: $city\n")
        sb.append("Current Weather: Temp ${current.temperature}°C, Humidity ${current.humidity}%, Wind ${current.windSpeed} km/h, Code ${current.weatherCode} (${getWeatherDescription(current.weatherCode)})\n")
        
        if (daily.time.isNotEmpty()) {
            sb.append("\n7-Day Forecast:\n")
            for (i in 0 until minOf(7, daily.time.size)) {
                val date = daily.time[i]
                val tMax = daily.tempMax[i]
                val tMin = daily.tempMin[i]
                val uv = daily.uvIndex[i]
                val code = daily.weatherCode[i]
                val desc = getWeatherDescription(code)
                sb.append("- $date: Max ${tMax}°C, Min ${tMin}°C, UV $uv, $desc\n")
            }
        }
        
        return sb.toString()
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    data class DryingTimeData(val value: Double, val condition: String)
    data class RainRiskData(val value: String, val label: String)
    data class UvIndexData(val value: String, val label: String)
    data class AiResponse(
        val text: String,
        val dryingTime: DryingTimeData? = null,
        val rainRisk: RainRiskData? = null,
        val uvIndex: UvIndexData? = null,
        val suggestions: List<String>? = null
    )

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val dryingTime: DryingTimeData? = null,
        val rainRisk: RainRiskData? = null,
        val uvIndex: UvIndexData? = null,
        val suggestions: List<String>? = null
    )

    class ChatAdapter(
        private val messages: List<ChatMessage>,
        private val onSuggestionClick: (String) -> Unit
    ) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

        class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val userContainer: View = view.findViewById(R.id.user_message_container)
            val userText: TextView = view.findViewById(R.id.user_text)
            val aiContainer: View = view.findViewById(R.id.ai_message_container)
            val aiText: TextView = view.findViewById(R.id.ai_text)
            
            val cardsContainer: View = view.findViewById(R.id.ai_cards_container)
            val dryingCard: View = view.findViewById(R.id.drying_time_card)
            val dryingValue: TextView = view.findViewById(R.id.drying_time_value)
            val dryingProgress: ProgressBar = view.findViewById(R.id.drying_progress)
            val dryingCondition: TextView = view.findViewById(R.id.drying_condition_text)
            
            val rainCard: View = view.findViewById(R.id.rain_risk_card)
            val rainValue: TextView = view.findViewById(R.id.rain_risk_value)
            val rainLabel: TextView = view.findViewById(R.id.rain_risk_label)
            
            val uvCard: View = view.findViewById(R.id.uv_index_card)
            val uvValue: TextView = view.findViewById(R.id.uv_index_value)
            val uvLabel: TextView = view.findViewById(R.id.uv_index_label)
            
            val suggestionsContainer: ViewGroup = view.findViewById(R.id.suggestions_container)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val message = messages[position]
            if (message.isUser) {
                holder.userContainer.visibility = View.VISIBLE
                holder.aiContainer.visibility = View.GONE
                holder.userText.text = message.text
            } else {
                holder.userContainer.visibility = View.GONE
                holder.aiContainer.visibility = View.VISIBLE
                holder.aiText.text = message.text
                
                val hasCards = message.dryingTime != null || message.rainRisk != null || message.uvIndex != null
                holder.cardsContainer.visibility = if (hasCards) View.VISIBLE else View.GONE
                
                // Drying Time
                if (message.dryingTime != null) {
                    holder.dryingCard.visibility = View.VISIBLE
                    holder.dryingValue.text = String.format(Locale.US, "%.1f", message.dryingTime.value)
                    holder.dryingCondition.text = message.dryingTime.condition
                    val progress = ((5.0 - message.dryingTime.value) / 5.0 * 100).toInt().coerceIn(0, 100)
                    holder.dryingProgress.progress = progress
                } else {
                    holder.dryingCard.visibility = View.GONE
                }
                
                // Rain Risk
                if (message.rainRisk != null) {
                    holder.rainCard.visibility = View.VISIBLE
                    holder.rainValue.text = message.rainRisk.value
                    holder.rainLabel.text = message.rainRisk.label
                } else {
                    holder.rainCard.visibility = View.GONE
                }
                
                // UV Index
                if (message.uvIndex != null) {
                    holder.uvCard.visibility = View.VISIBLE
                    holder.uvValue.text = message.uvIndex.value
                    holder.uvLabel.text = message.uvIndex.label
                } else {
                    holder.uvCard.visibility = View.GONE
                }
                
                // Suggestions
                holder.suggestionsContainer.removeAllViews()
                if (!message.suggestions.isNullOrEmpty()) {
                    holder.suggestionsContainer.visibility = View.VISIBLE
                    for (suggestion in message.suggestions) {
                        val textView = TextView(holder.itemView.context).apply {
                            text = suggestion
                            textSize = 13f
                            setTextColor(ContextCompat.getColor(context, R.color.font_color_dark_blue))
                            setBackgroundResource(R.drawable.bg_pill_picnic)
                            val paddingH = dpToPx(context, 20)
                            val paddingV = dpToPx(context, 10)
                            setPadding(paddingH, paddingV, paddingH, paddingV)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = dpToPx(context, 8)
                            }
                            layoutParams = params
                            setOnClickListener { onSuggestionClick(suggestion) }
                        }
                        holder.suggestionsContainer.addView(textView)
                    }
                } else {
                    holder.suggestionsContainer.visibility = View.GONE
                }
            }
        }

        private fun dpToPx(context: Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }

        override fun getItemCount() = messages.size
    }
}
