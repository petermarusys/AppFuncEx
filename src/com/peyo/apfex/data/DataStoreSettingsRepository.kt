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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Implementation of [SettingsRepository] using Jetpack DataStore. */
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        private object PreferencesKeys {
            val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
            val PINNED_APPS = stringSetPreferencesKey("pinned_apps")
            val DISCONNECTED_APPS = stringSetPreferencesKey("disconnected_apps")
            val PROGRESSIVE_TOOL_DISCLOSURE = booleanPreferencesKey("progressive_tool_disclosure")
        }

        override val progressiveToolDisclosure: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.PROGRESSIVE_TOOL_DISCLOSURE] ?: true
            }

        override suspend fun setProgressiveToolDisclosure(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.PROGRESSIVE_TOOL_DISCLOSURE] = enabled
            }
        }

        override val geminiApiKey: Flow<String?> =
            dataStore.data.map { preferences -> preferences[PreferencesKeys.GEMINI_API_KEY] }

        override val pinnedApps: Flow<Set<String>> =
            dataStore.data.map { preferences -> preferences[PreferencesKeys.PINNED_APPS] ?: emptySet() }

        override val disconnectedApps: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.DISCONNECTED_APPS] ?: emptySet()
            }

        override suspend fun setGeminiApiKey(apiKey: String) {
            dataStore.edit { preferences -> preferences[PreferencesKeys.GEMINI_API_KEY] = apiKey }
        }

        override suspend fun setAppPinned(
            packageName: String,
            pinned: Boolean,
        ) {
            dataStore.edit { preferences ->
                val currentPinned = preferences[PreferencesKeys.PINNED_APPS] ?: emptySet()
                val newPinned =
                    if (pinned) {
                        currentPinned + packageName
                    } else {
                        currentPinned - packageName
                    }
                preferences[PreferencesKeys.PINNED_APPS] = newPinned
            }
        }

        override suspend fun setAppConnected(
            packageName: String,
            connected: Boolean,
        ) {
            dataStore.edit { preferences ->
                val currentDisconnected = preferences[PreferencesKeys.DISCONNECTED_APPS] ?: emptySet()
                val newDisconnected =
                    if (connected) {
                        currentDisconnected - packageName
                    } else {
                        currentDisconnected + packageName
                    }
                preferences[PreferencesKeys.DISCONNECTED_APPS] = newDisconnected
            }
        }
    }
