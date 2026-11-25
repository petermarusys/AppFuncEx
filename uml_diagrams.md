# AppFuncEx (`com.peyo.apfex`) — UML Class & Sequence Diagrams

This document describes the architecture, static structure (UML Class Diagrams), and runtime behavior (UML Sequence Diagrams) of **AppFuncEx**, an Android Jetpack Compose + Hilt application for discovering, inspecting, and executing Android **AppFunctions** (`androidx.appfunctions`) and managing `AppInteractionSession` flows.

---

## 1. Architecture Overview

The application follows **Clean Architecture + MVVM** organized into four primary layers:

| Layer | Package(s) | Responsibilities |
| :--- | :--- | :--- |
| **Entry & DI** | `com.peyo.apfex`, `com.peyo.apfex.di` | Application bootstrap (`AppFunctionsApplication`), Activity host (`MainActivity`), privileged shell permission adoption (`ShellIdentityInstrumentation`), and Hilt bindings (`AppFunctionModule`, `DataModule`). |
| **UI (Presentation)** | `com.peyo.apfex.ui.screens.debugging`, `com.peyo.apfex.ui.theme` | Unidirectional data flow via `DebuggingViewModel` + `DebuggingUiState`, searchable/pinnable app dropdown, dynamic recursive parameter editors (`AppFunctionDataTypeInput`), and JSON/error/PendingIntent result dialogs (`FunctionsFoundContent`). |
| **Domain** | `com.peyo.apfex.domain.appfunction`, `com.peyo.apfex.domain.appinteraction`, `com.peyo.apfex.domain.pendingintent` | Stateless/reusable use cases for discovering installed apps and AppFunctions, checking enabled states, converting raw UI/LLM inputs into `AppFunctionData`, granting `content://` URI permissions, managing API 37.2 `AppInteractionSession`s + user consent, executing functions, formatting outputs/exceptions, and launching `PendingIntent`s. |
| **Data** | `com.peyo.apfex.data`, `com.peyo.apfex.data.db.entities` | Jetpack DataStore settings (`SettingsRepository`), thread-scoped in-memory `AppInteractionSession` cache (`AppInteractionSessionRepository`), built-in agent tools & progressive tool disclosure (`AgentInternalTools`, `ToolDiscoveryTools`), and Room persistence entities (`ThreadEntity`, `MessageEntity`). |

---

## 2. UML Class Diagrams

### 2.1 End-to-End Architecture Class Diagram

