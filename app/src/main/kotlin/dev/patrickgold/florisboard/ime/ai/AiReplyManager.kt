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

package dev.patrickgold.florisboard.ime.ai

import dev.patrickgold.florisboard.ime.ai.api.AiApiClient
import dev.patrickgold.florisboard.ime.ai.api.AiApiResult
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.time.Duration.Companion.minutes

object AiReplyManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentJob: Job? = null
    private var useFakeData: Boolean = true
    private val requestCounterLock = Any()
    private var requestCounter: Long = 0

    private val replyCache = Cache.Builder<String, List<String>>()
        .maximumCacheSize(50)
        .expireAfterWrite(5.minutes)
        .build()

    private val rewriteCache = Cache.Builder<String, List<String>>()
        .maximumCacheSize(50)
        .expireAfterWrite(5.minutes)
        .build()

    fun configure(useFakeData: Boolean, apiKey: String?, baseUrl: String?) {
        this.useFakeData = useFakeData
        AiApiClient.configure(apiKey, baseUrl)
    }

    private fun generateCacheKey(prefix: String, text: String, languageMode: LanguageMode, tone: ToneType): String {
        val dataToHash = "$text:${languageMode.name}:${tone.name}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(dataToHash.toByteArray(Charsets.UTF_8))
        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
        return "$prefix:$hashHex"
    }

    fun generateReplies(
        typedText: String,
        languageMode: LanguageMode,
        tone: ToneType,
        onResult: (List<String>) -> Unit
    ) {
        currentJob?.cancel()
        AiState.setAiLoading(true)

        val cacheKey = generateCacheKey("reply", typedText, languageMode, tone)
        val cachedResult = replyCache.get(cacheKey)
        if (cachedResult != null) {
            AiState.setAiLoading(false)
            AiState.setAiSuggestions(cachedResult)
            onResult(cachedResult)
            return
        }

        val thisRequestId = synchronized(requestCounterLock) { ++requestCounter }

        currentJob = scope.launch {
            val suggestions = if (useFakeData) {
                getFakeReplySuggestions(languageMode, tone)
            } else {
                when (val result = AiApiClient.getAiReplies(typedText, tone, languageMode)) {
                    is AiApiResult.Success -> result.data
                    is AiApiResult.Error -> getFakeReplySuggestions(languageMode, tone)
                    is AiApiResult.NetworkError -> getFakeReplySuggestions(languageMode, tone)
                    is AiApiResult.Timeout -> getFakeReplySuggestions(languageMode, tone)
                }
            }

            val isLatestRequest = synchronized(requestCounterLock) { thisRequestId == requestCounter }
            if (isLatestRequest && isActive) {
                replyCache.put(cacheKey, suggestions)
                AiState.setAiLoading(false)
                AiState.setAiSuggestions(suggestions)
                onResult(suggestions)
            }
        }
    }

    fun generateRewrites(
        typedText: String,
        languageMode: LanguageMode,
        tone: ToneType,
        onResult: (List<String>) -> Unit
    ) {
        currentJob?.cancel()
        AiState.setAiLoading(true)

        val cacheKey = generateCacheKey("rewrite", typedText, languageMode, tone)
        val cachedResult = rewriteCache.get(cacheKey)
        if (cachedResult != null) {
            AiState.setAiLoading(false)
            AiState.setAiSuggestions(cachedResult)
            onResult(cachedResult)
            return
        }

        val thisRequestId = synchronized(requestCounterLock) { ++requestCounter }

        currentJob = scope.launch {
            val rewrites = if (useFakeData) {
                getFakeRewriteSuggestions(typedText, languageMode, tone)
            } else {
                when (val result = AiApiClient.getAiRewrites(typedText, tone, languageMode)) {
                    is AiApiResult.Success -> result.data
                    is AiApiResult.Error -> getFakeRewriteSuggestions(typedText, languageMode, tone)
                    is AiApiResult.NetworkError -> getFakeRewriteSuggestions(typedText, languageMode, tone)
                    is AiApiResult.Timeout -> getFakeRewriteSuggestions(typedText, languageMode, tone)
                }
            }

            val isLatestRequest = synchronized(requestCounterLock) { thisRequestId == requestCounter }
            if (isLatestRequest && isActive) {
                rewriteCache.put(cacheKey, rewrites)
                AiState.setAiLoading(false)
                AiState.setAiSuggestions(rewrites)
                onResult(rewrites)
            }
        }
    }

    fun cancelCurrentRequest() {
        currentJob?.cancel()
        AiState.setAiLoading(false)
    }

    fun clearCache() {
        replyCache.invalidateAll()
        rewriteCache.invalidateAll()
    }

    private fun getFakeReplySuggestions(
        languageMode: LanguageMode,
        tone: ToneType
    ): List<String> {
        return when (languageMode) {
            LanguageMode.TELUGU -> when (tone) {
                ToneType.CASUAL -> listOf("సరే", "అవును", "తర్వాత చెప్తా")
                ToneType.FRIENDLY -> listOf("సరే, బాగుంది!", "అవును, చేద్దాం!", "తప్పకుండా!")
                ToneType.PROFESSIONAL -> listOf("అలాగే చేస్తాను", "ధన్యవాదాలు", "తెలియజేస్తాను")
            }
            LanguageMode.ENGLISH -> when (tone) {
                ToneType.CASUAL -> listOf("Ok", "Sure", "Sounds good")
                ToneType.FRIENDLY -> listOf("Sure, let's do it!", "Sounds great!", "Absolutely!")
                ToneType.PROFESSIONAL -> listOf("Certainly", "I will look into it", "Thank you for letting me know")
            }
            LanguageMode.TELUGLISH -> when (tone) {
                ToneType.CASUAL -> listOf("Ok ra", "Sare", "Cheptha")
                ToneType.FRIENDLY -> listOf("Sare, bagundi!", "Done ra!", "Definitely chesthanu!")
                ToneType.PROFESSIONAL -> listOf("Okay, I will do", "Sure, will update", "Thanks cheppinanduku")
            }
        }
    }

    private fun getFakeRewriteSuggestions(
        typedText: String,
        languageMode: LanguageMode,
        tone: ToneType
    ): List<String> {
        if (typedText.isBlank()) {
            return listOf("Type something to rewrite", "Enter text first", "No text to rewrite")
        }

        return when (languageMode) {
            LanguageMode.TELUGU -> when (tone) {
                ToneType.CASUAL -> listOf("${typedText} (short)", typedText, typedText)
                ToneType.FRIENDLY -> listOf("$typedText!", "$typedText :)", typedText)
                ToneType.PROFESSIONAL -> listOf(typedText, typedText, typedText)
            }
            LanguageMode.ENGLISH -> when (tone) {
                ToneType.CASUAL -> listOf(
                    typedText.take(20).trim() + if (typedText.length > 20) "..." else "",
                    typedText,
                    typedText.lowercase()
                )
                ToneType.FRIENDLY -> listOf(
                    "$typedText!",
                    "Hey, $typedText",
                    typedText
                )
                ToneType.PROFESSIONAL -> listOf(
                    "Please note: $typedText",
                    "I would like to inform you: $typedText",
                    typedText.replaceFirstChar { it.uppercase() }
                )
            }
            LanguageMode.TELUGLISH -> when (tone) {
                ToneType.CASUAL -> listOf("$typedText ra", typedText, typedText.lowercase())
                ToneType.FRIENDLY -> listOf("$typedText!", "Hey $typedText", typedText)
                ToneType.PROFESSIONAL -> listOf(typedText, "Please $typedText", typedText)
            }
        }
    }
}
