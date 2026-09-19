package com.example.data.remote

interface VerificationApi {

    suspend fun verify(
        request: VerificationRequest
    ): VerificationResponse
}