```mermaid
classDiagram
    direction TB

    namespace Entry_And_DI {
        class AppFunctionsApplication {
            <<HiltAndroidApp>>
        }

        class ShellIdentityInstrumentation {
            +onCreate(arguments)
            +onStart()
        }

        class MainActivity {
            <<AndroidEntryPoint>>
            +String EXTRA_INSTRUMENTATION_ERROR$
            +onCreate(savedInstanceState)
        }

        class AppFunctionModule {
            <<HiltModule>>
            +provideAppFunctionManager(context) AppFunctionManager
        }

        class DataModule {
            <<HiltModule>>
            +bindSettingsRepository(impl) SettingsRepository*
            +bindAppInteractionSessionRepository(impl) AppInteractionSessionRepository*
            +provideDataStore(context) DataStore~Preferences~
        }
    }

    namespace UI_Layer {
        class DebuggingScreen {
            <<Composable>>
            +DebuggingScreen(viewModel)
            +DebuggingScreenContent(uiState, callbacks)
            +AppDropdown(appGroups, searchQuery)
        }

        class FunctionsFoundContent {
            <<Composable>>
            +FunctionsFoundContent(state, callbacks)
            +AppFunctionErrorItem(function)
            -ExecutionResultDataView(formattedJson)
        }

        class AppFunctionItem {
            <<Composable>>
            +AppFunctionItem(function, isEnabled, expanded, inputValues)
            -ParameterInput(name, dataType, isRequired, value)
        }

        class AppFunctionDataTypeInput {
            <<Composable>>
            +AppFunctionDataTypeInput(dataType, value, onValueChange, components)
            +PrimitiveTextInput(value, onValueChange)
            +EnumDropdown(value, options)
            +BooleanTypeInput(value, onValueChange)
            +ObjectTypeInput(dataType, value)
            +ArrayTypeInput(dataType, value)
            +ComplexTypeInput(label, summary)
        }

        class DebuggingViewModel {
            <<HiltViewModel>>
            +StateFlow~DebuggingUiState~ uiState
            -List~AppInfo~ allInstalledApps
            -Set~String~ pinnedPackages
            -String DEBUG_THREAD_ID$
            +onAppSelected(appInfo)
            +onClearSelectedApp()
            +onSearchQueryChanged(query)
            +onFunctionInputsChange(functionId, inputs)
            +onFunctionExpandedChange(functionId, expanded)
            +invokeFunction(function)
            +clearResult()
            +launchPendingIntent(pendingIntent)
            +onTogglePin(appInfo)
        }

        class DebuggingUiState {
            +AppsGroupState filteredApps
            +AppInfo selectedApp
            +String searchQuery
            +Boolean isLoading
            +SearchAppResultState searchAppResultState
        }
    }

    namespace Domain_Layer {
        class GetInstalledAppsUseCase {
            +invoke() List~AppInfo~
        }

        class GetAppFunctionsUseCase {
            +invoke(params) Flow~AppFunctionsByPackage~
        }

        class GetAppFunctionStatesUseCase {
            +invoke(functionNames) List~AppFunctionState~
        }

        class ConvertInputToAppFunctionDataUseCase {
            +invoke(parameters, components, inputs) Result~AppFunctionData~
        }

        class ExecuteAppFunctionUseCase {
            +invoke(function, parameters, threadId) ExecuteAppFunctionResult
        }

        class GrantAppFunctionUriPermissionsUseCase {
            +invoke(function, parameters)
        }

        class ConvertAppFunctionDataToJsonUseCase {
            +invoke(name, data, dataType, components) String
        }

        class AppFunctionExceptionFormatter {
            <<object>>
            +format(exception, functionId) String
            +getAppFunctionException(throwable) AppFunctionException
        }

        class StartAppInteractionSessionUseCase {
            <<Singleton>>
            +invoke(threadId, targetPackages) AppInteractionSession
        }

        class HandleUserConsentUseCase {
            <<Singleton>>
            +invoke(intentSender)
        }

        class LaunchPendingIntentUseCase {
            +invoke(pendingIntent) Result~Unit~
        }
    }

    namespace Data_Layer {
        class SettingsRepository {
            <<interface>>
            +Flow~String~ geminiApiKey
            +Flow~Boolean~ progressiveToolDisclosure
            +Flow~StringSet~ pinnedApps
            +Flow~StringSet~ disconnectedApps
            +setGeminiApiKey(apiKey)
            +setProgressiveToolDisclosure(enabled)
            +setAppPinned(packageName, pinned)
            +setAppConnected(packageName, connected)
        }

        class DataStoreSettingsRepository {
            -DataStore~Preferences~ dataStore
        }

        class AppInteractionSessionRepository {
            <<interface>>
            +saveSession(threadId, session)
            +getSession(threadId) AppInteractionSession
            +invalidateSession(threadId)
        }

        class InMemoryAppInteractionSessionRepository {
            <<Singleton>>
            -ConcurrentHashMap sessionMap
            -ExecutorService executor
            -LifecycleListener lifecycleListener
        }

        class AgentInternalTools {
            <<Singleton>>
            +String INTERNAL_TOOL_PACKAGE$
            +String EXPAND_TOOL_DESCRIPTION$
            +String EXPAND_FUNCTION_DECLARATIONS$
            +geocodeAddress(address) LatLng
            +getCurrentLocation() LatLng
            +generateImage(prompt, aspectRatio) GeneratedImageResult
            +getInternalToolsMetadata() List~MetadataAndState~
            +buildToolDiscoveryTools(apps) List~AppFunctionMetadata~
            +executeInternalTool(function, parameters, availableApps) ExecuteAppFunctionResult
        }

        class ToolDiscoveryTools {
            <<Singleton>>
            +buildTools(apps) List~AppFunctionMetadata~
            +execute(function, parameters, apps) ExecuteAppFunctionResult
        }
    }

    %% Entry & UI relationships
    ShellIdentityInstrumentation ..> MainActivity : launches with shell identity
    MainActivity --> DebuggingScreen : hosts
    DebuggingScreen --> DebuggingViewModel : observes uiState & dispatches events
    DebuggingScreen --> FunctionsFoundContent : renders when FunctionsFoundState
    FunctionsFoundContent --> AppFunctionItem : renders per function
    FunctionsFoundContent ..> AppFunctionExceptionFormatter : formats errors
    AppFunctionItem --> AppFunctionDataTypeInput : renders per parameter
    AppFunctionDataTypeInput --> AppFunctionDataTypeInput : recurses for Object / Array / Ref
    DebuggingViewModel *-- DebuggingUiState : exposes StateFlow

    %% ViewModel to Domain/Data relationships
    DebuggingViewModel --> GetInstalledAppsUseCase : loads installed apps
    DebuggingViewModel --> GetAppFunctionsUseCase : observes AppFunctions
    DebuggingViewModel --> GetAppFunctionStatesUseCase : queries enabled states
    DebuggingViewModel --> ConvertInputToAppFunctionDataUseCase : builds AppFunctionData
    DebuggingViewModel --> ExecuteAppFunctionUseCase : executes function
    DebuggingViewModel --> LaunchPendingIntentUseCase : launches PendingIntent
    DebuggingViewModel --> SettingsRepository : observes/updates pinnedApps

    %% Domain internal relationships
    GetAppFunctionsUseCase --> GetAppFunctionStatesUseCase : filters by enabled state
    GetAppFunctionsUseCase --> SettingsRepository : filters disconnectedApps
    GetAppFunctionsUseCase --> AgentInternalTools : gets internal & discovery tools
    ExecuteAppFunctionUseCase --> GrantAppFunctionUriPermissionsUseCase : grants content URI read perms
    ExecuteAppFunctionUseCase --> ConvertAppFunctionDataToJsonUseCase : serializes return value
    ExecuteAppFunctionUseCase --> AgentInternalTools : routes internal tools
    ExecuteAppFunctionUseCase --> GetAppFunctionsUseCase : resolves connected apps for discovery
    ExecuteAppFunctionUseCase --> StartAppInteractionSessionUseCase : Lazy acquisition on API 37.2+
    StartAppInteractionSessionUseCase --> AppInteractionSessionRepository : caches session by threadId
    StartAppInteractionSessionUseCase --> HandleUserConsentUseCase : launches consent IntentSender

    %% Data & DI relationships
    DataStoreSettingsRepository ..|> SettingsRepository
    InMemoryAppInteractionSessionRepository ..|> AppInteractionSessionRepository
    AgentInternalTools --> ToolDiscoveryTools : delegates progressive disclosure
    DataModule ..> DataStoreSettingsRepository : binds
    DataModule ..> InMemoryAppInteractionSessionRepository : binds
```

