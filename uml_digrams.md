# AppFunction Testing Agent (`com.peyo.agent`) — UML Class & Sequence Diagrams

This document provides comprehensive UML **Class Diagrams** and **Sequence Diagrams** for the Android AppFunction Testing Agent (`com.peyo.agent`).

---

## Architecture Overview

The application follows a clean, reactive **UI → Domain → Data** architecture wired with **Dagger Hilt**, **Jetpack Compose**, **Room**, **DataStore**, **Ktor**, and **AndroidX AppFunctions**.

```mermaid
flowchart TB
    subgraph Entry["Application & Shell Privilege"]
        App["AppFunctionsApplication (@HiltAndroidApp)"]
        Instr["ShellIdentityInstrumentation"]
        Act["MainActivity (@AndroidEntryPoint)"]
    end

    subgraph UI["UI Layer (com.peyo.agent.ui)"]
        Screen["AgentDemoScreen & Composables"]
        VM["AgentDemoViewModel (@HiltViewModel)"]
        State["AgentUiState / AgentUiEvent / LiveSetupPrompt"]
    end

    subgraph Domain["Domain Layer (com.peyo.agent.domain)"]
        Orch["AgentOrchestrator (@Singleton)"]
        ChatUC["Chat UseCases"]
        ApfUC["AppFunction UseCases"]
        PiUC["PendingIntent & Consent UseCases"]
        LlmPort["LlmProvider / LlmProviderFactory / LiveLlmProvider"]
    end

    subgraph Data["Data Layer (com.peyo.agent.data)"]
        Gemini["GeminiProviderImpl & GeminiToolConverter"]
        IntTools["AgentInternalTools & ToolDiscoveryTools"]
        Repos["RoomChatRepository / DataStoreSettingsRepository / InMemoryPendingIntentRepository"]
        Helpers["FileDownloadHelperImpl / ResultImageReaderImpl"]
        DB["AppDatabase & ChatDao (Room)"]
    end

    subgraph External["System & Remote Services"]
        ApfMgr["AndroidX AppFunctionManager"]
        GeminiAPI["Google Gemini API (Interactions / Models / Image)"]
    end

    Instr -->|adopts EXECUTE_APP_FUNCTIONS & launches| Act
    Act --> Screen
    Screen <-->|StateFlow / AgentUiEvent| VM
    VM --> ChatUC
    VM --> ApfUC
    VM --> PiUC
    VM --> Orch
    VM --> LlmPort
    Orch --> ChatUC
    Orch --> ApfUC
    Orch --> PiUC
    Orch --> LlmPort
    ChatUC --> Repos
    PiUC --> Repos
    Repos --> DB
    ApfUC --> ApfMgr
    ApfUC --> IntTools
    ApfUC --> Helpers
    Gemini -.->|implements| LlmPort
    Gemini --> GeminiAPI
    IntTools --> GeminiAPI
```

---

## 1. UML Class Diagrams

### 1.1 Application Entry Points & UI Presentation Layer

Covers application bootstrap, shell permission instrumentation, Compose UI components, state holders, and `AgentDemoViewModel`.

