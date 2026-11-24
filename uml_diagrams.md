# AppFuncEx (`com.peyo.apfex`) — UML Class & Sequence Diagrams

> [!NOTE]
> **Architecture Overview**: `AppFuncEx` follows Android Clean Architecture + MVVM with Hilt dependency injection:
> - **UI Layer** (`ui.screens.debugging`): Jetpack Compose screens and [DebuggingViewModel.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/DebuggingViewModel.kt) managing unidirectional `StateFlow<DebuggingUiState>`.
> - **Domain Layer** (`domain.*`): Focused use cases for querying/executing Android `AppFunctions`, managing API 37.2+ `AppInteractionSession`s & user consent, launching `PendingIntent`s, and troubleshooting target apps.
> - **Data Layer** (`data.*`): Jetpack DataStore settings, in-memory `AppInteractionSession` caching, internal agent tools (location, geocoding, Gemini image generation, progressive tool discovery), and Room entities.

---

## 1. UML Class Diagrams

### 1.1 `DebuggingViewModel` & Direct Collaborators (UI Entry & Injected Dependencies)

Focused view of [DebuggingViewModel.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/DebuggingViewModel.kt), the UI Composables in [DebuggingScreen.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/DebuggingScreen.kt) / [FunctionsFoundContent.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/FunctionsFoundContent.kt) / [TroubleshootBottomSheet.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/TroubleshootBottomSheet.kt) that observe and invoke it, and the exact Use Cases and Repository it directly accesses.

```mermaid
classDiagram
    direction TB

    class MainActivity {
        <<AndroidEntryPoint>>
        +onCreate(Bundle savedInstanceState) void
    }

    class DebuggingScreen {
        <<Composable>>
        +DebuggingScreen(DebuggingViewModel viewModel) void
        +DebuggingScreenContent(DebuggingUiState uiState) void
        +AppDropdown(AppsGroupState appGroups) void
    }

    class FunctionsFoundContent {
        <<Composable>>
        +FunctionsFoundContent(FunctionsFoundState state) void
        +AppFunctionItem(AppFunctionMetadata function) void
        +AppFunctionDataTypeInput(AppFunctionDataTypeMetadata dataType) void
    }

    class TroubleshootResult {
        <<Composable>>
        +TroubleshootResult(TroubleshootUiState state) void
    }

    class DebuggingViewModel {
        <<HiltViewModel>>
        -MutableStateFlow~DebuggingUiState~ _uiState
        +StateFlow~DebuggingUiState~ uiState
        -List~AppInfo~ allInstalledApps
        -Set~String~ pinnedPackages
        -Map allAppFunctions
        -Map allAppFunctionStates
        +onAppSelected(AppInfo appInfo) void
        +onClearSelectedApp() void
        +onSearchQueryChanged(String query) void
        +onFunctionInputsChange(String functionId, Map inputs) void
        +onFunctionExpandedChange(String functionId, Boolean expanded) void
        +invokeFunction(AppFunctionMetadata function) void
        +clearResult() void
        +launchPendingIntent(PendingIntent pendingIntent) void
        +onTogglePin(AppInfo appInfo) void
        -observePinnedApps() void
        -loadAppFunctions() void
        -loadInstalledApps() void
        -runTroubleshooting(String packageName) void
    }

    class DebuggingUiState {
        +AppsGroupState filteredApps
        +AppInfo selectedApp
        +String searchQuery
        +Boolean isLoading
        +SearchAppResultState searchAppResultState
    }

    class GetInstalledAppsUseCase {
        +invoke() List~AppInfo~
    }

    class GetAppFunctionsUseCase {
        +invoke(GetAppFunctionsParams params) Flow~AppFunctionMap~
    }

    class GetAppFunctionStatesUseCase {
        +invoke(List~AppFunctionName~ functionNames) List~AppFunctionState~
    }

    class ConvertInputToAppFunctionDataUseCase {
        +invoke(List parameters, AppFunctionComponentsMetadata components, Map inputs) Result~AppFunctionData~
    }

    class ExecuteAppFunctionUseCase {
        +invoke(AppFunctionMetadata function, AppFunctionData parameters, String threadId) ExecuteAppFunctionResult
    }

    class TroubleshootAppUseCase {
        +invoke(String packageName) TroubleshootReport
    }

    class LaunchPendingIntentUseCase {
        +invoke(PendingIntent pendingIntent) Result~Unit~
    }

    class SettingsRepository {
        <<interface>>
        +Flow~Set~ pinnedApps
        +setAppPinned(String packageName, Boolean pinned) void
    }

    %% UI to ViewModel
    MainActivity --> DebuggingScreen : renders
    DebuggingScreen --> FunctionsFoundContent : renders when FunctionsFoundState
    DebuggingScreen --> TroubleshootResult : renders when TroubleshootUiState
    DebuggingScreen --> DebuggingViewModel : observes uiState & dispatches events

    %% ViewModel State & Direct Accesses
    DebuggingViewModel *-- DebuggingUiState : exposes via StateFlow
    DebuggingViewModel --> GetInstalledAppsUseCase : loads installed apps
    DebuggingViewModel --> GetAppFunctionsUseCase : observes functions
    DebuggingViewModel --> GetAppFunctionStatesUseCase : queries enabled states
    DebuggingViewModel --> ConvertInputToAppFunctionDataUseCase : converts UI inputs
    DebuggingViewModel --> ExecuteAppFunctionUseCase : invokes AppFunction
    DebuggingViewModel --> TroubleshootAppUseCase : diagnoses unsupported app
    DebuggingViewModel --> LaunchPendingIntentUseCase : opens PendingIntent result
    DebuggingViewModel --> SettingsRepository : observes & updates pinnedApps
```