---

### 2.2 Detailed UI State & Domain Model Class Diagram

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
        +Map~AppFunctionName, Boolean~ enabledState
        +Map~String, ParameterMap~ functionInputs
        +ExecuteAppFunctionResult executionResult
        +Set~String~ expandedFunctions
        +AppFunctionMetadata executedFunction
        +Map~String, Any~ executedInputs
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

    class ExecutionDisplayItem {
        <<sealed>>
    }

    class SimpleProperty {
        +String key
        +String value
    }

    class Group {
        +String header
        +List~SimpleProperty~ properties
    }

    class GetAppFunctionsParams {
        +Set~String~ targetPackageNames
        +Boolean isEnabled
        +Boolean isConnected
        +Boolean includeInternalTools
        +Boolean progressiveToolDisclosure
    }

    DebuggingUiState *-- AppsGroupState
    DebuggingUiState o-- AppInfo : selectedApp
    DebuggingUiState *-- SearchAppResultState
    AppsGroupState *-- AppSection : sections
    AppSection o-- AppInfo : apps

    SearchAppResultState <|-- Idle
    SearchAppResultState <|-- FunctionsFoundState
    FunctionsFoundState o-- ExecuteAppFunctionResult : executionResult

    ExecuteAppFunctionResult <|-- Data
    ExecuteAppFunctionResult <|-- PendingIntentAction
    ExecuteAppFunctionResult <|-- ToolsDiscovered
    ExecuteAppFunctionResult <|-- Error

    ExecutionDisplayItem <|-- SimpleProperty
    ExecutionDisplayItem <|-- Group
    Group *-- SimpleProperty : properties
