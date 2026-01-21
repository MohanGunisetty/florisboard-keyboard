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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.ui.observeAsState
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

@Composable
fun AiToolbar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val editorInstance by context.editorInstance()
    val prefs by FlorisPreferenceStore
    
    val useFakeData by prefs.smartbar.aiUseFakeData.observeAsState()
    val apiKey by prefs.smartbar.aiApiKey.observeAsState()
    val apiBaseUrl by prefs.smartbar.aiApiBaseUrl.observeAsState()
    
    LaunchedEffect(useFakeData, apiKey, apiBaseUrl) {
        AiReplyManager.configure(
            useFakeData = useFakeData,
            apiKey = apiKey.ifBlank { null },
            baseUrl = apiBaseUrl.ifBlank { null }
        )
    }
    
    val currentTone by AiState.currentTone.collectAsState()
    val detectedMode by AiState.detectedLanguageMode.collectAsState()

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AiToolbarButton(
            text = "AI Reply",
            onClick = {
                val composingText = editorInstance.activeContent.composingText.toString()
                val textBeforeSelection = editorInstance.activeContent.textBeforeSelection.toString()
                val textForContext = composingText.ifBlank { textBeforeSelection }
                
                if (textForContext.isBlank()) {
                    AiState.setAiSuggestions(listOf("Type something first", "No text to reply to", "Enter text"))
                    return@AiToolbarButton
                }
                
                val detectedLanguage = LanguageDetector.detect(textForContext)
                AiState.updateDetectedLanguage(textForContext)
                
                AiReplyManager.generateReplies(
                    typedText = textForContext,
                    languageMode = detectedLanguage,
                    tone = currentTone,
                    onResult = { }
                )
            }
        )

        AiToolbarButton(
            text = "Rewrite",
            onClick = {
                val composingText = editorInstance.activeContent.composingText.toString()
                val textBeforeSelection = editorInstance.activeContent.textBeforeSelection.toString()
                val textToRewrite = composingText.ifBlank { textBeforeSelection }
                
                if (textToRewrite.isBlank()) {
                    AiState.setAiSuggestions(listOf("Type something first", "No text to rewrite", "Enter text"))
                    return@AiToolbarButton
                }
                
                val detectedLanguage = LanguageDetector.detect(textToRewrite)
                AiState.updateDetectedLanguage(textToRewrite)
                
                AiReplyManager.generateRewrites(
                    typedText = textToRewrite,
                    languageMode = detectedLanguage,
                    tone = currentTone,
                    onResult = { }
                )
            }
        )

        ToneSelector(
            currentTone = currentTone,
            onToneChange = { AiState.setTone(it) }
        )

        SnyggBox(
            elementName = FlorisImeUi.SmartbarCandidateWord.elementName,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarCandidateWord.elementName + "-text",
                text = "Mode: ${detectedMode.displayName()}",
            )
        }
    }
}

@Composable
private fun AiToolbarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggBox(
        elementName = FlorisImeUi.SmartbarCandidateWord.elementName,
        selector = selector,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    if (down.pressed != down.previousPressed) down.consume()
                    val upOrCancel = waitForUpOrCancellation()
                    upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                    if (upOrCancel != null) {
                        onClick()
                    }
                    isPressed = false
                }
            },
    ) {
        SnyggText(
            elementName = FlorisImeUi.SmartbarCandidateWord.elementName + "-text",
            selector = selector,
            text = text,
        )
    }
}

@Composable
private fun ToneSelector(
    currentTone: ToneType,
    onToneChange: (ToneType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    if (expanded) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ToneType.entries.forEach { tone ->
                val tonePressed = tone == currentTone
                SnyggBox(
                    elementName = FlorisImeUi.SmartbarCandidateWord.elementName,
                    selector = if (tonePressed) SnyggSelector.PRESSED else SnyggSelector.NONE,
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (down.pressed != down.previousPressed) down.consume()
                                val upOrCancel = waitForUpOrCancellation()
                                upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                                if (upOrCancel != null) {
                                    onToneChange(tone)
                                    expanded = false
                                }
                            }
                        },
                ) {
                    SnyggText(
                        elementName = FlorisImeUi.SmartbarCandidateWord.elementName + "-text",
                        text = tone.displayName().take(3),
                    )
                }
            }
        }
    } else {
        SnyggBox(
            elementName = FlorisImeUi.SmartbarCandidateWord.elementName,
            selector = selector,
            modifier = modifier
                .padding(horizontal = 4.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isPressed = true
                        if (down.pressed != down.previousPressed) down.consume()
                        val upOrCancel = waitForUpOrCancellation()
                        upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                        if (upOrCancel != null) {
                            expanded = true
                        }
                        isPressed = false
                    }
                },
        ) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarCandidateWord.elementName + "-text",
                selector = selector,
                text = currentTone.displayName(),
            )
        }
    }
}