```mermaid
classDiagram
    direction TB

    class AppFunctionsApplication {
        <<HiltAndroidApp>>
    }

    class ShellIdentityInstrumentation {
        +onCreate(arguments: Bundle?)
        +onStart()
    }

    class MainActivity {
        +ARG_THREAD_ID: String$
        +EXTRA_INSTRUMENTATION_ERROR: String$
        +onCreate(savedInstanceState: Bundle?)
    }

    class AgentDemoScreenKt {
        <<ComposableModule>>
        +AgentDemoScreen(viewModel: AgentDemoViewModel)
        +AgentDemoContent(uiState: AgentUiState, onEvent: Function1)
        +AgentDemoLoadingScreen()
        +AgentDemoLoadedScreen(uiState: Loaded, onEvent: Function1)
        +ModelDropdown(currentThread: ThreadEntity?, availableModels: List~LlmModel~)
        +MessageBubble(message: MessageEntity, isValidAction: Boolean)
        +StatusIndicator(status: AgentStatus, packageManager: PackageManager)
        +ChatHistorySidePanel(threads: List~ThreadEntity~, currentThread: ThreadEntity?)
        +formatMessageText(text: String, installedApps: List~AppInfo~) AnnotatedString
    }

    class InlineAppScopingVisualTransformation {
        -installedApps: List~AppInfo~
        -chipTextColor: Color
        -regex: Regex?
        +filter(text: AnnotatedString) TransformedText
    }

    class LazyExposedDropdownMenuKt {
        <<ComposableModule>>
        +LazyExposedDropdownMenu(expanded: Boolean, onDismissRequest: Function0)
    }

    class StatusWarningDialogKt {
        <<ComposableModule>>
        +StatusWarningDialog(title: String, message: String, isError: Boolean, onDismiss: Function0)
    }

    class AgentDemoViewModel {
        -savedStateHandle: SavedStateHandle
        -getChatHistoryUseCase: GetChatHistoryUseCase
        -manageThreadsUseCase: ManageThreadsUseCase
        -sendMessageUseCase: SendMessageUseCase
        -agentOrchestrator: AgentOrchestrator
        -settingsRepository: SettingsRepository
        -llmProviderFactory: LlmProviderFactory
        -observeActivePendingIntentsUseCase: ObserveActivePendingIntentsUseCase
        -launchPendingIntentUseCase: LaunchPendingIntentUseCase
        -consumePendingIntentUseCase: ConsumePendingIntentUseCase
        -getInstalledAppsUseCase: GetInstalledAppsUseCase
        -getAppFunctionsUseCase: GetAppFunctionsUseCase
        +uiState: StateFlow~AgentUiState~
        +onEvent(event: AgentUiEvent)
        -createAndSelectThread(llmModel: LlmModel)
        -clearPendingLaunchThread()
    }

    class AgentUiState {
        <<sealed>>
    }

    class AgentUiState_Loading {
        <<object>>
    }

    class AgentUiState_Loaded {
        +currentThread: ThreadEntity?
        +isCurrentThreadLive: Boolean
        +messages: List~MessageEntity~
        +status: AgentStatus
        +liveStatus: LiveAgentStatus?
        +isAnotherThreadLiveBusy: Boolean
        +liveSetupPrompt: LiveSetupPrompt?
        +threads: List~ThreadEntity~
        +activePendingActionIds: Set~String~
        +installedApps: List~AppInfo~
        +availableModels: List~LlmModel~
        +defaultModel: LlmModel?
        +newThreadModel: LlmModel?
    }

    class AgentUiEvent {
        <<sealed>>
        +OnSendMessage(text: String, targetPackageName: String?)
        +OnModelSelected(model: LlmModel)
        +OnCreateThread(model: LlmModel)
        +OnThreadSelected(threadId: String)
        +OnConfirmAction(pendingIntentId: String)
        +OnAssistantSettingsReturned
        +OnAssistantSettingsUnavailable
    }

    class LiveSetupPrompt {
        <<sealed>>
        +RequestAudioPermission(targetPackageName: String?)
        +SelectDigitalAssistant(targetPackageName: String?)
        +LaunchAssistSession(threadId: String, targetPackageName: String?)
        +AudioPermissionDenied
        +AssistantNotSelected
        +AssistantSettingsUnavailable
        +AssistLaunchFailed
    }

    ShellIdentityInstrumentation ..> MainActivity : launches with shell UID permission
    MainActivity --> AgentDemoScreenKt : hosts
    AgentDemoScreenKt --> AgentDemoViewModel : observes uiState & dispatches events
    AgentDemoScreenKt --> InlineAppScopingVisualTransformation : formats @app mentions
    AgentDemoScreenKt --> LazyExposedDropdownMenuKt : renders model list
    AgentDemoScreenKt ..> StatusWarningDialogKt : status dialog
    AgentDemoViewModel --> AgentUiState : emits
    AgentDemoViewModel ..> AgentUiEvent : handles
    AgentUiState <|-- AgentUiState_Loading
    AgentUiState <|-- AgentUiState_Loaded
    AgentUiState_Loaded --> LiveSetupPrompt : optional prompt
```

---

### 1.2 Core Domain: Agent Orchestration, LLM Contracts & Live Session Abstractions

Covers `AgentOrchestrator`, status state machines, text/multimodal LLM provider abstractions, and realtime bidirectional voice (`LiveLlmProvider` / `LiveAudioSession`) contracts.

