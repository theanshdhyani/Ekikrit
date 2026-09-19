package com.example.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType

object MockHttpClient {

    private val fakeInterceptor = Interceptor { chain ->

        val request = chain.request()

        val provider = request.header("X-Provider") ?: "UNKNOWN"

        val json = when (provider) {

            "e-District" -> """
                {
                    "success": true,
                    "verified": false,
                    "status": "MISMATCH",
                    "message": "Income variance detected. Routed for officer review."
                }
            """.trimIndent()

            else -> """
                {
                    "success": true,
                    "verified": true,
                    "status": "VERIFIED",
                    "message": "Mock HTTP verification successful for $provider"
                }
            """.trimIndent()
        }

        Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                json.toResponseBody(
                    "application/json".toMediaType()
                )
            )
            .build()
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(fakeInterceptor)
        .build()
}