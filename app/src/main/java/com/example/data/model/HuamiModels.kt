package com.example.data.model

enum class LoginMethod {
    AMAZFIT,
    XIAOMI
}

enum class LogType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class LogMessage(
    val timestamp: String,
    val message: String,
    val type: LogType = LogType.INFO
)

data class DeviceKey(
    val macAddress: String,
    val authKey: String
)

data class TokenInfo(
    val appToken: String,
    val loginToken: String,
    val userId: String
)

data class GeneratedFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)
