package com.example.gnano

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gnano.models.DetectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReviewUiState {
    object Idle : ReviewUiState()
    object Loading : ReviewUiState()
    data class Success(val result: DetectionResult) : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}

class ReviewViewModel(
    private val detectionService: HybridObjectSearchService = HybridObjectSearchService()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Idle)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    fun runDetection(bitmap: Bitmap, targetObject: String, reviewString: String) {
        if (targetObject.isBlank() || reviewString.isBlank()) {
            _uiState.value = ReviewUiState.Error("Please fill out both text fields.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            try {
                // The Hybrid service automatically routes between Nano and Cloud!
                val result = detectionService.findObjectInImage(bitmap, targetObject, reviewString)
                _uiState.value = ReviewUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = ReviewUiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun reset() {
        _uiState.value = ReviewUiState.Idle
    }
}