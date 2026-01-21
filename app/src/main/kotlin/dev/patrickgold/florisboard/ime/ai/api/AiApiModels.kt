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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiReplyRequest(
    @SerialName("context") val context: String,
    @SerialName("tone") val tone: String,
    @SerialName("language_mode") val languageMode: String,
    @SerialName("count") val count: Int = 3
)

@Serializable
data class AiReplyResponse(
    @SerialName("suggestions") val suggestions: List<String>,
    @SerialName("language_used") val languageUsed: String? = null
)

@Serializable
data class AiRewriteRequest(
    @SerialName("text") val text: String,
    @SerialName("tone") val tone: String,
    @SerialName("language_mode") val languageMode: String,
    @SerialName("count") val count: Int = 3
)

@Serializable
data class AiRewriteResponse(
    @SerialName("rewrites") val rewrites: List<String>,
    @SerialName("language_used") val languageUsed: String? = null
)

@Serializable
data class AiErrorResponse(
    @SerialName("error") val error: String,
    @SerialName("code") val code: String? = null
)

fun ToneType.toApiString(): String = when (this) {
    ToneType.CASUAL -> "casual"
    ToneType.FRIENDLY -> "friendly"
    ToneType.PROFESSIONAL -> "professional"
}

fun LanguageMode.toApiString(): String = when (this) {
    LanguageMode.ENGLISH -> "english"
    LanguageMode.TELUGU -> "telugu"
    LanguageMode.TELUGLISH -> "teluglish"
}
