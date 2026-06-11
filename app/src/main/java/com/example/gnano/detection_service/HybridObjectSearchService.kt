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
        val nativeResult = nativeService.findObjectInImageOffline(bitmap, targetObject, reviewString)

        // If Native fails (e.g., model not downloaded, older phone, or memory crash)
        val errorMessage = nativeResult.details.lowercase()
        if (errorMessage.contains("fail") || errorMessage.contains("error")) {
            println("⚠️ Native failed or unsupported. Bouncing to Gemini Cloud.")
            return cloudService.findObjectInImage(bitmap, targetObject, reviewString)
        }

        println("✅ Native Analysis Successful.")
        return nativeResult
    }
}