```mermaid
classDiagram
    direction TB

    class AgentOrchestrator {
        <<Singleton>>
        -manageThreadsUseCase: ManageThreadsUseCase
        -observePendingMessagesUseCase: ObservePendingMessagesUseCase
        -sendMessageUseCase: SendMessageUseCase
        -updateMessageUseCase: UpdateMessageUseCase
        -updateThreadUseCase: UpdateThreadUseCase
        -llmProviderFactory: LlmProviderFactory
        -settingsRepository: SettingsRepository
        -getAppFunctionsUseCase: GetAppFunctionsUseCase
        -resolveRemoteFileToLocalUriUseCase: ResolveRemoteFileToLocalUriUseCase
        -convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase
        -executeAppFunctionUseCase: ExecuteAppFunctionUseCase
        -loadResultImagesUseCase: LoadResultImagesUseCase
        -savePendingIntentUseCase: SavePendingIntentUseCase
        +status: StateFlow~AgentStatus~
        +observeAndProcessMessages(threadId: String)
        -processMessage(message: MessageEntity, thread: ThreadEntity)
        -runInteractionLoop(message: MessageEntity, thread: ThreadEntity, apiKey: String, tools: List~AppFunctionMetadata~, progressiveToolDisclosure: Boolean, initialInput: String)
        -handleLlmResponse(response: LlmResponse, message: MessageEntity, activeTools: MutableList~AppFunctionMetadata~, progressiveToolDisclosure: Boolean, capturedAttachments: MutableList~MessageAttachment~) HandleResult
        -executeToolCalls(toolCalls: List~LlmResponsePart~, activeTools: MutableList~AppFunctionMetadata~, progressiveToolDisclosure: Boolean, message: MessageEntity, capturedAttachments: MutableList~MessageAttachment~) ExecuteToolCallsResult
    }

    class AgentStatus {
        <<sealed>>
        +Idle
        +Thinking
        +CheckingCapabilities
        +ExploringApps(packageNames: List~String~)
        +PlanningWithFunctions(packageNames: List~String~, functionNames: List~String~)
        +InvokingTool(functionId: String, packageName: String)
    }

    class LlmProviderFactory {
        <<interface>>
        +getProvider(name: LlmProviderName) LlmProvider
    }

    class LlmProvider {
        <<interface>>
        +defaultModelName: String?
        +getAvailableModels(apiKey: String?) List~LlmModel~
        +generateResponse(previousInteractionId: String?, input: LlmInput, tools: List~AppFunctionMetadata~, apiKey: String, modelName: String, serviceTier: ServiceTier) LlmResponse
    }

    class LlmInput {
        <<sealed>>
        +UserMessage(text: String)
        +ToolResponse(outputs: List~ToolOutput~)
    }

    class ToolOutput {
        +functionId: String
        +callId: String
        +result: String
        +images: List~ToolOutputImage~
    }

    class ToolOutputImage {
        +mimeType: String
        +data: ByteArray
    }

    class LlmResponse {
        <<sealed>>
        +Success(interactionId: String, parts: List~LlmResponsePart~)
        +Error(errorMessage: String)
    }

    class LlmResponsePart {
        <<sealed>>
        +Text(text: String)
        +ToolCall(packageName: String, functionId: String, arguments: Map, callId: String)
        +Thought(thought: String)
        +UnknownToolCall(name: String, callId: String)
    }

    class LiveLlmProvider {
        <<interface>>
        +liveModel: LlmModel
        +connect(apiKey: String, tools: List~AppFunctionMetadata~, voiceName: String?) LiveLlmSession
    }

    class LiveLlmSession {
        <<interface>>
        +audioConfig: LiveAudioConfig
        +events: Flow~LiveLlmEvent~
        +sendAudio(pcmBytes: ByteArray, sampleRateHz: Int)
        +sendToolResponses(outputs: List~ToolOutput~)
        +close()
    }

    class LiveLlmEvent {
        <<sealed>>
        +Interrupted
        +InputTranscription(text: String)
        +OutputTranscription(text: String)
        +AudioChunk(pcmBytes: ByteArray, sampleRateHz: Int)
        +TurnComplete
        +ToolCallRequest(calls: List~ToolCall~)
        +Error(message: String, cause: Throwable?, isFatal: Boolean)
    }

    class LiveAudioSessionFactory {
        <<fun interface>>
        +create(config: LiveAudioConfig) LiveAudioSession
    }

    class LiveAudioSession {
        <<interface>>
        +inputSampleRateHz: Int
        +start()
        +listenToRecording() Flow~ByteArray~
        +pauseRecording()
        +resumeRecording()
        +queueAudio(pcmBytes: ByteArray, sampleRateHz: Int)
        +stopAudio()
        +release()
    }

    class LiveAudioConfig {
        +inputSampleRateHz: Int
        +outputSampleRateHz: Int
    }

    class LiveAgentStatus {
        <<sealed>>
        +Connecting
        +Listening
        +InvokingTool(functionId: String, packageName: String)
        +Ended
        +Error(message: String)
    }

    class ActiveLiveSession {
        +threadId: String
        +status: LiveAgentStatus
    }

    AgentOrchestrator --> AgentStatus : publishes
    AgentOrchestrator --> LlmProviderFactory : resolves provider
    LlmProviderFactory --> LlmProvider : provides
    LlmProvider ..> LlmInput : consumes
    LlmProvider ..> LlmResponse : returns
    LlmInput --> ToolOutput : wraps
    ToolOutput --> ToolOutputImage : contains
    LlmResponse --> LlmResponsePart : contains
    LiveLlmProvider --> LiveLlmSession : creates
    LiveLlmSession --> LiveAudioConfig : configures
    LiveLlmSession --> LiveLlmEvent : streams
    LiveAudioSessionFactory --> LiveAudioSession : creates
    LiveAudioSessionFactory ..> LiveAudioConfig : uses
    ActiveLiveSession --> LiveAgentStatus : tracks
```

---

### 1.3 Domain Use Cases (Chat, AppFunction, PendingIntent & User Consent)

Covers all domain use cases mediating between `AgentDemoViewModel` / `AgentOrchestrator` and the data repositories / Android `AppFunctionManager`.

