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
package com.peyo.agent.ui.screens.agentdemo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peyo.agent.MainActivity
import com.peyo.agent.data.LlmModel
import com.peyo.agent.data.LlmProviderName
import com.peyo.agent.data.SettingsRepository
import com.peyo.agent.data.db.entities.MessageProcessingStatus
import com.peyo.agent.data.db.entities.MessageRole
import com.peyo.agent.data.db.entities.ThreadEntity
import com.peyo.agent.domain.AgentOrchestrator
import com.peyo.agent.domain.AgentStatus
import com.peyo.agent.domain.LlmProviderFactory
import com.peyo.agent.domain.appfunction.AppInfo
import com.peyo.agent.domain.appfunction.GetAppFunctionsParams
import com.peyo.agent.domain.appfunction.GetAppFunctionsUseCase
import com.peyo.agent.domain.appfunction.GetInstalledAppsUseCase
import com.peyo.agent.domain.chat.GetChatHistoryUseCase
import com.peyo.agent.domain.chat.ManageThreadsUseCase
import com.peyo.agent.domain.chat.SendMessageUseCase
import com.peyo.agent.domain.pendingintent.ConsumePendingIntentUseCase
import com.peyo.agent.domain.pendingintent.LaunchPendingIntentUseCase
import com.peyo.agent.domain.pendingintent.ObserveActivePendingIntentsUseCase
import com.peyo.agent.domain.resolveDefaultModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AgentDemoViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val getChatHistoryUseCase: GetChatHistoryUseCase,
        private val manageThreadsUseCase: ManageThreadsUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val agentOrchestrator: AgentOrchestrator,
        private val settingsRepository: SettingsRepository,
        private val llmProviderFactory: LlmProviderFactory,
        private val observeActivePendingIntentsUseCase: ObserveActivePendingIntentsUseCase,
        private val launchPendingIntentUseCase: LaunchPendingIntentUseCase,
        private val consumePendingIntentUseCase: ConsumePendingIntentUseCase,
        private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
        private val getAppFunctionsUseCase: GetAppFunctionsUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AgentUiState>(AgentUiState.Loading)
        val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

        private val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
        private val modelChoices = MutableStateFlow(ModelChoices())

        private var observationJob: Job? = null
        private var createLiveThreadJob: Job? = null

        init {
            viewModelScope.launch {
                settingsRepository.geminiApiKey.collectLatest { geminiApiKey ->
                    val choicesByProvider =
                        LlmProviderName.entries.map { providerName ->
                            val apiKey =
                                when (providerName) {
                                    LlmProviderName.GEMINI -> geminiApiKey
                                }
                            val provider = llmProviderFactory.getProvider(providerName)
                            val models = provider.getAvailableModels(apiKey)
                            ModelChoices(models, provider.resolveDefaultModel(models))
                        }
                    val choices =
                        ModelChoices(
                            availableModels = choicesByProvider.flatMap { it.availableModels },
                            defaultModel = choicesByProvider.firstNotNullOfOrNull { it.defaultModel },
                        )
                    modelChoices.value = choices
                    val defaultModel = choices.defaultModel
                    if (defaultModel != null && manageThreadsUseCase.getThreads().first().isEmpty()) {
                        createAndSelectThread(defaultModel)
                    }
                }
            }

            viewModelScope.launch {
                getAppFunctionsUseCase(
                    GetAppFunctionsParams(includeInternalTools = false),
                ).collect { toolsMap ->
                    val allTools = toolsMap.values.flatten()
                    val packagesWithTools = allTools.map { it.packageName }.toSet()
                    val filteredApps =
                        withContext(Dispatchers.IO) {
                            getInstalledAppsUseCase().filter { it.packageName in packagesWithTools }
                        }
                    installedApps.value = filteredApps
                }
            }

            viewModelScope.launch {
                observeActivePendingIntentsUseCase().collect { activePendingActionIds ->
                    val currentState = _uiState.value
                    if (currentState is AgentUiState.Loaded) {
                        _uiState.value =
                            currentState.copy(activePendingActionIds = activePendingActionIds)
                    }
                }
            }

            viewModelScope.launch {
                combine(
                    combine(
                        manageThreadsUseCase.getThreads(),
                        settingsRepository.selectedProvider,
                        agentOrchestrator.status,
                    ) { threads, provider, status ->
                        AgentStatusSnapshot(threads, provider, status)
                    },
                    savedStateHandle.getStateFlow<String?>(MainActivity.ARG_THREAD_ID, null),
                    installedApps,
                    modelChoices,
                ) {
                        snapshot,
                        targetThreadId,
                        apps,
                        choices,
                    ->
                    ThreadConfig(
                        threads = snapshot.threads,
                        provider = snapshot.provider,
                        status = snapshot.status,
                        targetThreadId = targetThreadId,
                        installedApps = apps,
                        modelChoices = choices,
                    )
                }
                    .collectLatest { config ->
                        val currentThread =
                            config.threads.find { it.threadId == config.targetThreadId }
                                ?: config.threads.firstOrNull()

                        val previousThreadId =
                            (_uiState.value as? AgentUiState.Loaded)?.currentThread?.threadId

                        val currentLoadedState = _uiState.value as? AgentUiState.Loaded
                        _uiState.value =
                            AgentUiState.Loaded(
                                currentThread = currentThread,
                                messages =
                                    if (currentThread != null && currentThread.threadId == previousThreadId) {
                                        currentLoadedState?.messages ?: emptyList()
                                    } else {
                                        emptyList()
                                    },
                                status = config.status,
                                threads = config.threads,
                                activePendingActionIds =
                                    currentLoadedState?.activePendingActionIds ?: emptySet(),
                                installedApps = config.installedApps,
                                availableModels = config.modelChoices.availableModels,
                                defaultModel = config.modelChoices.defaultModel,
                            )

                        if (currentThread == null) {
                            observationJob?.cancel()
                            observationJob = null
                        } else if (observationJob == null || previousThreadId != currentThread.threadId) {
                            observationJob?.cancel()
                            observationJob =
                                viewModelScope.launch {
                                    launch {
                                        getChatHistoryUseCase(currentThread.threadId).collect {
                                                messages ->
                                            val currentState = _uiState.value
                                            if (currentState is AgentUiState.Loaded) {
                                                _uiState.value =
                                                    currentState.copy(messages = messages)
                                            }
                                        }
                                    }
                                    launch {
                                        agentOrchestrator.observeAndProcessMessages(
                                            currentThread.threadId,
                                        )
                                    }
                                }
                        }
                    }
            }
        }

        fun onEvent(event: AgentUiEvent) {
            val currentState = _uiState.value
            when (event) {
                is AgentUiEvent.OnSendMessage -> {
                    val currentThread =
                        (currentState as? AgentUiState.Loaded)
                            ?.takeUnless { it.isCurrentThreadLive }
                            ?.currentThread
                    if (currentThread != null) {
                        viewModelScope.launch {
                            sendMessageUseCase(
                                threadId = currentThread.threadId,
                                role = MessageRole.USER,
                                textContent = event.text,
                                processingStatus = MessageProcessingStatus.PENDING_AGENT_RESPONSE,
                                targetPackageName = event.targetPackageName,
                            )
                        }
                    }
                }
                is AgentUiEvent.OnModelSelected -> {
                    if (currentState is AgentUiState.Loaded) {
                        viewModelScope.launch {
                            val currentThread = currentState.currentThread
                            if (currentThread != null && !currentState.isCurrentThreadLive) {
                                manageThreadsUseCase.updateThreadModel(
                                    currentThread.threadId,
                                    event.model,
                                )
                            } else {
                                createAndSelectThread(event.model)
                            }
                        }
                    }
                }
                is AgentUiEvent.OnCreateThread -> {
                    viewModelScope.launch { createAndSelectThread(event.model) }
                }
                is AgentUiEvent.OnThreadSelected -> {
                    savedStateHandle[MainActivity.ARG_THREAD_ID] = event.threadId
                }
                is AgentUiEvent.OnConfirmAction -> {
                    val pendingIntent = consumePendingIntentUseCase(event.pendingIntentId)
                    if (pendingIntent != null) {
                        launchPendingIntentUseCase(pendingIntent)
                    }
                }

                AgentUiEvent.OnAssistantSettingsReturned -> {
                }
                AgentUiEvent.OnAssistantSettingsUnavailable -> {
                }
            }
        }

        private fun clearPendingLaunchThread() {
            savedStateHandle.remove<String?>(KEY_PENDING_LAUNCH_THREAD_ID)
            savedStateHandle.remove<String?>(KEY_PREVIOUS_THREAD_ID)
        }

        private suspend fun createAndSelectThread(llmModel: LlmModel) {
            val threadId = manageThreadsUseCase.createThread(llmModel)
            savedStateHandle[MainActivity.ARG_THREAD_ID] = threadId
        }

        private companion object {
            const val KEY_PENDING_LIVE_TARGET_PACKAGE = "pending_live_target_package"
            const val KEY_PENDING_LAUNCH_THREAD_ID = "pending_launch_live_thread_id"
            const val KEY_PREVIOUS_THREAD_ID = "previous_thread_id_before_live"
        }
    }

private data class AgentStatusSnapshot(
    val threads: List<ThreadEntity>,
    val provider: LlmProviderName,
    val status: AgentStatus,
)

private data class ThreadConfig(
    val threads: List<ThreadEntity>,
    val provider: LlmProviderName,
    val status: AgentStatus,
    val targetThreadId: String?,
    val installedApps: List<AppInfo>,
    val modelChoices: ModelChoices,
)

/** The models the picker offers, and the default model among them for new threads. */
private data class ModelChoices(
    val availableModels: List<LlmModel> = emptyList(),
    val defaultModel: LlmModel? = null,
)
