package com.example.gnano.detection_service

import android.graphics.Bitmap
import com.example.gnano.models.DetectionResult
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import org.json.JSONObject

class NativeObjectDetectionService {

    // Notice: NO API KEY REQUIRED.
    // This connects directly to the Pixel/Galaxy NPU via Android AICore.
    private val nanoModel = Generation.getClient()

    suspend fun findObjectInImageOffline(
        bitmap: Bitmap,
        targetObject: String,
        reviewString: String
    ): DetectionResult {

        // Don't waste compute if the text is empty
        if (targetObject.trim().isEmpty()) {
            return DetectionResult(false, false, "Please enter an object to search for.")
        }
        if (reviewString.trim().isEmpty()) {
            return DetectionResult(false, false, "Please enter the product's review.")
        }

        // The decoupled prompt for dual-task analysis
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

        return try {
            // Gemini Nano handles the image and text parts natively!
            val request = generateContentRequest(
                ImagePart(bitmap),
                TextPart(promptText)
            ) {
                // Config can be added here if needed
            }

            val response = nanoModel.generateContent(request)
            val resultText = response.candidates.firstOrNull()?.text ?: ""

            parseDetectionJson(resultText)

        } catch (e: Exception) {
            val message = e.message ?: ""
            val isSafetyBlock = message.contains("ErrorCode 4") || message.contains("policy", ignoreCase = true)
            
            DetectionResult(
                isFound = false,
                isReviewSafe = !isSafetyBlock, // false if it's a safety block
                details = if (isSafetyBlock) {
                    "Safety Block: The content contains profanity or other material that violates on-device safety policies."
                } else {
                    "Native technical failure: $message"
                }
            )
        }
    }

    private fun parseDetectionJson(jsonString: String): DetectionResult {
        return try {
            // Safely define backticks to avoid markdown parsing errors
            val jsonMarker = "`" + "`" + "`json"
            val backticks = "`" + "`" + "`"

            // Clean the string if the model returns it wrapped in markdown code blocks
            val cleaned = jsonString.trim()
                .removeSurrounding(jsonMarker, backticks)
                .removeSurrounding(backticks)
                .trim()

            val json = JSONObject(cleaned)
            DetectionResult(
                isFound = json.getBoolean("isFound"),
                isReviewSafe = json.getBoolean("isReviewSafe"),
                details = json.getString("details")
            )
        } catch (e: Exception) {
            // If the model didn't return valid JSON, we return the raw text as the details
            DetectionResult(
                isFound = false,
                isReviewSafe = false,
                details = jsonString.ifBlank { "No valid response provided by model." }
            )
        }
    }
}