```mermaid
classDiagram
    direction TB

    class GetChatHistoryUseCase {
        -chatRepository: ChatRepository
        +invoke(threadId: String) Flow~List_MessageEntity~
    }

    class ManageThreadsUseCase {
        -chatRepository: ChatRepository
        +getThreads() Flow~List_ThreadEntity~
        +getThread(threadId: String) Flow~ThreadEntity~
        +createThread(llmModel: LlmModel) String
        +updateThreadModel(threadId: String, llmModel: LlmModel)
        +deleteThread(threadId: String)
    }

    class ObservePendingMessagesUseCase {
        -chatRepository: ChatRepository
        +invoke(threadId: String) Flow~MessageEntity~
    }

    class SendMessageUseCase {
        -chatRepository: ChatRepository
        +invoke(threadId: String, role: MessageRole, textContent: String, processingStatus: MessageProcessingStatus, pendingIntentId: String?, targetPackageName: String?, attachments: List~MessageAttachment~)
    }

    class UpdateMessageUseCase {
        -chatRepository: ChatRepository
        +invoke(messageId: String, params: UpdateMessageParams)
    }

    class UpdateThreadUseCase {
        -chatRepository: ChatRepository
        +invoke(threadId: String, params: UpdateThreadParams)
    }

    class GetAppFunctionsUseCase {
        -appFunctionManager: AppFunctionManager?
        -getAppFunctionStatesUseCase: GetAppFunctionStatesUseCase
        -settingsRepository: SettingsRepository
        -agentInternalTools: AgentInternalTools
        +invoke(params: GetAppFunctionsParams) Flow~AppFunctionsMap~
    }

    class GetAppFunctionsParams {
        +targetPackageNames: Set~String~?
        +isEnabled: Boolean?
        +isConnected: Boolean?
        +includeInternalTools: Boolean
        +progressiveToolDisclosure: Boolean
    }

    class GetAppFunctionStatesUseCase {
        -appFunctionManager: AppFunctionManager?
        +invoke(functionNames: List~AppFunctionName~) List~AppFunctionState~
    }

    class GetInstalledAppsUseCase {
        -context: Context
        +invoke() List~AppInfo~
    }

    class AppInfo {
        +packageName: String
        +label: String
        +icon: Drawable?
    }

    class ExecuteAppFunctionUseCase {
        -appFunctionManager: AppFunctionManager?
        -convertAppFunctionDataToJsonUseCase: ConvertAppFunctionDataToJsonUseCase
        -agentInternalTools: AgentInternalTools
        -grantAppFunctionUriPermissionsUseCase: GrantAppFunctionUriPermissionsUseCase
        -getAppFunctionsUseCase: GetAppFunctionsUseCase
        +invoke(function: AppFunctionMetadata, parameters: AppFunctionData, threadId: String) ExecuteAppFunctionResult
    }

    class ExecuteAppFunctionResult {
        <<sealed>>
        +Data(data: AppFunctionData, formattedJson: String)
        +PendingIntentAction(pendingIntent: PendingIntent)
        +ToolsDiscovered(formattedJson: String, tools: List~AppFunctionMetadata~)
        +Error(exception: Exception)
    }

    class ConvertInputToAppFunctionDataUseCase {
        +invoke(parameters: List~AppFunctionParameterMetadata~, components: AppFunctionComponentsMetadata, inputs: Map) Result~AppFunctionData~
    }

    class ConvertAppFunctionDataToJsonUseCase {
        +invoke(name: String, data: AppFunctionData, dataType: AppFunctionDataTypeMetadata, components: AppFunctionComponentsMetadata) String
    }

    class ResolveRemoteFileToLocalUriUseCase {
        -fileDownloadHelper: FileDownloadHelper
        +invoke(parameters: List~AppFunctionParameterMetadata~, components: AppFunctionComponentsMetadata, inputs: Map) Map
    }

    class GrantAppFunctionUriPermissionsUseCase {
        -context: Context
        +invoke(function: AppFunctionMetadata, parameters: AppFunctionData)
    }

    class LoadResultImagesUseCase {
        -resultImageReader: ResultImageReader
        +invoke(function: AppFunctionMetadata, result: ExecuteAppFunctionResult_Data) ResultImages
    }

    class ResultImages {
        +images: List~ToolOutputImage~
        +skipped: List~SkippedResultImage~
    }

    class AppFunctionExceptionFormatter {
        <<object>>
        +format(exception: AppFunctionException, functionId: String?) String
        +getAppFunctionException(throwable: Throwable?) AppFunctionException?
    }

    class ToolConverter~T~ {
        <<interface>>
        +convert(tool: AppFunctionMetadata) T
        +getToolName(tool: AppFunctionMetadata) String
    }

    class SavePendingIntentUseCase {
        -pendingIntentRepository: PendingIntentRepository
        +invoke(pendingIntentId: String, pendingIntent: PendingIntent)
    }

    class ConsumePendingIntentUseCase {
        -pendingIntentRepository: PendingIntentRepository
        +invoke(pendingIntentId: String) PendingIntent?
    }

    class ObserveActivePendingIntentsUseCase {
        -pendingIntentRepository: PendingIntentRepository
        +invoke() StateFlow~Set_String~
    }

    class LaunchPendingIntentUseCase {
        -context: Context
        +invoke(pendingIntent: PendingIntent) Result~Unit~
    }

    class HandleUserConsentUseCase {
        <<Singleton>>
        -context: Context
        +invoke(intentSender: IntentSender)
    }

    GetAppFunctionsUseCase ..> GetAppFunctionsParams : uses
    GetAppFunctionsUseCase --> GetAppFunctionStatesUseCase : checks enabled state
    GetInstalledAppsUseCase ..> AppInfo : produces
    ExecuteAppFunctionUseCase --> GrantAppFunctionUriPermissionsUseCase : grants content URIs
    ExecuteAppFunctionUseCase --> ConvertAppFunctionDataToJsonUseCase : serializes output
    ExecuteAppFunctionUseCase --> GetAppFunctionsUseCase : queries apps for discovery
    ExecuteAppFunctionUseCase ..> ExecuteAppFunctionResult : returns
    LoadResultImagesUseCase ..> ResultImages : returns
```

---

### 1.4 Data Layer, Internal Tools, Persistence (Room & DataStore) & Hilt DI

Covers repository interfaces and implementations, Gemini HTTP client integration, built-in and progressive discovery tools, Room database schema, and Hilt modules.

