/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.ai.api

import dev.patrickgold.florisboard.ime.ai.LanguageMode
import dev.patrickgold.florisboard.ime.ai.ToneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AiApiResult<out T> {
    data class Success<T>(val data: T) : AiApiResult<T>()
    data class Error(val message: String, val code: String? = null) : AiApiResult<Nothing>()
    data object NetworkError : AiApiResult<Nothing>()
    data object Timeout : AiApiResult<Nothing>()
}

object AiApiClient {
    private const val DEFAULT_TIMEOUT_SECONDS = 10L
    private const val BASE_URL = "https://api.florisboard.ai/v1"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private var apiKey: String? = null
    private var baseUrl: String = BASE_URL

    fun configure(apiKey: String?, baseUrl: String? = null) {
        this.apiKey = apiKey
        baseUrl?.let { this.baseUrl = it }
    }

    suspend fun getAiReplies(
        context: String,
        tone: ToneType,
        languageMode: LanguageMode,
        count: Int = 3
    ): AiApiResult<List<String>> = withContext(Dispatchers.IO) {
        val request = AiReplyRequest(
            context = context,
            tone = tone.toApiString(),
            languageMode = languageMode.toApiString(),
            count = count
        )

        val result = post("$baseUrl/reply", json.encodeToString(AiReplyRequest.serializer(), request))
        when (result) {
            is AiApiResult.Success -> {
                try {
                    val response = json.decodeFromString(AiReplyResponse.serializer(), result.data)
                    AiApiResult.Success(response.suggestions)
                } catch (e: Exception) {
                    AiApiResult.Error("Failed to parse response: ${e.message}")
                }
            }
            is AiApiResult.Error -> result
            is AiApiResult.NetworkError -> result
            is AiApiResult.Timeout -> result
        }
    }

    suspend fun getAiRewrites(
        text: String,
        tone: ToneType,
        languageMode: LanguageMode,
        count: Int = 3
    ): AiApiResult<List<String>> = withContext(Dispatchers.IO) {
        val request = AiRewriteRequest(
            text = text,
            tone = tone.toApiString(),
            languageMode = languageMode.toApiString(),
            count = count
        )

        val result = post("$baseUrl/rewrite", json.encodeToString(AiRewriteRequest.serializer(), request))
        when (result) {
            is AiApiResult.Success -> {
                try {
                    val response = json.decodeFromString(AiRewriteResponse.serializer(), result.data)
                    AiApiResult.Success(response.rewrites)
                } catch (e: Exception) {
                    AiApiResult.Error("Failed to parse response: ${e.message}")
                }
            }
            is AiApiResult.Error -> result
            is AiApiResult.NetworkError -> result
            is AiApiResult.Timeout -> result
        }
    }

    private suspend fun post(url: String, body: String): AiApiResult<String> {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body.toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")

        apiKey?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).await()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                AiApiResult.Success(responseBody)
            } else {
                try {
                    val errorResponse = json.decodeFromString(AiErrorResponse.serializer(), responseBody)
                    AiApiResult.Error(errorResponse.error, errorResponse.code)
                } catch (e: Exception) {
                    AiApiResult.Error("HTTP ${response.code}: $responseBody")
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            AiApiResult.Timeout
        } catch (e: IOException) {
            AiApiResult.NetworkError
        } catch (e: Exception) {
            AiApiResult.Error("Unexpected error: ${e.message}")
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }

        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isCancelled) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }
}