```

---

### 2.3 Detailed Data, Internal Tools & Room Entities Class Diagram

```mermaid
classDiagram
    direction TB

    class AgentInternalTools {
        <<Singleton>>
        -Context context
        -DataStore~Preferences~ settingsDataStore
        -ToolDiscoveryTools toolDiscoveryTools
        +geocodeAddress(address) LatLng
        +getCurrentLocation() LatLng
        +generateImage(prompt, aspectRatio) GeneratedImageResult
        +getInternalToolsMetadata() List~MetadataAndState~
        +buildToolDiscoveryTools(apps) List~AppFunctionMetadata~
        +executeInternalTool(function, parameters, availableApps) ExecuteAppFunctionResult
    }

    class LatLng {
        +Double latitude
        +Double longitude
    }

    class GeneratedImageResult {
        +String imageUri
        +String mimeType
        +String prompt
    }

    class ToolDiscoveryTools {
        <<Singleton>>
        -Context context
        +buildTools(apps) List~AppFunctionMetadata~
        +execute(function, parameters, apps) ExecuteAppFunctionResult
        -expandToolDescription(packageMetadata, functions) ExecuteAppFunctionResult
        -expandFunctionDeclarations(packageName, functions, requestedIds) ExecuteAppFunctionResult
    }

    class CatalogEntry {
        -String packageName
        -String label
        -String instructions
        -List~AppFunctionMetadata~ functions
        +summary() String
    }

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
        <<Serializable>>
        +String uri
        +String mimeType
    }

    class MessageAttachmentConverter {
        <<RoomTypeConverter>>
        +fromAttachments(attachments) String
        +toAttachments(jsonString) List~MessageAttachment~
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

    AgentInternalTools *-- LatLng
    AgentInternalTools *-- GeneratedImageResult
    AgentInternalTools --> ToolDiscoveryTools
    ToolDiscoveryTools *-- CatalogEntry

    ThreadEntity "1" <-- "*" MessageEntity : ForeignKey(CASCADE)
    MessageEntity *-- MessageAttachment : attachments
    MessageEntity --> MessageRole
    MessageEntity --> MessageProcessingStatus
    MessageAttachmentConverter ..> MessageAttachment : JSON serialize/deserialize
```

---

## 3. UML Sequence Diagrams

### 3.1 Privileged App Launch & Shell Permission Adoption

Launching `AppFuncEx` via [`startAppFunctionTestingAgent`](file:///home/hdyoon/ex/apfex/startAppFunctionTestingAgent) configures device allowlists and starts [`ShellIdentityInstrumentation`](file:///home/hdyoon/ex/apfex/src/com/peyo/apfex/ShellIdentityInstrumentation.kt) so the process holds `android.permission.EXECUTE_APP_FUNCTIONS`.

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant Script as startAppFunctionTestingAgent
    participant ADB as Android OS / ADB Shell
    participant Inst as ShellIdentityInstrumentation
    participant UiAuto as UiAutomation
    participant Main as MainActivity

    Dev->>Script: ./startAppFunctionTestingAgent [-s SERIAL]
    Script->>ADB: pm list packages & dumpsys package com.peyo.apfex
    ADB-->>Script: Package signature fingerprint

    opt SDK >= 37
        Script->>ADB: cmd allowlist add-package-with-metadata-multimap (...)
        Script->>ADB: cmd app_function flush-allowlist-changes
    end

    opt SDK >= 35
        Script->>ADB: cmd appops set com.android.shell (AUDIO ops) allow
    end

    Script->>ADB: am force-stop com.peyo.apfex
    Script->>ADB: am instrument -w com.peyo.apfex/.ShellIdentityInstrumentation (background)

    ADB->>Inst: onCreate(arguments)
    Inst->>Inst: start()
    ADB->>Inst: onStart()
    Inst->>UiAuto: getUiAutomation()

    alt UiAutomation available
        Inst->>UiAuto: adoptShellPermissionIdentity("android.permission.EXECUTE_APP_FUNCTIONS")
        Inst->>Main: startActivity(Intent FLAG_ACTIVITY_NEW_TASK)
    else UiAutomation is null
        Inst->>Main: startActivity(Intent + EXTRA_INSTRUMENTATION_ERROR)
    end

    Inst->>Inst: CountDownLatch(1).await() (keeps shell identity alive)

    opt Instrumentation process exited within 2s
        Script->>ADB: Fallback am start -n com.peyo.apfex/.MainActivity
    end
```

---

### 3.2 App Discovery, Filtering & Selection Flow

When [`MainActivity`](file:///home/hdyoon/ex/apfex/src/com/peyo/apfex/MainActivity.kt) mounts [`DebuggingScreen`](file:///home/hdyoon/ex/apfex/src/com/peyo/apfex/ui/screens/debugging/DebuggingScreen.kt), [`DebuggingViewModel`](file:///home/hdyoon/ex/apfex/src/com/peyo/apfex/ui/screens/debugging/DebuggingViewModel.kt) concurrently loads installed apps, observes `AppFunctionManager` search results + enabled states, and observes pinned packages from DataStore.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Screen as DebuggingScreen
    participant VM as DebuggingViewModel
    participant InstalledUC as GetInstalledAppsUseCase
    participant FuncsUC as GetAppFunctionsUseCase
    participant StatesUC as GetAppFunctionStatesUseCase
    participant Settings as SettingsRepository
    participant AFM as AppFunctionManager
    participant PM as PackageManager

    Screen->>VM: hiltViewModel() / init

    par Load Installed Apps
        VM->>InstalledUC: invoke()
        InstalledUC->>PM: getInstalledApplications(0)
        PM-->>InstalledUC: ApplicationInfo list
        InstalledUC-->>VM: Sorted List of AppInfo
        VM->>VM: updateAppsGroupState()
    and Observe AppFunctions & States
        VM->>FuncsUC: invoke(GetAppFunctionsParams(includeInternalTools = false))
        FuncsUC->>AFM: observeAppFunctions() (debounced 500ms)
        AFM-->>FuncsUC: Change notification / onStart
        FuncsUC->>AFM: searchAppFunctions(AppFunctionSearchSpec)
        AFM-->>FuncsUC: List of AppFunctionMetadata
        FuncsUC-->>VM: Map of PackageMetadata to AppFunctionMetadata list
        VM->>StatesUC: invoke(allFunctionNames)
        StatesUC->>AFM: getAppFunctionStates(functionNames)
        AFM-->>StatesUC: List of AppFunctionState
        StatesUC-->>VM: List of AppFunctionState
        VM->>VM: updateAppsGroupState()
    and Observe Pinned Apps
        VM->>Settings: pinnedApps.collect()
        Settings-->>VM: Set of pinnedPackages
        VM->>VM: updateAppsGroupState()
    end

    VM-->>Screen: Emit DebuggingUiState(filteredApps)

    opt User toggles app pin
        User->>Screen: Tap Pin icon on AppDropdownItem
        Screen->>VM: onTogglePin(appInfo)
        VM->>Settings: setAppPinned(packageName, !isPinned)
        Settings-->>VM: Emits updated pinnedApps Set
        VM-->>Screen: Emit updated DebuggingUiState
    end

    User->>Screen: Select app from AppDropdown
    Screen->>VM: onAppSelected(appInfo)
    VM->>VM: Match app functions & build enabledState map
    VM-->>Screen: Emit DebuggingUiState(selectedApp, FunctionsFoundState)
```

---

### 3.3 External AppFunction Execution, User Consent & PendingIntent Flow

This sequence traces parameter input editing, conversion into `AppFunctionData`, `content://` URI permission granting, API 37.2 `AppInteractionSession` creation/updating (with `IntentSender` user consent), execution via `AppFunctionManager`, and handling `Data`, `PendingIntentAction`, or `Error` results.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as FunctionsFoundContent / AppFunctionItem
    participant VM as DebuggingViewModel
    participant ConvertIn as ConvertInputToAppFunctionDataUseCase
    participant ExecUC as ExecuteAppFunctionUseCase
    participant UriPermUC as GrantAppFunctionUriPermissionsUseCase
    participant SessionUC as StartAppInteractionSessionUseCase
    participant SessionRepo as AppInteractionSessionRepository
    participant ConsentUC as HandleUserConsentUseCase
    participant AIM as AppInteractionManager / Session
    participant AFM as AppFunctionManager
    participant ConvertOut as ConvertAppFunctionDataToJsonUseCase
    participant LaunchPI as LaunchPendingIntentUseCase

    User->>UI: Expand function & enter parameter values
    UI->>VM: onFunctionInputsChange(functionId, inputs)
    VM-->>UI: Update functionInputs in FunctionsFoundState

    User->>UI: Tap "Run"
    UI->>VM: invokeFunction(function)
    VM->>ConvertIn: invoke(function.parameters, function.components, inputs)
    ConvertIn-->>VM: Result(AppFunctionData)

    VM->>ExecUC: invoke(function, appFunctionData, "debugging-thread")
    ExecUC->>UriPermUC: invoke(function, appFunctionData)
    UriPermUC->>UriPermUC: Recursively grant FLAG_GRANT_READ_URI_PERMISSION on content:// URIs

    alt API >= 37.2 (CINNAMON_BUN_2)
        ExecUC->>SessionUC: invoke("debugging-thread", [targetPackageName])
        SessionUC->>SessionRepo: getSession("debugging-thread")
        alt Session not cached
            SessionUC->>AIM: createSession(CreateParams)
            opt System requests user consent
                AIM->>ConsentUC: onStartIntentSender(intentSender)
                ConsentUC->>User: startIntentSender(FLAG_ACTIVITY_NEW_TASK)
                User-->>AIM: Approve / Deny consent
            end
            AIM-->>SessionUC: AppInteractionSession
            SessionUC->>SessionRepo: saveSession("debugging-thread", session)
        else Session already cached
            SessionUC->>AIM: session.update(UpdateParams)
            opt Additional target package requires consent
                AIM->>ConsentUC: onStartIntentSender(intentSender)
                ConsentUC->>User: startIntentSender(FLAG_ACTIVITY_NEW_TASK)
            end
            AIM-->>SessionUC: Updated AppInteractionSession
        end
        SessionUC-->>ExecUC: Active AppInteractionSession
        ExecUC->>AFM: executeAppFunction(session, request)
    else API < 37.2
        ExecUC->>AFM: executeAppFunction(request)
    end

    AFM-->>ExecUC: ExecuteAppFunctionResponse

    alt ExecuteAppFunctionResponse.Success (Standard Data)
        ExecUC->>ConvertOut: invoke(PROPERTY_RETURN_VALUE, data, valueType, components)
        ConvertOut-->>ExecUC: Pretty-printed JSON String
        ExecUC-->>VM: ExecuteAppFunctionResult.Data(data, formattedJson)
        VM-->>UI: Show Result AlertDialog (ExecutionResultDataView)
    else ExecuteAppFunctionResponse.Success (PendingIntent Return Value)
        ExecUC-->>VM: ExecuteAppFunctionResult.PendingIntentAction(pendingIntent)
        VM-->>UI: Show Action Required AlertDialog
        User->>UI: Tap "Open"
        UI->>VM: launchPendingIntent(pendingIntent)
        VM->>LaunchPI: invoke(pendingIntent)
        LaunchPI->>LaunchPI: pendingIntent.send(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        LaunchPI-->>VM: Result.success
        VM->>VM: clearResult()
    else ExecuteAppFunctionResponse.Error or Exception
        ExecUC-->>VM: ExecuteAppFunctionResult.Error(exception)
        VM-->>UI: Show Error AlertDialog (AppFunctionExceptionFormatter)
    end
```

---

### 3.4 Internal Agent Tools & Progressive Tool Disclosure Flow

In addition to external `AppFunctions`, the domain and data layers provide built-in agent tools (`com.example.appfunctions.agent.internal`) and a two-step progressive tool disclosure protocol via [`AgentInternalTools`](file:///home/hdyoon/ex/apfex/src/com/peyo/apfex/data/AgentInternalTools.kt) and [`ToolDiscoveryTools`](file:///home/hdyoon/ex/apfex/src/com/peyo/apfex/data/ToolDiscoveryTools.kt).

```mermaid
sequenceDiagram
    autonumber
    actor Caller as Agent / Caller
    participant FuncsUC as GetAppFunctionsUseCase
    participant ExecUC as ExecuteAppFunctionUseCase
    participant Internal as AgentInternalTools
    participant Discovery as ToolDiscoveryTools
    participant AndroidSys as LocationManager / Geocoder / Gemini API

    Caller->>FuncsUC: invoke(GetAppFunctionsParams(progressiveToolDisclosure = true))
    FuncsUC->>Internal: getInternalToolsMetadata()
    FuncsUC->>Internal: buildToolDiscoveryTools(externalAppsByPackage)
    Internal->>Discovery: buildTools(externalAppsByPackage)
    Discovery-->>FuncsUC: [expand_tool_description, expand_function_declarations]
    FuncsUC-->>Caller: Internal tools + Discovery tools

    alt Progressive Discovery Step 1: Inspect App Summary
        Caller->>ExecUC: invoke(expand_tool_description, {package_name}, threadId)
        ExecUC->>FuncsUC: invoke(isEnabled=true, isConnected=true, includeInternalTools=false).first()
        FuncsUC-->>ExecUC: availableApps map
        ExecUC->>Internal: executeInternalTool(function, params, availableApps)
        Internal->>Discovery: execute(expand_tool_description, params, availableApps)
        Discovery-->>Caller: ExecuteAppFunctionResult.Data (instructions + function_id summaries)
    else Progressive Discovery Step 2: Load Full Schemas
        Caller->>ExecUC: invoke(expand_function_declarations, {package_name, function_ids}, threadId)
        ExecUC->>FuncsUC: invoke(isEnabled=true, isConnected=true, includeInternalTools=false).first()
        FuncsUC-->>ExecUC: availableApps map
        ExecUC->>Internal: executeInternalTool(function, params, availableApps)
        Internal->>Discovery: execute(expand_function_declarations, params, availableApps)
        Discovery-->>Caller: ExecuteAppFunctionResult.ToolsDiscovered(json, loadedMetadataList)
    else Built-in Utility Tool (getCurrentLocation / geocodeAddress / generateImage)
        Caller->>ExecUC: invoke(internalTool, params, threadId)
        ExecUC->>Internal: executeInternalTool(internalTool, params, emptyMap())
        Internal->>AndroidSys: Query GPS/Network, Geocoder, or Gemini Image REST API + FileProvider
        AndroidSys-->>Internal: LatLng or GeneratedImageResult (content:// URI)
        Internal-->>Caller: ExecuteAppFunctionResult.Data(data, formattedJson)
    end
```
