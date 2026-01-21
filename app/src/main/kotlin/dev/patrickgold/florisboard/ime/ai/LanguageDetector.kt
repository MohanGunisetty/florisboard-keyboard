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

object LanguageDetector {

    private val teluguUnicodeRange = '\u0C00'..'\u0C7F'

    private val strongTeluglishPatterns = listOf(
        "naku", "nenu", "nuvvu", "enti", "cheppu", "undi", "ledu",
        "vachindi", "velli", "chestha", "chesthav", "chestharu",
        "antha", "antey", "kavali", "kadu", "avunu", "kaadu", "meeru",
        "vaadu", "aavidam", "evaru", "ekkada", "epudu", "endhuku",
        "bagundi", "baguntundi", "chala", "koncham", "inka", "aithe", "kani",
        "chesanu", "chesav", "chesadu", "chesindi", "velthunna", "vasthunna",
        "anna", "akka", "amma", "nanna", "thindi", "pani", "intiki", "bayatiki",
        "chesthunav", "chesthunnav", "veltunna", "vastunna", "ochadu", "ochindi"
    )

    private val weakTeluglishPatterns = listOf(
        "ela", "em", "ra", "naa", "nee", "mee"
    )

    private val strongPatternRegex = strongTeluglishPatterns
        .joinToString("|") { "\\b$it\\b" }
        .toRegex(RegexOption.IGNORE_CASE)

    private val weakPatternRegex = weakTeluglishPatterns
        .joinToString("|") { "\\b$it\\b" }
        .toRegex(RegexOption.IGNORE_CASE)

    private const val WEAK_PATTERN_THRESHOLD = 2

    fun detect(text: String): LanguageMode {
        if (text.isBlank()) {
            return LanguageMode.ENGLISH
        }

        val hasTeluguScript = text.any { it in teluguUnicodeRange }

        if (hasTeluguScript) {
            return LanguageMode.TELUGU
        }

        val hasStrongTeluglishPatterns = strongPatternRegex.containsMatchIn(text)
        if (hasStrongTeluglishPatterns) {
            return LanguageMode.TELUGLISH
        }

        val weakMatchCount = weakPatternRegex.findAll(text).count()
        if (weakMatchCount >= WEAK_PATTERN_THRESHOLD) {
            return LanguageMode.TELUGLISH
        }

        return LanguageMode.ENGLISH
    }

    fun detectFromCurrentInput(currentWord: String, fullText: String): LanguageMode {
        val combinedText = if (fullText.isNotBlank()) fullText else currentWord
        return detect(combinedText)
    }
}
