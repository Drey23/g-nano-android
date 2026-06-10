package com.example.gnano

import android.graphics.Bitmap
import com.example.gnano.ui.theme.Constants
import com.google.mlkit.genai.prompt.*
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.generateContentRequest
import org.json.JSONObject

class OnDeviceModerationService {

    // Notice: NO API KEY REQUIRED.
    // This connects directly to the Pixel/Galaxy NPU via Android AICore.
    private val nanoModel = Generation.getClient()

    suspend fun moderateImageOffline(bitmap: Bitmap): ModerationResult {
        return try {
            val request = generateContentRequest(
                ImagePart(bitmap),
                TextPart(Constants.promptText)
            ) {
                // Config can be added here if needed
            }

            val response = nanoModel.generateContent(request)
            val resultText = response.candidates.firstOrNull()?.text ?: ""

            parseModerationJson(resultText)

        } catch (e: Exception) {
            // If Android OS Safety blocks the prompt, it throws an exception here
            ModerationResult(
                isViolating = true,
                reason = "Blocked by on-device safety filters: ${e.message}"
            )
        }
    }

    private fun parseModerationJson(jsonString: String): ModerationResult {
        return try {
            // Clean the string if the model returns it wrapped in markdown code blocks
            val cleaned = jsonString.trim().removeSurrounding("```json", "```").trim()
            val json = JSONObject(cleaned)
            ModerationResult(
                isViolating = json.getBoolean("isViolating"),
                reason = json.getString("reason")
            )
        } catch (e: Exception) {
            // If the model didn't return valid JSON, we return the raw text as the reason
            ModerationResult(
                isViolating = false,
                reason = jsonString.ifBlank { "No description provided by model." }
            )
        }
    }
}