```mermaid
classDiagram
    direction TB

    class DataModule {
        <<HiltModule>>
        +bindSettingsRepository(impl: DataStoreSettingsRepository) SettingsRepository
        +bindChatRepository(impl: RoomChatRepository) ChatRepository
        +bindPendingIntentRepository(impl: InMemoryPendingIntentRepository) PendingIntentRepository
        +bindLlmProviderFactory(impl: LlmProviderFactoryImpl) LlmProviderFactory
        +bindFileDownloadHelper(impl: FileDownloadHelperImpl) FileDownloadHelper
        +bindResultImageReader(impl: ResultImageReaderImpl) ResultImageReader
        +provideDataStore(context: Context) DataStore$
        +provideAppDatabase(context: Context) AppDatabase$
        +provideChatDao(db: AppDatabase) ChatDao$
        +provideHttpClient() HttpClient$
    }

    class AppFunctionModule {
        <<HiltModule>>
        +provideAppFunctionManager(context: Context) AppFunctionManager?$
    }

    class ChatRepository {
        <<interface>>
        +getMessagesForThread(threadId: String) Flow~List_MessageEntity~
        +sendMessage(message: MessageEntity)
        +updateMessageStatus(messageId: String, status: MessageProcessingStatus)
        +createThread(thread: ThreadEntity)
        +updateThreadModel(threadId: String, llmModel: LlmModel)
        +getThreads() Flow~List_ThreadEntity~
        +getThread(threadId: String) Flow~ThreadEntity~
        +deleteThread(threadId: String)
        +getLatestInteractionId(threadId: String) Flow~String~
        +updateLatestInteractionId(threadId: String, interactionId: String)
        +getThreadByInteractionId(interactionId: String) Flow~ThreadEntity~
    }

    class RoomChatRepository {
        <<Singleton>>
        -chatDao: ChatDao
    }

    class AppDatabase {
        <<RoomDatabase>>
        +chatDao() ChatDao
    }

    class ChatDao {
        <<Dao>>
        +getMessagesForThread(threadId: String) Flow~List_MessageEntity~
        +insertMessage(message: MessageEntity)
        +updateMessageStatus(messageId: String, status: MessageProcessingStatus)
        +insertThread(thread: ThreadEntity)
        +updateLatestInteractionId(threadId: String, interactionId: String)
        +updateThreadModel(threadId: String, llmModel: LlmModel)
        +getThreads() Flow~List_ThreadEntity~
        +getThread(threadId: String) Flow~ThreadEntity~
        +deleteThread(threadId: String)
    }

    class ThreadEntity {
        <<Entity>>
        +threadId: String
        +createdAt: Long
        +llmModel: LlmModel
        +latestInteractionId: String?
    }

    class MessageEntity {
        <<Entity>>
        +messageId: String
        +threadId: String
        +role: MessageRole
        +textContent: String
        +timestamp: Long
        +processingStatus: MessageProcessingStatus
        +pendingIntentId: String?
        +targetPackageName: String?
        +attachments: List~MessageAttachment~
    }

    class MessageAttachment {
        +uri: String
        +mimeType: String
    }

    class MessageRole {
        <<enumeration>>
        USER
        ASSISTANT
    }

    class MessageProcessingStatus {
        <<enumeration>>
        PENDING_AGENT_RESPONSE
        PROCESSED
        FAILED
    }

    class SettingsRepository {
        <<interface>>
        +geminiApiKey: Flow~String~
        +selectedProvider: Flow~LlmProviderName~
        +serviceTier: Flow~ServiceTier~
        +progressiveToolDisclosure: Flow~Boolean~
        +pinnedApps: Flow~Set_String~
        +disconnectedApps: Flow~Set_String~
        +getApiKey(provider: LlmProviderName) Flow~String~
        +setGeminiApiKey(apiKey: String)
        +setSelectedProvider(provider: LlmProviderName)
        +setServiceTier(tier: ServiceTier)
        +setProgressiveToolDisclosure(enabled: Boolean)
        +setAppPinned(packageName: String, pinned: Boolean)
        +setAppConnected(packageName: String, connected: Boolean)
    }

    class DataStoreSettingsRepository {
        -dataStore: DataStore~Preferences~
    }

    class LlmProviderName {
        <<enumeration>>
        GEMINI
        +displayName: String
    }

    class LlmModel {
        +providerName: LlmProviderName
        +modelName: String
    }

    class ServiceTier {
        <<enumeration>>
        STANDARD
        PRIORITY
        +apiValue: String?
    }

    class PendingIntentRepository {
        <<interface>>
        +pendingIntentIds: StateFlow~Set_String~
        +savePendingIntent(pendingIntentId: String, pendingIntent: PendingIntent)
        +consumePendingIntent(pendingIntentId: String) PendingIntent?
    }

    class InMemoryPendingIntentRepository {
        <<Singleton>>
        -actions: MutableMap~String_PendingIntent~
    }

    class LlmProviderFactoryImpl {
        <<Singleton>>
        -geminiProvider: GeminiProviderImpl
        +getProvider(name: LlmProviderName) LlmProvider
    }

    class GeminiProviderImpl {
        <<Singleton>>
        -httpClient: HttpClient
        -toolConverter: GeminiToolConverter
        +defaultModelName: String
        +getAvailableModels(apiKey: String?) List~LlmModel~
        +generateResponse(previousInteractionId: String?, input: LlmInput, tools: List~AppFunctionMetadata~, apiKey: String, modelName: String, serviceTier: ServiceTier) LlmResponse
    }

    class GeminiToolConverter {
        <<Singleton>>
        +convert(tool: AppFunctionMetadata) JsonObject
        +getToolName(tool: AppFunctionMetadata) String
    }

    class AgentInternalTools {
        <<Singleton>>
        -context: Context
        -settingsDataStore: DataStore~Preferences~
        -toolDiscoveryTools: ToolDiscoveryTools
        +geocodeAddress(address: String) LatLng?
        +getCurrentLocation() LatLng?
        +generateImage(prompt: String, aspectRatio: String?) GeneratedImageResult
        +getInternalToolsMetadata() List~Pair~
        +buildToolDiscoveryTools(apps: Map) List~AppFunctionMetadata~
        +executeInternalTool(function: AppFunctionMetadata, parameters: AppFunctionData, availableApps: Map) ExecuteAppFunctionResult
    }

    class ToolDiscoveryTools {
        <<Singleton>>
        -context: Context
        +buildTools(apps: Map) List~AppFunctionMetadata~
        +execute(function: AppFunctionMetadata, parameters: AppFunctionData, apps: Map) ExecuteAppFunctionResult
    }

    class FileDownloadHelper {
        <<interface>>
        +downloadToLocalUri(url: String) String
    }

    class FileDownloadHelperImpl {
        <<Singleton>>
        -context: Context
        +downloadToLocalUri(url: String) String
    }

    class ResultImageReader {
        <<interface>>
        +read(uri: Uri, mimeType: String?, maxBytes: Int) ToolOutputImage
    }

    class ResultImageReaderImpl {
        <<Singleton>>
        -context: Context
        +read(uri: Uri, mimeType: String?, maxBytes: Int) ToolOutputImage
    }

    ChatRepository <|.. RoomChatRepository
    RoomChatRepository --> ChatDao : delegates
    AppDatabase --> ChatDao : exposes
    ChatDao ..> ThreadEntity : manages
    ChatDao ..> MessageEntity : manages
    ThreadEntity *-- LlmModel
    LlmModel --> LlmProviderName
    MessageEntity "0..*" --> "1" ThreadEntity : FK threadId (CASCADE)
    MessageEntity *-- MessageAttachment
    MessageEntity --> MessageRole
    MessageEntity --> MessageProcessingStatus
    SettingsRepository <|.. DataStoreSettingsRepository
    SettingsRepository ..> ServiceTier
    PendingIntentRepository <|.. InMemoryPendingIntentRepository
    LlmProviderFactoryImpl --> GeminiProviderImpl
    GeminiProviderImpl --> GeminiToolConverter : converts schemas
    AgentInternalTools --> ToolDiscoveryTools : delegates L1/L2 discovery
    FileDownloadHelper <|.. FileDownloadHelperImpl
    ResultImageReader <|.. ResultImageReaderImpl
    DataModule ..> RoomChatRepository : binds
    DataModule ..> DataStoreSettingsRepository : binds
    DataModule ..> InMemoryPendingIntentRepository : binds
    DataModule ..> LlmProviderFactoryImpl : binds
```