---

### 1.2 Domain, Data & DI Layer Class Diagram

Shows how Domain Use Cases collaborate with AndroidX `AppFunctionManager`, API 37.2+ `AppInteractionSession` management, internal agent tools, repositories, and Hilt DI modules ([ExecuteAppFunctionUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appfunction/ExecuteAppFunctionUseCase.kt), [GetAppFunctionsUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appfunction/GetAppFunctionsUseCase.kt), [StartAppInteractionSessionUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appinteraction/StartAppInteractionSessionUseCase.kt), [AgentInternalTools.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/data/AgentInternalTools.kt), [AppFunctionModule.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/di/AppFunctionModule.kt), [DataModule.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/di/DataModule.kt)).

```mermaid
classDiagram
    direction TB

    class ExecuteAppFunctionUseCase {
        +invoke(AppFunctionMetadata function, AppFunctionData parameters, String threadId) ExecuteAppFunctionResult
    }

    class GetAppFunctionsUseCase {
        +invoke(GetAppFunctionsParams params) Flow~AppFunctionMap~
    }

    class GetAppFunctionStatesUseCase {
        +invoke(List~AppFunctionName~ functionNames) List~AppFunctionState~
    }

    class TroubleshootAppUseCase {
        +invoke(String packageName) TroubleshootReport
    }

    class GrantAppFunctionUriPermissionsUseCase {
        +invoke(AppFunctionMetadata function, AppFunctionData parameters) void
    }

    class ConvertAppFunctionDataToJsonUseCase {
        +invoke(String name, AppFunctionData data, AppFunctionDataTypeMetadata dataType, AppFunctionComponentsMetadata components) String
    }

    class StartAppInteractionSessionUseCase {
        +invoke(String threadId, List~String~ targetPackages) AppInteractionSession
    }

    class HandleUserConsentUseCase {
        +invoke(IntentSender intentSender) void
    }

    class AppFunctionExceptionFormatter {
        <<object>>
        +format(AppFunctionException exception, String functionId) String
        +getAppFunctionException(Throwable throwable) AppFunctionException
    }

    class SettingsRepository {
        <<interface>>
        +Flow~String~ geminiApiKey
        +Flow~Boolean~ progressiveToolDisclosure
        +Flow~Set~ pinnedApps
        +Flow~Set~ disconnectedApps
        +setGeminiApiKey(String apiKey) void
        +setProgressiveToolDisclosure(Boolean enabled) void
        +setAppPinned(String packageName, Boolean pinned) void
        +setAppConnected(String packageName, Boolean connected) void
    }

    class DataStoreSettingsRepository {
        -DataStore~Preferences~ dataStore
    }

    class AppInteractionSessionRepository {
        <<interface>>
        +saveSession(String threadId, AppInteractionSession session) void
        +getSession(String threadId) AppInteractionSession
        +invalidateSession(String threadId) void
    }

    class InMemoryAppInteractionSessionRepository {
        -ConcurrentHashMap sessionMap
        -LifecycleListener lifecycleListener
    }

    class AgentInternalTools {
        +geocodeAddress(String address) LatLng
        +getCurrentLocation() LatLng
        +generateImage(String prompt, String aspectRatio) GeneratedImageResult
        +getInternalToolsMetadata() List~Pair~
        +buildToolDiscoveryTools(Map apps) List~AppFunctionMetadata~
        +executeInternalTool(AppFunctionMetadata function, AppFunctionData parameters, Map availableApps) ExecuteAppFunctionResult
    }

    class ToolDiscoveryTools {
        +buildTools(Map apps) List~AppFunctionMetadata~
        +execute(AppFunctionMetadata function, AppFunctionData parameters, Map apps) ExecuteAppFunctionResult
    }

    class AppFunctionModule {
        <<HiltModule>>
        +provideAppFunctionManager(Context context) AppFunctionManager
    }

    class DataModule {
        <<HiltModule>>
        +bindSettingsRepository(DataStoreSettingsRepository impl) SettingsRepository
        +bindAppInteractionSessionRepository(InMemoryAppInteractionSessionRepository impl) AppInteractionSessionRepository
        +provideDataStore(Context context) DataStore~Preferences~
    }

    class AppFunctionManager {
        <<AndroidX>>
    }

    %% Domain Use Case Collaborations
    TroubleshootAppUseCase --> GetAppFunctionsUseCase

    GetAppFunctionsUseCase --> AppFunctionManager
    GetAppFunctionsUseCase --> GetAppFunctionStatesUseCase
    GetAppFunctionsUseCase --> SettingsRepository
    GetAppFunctionsUseCase --> AgentInternalTools

    GetAppFunctionStatesUseCase --> AppFunctionManager

    ExecuteAppFunctionUseCase --> AppFunctionManager
    ExecuteAppFunctionUseCase --> ConvertAppFunctionDataToJsonUseCase
    ExecuteAppFunctionUseCase --> AgentInternalTools
    ExecuteAppFunctionUseCase --> GrantAppFunctionUriPermissionsUseCase
    ExecuteAppFunctionUseCase --> GetAppFunctionsUseCase
    ExecuteAppFunctionUseCase --> StartAppInteractionSessionUseCase : Lazy

    StartAppInteractionSessionUseCase --> AppInteractionSessionRepository
    StartAppInteractionSessionUseCase --> HandleUserConsentUseCase

    %% Data Implementations & DI
    SettingsRepository <|.. DataStoreSettingsRepository
    AppInteractionSessionRepository <|.. InMemoryAppInteractionSessionRepository
    AgentInternalTools --> ToolDiscoveryTools
    AppFunctionModule ..> AppFunctionManager : provides
    DataModule ..> DataStoreSettingsRepository : binds
    DataModule ..> InMemoryAppInteractionSessionRepository : binds
```

