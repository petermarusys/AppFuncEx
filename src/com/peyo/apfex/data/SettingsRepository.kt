/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.peyo.apfex.data

import kotlinx.coroutines.flow.Flow

/** Repository to manage user settings like API keys and provider selection. */
interface SettingsRepository {
    /** Flow of the Gemini API key. */
    val geminiApiKey: Flow<String?>


    /** Sets the Gemini API key. */
    suspend fun setGeminiApiKey(apiKey: String)

    /**
     * Flow of whether progressive disclosure is enabled for tool selection. When enabled, the model
     * first sees one-line app descriptions and expands app and function details on demand.
     */
    val progressiveToolDisclosure: Flow<Boolean>

    /** Sets whether progressive disclosure is enabled for tool selection. */
    suspend fun setProgressiveToolDisclosure(enabled: Boolean)


    /** Flow of pinned app package names. */
    val pinnedApps: Flow<Set<String>>

    /** Sets the pin status of an app. */
    suspend fun setAppPinned(
        packageName: String,
        pinned: Boolean,
    )

    /** Flow of disconnected app package names. */
    val disconnectedApps: Flow<Set<String>>

    /** Sets the connection status of an app. */
    suspend fun setAppConnected(
        packageName: String,
        connected: Boolean,
    )
}
