package com.ali.aiagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAgentClient(
    private val apiKey: String,
    private val modelName: String = "gemini-2.5-flash"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun runNextStep(
        goal: String,
        historyLog: List<String>,
        stepIndex: Int
    ): AgentStepResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AgentStepResult(
                done = true,
                thought = "Missing Gemini API key",
                finalReply = "Please enter your Gemini API key first."
            )
        }

        val systemInstruction = """
            You are an autonomous Android AI Agent built by Ali.
            ${AgentTools.availableToolsDescription()}

            At each step, respond with STRICT JSON ONLY (no markdown fences, no extra text):
            {
              "done": boolean,
              "thought": "short internal reasoning",
              "tool": "calc" | "time" | "notes" | null,
              "arg": "string argument or null",
              "reply": "final answer to user when done=true, or null when done=false"
            }

            Rules:
            - If a tool is needed, set "done": false, provide "tool" and "arg", and set "reply": null.
            - Once you have enough information to answer the user's goal, set "done": true, set "tool": null, set "arg": null, and provide a clear helpful "reply".
            - Never loop endlessly; finish cleanly if no further tool is needed.
        """.trimIndent()

        val logText = if (historyLog.isEmpty()) "(No prior actions yet)" else historyLog.joinToString("\n")
        val promptText = """
            User Goal: $goal
            Step: $stepIndex
            Execution Log:
            $logText
        """.trimIndent()

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemInstruction))
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", promptText))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${apiKey.trim()}"
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini API error (${response.code}):$bodyStr")
            }

            val root = JSONObject(bodyStr)
            val candidates = root.optJSONArray("candidates")
            val firstPartText = candidates
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
                .trim()

            val cleanedJson = firstPartText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val stepJson = JSONObject(cleanedJson)
            val done = stepJson.optBoolean("done", true)
            val thought = stepJson.optString("thought", "")
            val tool = stepJson.optString("tool").takeIf { it.isNotBlank() && it != "null" }
            val arg = stepJson.optString("arg").takeIf { it.isNotBlank() && it != "null" }
            val reply = stepJson.optString("reply").takeIf { it.isNotBlank() && it != "null" }

            AgentStepResult(
                done = done,
                thought = thought,
                toolName = tool,
                toolArgument = arg,
                finalReply = reply
            )
        }
    }
}
