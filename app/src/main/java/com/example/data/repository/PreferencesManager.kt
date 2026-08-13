package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.LoginMethod

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("huafetcher_prefs", Context.MODE_PRIVATE)

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var loginMethod: LoginMethod
        get() {
            val name = prefs.getString(KEY_LOGIN_METHOD, LoginMethod.XIAOMI.name)
            return try {
                LoginMethod.valueOf(name ?: LoginMethod.XIAOMI.name)
            } catch (e: Exception) {
                LoginMethod.XIAOMI
            }
        }
        set(value) = prefs.edit().putString(KEY_LOGIN_METHOD, value.name).apply()

    var lastXiaomiUrl: String
        get() = prefs.getString(KEY_XIAOMI_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_XIAOMI_URL, value).apply()

    var downloadDirType: String
        get() = prefs.getString(KEY_DOWNLOAD_DIR_TYPE, "EXTERNAL") ?: "EXTERNAL"
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_DIR_TYPE, value).apply()

    var customFolderName: String
        get() = prefs.getString(KEY_CUSTOM_FOLDER_NAME, "Huafetcher") ?: "Huafetcher"
        set(value) = prefs.edit().putString(KEY_CUSTOM_FOLDER_NAME, value).apply()

    var isFirstStartCompleted: Boolean
        get() = prefs.getBoolean(KEY_FIRST_START_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_START_COMPLETED, value).apply()

    var preferredExportFile: String
        get() = prefs.getString(KEY_PREFERRED_EXPORT_FILE, "lto.zip") ?: "lto.zip"
        set(value) = prefs.edit().putString(KEY_PREFERRED_EXPORT_FILE, value).apply()

    var hideConsoleLogs: Boolean
        get() = prefs.getBoolean(KEY_HIDE_CONSOLE_LOGS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_CONSOLE_LOGS, value).apply()

    var compactMode: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_MODE, value).apply()

    companion object {
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LOGIN_METHOD = "login_method"
        private const val KEY_XIAOMI_URL = "xiaomi_url"
        private const val KEY_DOWNLOAD_DIR_TYPE = "download_dir_type"
        private const val KEY_CUSTOM_FOLDER_NAME = "custom_folder_name"
        private const val KEY_FIRST_START_COMPLETED = "first_start_completed"
        private const val KEY_PREFERRED_EXPORT_FILE = "preferred_export_file"
        private const val KEY_HIDE_CONSOLE_LOGS = "hide_console_logs"
        private const val KEY_COMPACT_MODE = "compact_mode"
    }
}