---

## 2. UML Sequence Diagrams

### 2.1 Elevated Shell Launch & ViewModel Initialization

Illustrates how `./startAppFunctionTestingAgent` launches `ShellIdentityInstrumentation` to adopt `android.permission.EXECUTE_APP_FUNCTIONS`, starts `MainActivity`, and initializes `AgentDemoViewModel` streams.

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / Script
    participant Instr as ShellIdentityInstrumentation
    participant Act as MainActivity
    participant Screen as AgentDemoScreen
    participant VM as AgentDemoViewModel
    participant Settings as SettingsRepository
    participant Gemini as GeminiProviderImpl
    participant ThreadsUC as ManageThreadsUseCase
    participant ApfUC as GetAppFunctionsUseCase
    participant HistoryUC as GetChatHistoryUseCase
    participant Orch as AgentOrchestrator

    Dev->>Instr: adb shell am instrument -w com.peyo.agent/.ShellIdentityInstrumentation
    activate Instr
    Instr->>Instr: uiAutomation.adoptShellPermissionIdentity("EXECUTE_APP_FUNCTIONS")
    Instr->>Act: startActivity(Intent(MainActivity, FLAG_ACTIVITY_NEW_TASK))
    Note over Instr: CountDownLatch(1).await() keeps shell identity alive

    activate Act
    Act->>Screen: setContent { ApfAgentTheme { AgentDemoScreen() } }
    Screen->>VM: hiltViewModel() & collectAsStateWithLifecycle(uiState)
    activate VM

    par Load Available Models & Ensure Default Thread
        VM->>Settings: collectLatest(geminiApiKey)
        Settings-->>VM: apiKey
        VM->>Gemini: getAvailableModels(apiKey)
        Gemini-->>VM: List<LlmModel>
        VM->>ThreadsUC: getThreads().first()
        alt No existing threads
            VM->>ThreadsUC: createThread(defaultModel)
            ThreadsUC-->>VM: newThreadId
            VM->>VM: savedStateHandle["threadId"] = newThreadId
        end
    and Discover Installed Apps Exposing AppFunctions
        VM->>ApfUC: invoke(includeInternalTools = false)
        ApfUC-->>VM: Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>
        VM->>VM: getInstalledAppsUseCase() filtered by packagesWithTools
    and Combine Thread State & Start Observation Job
        VM->>ThreadsUC: getThreads()
        ThreadsUC-->>VM: List<ThreadEntity>
        VM-->>Screen: AgentUiState.Loaded(currentThread, availableModels, installedApps, ...)
        par Observe Chat History
            VM->>HistoryUC: invoke(currentThread.threadId)
            HistoryUC-->>VM: Flow<List<MessageEntity>>
            VM-->>Screen: uiState.copy(messages = messages)
        and Observe Pending Messages
            VM->>Orch: observeAndProcessMessages(currentThread.threadId)
        end
    end
    deactivate VM
    deactivate Act
    deactivate Instr
