package com.example.gnano.models

data class DetectionResult(
    val isFound: Boolean,
    val isReviewSafe: Boolean,
    val details: String
)