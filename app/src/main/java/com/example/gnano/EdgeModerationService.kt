package com.example.gnano

import android.graphics.Bitmap
import com.example.gnano.ui.theme.Constants
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ModerationResult(
    val isViolating: Boolean, val reason: String
)

class EdgeModerationService {

    private val model = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = BuildConfig.GEMINI_API_KEY, // PLEASE REPLACE THIS WITH YOUR REAL KEY
        generationConfig = generationConfig {
            responseMimeType = "application/json" // <--- This forces clean JSON output
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
        )
    )

    suspend fun moderateImage(bitmap: Bitmap): ModerationResult = withContext(Dispatchers.IO) {
        val inputContent = content {
            image(bitmap)
            text(Constants.promptText)
        }

        try {
            val response = model.generateContent(inputContent)

            // Check for OS-level safety blocks
            response.promptFeedback?.blockReason?.let { blockReason ->
                return@withContext ModerationResult(
                    isViolating = true,
                    reason = "Blocked by system safety filters: ${blockReason.name}"
                )
            }

            val responseText = response.text ?: return@withContext ModerationResult(
                isViolating = false, reason = "No clear violation detected, but response was empty."
            )

            // Basic JSON parsing from the response string
            return@withContext parseModerationJson(responseText)

        } catch (e: Exception) {
            ModerationResult(
                isViolating = true,
                reason = "Error: ${e.localizedMessage}\n\nMake sure your API key is valid and the model is available in your region."
            )
        }
    }

    private fun parseModerationJson(jsonString: String): ModerationResult {
        return try {
            // Clean the string if the model returns it wrapped in markdown code blocks
            val cleaned = jsonString.trim().removeSurrounding("```json", "```").trim()
            val json = JSONObject(cleaned)
            ModerationResult(
                isViolating = json.getBoolean("isViolating"), reason = json.getString("reason")
            )
        } catch (e: Exception) {
            ModerationResult(
                isViolating = false, reason = "Response was not valid JSON: $jsonString"
            )
        }
    }
}