```

---

### 2.2 User Message Submission & `@App` Mention Scoping

Illustrates how typing a prompt (optionally scoped to a specific app via `@AppLabel` autocomplete) persists a `PENDING_AGENT_RESPONSE` message in Room and reactively triggers both the UI chat list and `AgentOrchestrator`.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Screen as AgentDemoScreen
    participant VM as AgentDemoViewModel
    participant SendUC as SendMessageUseCase
    participant Repo as RoomChatRepository
    participant Dao as ChatDao (Room)
    participant ObsUC as ObservePendingMessagesUseCase
    participant Orch as AgentOrchestrator

    opt User types '@' to scope to an installed app
        User->>Screen: Types "@Notes "
        Screen->>Screen: Show autocomplete Popup from installedApps
        User->>Screen: Selects app item
        Screen->>Screen: Set selectedAppPackageName = app.packageName
    end

    User->>Screen: Presses Enter or taps Send
    Screen->>VM: onEvent(OnSendMessage(text, selectedAppPackageName))
    VM->>SendUC: invoke(threadId, USER, text, PENDING_AGENT_RESPONSE, targetPackageName)
    SendUC->>Repo: sendMessage(MessageEntity)
    Repo->>Dao: insertMessage(message)

    par Update UI Message List
        Dao-->>Screen: getMessagesForThread() emits updated List<MessageEntity>
    and Wake AgentOrchestrator
        Dao-->>ObsUC: getMessagesForThread() emits List<MessageEntity>
        ObsUC-->>Orch: emits latest message with PENDING_AGENT_RESPONSE
        Orch->>Orch: processMessage(message, thread)
    end
```

---

### 2.3 Multi-Turn Progressive Tool Disclosure & AppFunction Execution

Illustrates the full agent loop in `AgentOrchestrator`:
- **L0 (`CheckingCapabilities`)**: Declares internal tools plus `expand_tool_description` and `expand_function_declarations` (skipped when `targetPackageName != null`).
- **L1 (`ExploringApps`)**: Model inspects an app's instructions and function summaries.
- **L2 (`PlanningWithFunctions`)**: Model loads full function schemas dynamically into `activeTools` for the remainder of the turn.
- **Tool Execution (`InvokingTool`)**: Resolves remote `http(s)://` file URLs, builds `AppFunctionData`, grants `content://` read permissions, invokes `AppFunctionManager`, reads any returned images, and sends final assistant Markdown + attachments.

```mermaid
sequenceDiagram
    autonumber
    participant Orch as AgentOrchestrator
    participant ApfUC as GetAppFunctionsUseCase
    participant Gemini as GeminiProviderImpl
    participant ExecUC as ExecuteAppFunctionUseCase
    participant Disc as ToolDiscoveryTools
    participant ResolveUC as ResolveRemoteFileToLocalUriUseCase
    participant ConvInUC as ConvertInputToAppFunctionDataUseCase
    participant ApfMgr as AppFunctionManager
    participant ImgUC as LoadResultImagesUseCase
    participant ChatUC as Chat UseCases (Room)

    Orch->>Orch: status = Thinking
    Orch->>ApfUC: invoke(isEnabled=true, isConnected=true, includeInternalTools=true, progressiveToolDisclosure)
    ApfUC->>Disc: buildTools(externalApps)
    Disc-->>ApfUC: [expand_tool_description, expand_function_declarations]
    ApfUC-->>Orch: initial activeTools

    opt progressiveToolDisclosure == true
        Orch->>Orch: status = CheckingCapabilities
    end

    rect rgb(235, 245, 255)
        Note over Orch,Disc: Turn 1 (L1): Explore App Capabilities
        Orch->>Gemini: generateResponse(prevId, UserMessage(text), activeTools)
        Gemini-->>Orch: Success(id1, [ToolCall(expand_tool_description, package_name)])
        Orch->>ChatUC: updateThread(id1) & updateMessage(PROCESSED)
        Orch->>ExecUC: invoke(expand_tool_description, params, threadId)
        ExecUC->>Disc: execute(expand_tool_description, params, availableApps)
        Disc-->>ExecUC: Data(app instructions + function summaries JSON)
        ExecUC-->>Orch: Data(formattedJson)
        Orch->>Orch: status = ExploringApps([packageName])
    end

    rect rgb(235, 255, 240)
        Note over Orch,Disc: Turn 2 (L2): Load Selected Function Declarations
        Orch->>Gemini: generateResponse(id1, ToolResponse(summaries), activeTools)
        Gemini-->>Orch: Success(id2, [ToolCall(expand_function_declarations, function_ids)])
        Orch->>ExecUC: invoke(expand_function_declarations, params, threadId)
        ExecUC->>Disc: execute(expand_function_declarations, params, availableApps)
        Disc-->>ExecUC: ToolsDiscovered(json, loadedMetadataList)
        ExecUC-->>Orch: ToolsDiscovered(json, loadedMetadataList)
        Orch->>Orch: activeTools.addAll(loadedMetadataList)
        Orch->>Orch: status = PlanningWithFunctions(packages, functionNames)
    end

    rect rgb(255, 248, 235)
        Note over Orch,ImgUC: Turn 3: Execute Target AppFunction
        Orch->>Gemini: generateResponse(id2, ToolResponse(loadedAck), activeTools)
        Gemini-->>Orch: Success(id3, [ToolCall(targetPkg, targetFnId, args)])
        Orch->>Orch: status = InvokingTool(targetFnId, targetPkg)
        Orch->>ResolveUC: invoke(parameters, components, args)
        ResolveUC-->>Orch: localUriInputs (downloads http/https if URI param)
        Orch->>ConvInUC: invoke(parameters, components, localUriInputs)
        ConvInUC-->>Orch: Result.success(AppFunctionData)
        Orch->>ExecUC: invoke(matchingTool, appFunctionData, threadId)
        ExecUC->>ExecUC: grantAppFunctionUriPermissionsUseCase(function, data)
        ExecUC->>ApfMgr: executeAppFunction(ExecuteAppFunctionRequest)
        ApfMgr-->>ExecUC: ExecuteAppFunctionResponse.Success(returnValue)
        ExecUC->>ExecUC: convertAppFunctionDataToJsonUseCase(returnValue)
        ExecUC-->>Orch: ExecuteAppFunctionResult.Data(data, formattedJson)
        Orch->>ImgUC: invoke(matchingTool, executionResult)
        ImgUC-->>Orch: ResultImages(images, skipped)
    end

    rect rgb(245, 240, 255)
        Note over Orch,ChatUC: Turn 4: Final Natural Language Response
        Orch->>Gemini: generateResponse(id3, ToolResponse(json + images), activeTools)
        Gemini-->>Orch: Success(id4, [Text(finalReply)])
        Orch->>ChatUC: updateThread(id4)
        Orch->>ChatUC: sendMessage(ASSISTANT, finalReply, PROCESSED, capturedAttachments)
        Orch->>Orch: status = Idle
    end
```

