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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AiState {
    private val _currentTone = MutableStateFlow(ToneType.CASUAL)
    val currentTone: StateFlow<ToneType> = _currentTone.asStateFlow()

    private val _detectedLanguageMode = MutableStateFlow(LanguageMode.ENGLISH)
    val detectedLanguageMode: StateFlow<LanguageMode> = _detectedLanguageMode.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiSuggestions = MutableStateFlow<List<String>>(emptyList())
    val aiSuggestions: StateFlow<List<String>> = _aiSuggestions.asStateFlow()

    private val _showAiSuggestions = MutableStateFlow(false)
    val showAiSuggestions: StateFlow<Boolean> = _showAiSuggestions.asStateFlow()

    fun setTone(tone: ToneType) {
        _currentTone.value = tone
    }

    fun updateDetectedLanguage(text: String) {
        _detectedLanguageMode.value = LanguageDetector.detect(text)
    }

    fun setAiLoading(loading: Boolean) {
        _isAiLoading.value = loading
    }

    fun setAiSuggestions(suggestions: List<String>) {
        _aiSuggestions.value = suggestions
        _showAiSuggestions.value = suggestions.isNotEmpty()
    }

    fun clearAiSuggestions() {
        _aiSuggestions.value = emptyList()
        _showAiSuggestions.value = false
    }

    fun hideAiSuggestions() {
        _showAiSuggestions.value = false
    }
}
