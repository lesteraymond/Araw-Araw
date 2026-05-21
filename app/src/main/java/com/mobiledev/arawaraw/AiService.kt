package com.mobiledev.arawaraw

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

interface AiService {
    suspend fun generateContent(prompt: String): String?
}

class GeminiService(apiKey: String) : AiService {
    private val model = GenerativeModel(modelName = "gemini-2.5-flash", apiKey = apiKey)

    override suspend fun generateContent(prompt: String): String? {
        val response = model.generateContent(prompt)
        val text = response.text
        if (text == null) {
            val reason = response.candidates.firstOrNull()?.finishReason?.name ?: "UNKNOWN"
            throw Exception("Gemini blocked the response. Reason: $reason")
        }
        return text
    }
}

class GroqService(private val apiKey: String) : AiService {
    private val apiUrl = "https://api.groq.com/openai/v1/chat/completions"

    override suspend fun generateContent(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true

                val body = mapOf(
                    "model" to "llama-3.3-70b-versatile",
                    "messages" to listOf(
                        mapOf("role" to "user", "content" to prompt)
                    )
                )
                val jsonBody = Gson().toJson(body)
                conn.outputStream.write(jsonBody.toByteArray())

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = Gson().fromJson(response, GroqResponse::class.java)
                    jsonResponse.choices.firstOrNull()?.message?.content
                } else {
                    val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    throw Exception("Groq Error ${conn.responseCode}: $error")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    private data class GroqResponse(val choices: List<Choice>)
    private data class Choice(val message: Message)
    private data class Message(val content: String)
}

class OpenRouterService(private val apiKey: String) : AiService {
    private val apiUrl = "https://openrouter.ai/api/v1/chat/completions"

    override suspend fun generateContent(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("HTTP-Referer", "https://github.com/mobiledev/arawaraw") // Optional but good practice
                conn.setRequestProperty("X-Title", "Araw-Araw Weather App") // Optional
                conn.doOutput = true

                val body = mapOf(
                    "model" to "openai/gpt-oss-120b:free",
                    "messages" to listOf(
                        mapOf("role" to "user", "content" to prompt)
                    )
                )
                val jsonBody = Gson().toJson(body)
                conn.outputStream.write(jsonBody.toByteArray())

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = Gson().fromJson(response, OpenRouterResponse::class.java)
                    jsonResponse.choices.firstOrNull()?.message?.content
                } else {
                    val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    throw Exception("OpenRouter Error ${conn.responseCode}: $error")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    private data class OpenRouterResponse(val choices: List<Choice>)
    private data class Choice(val message: Message)
    private data class Message(val content: String)
}
