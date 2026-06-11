package com.example.gnano.detection_service

import android.graphics.Bitmap
import com.example.gnano.detection_service.NativeObjectDetectionService
import com.example.gnano.models.DetectionResult

class HybridObjectSearchService {

    private val nativeService = NativeObjectDetectionService()
    private val cloudService = CloudObjectDetectionService()

    suspend fun findObjectInImage(
        bitmap: Bitmap,
        targetObject: String,
        reviewString: String
    ): DetectionResult {

        println("🚀 Attempting Native On-Device Analysis...")

        // Create a software copy to pass to the native service.
        // This prevents the "recycled bitmap" error if the SDK clears it internally.
        val nativeBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

        val nativeResult = nativeService.findObjectInImageOffline(nativeBitmap, targetObject, reviewString)

        // If it's a safety block, stop here and return the result immediately.
        if (!nativeResult.isReviewSafe || nativeResult.details.contains("Safety Block")) {
            println("🛑 Native Safety Block detected. Stopping analysis.")
            return nativeResult
        }

        // If Native fails technically (e.g., model not downloaded or memory crash), bounce to Cloud.
        val errorMessage = nativeResult.details.lowercase()
        if (errorMessage.contains("failure") || errorMessage.contains("error")) {
            println("⚠️ Native technical failure: ${nativeResult.details}. Bouncing to Gemini Cloud.")
            return cloudService.findObjectInImage(bitmap, targetObject, reviewString)
        }

        println("✅ Native Analysis Successful.")
        return nativeResult
    }
}