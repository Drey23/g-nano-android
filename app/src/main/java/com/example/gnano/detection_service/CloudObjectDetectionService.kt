package com.example.gnano.detection_service

import android.graphics.Bitmap
import com.example.gnano.BuildConfig
import com.example.gnano.models.DetectionResult
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CloudObjectDetectionService {

    private val model = GenerativeModel(
        modelName = "gemini-3-flash-preview",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
        )
    )

    suspend fun findObjectInImage(
        bitmap: Bitmap,
        targetObject: String,
        reviewString: String
    ): DetectionResult = withContext(Dispatchers.IO) {

        if (targetObject.trim().isEmpty()) {
            return@withContext DetectionResult(
                false,
                false,
                "Please enter an object to search for."
            )
        }
        if (reviewString.trim().isEmpty()) {
            return@withContext DetectionResult(false, false, "Please enter the product's review.")
        }

        val promptText = """
        You are a retail moderation assistant performing TWO completely independent tasks.
        
        TASK 1: OBJECT DETECTION
        Analyze this image and determine if it contains the object: "$targetObject".
        Allow for synonyms. Set `isFound` to true ONLY if the object is in the image. Do not let the text review affect this.
        
        TASK 2: REVIEW SAFETY
        Analyze this product review: "$reviewString".
        Does it violate safety policies (e.g., contains profanity, hate speech, or harassment)?
        Set `isReviewSafe` to false if it violates policy, otherwise true.
        
        You MUST respond ONLY with raw, valid JSON matching this exact structure:
        {
          "isFound": true, 
          "isReviewSafe": true,
          "details": "Explicitly state the result of BOTH tasks. (e.g., 'The item was found in the image. However, the review contains offensive language.')"
        }
        """.trimIndent()

        val inputContent = content {
            image(bitmap)
            text(promptText)
        }

        try {
            val response = model.generateContent(inputContent)

            // Check for safety blocks from the cloud
            response.promptFeedback?.blockReason?.let {
                return@withContext DetectionResult(
                    isFound = false,
                    isReviewSafe = false,
                    details = "Safety Block: The review or image violates safety policies."
                )
            }

            val responseText = response.text ?: return@withContext DetectionResult(
                isFound = false, isReviewSafe = false, details = "Response was empty."
            )

            return@withContext parseDetectionJson(responseText)

        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: "Unknown error"
            val detail = if (errorMessage.contains("SAFETY", ignoreCase = true)) {
                "Safety Block: The content violates safety policies (e.g. profanity)."
            } else {
                "Cloud API error: $errorMessage"
            }
            
            DetectionResult(isFound = false, isReviewSafe = false, details = detail)
        }
    }

    private fun parseDetectionJson(jsonString: String): DetectionResult {
        return try {
            val cleaned = jsonString.trim().removeSurrounding("```json", "```").removeSurrounding("```").trim()
            val json = JSONObject(cleaned)
            DetectionResult(
                isFound = json.getBoolean("isFound"),
                isReviewSafe = json.getBoolean("isReviewSafe"),
                details = json.getString("details")
            )
        } catch (e: Exception) {
            DetectionResult(false, false, "Failed to parse JSON: $jsonString")
        }
    }
}