---

### 2.4 Internal Image Generation & Multimodal Result Attachment

Illustrates how `AgentInternalTools.generateImage` calls the Gemini image generation endpoint, caches the image via `FileProvider`, exposes the `content://` URI to both Gemini 3+ (`ToolOutputImage`) and the Compose chat UI (`MessageAttachment`), and allows chaining into downstream AppFunctions (with automatic URI read permission grant).

```mermaid
sequenceDiagram
    autonumber
    participant Orch as AgentOrchestrator
    participant ExecUC as ExecuteAppFunctionUseCase
    participant IntTools as AgentInternalTools
    participant GeminiImg as Gemini Image API
    participant FP as FileProvider (Cache)
    participant ImgUC as LoadResultImagesUseCase
    participant Reader as ResultImageReaderImpl
    participant ChatUC as SendMessageUseCase

    Orch->>ExecUC: invoke(generateImage, {prompt, aspectRatio}, threadId)
    ExecUC->>IntTools: executeInternalTool(generateImage, parameters)
    IntTools->>GeminiImg: POST /v1beta/models/gemini-3.1-flash-image-preview:generateContent
    GeminiImg-->>IntTools: inlineData (base64 + mimeType)
    IntTools->>FP: write cached file & getUriForFile(...)
    FP-->>IntTools: content://com.peyo.agent.fileprovider/...
    IntTools-->>ExecUC: Data(data, {"imageUri": "content://...", "mimeType": "image/png", "prompt": "..."})
    ExecUC-->>Orch: ExecuteAppFunctionResult.Data

    Orch->>ImgUC: invoke(generateImage, result)
    ImgUC->>Reader: read(contentUri, mimeType, maxBytes = 4 MiB)
    Reader-->>ImgUC: ToolOutputImage(mimeType, bytes)
    ImgUC-->>Orch: ResultImages([ToolOutputImage], skipped=[])

    Orch->>Orch: result.toAttachment() -> MessageAttachment(uri, mimeType)
    Note over Orch: Sends ToolOutput (JSON + base64 image for Gemini 3+) to LLM.<br/>If LLM chains another AppFunction with imageUri,<br/>GrantAppFunctionUriPermissionsUseCase grants FLAG_GRANT_READ_URI_PERMISSION.
    Orch->>ChatUC: sendMessage(ASSISTANT, replyText, PROCESSED, attachments=[MessageAttachment])
```

---

### 2.5 Dynamic `PendingIntent` Confirmation Flow

Illustrates how an AppFunction returning a `PendingIntent` pauses automatic execution, caches the `PendingIntent` transiently in memory, renders an interactive confirmation button in `MessageBubble`, and launches the `PendingIntent` once confirmed by the user.

```mermaid
sequenceDiagram
    autonumber
    participant ApfMgr as AppFunctionManager
    participant ExecUC as ExecuteAppFunctionUseCase
    participant Orch as AgentOrchestrator
    participant SavePI as SavePendingIntentUseCase
    participant PIRepo as InMemoryPendingIntentRepository
    participant ChatUC as SendMessageUseCase
    participant Screen as AgentDemoScreen (MessageBubble)
    participant VM as AgentDemoViewModel
    participant ConsumePI as ConsumePendingIntentUseCase
    participant LaunchPI as LaunchPendingIntentUseCase
    actor User

    ApfMgr-->>ExecUC: ExecuteAppFunctionResponse.Success(PendingIntent parcelable)
    ExecUC-->>Orch: ExecuteAppFunctionResult.PendingIntentAction(pendingIntent)
    Orch->>SavePI: invoke(pendingIntentId = UUID, pendingIntent)
    SavePI->>PIRepo: savePendingIntent(pendingIntentId, pendingIntent)
    PIRepo-->>VM: pendingIntentIds StateFlow emits activePendingActionIds
    Orch->>ChatUC: sendMessage(ASSISTANT, textContent, PROCESSED, pendingIntentId)
    Orch->>Orch: HandleResult.Stop (status = Idle)

    ChatUC-->>Screen: MessageEntity with pendingIntentId rendered
    Note over Screen: Button enabled while pendingIntentId in activePendingActionIds

    User->>Screen: Taps "Confirm Action" button
    Screen->>VM: onEvent(OnConfirmAction(pendingIntentId))
    VM->>ConsumePI: invoke(pendingIntentId)
    ConsumePI->>PIRepo: consumePendingIntent(pendingIntentId)
    PIRepo-->>ConsumePI: PendingIntent (removed from active set)
    PIRepo-->>Screen: activePendingActionIds updated (button shows expired/disabled)
    VM->>LaunchPI: invoke(pendingIntent)
    LaunchPI->>LaunchPI: ActivityOptions(MODE_BACKGROUND_ACTIVITY_START_ALLOWED) & pendingIntent.send(...)
```
