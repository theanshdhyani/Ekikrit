package com.example.data.remote

data class VerificationRequest(
    val provider: String,
    val applicationId: String,
    val maskedIdentifier: String
)

data class VerificationResponse(
    val success: Boolean,
    val verified: Boolean,
    val status: String,
    val message: String?
)