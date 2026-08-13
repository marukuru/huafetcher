package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DeviceKey
import com.example.data.model.GeneratedFile
import com.example.data.model.LogMessage
import com.example.data.model.LogType
import com.example.data.model.LoginMethod
import com.example.data.model.TokenInfo
import com.example.data.network.HuamiApiService
import com.example.data.repository.PreferencesManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HuafetcherViewModel(
    private val apiService: HuamiApiService,
    private val prefsManager: PreferencesManager
) : ViewModel() {

    private val _loginMethod = MutableStateFlow(prefsManager.loginMethod)
    val loginMethod: StateFlow<LoginMethod> = _loginMethod.asStateFlow()

    private val _email = MutableStateFlow(prefsManager.email)
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow(prefsManager.password)
    val password: StateFlow<String> = _password.asStateFlow()

    private val _xiaomiUrl = MutableStateFlow(prefsManager.lastXiaomiUrl)
    val xiaomiUrl: StateFlow<String> = _xiaomiUrl.asStateFlow()

    private val _parsedXiaomiCode = MutableStateFlow<String?>(null)
    val parsedXiaomiCode: StateFlow<String?> = _parsedXiaomiCode.asStateFlow()

    private val _deviceKeys = MutableStateFlow<List<DeviceKey>>(emptyList())
    val deviceKeys: StateFlow<List<DeviceKey>> = _deviceKeys.asStateFlow()

    private val _selectedDeviceKey = MutableStateFlow<DeviceKey?>(null)
    val selectedDeviceKey: StateFlow<DeviceKey?> = _selectedDeviceKey.asStateFlow()

    private val _logs = MutableStateFlow<List<LogMessage>>(emptyList())
    val logs: StateFlow<List<LogMessage>> = _logs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusText = MutableStateFlow("Ready")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _generatedFile = MutableStateFlow<GeneratedFile?>(null)
    val generatedFile: StateFlow<GeneratedFile?> = _generatedFile.asStateFlow()

    private val _downloadDirType = MutableStateFlow(prefsManager.downloadDirType)
    val downloadDirType: StateFlow<String> = _downloadDirType.asStateFlow()

    private val _customFolderName = MutableStateFlow(prefsManager.customFolderName)
    val customFolderName: StateFlow<String> = _customFolderName.asStateFlow()

    private val _isFirstStartCompleted = MutableStateFlow(prefsManager.isFirstStartCompleted)
    val isFirstStartCompleted: StateFlow<Boolean> = _isFirstStartCompleted.asStateFlow()

    private val _preferredExportFile = MutableStateFlow(prefsManager.preferredExportFile)
    val preferredExportFile: StateFlow<String> = _preferredExportFile.asStateFlow()

    private val _hideConsoleLogs = MutableStateFlow(prefsManager.hideConsoleLogs)
    val hideConsoleLogs: StateFlow<Boolean> = _hideConsoleLogs.asStateFlow()

    private val _compactMode = MutableStateFlow(prefsManager.compactMode)
    val compactMode: StateFlow<Boolean> = _compactMode.asStateFlow()

    private val _shareFileEvent = MutableSharedFlow<File>()
    val shareFileEvent: SharedFlow<File> = _shareFileEvent.asSharedFlow()

    private val _tokenInfo = MutableStateFlow<TokenInfo?>(null)
    val tokenInfo: StateFlow<TokenInfo?> = _tokenInfo.asStateFlow()

    private val _hasUihhFiles = MutableStateFlow(apiService.hasUihhComponentFiles())
    val hasUihhFiles: StateFlow<Boolean> = _hasUihhFiles.asStateFlow()

    init {
        log("Welcome to Huafetcher! Select a login method to begin.", LogType.INFO)
        if (_xiaomiUrl.value.isNotBlank()) {
            _parsedXiaomiCode.value = apiService.parseXiaomiCode(_xiaomiUrl.value)
        }
        updateUihhFilesState()
    }

    fun updateUihhFilesState() {
        _hasUihhFiles.value = apiService.hasUihhComponentFiles()
    }

    fun completeFirstStartWizard(
        method: LoginMethod,
        dirType: String,
        folderName: String,
        preferredExport: String
    ) {
        setLoginMethod(method)
        setDownloadDirType(dirType)
        setCustomFolderName(folderName)
        setPreferredExportFile(preferredExport)
        _isFirstStartCompleted.value = true
        prefsManager.isFirstStartCompleted = true
        log("First start setup completed.", LogType.SUCCESS)
    }

    fun resetFirstStartWizard() {
        _isFirstStartCompleted.value = false
        prefsManager.isFirstStartCompleted = false
        log("First start wizard reset.", LogType.INFO)
    }

    fun setLoginMethod(method: LoginMethod) {
        _loginMethod.value = method
        _tokenInfo.value = null
        prefsManager.loginMethod = method
        log("Login method set to: ${method.name}", LogType.INFO)
    }

    fun setEmail(text: String) {
        _email.value = text
    }

    fun setPassword(text: String) {
        _password.value = text
    }

    fun saveCredentials() {
        prefsManager.email = _email.value
        prefsManager.password = _password.value
        log("Credentials saved to local preferences.", LogType.SUCCESS)
    }

    fun setDownloadDirType(type: String) {
        _downloadDirType.value = type
        prefsManager.downloadDirType = type
        updateUihhFilesState()
        log("Download storage location set to: $type", LogType.INFO)
    }

    fun setCustomFolderName(name: String) {
        val trimmed = name.trim()
        _customFolderName.value = trimmed
        prefsManager.customFolderName = trimmed
        updateUihhFilesState()
        log("Download folder name updated to: '$trimmed'", LogType.INFO)
    }

    fun setPreferredExportFile(filename: String) {
        val trimmed = filename.trim()
        _preferredExportFile.value = trimmed
        prefsManager.preferredExportFile = trimmed
        log("Preferred export file set to: '$trimmed'", LogType.INFO)
    }

    fun setHideConsoleLogs(hide: Boolean) {
        _hideConsoleLogs.value = hide
        prefsManager.hideConsoleLogs = hide
        log(if (hide) "Console logs hidden." else "Console logs visible.", LogType.INFO)
    }

    fun setCompactMode(compact: Boolean) {
        _compactMode.value = compact
        prefsManager.compactMode = compact
        log(if (compact) "Compact Mode enabled." else "Compact Mode disabled.", LogType.INFO)
    }

    private suspend fun triggerAutoShareIfAvailable() {
        val preferred = _preferredExportFile.value.trim()
        if (preferred.isNotBlank() && !preferred.equals("None", ignoreCase = true)) {
            val dir = getDataDirectory()
            val files = dir.listFiles() ?: return
            val matchedFile = files.firstOrNull {
                it.isFile && it.name.equals(preferred, ignoreCase = true)
            }
            if (matchedFile != null && matchedFile.exists()) {
                log("Auto-opening share dialog for preferred export file: ${matchedFile.name}", LogType.SUCCESS)
                _shareFileEvent.emit(matchedFile)
            }
        }
    }

    fun setXiaomiUrl(url: String) {
        _xiaomiUrl.value = url
        prefsManager.lastXiaomiUrl = url
        val code = apiService.parseXiaomiCode(url)
        _parsedXiaomiCode.value = code
        if (code != null) {
            log("Parsed Xiaomi authorization code: $code", LogType.SUCCESS)
        } else if (url.isNotBlank()) {
            log("No authorization code found in current Xiaomi URL.", LogType.WARNING)
        }
    }

    fun selectDeviceKey(deviceKey: DeviceKey) {
        _selectedDeviceKey.value = deviceKey
        log("Selected key for device: ${deviceKey.macAddress}", LogType.INFO)
    }

    fun fetchDeviceAuthKeys() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = "Authenticating..."
            log("Initiating login for ${_loginMethod.value.name}...", LogType.INFO)

            try {
                val currentToken = ensureLoggedIn()
                log("Logged in successfully! User ID: ${currentToken.userId}", LogType.SUCCESS)

                _statusText.value = "Fetching device keys..."
                log("Querying linked wearables...", LogType.INFO)
                val keys = apiService.getWearableAuthKeys(currentToken)
                _deviceKeys.value = keys

                if (keys.isNotEmpty()) {
                    _selectedDeviceKey.value = keys.first()
                    log("Retrieved ${keys.size} wearable device key(s).", LogType.SUCCESS)
                } else {
                    log("No wearable devices or keys found for this account.", LogType.WARNING)
                }
                _statusText.value = "Done"
            } catch (e: Exception) {
                log("Failed to fetch keys: ${e.localizedMessage ?: e.message}", LogType.ERROR)
                _statusText.value = "Error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchAgpsPacks() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = "Fetching aGPS data..."
            log("Starting aGPS/GPS data pack download...", LogType.INFO)

            try {
                val currentToken = ensureLoggedIn()
                val workDir = apiService.fetchAgpsPacks(currentToken) { msg ->
                    log(msg, LogType.INFO)
                }
                log("aGPS files saved to: ${workDir.absolutePath}", LogType.SUCCESS)
                _statusText.value = "aGPS download complete"
                triggerAutoShareIfAvailable()
            } catch (e: Exception) {
                log("Failed to fetch aGPS files: ${e.localizedMessage ?: e.message}", LogType.ERROR)
                _statusText.value = "Error"
            } finally {
                updateUihhFilesState()
                _isLoading.value = false
            }
        }
    }

    fun createUihhBinaryFile() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = "Creating aGPS_UIHH.bin..."
            log("Packing downloaded aGPS files into UIHH format...", LogType.INFO)

            try {
                val genFile = apiService.createUihhAgpsFile { msg ->
                    log(msg, LogType.INFO)
                }
                _generatedFile.value = genFile
                log("Created ${genFile.name} (${genFile.sizeBytes} bytes) successfully!", LogType.SUCCESS)
                _statusText.value = "UIHH file created"
                triggerAutoShareIfAvailable()
            } catch (e: Exception) {
                log("Failed to create UIHH binary: ${e.localizedMessage ?: e.message}", LogType.ERROR)
                _statusText.value = "Error"
            } finally {
                updateUihhFilesState()
                _isLoading.value = false
            }
        }
    }

    private suspend fun ensureLoggedIn(): TokenInfo {
        _tokenInfo.value?.let { return it }

        val tokenInfo = when (_loginMethod.value) {
            LoginMethod.AMAZFIT -> {
                val mail = _email.value.trim()
                val pass = _password.value
                if (mail.isBlank() || pass.isBlank()) {
                    throw IllegalArgumentException("Please enter your Amazfit Email and Password.")
                }
                log("Fetching access token for $mail...", LogType.INFO)
                val (accessToken, countryCode) = apiService.getAmazfitAccessToken(mail, pass)
                log("Access token obtained ($countryCode). Logging into client...", LogType.INFO)
                apiService.login(LoginMethod.AMAZFIT, accessToken, countryCode)
            }
            LoginMethod.XIAOMI -> {
                val code = _parsedXiaomiCode.value
                if (code.isNullOrBlank()) {
                    throw IllegalArgumentException("Please sign in via Xiaomi link and paste the redirect URL.")
                }
                log("Logging in with Xiaomi authorization code...", LogType.INFO)
                apiService.login(LoginMethod.XIAOMI, code, "US")
            }
        }

        _tokenInfo.value = tokenInfo
        return tokenInfo
    }

    fun getDataDirectory(): File = apiService.getDataDirectory()

    fun getDirectoryFiles(): List<GeneratedFile> {
        val dir = getDataDirectory()
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isFile }.map { file ->
            GeneratedFile(
                name = file.name,
                path = file.absolutePath,
                sizeBytes = file.length(),
                lastModified = file.lastModified()
            )
        }.sortedByDescending { it.lastModified }
    }

    fun clearDataDirectory() {
        val deletedCount = apiService.clearAllDataDirectories()
        _generatedFile.value = null
        updateUihhFilesState()
        log("Data directory cleaned ($deletedCount item(s) removed).", LogType.INFO)
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Text") {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        log("Copied $label to clipboard.", LogType.INFO)
    }

    fun log(msg: String, type: LogType = LogType.INFO) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.value = _logs.value + LogMessage(timeStr, msg, type)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

class HuafetcherViewModelFactory(
    private val apiService: HuamiApiService,
    private val prefsManager: PreferencesManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HuafetcherViewModel::class.java)) {
            return HuafetcherViewModel(apiService, prefsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
