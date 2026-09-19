package com.example.data.remote

import okhttp3.Request

class FakeVerificationApi : VerificationApi {

    override suspend fun verify(
        request: VerificationRequest
    ): VerificationResponse {

        val httpRequest = Request.Builder()
            .url("https://mock.ekikrit.local/verify")
            .header("X-Provider", request.provider)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()

        MockHttpClient.client.newCall(httpRequest).execute().use { response ->

            val body = response.body?.string()
                ?: throw IllegalStateException("Empty mock response")

            return com.squareup.moshi.Moshi.Builder()
                .build()
                .adapter(VerificationResponse::class.java)
                .fromJson(body)
                ?: throw IllegalStateException("Invalid mock response")
        }
    }
}