---

### 1.3 UI State & Domain Result Models

Defined in [DebuggingUiState.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/DebuggingUiState.kt), [ExecuteAppFunctionUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appfunction/ExecuteAppFunctionUseCase.kt#L175-L221), [AppInfo.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appfunction/AppInfo.kt), and [TroubleshootReport.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/troubleshoot/TroubleshootReport.kt).

```mermaid
classDiagram
    direction TB

    class DebuggingUiState {
        +AppsGroupState filteredApps
        +AppInfo selectedApp
        +String searchQuery
        +Boolean isLoading
        +SearchAppResultState searchAppResultState
    }

    class AppsGroupState {
        +List~AppSection~ sections
    }

    class AppSection {
        +Int titleRes
        +List~AppInfo~ apps
        +Boolean showPin
    }

    class AppInfo {
        +String packageName
        +String label
        +Drawable icon
    }

    class SearchAppResultState {
        <<sealed>>
    }

    class Idle {
        <<object>>
    }

    class FunctionsFoundState {
        +List~AppFunctionMetadata~ functions
        +Map~AppFunctionName_Boolean~ enabledState
        +Map~String_Map~ functionInputs
        +ExecuteAppFunctionResult executionResult
        +Set~String~ expandedFunctions
        +AppFunctionMetadata executedFunction
        +Map~String_Any~ executedInputs
    }

    class TroubleshootUiState {
        +TroubleshootReport report
        +Boolean isLoading
    }

    class ExecuteAppFunctionResult {
        <<sealed>>
    }

    class Data {
        +AppFunctionData data
        +String formattedJson
        +toAttachment() MessageAttachment
    }

    class PendingIntentAction {
        +PendingIntent pendingIntent
    }

    class ToolsDiscovered {
        +String formattedJson
        +List~AppFunctionMetadata~ tools
    }

    class Error {
        +Exception exception
    }

    class TroubleshootReport {
        +String packageName
        +List~TroubleshootStep~ steps
    }

    class TroubleshootStep {
        +Int titleResId
        +StepStatus status
        +Int messageResId
        +List~String~ messageArgs
    }

    class StepStatus {
        <<enumeration>>
        PASS
        FAIL
        WARNING
    }

    DebuggingUiState *-- AppsGroupState
    DebuggingUiState o-- AppInfo
    DebuggingUiState *-- SearchAppResultState
    AppsGroupState *-- AppSection
    AppSection o-- AppInfo

    SearchAppResultState <|-- Idle
    SearchAppResultState <|-- FunctionsFoundState
    SearchAppResultState <|-- TroubleshootUiState

    FunctionsFoundState o-- ExecuteAppFunctionResult
    TroubleshootUiState o-- TroubleshootReport

    ExecuteAppFunctionResult <|-- Data
    ExecuteAppFunctionResult <|-- PendingIntentAction
    ExecuteAppFunctionResult <|-- ToolsDiscovered
    ExecuteAppFunctionResult <|-- Error

    TroubleshootReport *-- TroubleshootStep
    TroubleshootStep --> StepStatus
```

---

### 1.4 Room Database Entities

Defined in [ThreadEntity.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/data.db.entities/ThreadEntity.kt) and [MessageEntity.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/data.db.entities/MessageEntity.kt).

```mermaid
classDiagram
    direction LR

    class ThreadEntity {
        <<RoomEntity>>
        +String threadId
        +Long createdAt
        +String latestInteractionId
    }

    class MessageEntity {
        <<RoomEntity>>
        +String messageId
        +String threadId
        +MessageRole role
        +String textContent
        +Long timestamp
        +MessageProcessingStatus processingStatus
        +String pendingIntentId
        +String targetPackageName
        +List~MessageAttachment~ attachments
    }

    class MessageAttachment {
        +String uri
        +String mimeType
    }

    class MessageAttachmentConverter {
        +fromAttachments(List~MessageAttachment~ attachments) String
        +toAttachments(String jsonString) List~MessageAttachment~
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

    ThreadEntity "1" <-- "*" MessageEntity : foreignKey threadId
    MessageEntity *-- MessageAttachment
    MessageEntity --> MessageRole
    MessageEntity --> MessageProcessingStatus
    MessageAttachmentConverter ..> MessageAttachment : serializes
```

---

## 2. UML Sequence Diagrams

### 2.1 Initialization & Reactive App Discovery

Triggered when `MainActivity` mounts `DebuggingScreen` and instantiates [DebuggingViewModel.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/DebuggingViewModel.kt#L79-L121). Three concurrent coroutines load installed packages, observe `AppFunctionManager` metadata + states, and observe pinned apps from DataStore to build grouped dropdown sections (`Pinned`, `Supported`, `Unsupported`).

```mermaid
sequenceDiagram
    actor User
    participant Activity as MainActivity
    participant Screen as DebuggingScreen
    participant VM as DebuggingViewModel
    participant InstalledUC as GetInstalledAppsUseCase
    participant FuncsUC as GetAppFunctionsUseCase
    participant StatesUC as GetAppFunctionStatesUseCase
    participant Settings as SettingsRepository
    participant AFM as AppFunctionManager
    participant PM as PackageManager

    User->>Activity: Launch App
    Activity->>Screen: setContent { DebuggingScreen() }
    Screen->>VM: hiltViewModel() & collect uiState

    par Load Installed Apps
        VM->>InstalledUC: invoke()
        InstalledUC->>PM: getInstalledApplications(0)
        PM-->>InstalledUC: ApplicationInfo list
        InstalledUC-->>VM: List<AppInfo> (sorted by label)
        VM->>VM: updateAppsGroupState()
    and Observe Available AppFunctions
        VM->>FuncsUC: invoke(includeInternalTools = false)
        FuncsUC->>AFM: observeAppFunctions() + searchAppFunctions(spec)
        AFM-->>FuncsUC: List<AppFunctionMetadata>
        FuncsUC-->>VM: Flow<Map<AppFunctionPackageMetadata, List<AppFunctionMetadata>>>
        VM->>StatesUC: invoke(allFunctionNames)
        StatesUC->>AFM: getAppFunctionStates(allFunctionNames)
        AFM-->>StatesUC: List<AppFunctionState>
        StatesUC-->>VM: List<AppFunctionState>
        VM->>VM: updateAppsGroupState()
    and Observe Pinned Apps
        VM->>Settings: pinnedApps.collect
        Settings-->>VM: Set<String> pinnedPackages
        VM->>VM: updateAppsGroupState()
    end

    VM-->>Screen: Emit DebuggingUiState (Pinned / Supported / Unsupported sections)
    Screen-->>User: Display searchable AppDropdown
```

---

### 2.2 App Selection & Automated Troubleshooting Flow

When a user selects an app in [DebuggingScreen.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/DebuggingScreen.kt#L333-L337), [DebuggingViewModel.onAppSelected](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/ui/screens/debugging/DebuggingViewModel.kt#L124-L152) either populates the discovered functions list or runs the 5-step diagnostic pipeline in [TroubleshootAppUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/troubleshoot/TroubleshootAppUseCase.kt).

```mermaid
sequenceDiagram
    actor User
    participant Screen as DebuggingScreen
    participant VM as DebuggingViewModel
    participant TroubleshootUC as TroubleshootAppUseCase
    participant FuncsUC as GetAppFunctionsUseCase
    participant PM as PackageManager
    participant PkgCtx as TargetPackageContext

    User->>Screen: Select app from AppDropdown
    Screen->>VM: onAppSelected(appInfo)

    alt App has discovered AppFunctions (Supported)
        VM->>VM: Map function.name -> enabledState.isEnabled
        VM-->>Screen: Update uiState with FunctionsFoundState
        Screen-->>User: Render FunctionsFoundContent (AppFunctionItem list)
    else App has no discovered AppFunctions (Unsupported)
        VM-->>Screen: Update uiState with TroubleshootUiState(isLoading = true)
        VM->>TroubleshootUC: invoke(appInfo.packageName)

        TroubleshootUC->>FuncsUC: Step 1 - invoke(targetPackageNames = {pkg}).first()
        FuncsUC-->>TroubleshootUC: Map of visible functions

        alt Already visible on re-check
            TroubleshootUC-->>VM: TroubleshootReport (Step 1 PASS)
        else Not listed in AppFunctionManager
            TroubleshootUC->>PM: Step 2 - queryIntentServices("android.app.appfunctions.AppFunctionService")
            PM-->>TroubleshootUC: ResolveInfo list

            alt Service missing
                TroubleshootUC-->>VM: TroubleshootReport (Step 2 FAIL)
            else Service declared
                TroubleshootUC->>PM: Step 3 - getProperty("android.app.appfunctions.v2", componentName)
                PM-->>TroubleshootUC: Property XML asset path (or NameNotFoundException)

                alt Property missing
                    TroubleshootUC-->>VM: TroubleshootReport (Step 3 FAIL)
                else Property present
                    TroubleshootUC->>PkgCtx: Step 4 - createPackageContext(pkg) & assets.open(propertyValue)
                    PkgCtx-->>TroubleshootUC: Asset stream or Exception

                    alt Asset missing
                        TroubleshootUC-->>VM: TroubleshootReport (Step 4 FAIL)
                    else Asset valid
                        TroubleshootUC-->>VM: TroubleshootReport (Steps 2-4 PASS, Step 5 WARNING fallback)
                    end
                end
            end
        end

        VM-->>Screen: Update uiState with TroubleshootUiState(isLoading = false, report)
        Screen-->>User: Render TroubleshootResult steps
    end
```

---

### 2.3 AppFunction Execution, Session Consent & PendingIntent Launch

Covers parameter input conversion ([ConvertInputToAppFunctionDataUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appfunction/ConvertInputToAppFunctionDataUseCase.kt)), URI permission grants ([GrantAppFunctionUriPermissionsUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appfunction/GrantAppFunctionUriPermissionsUseCase.kt)), API 37.2+ `AppInteractionSession` acquisition and consent UI ([StartAppInteractionSessionUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appinteraction/StartAppInteractionSessionUseCase.kt)), execution ([ExecuteAppFunctionUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/appfunction/ExecuteAppFunctionUseCase.kt)), and optional `PendingIntent` follow-up ([LaunchPendingIntentUseCase.kt](file:///home/hdyoon/ex/rtlmex/src/com/peyo/apfex/domain/pendingintent/LaunchPendingIntentUseCase.kt)).

```mermaid
sequenceDiagram
    actor User
    participant UI as FunctionsFoundContent
    participant VM as DebuggingViewModel
    participant InputUC as ConvertInputToAppFunctionDataUseCase
    participant ExecUC as ExecuteAppFunctionUseCase
    participant Internal as AgentInternalTools
    participant UriUC as GrantAppFunctionUriPermissionsUseCase
    participant SessionUC as StartAppInteractionSessionUseCase
    participant SessionRepo as AppInteractionSessionRepository
    participant AIM as AppInteractionManager
    participant ConsentUC as HandleUserConsentUseCase
    participant AFM as AppFunctionManager
    participant JsonUC as ConvertAppFunctionDataToJsonUseCase
    participant LaunchUC as LaunchPendingIntentUseCase

    User->>UI: Fill parameters & tap Invoke
    UI->>VM: invokeFunction(function)
    VM->>InputUC: invoke(parameters, components, inputs)
    InputUC-->>VM: Result.success(AppFunctionData)

    VM->>ExecUC: invoke(function, appFunctionData, "debugging-thread")

    alt Internal Agent Tool (com.example.appfunctions.agent.internal)
        ExecUC->>Internal: executeInternalTool(function, parameters, availableApps)
        Internal-->>ExecUC: ExecuteAppFunctionResult (Data / ToolsDiscovered / Error)
    else External AppFunction
        ExecUC->>UriUC: invoke(function, appFunctionData)
        Note over UriUC: Recursively grants FLAG_GRANT_READ_URI_PERMISSION for content:// URIs
        UriUC-->>ExecUC: Unit

        ExecUC->>ExecUC: Build ExecuteAppFunctionRequest (with AppInteractionAttribution on API 37+)

        alt API >= 37.2 (CINNAMON_BUN_2)
            ExecUC->>SessionUC: invoke(threadId, [targetPackageName])
            SessionUC->>SessionRepo: getSession(threadId)
            alt Cached session exists
                SessionRepo-->>SessionUC: existingSession
                SessionUC->>AIM: existingSession.update(UpdateParams)
            else No cached session
                SessionRepo-->>SessionUC: null
                SessionUC->>AIM: createSession(CreateParams)
                opt User consent required by OS
                    AIM->>ConsentUC: onStartIntentSenderCallback(intentSender)
                    ConsentUC->>User: context.startIntentSender() Consent Prompt
                    User-->>AIM: Approve / Deny interaction
                end
                AIM-->>SessionUC: active AppInteractionSession
                SessionUC->>SessionRepo: saveSession(threadId, session)
            end
            SessionUC-->>ExecUC: AppInteractionSession
            ExecUC->>AFM: executeAppFunction(session, request)
        else API < 37.2
            ExecUC->>AFM: executeAppFunction(request)
        end

        AFM-->>ExecUC: ExecuteAppFunctionResponse

        alt Response is Success (PendingIntent return type)
            ExecUC-->>VM: ExecuteAppFunctionResult.PendingIntentAction(pendingIntent)
        else Response is Success (Standard data return type)
            ExecUC->>JsonUC: invoke(PROPERTY_RETURN_VALUE, returnValue, valueType, components)
            JsonUC-->>ExecUC: Pretty-printed JSON string
            ExecUC-->>VM: ExecuteAppFunctionResult.Data(data, formattedJson)
        else Response is Error or Exception thrown
            ExecUC-->>VM: ExecuteAppFunctionResult.Error(exception)
        end
    end

    VM-->>UI: Update FunctionsFoundState(executionResult)
    UI-->>User: Show AlertDialog (Formatted JSON / Error / PendingIntent Action)

    opt Result is PendingIntentAction & User taps Open
        User->>UI: Tap Open button
        UI->>VM: launchPendingIntent(pendingIntent)
        VM->>LaunchUC: invoke(pendingIntent)
        Note over LaunchUC: Sends PendingIntent with MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        LaunchUC-->>VM: Result<Unit>
        VM-->>UI: clearResult() or display error
    end
```
