package com.example.data.network

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.model.DeviceKey
import com.example.data.model.GeneratedFile
import com.example.data.model.LoginMethod
import com.example.data.model.TokenInfo
import com.example.data.repository.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

class HuamiApiService(
    private val context: Context,
    private val prefsManager: PreferencesManager? = null
) {

    private val clientWithoutRedirects = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val defaultClient = OkHttpClient.Builder().build()

    val deviceId: String = generateRandomDeviceId()

    companion object {
        const val XIAOMI_OAUTH_URL =
            "https://account.xiaomi.com/oauth2/authorize?skip_confirm=false&client_id=2882303761517383915&pt=0&scope=1+6000+16001+20000&redirect_uri=https%3A%2F%2Fhm.xiaomi.com%2Fwatch.do&_locale=en_US&response_type=code"

        private const val URL_TOKENS_AMAZFIT = "https://api-user.huami.com/registrations/%s/tokens"
        private const val URL_LOGIN_AMAZFIT = "https://account.huami.com/v2/client/login"
        private const val URL_DEVICES = "https://api-mifit-us2.huami.com/users/%s/devices"
        private const val URL_AGPS = "https://%s.huami.com/apps/com.huami.midong/fileTypes/%s/files"

        private fun generateRandomDeviceId(): String {
            val random = Random()
            return String.format(
                "02:00:00:%02x:%02x:%02x",
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256)
            )
        }
    }

    /**
     * Parse code parameter from Xiaomi redirect URL
     */
    fun parseXiaomiCode(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val uri = Uri.parse(url)
            uri.getQueryParameter("code")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get Amazfit access token by making registration token request
     */
    suspend fun getAmazfitAccessToken(email: String, pass: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val authUrl = String.format(URL_TOKENS_AMAZFIT, encodedEmail)

            val formBody = FormBody.Builder()
                .add("state", "REDIRECTION")
                .add("client_id", "HuaMi")
                .add("password", pass)
                .add("redirect_uri", "https://s3-us-west-2.amazonws.com/hm-registration/successsignin.html")
                .add("region", "us-west-2")
                .add("token", "access")
                .add("country_code", "US")
                .build()

            val request = Request.Builder()
                .url(authUrl)
                .post(formBody)
                .build()

            val response = clientWithoutRedirects.newCall(request).execute()
            val locationHeader = response.header("Location")
                ?: throw IllegalArgumentException("No 'Location' header in Amazfit auth response. Code: ${response.code}")

            val redirectUri = Uri.parse(locationHeader)
            val error = redirectUri.getQueryParameter("error")
            if (!error.isNullOrEmpty()) {
                throw IllegalArgumentException("Login failed: $error (check email/password)")
            }

            val access = redirectUri.getQueryParameter("access")
                ?: throw IllegalArgumentException("No 'access' token parameter in login redirect URL.")

            val countryCode = redirectUri.getQueryParameter("country_code") ?: "US"

            Pair(access, countryCode)
        }

    /**
     * Login to Huami backend using access token or authorization code
     */
    suspend fun login(
        method: LoginMethod,
        accessTokenOrCode: String,
        countryCode: String = "US"
    ): TokenInfo = withContext(Dispatchers.IO) {
        val thirdName = if (method == LoginMethod.AMAZFIT) "huami" else "mi-watch"
        val grantType = if (method == LoginMethod.AMAZFIT) "access_token" else "request_token"

        val formBody = FormBody.Builder()
            .add("dn", "account.huami.com,api-user.huami.com,app-analytics.huami.com,api-watch.huami.com,api-analytics.huami.com,api-mifit.huami.com")
            .add("app_version", "5.9.2-play_100355")
            .add("source", "com.huami.watch.hmwatchmanager")
            .add("country_code", countryCode)
            .add("device_id", deviceId)
            .add("third_name", thirdName)
            .add("lang", "en")
            .add("device_model", "android_phone")
            .add("allow_registration", "false")
            .add("app_name", "com.huami.midong")
            .add("code", accessTokenOrCode)
            .add("grant_type", grantType)
            .build()

        val request = Request.Builder()
            .url(URL_LOGIN_AMAZFIT)
            .post(formBody)
            .build()

        val response = defaultClient.newCall(request).execute()
        val bodyStr = response.body?.string()
            ?: throw IllegalStateException("Empty response body from login server")

        val json = JSONObject(bodyStr)
        if (json.has("error_code")) {
            throw IllegalArgumentException("Login error code: ${json.optString("error_code")}")
        }

        if (!json.has("token_info")) {
            throw IllegalArgumentException("No 'token_info' in login response JSON")
        }

        val tokenInfoObj = json.getJSONObject("token_info")
        val appToken = tokenInfoObj.getString("app_token")
        val loginToken = tokenInfoObj.getString("login_token")
        val userId = tokenInfoObj.getString("user_id")

        TokenInfo(appToken, loginToken, userId)
    }

    /**
     * Fetch wearable devices and their auth keys
     */
    suspend fun getWearableAuthKeys(tokenInfo: TokenInfo): List<DeviceKey> =
        withContext(Dispatchers.IO) {
            val url = String.format(URL_DEVICES, Uri.encode(tokenInfo.userId))
            val request = Request.Builder()
                .url(url)
                .header("apptoken", tokenInfo.appToken)
                .get()
                .build()

            val response = defaultClient.newCall(request).execute()
            val bodyStr = response.body?.string()
                ?: throw IllegalStateException("Empty response from devices endpoint")

            val json = JSONObject(bodyStr)
            if (!json.has("items")) {
                throw IllegalArgumentException("No 'items' in devices response JSON")
            }

            val items: JSONArray = json.getJSONArray("items")
            val deviceKeys = mutableListOf<DeviceKey>()

            for (i in 0 until items.length()) {
                val wearable = items.getJSONObject(i)
                val macAddress = wearable.optString("macAddress", "Unknown MAC")
                val additionalInfoStr = wearable.optString("additionalInfo", "{}")

                val authKey = try {
                    val addInfoJson = JSONObject(additionalInfoStr)
                    val keyStr = addInfoJson.optString("auth_key", "")
                    if (keyStr.isNotEmpty()) "0x$keyStr" else "0x00"
                } catch (e: Exception) {
                    "0x00"
                }

                deviceKeys.add(DeviceKey(macAddress, authKey))
            }

            deviceKeys
        }

    /**
     * Check if component files required for building aGPS_UIHH.bin are present.
     */
    fun hasUihhComponentFiles(): Boolean {
        val workDir = getDataDirectory()
        if (!workDir.exists()) return false
        val componentFiles = listOf(
            "gps_alm.bin",
            "gln_alm.bin",
            "lle_bds.lle",
            "lle_gps.lle",
            "lle_glo.lle",
            "lle_gal.lle",
            "lle_qzss.lle"
        )
        return componentFiles.any { File(workDir, it).exists() }
    }

    /**
     * Get or create local data directory for downloaded & converted files
     */
    fun getDataDirectory(): File {
        val pm = prefsManager ?: PreferencesManager(context)
        val dirType = pm.downloadDirType
        val rawFolderName = pm.customFolderName.trim()
        val folderName = if (rawFolderName.isBlank()) "Huafetcher" else rawFolderName

        val targetDir: File = when (dirType) {
            "INTERNAL" -> File(context.filesDir, folderName)
            "PUBLIC_DOWNLOADS" -> {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadsDir, folderName)
            }
            else -> { // "EXTERNAL"
                context.getExternalFilesDir(folderName) ?: File(context.filesDir, folderName)
            }
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        return targetDir
    }

    /**
     * Thoroughly clean all potential download directory locations
     */
    fun clearAllDataDirectories(): Int {
        val pm = prefsManager ?: PreferencesManager(context)
        val rawFolderName = pm.customFolderName.trim()
        val folderName = if (rawFolderName.isBlank()) "Huafetcher" else rawFolderName

        val locationsToClean = mutableListOf<File>()

        locationsToClean.add(getDataDirectory())
        locationsToClean.add(File(context.filesDir, "huafetcher_data"))
        locationsToClean.add(File(context.filesDir, folderName))
        context.getExternalFilesDir("huafetcher_data")?.let { locationsToClean.add(it) }
        context.getExternalFilesDir(folderName)?.let { locationsToClean.add(it) }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        locationsToClean.add(File(downloadsDir, "huafetcher_data"))
        locationsToClean.add(File(downloadsDir, folderName))

        var totalDeleted = 0
        locationsToClean.distinctBy { it.absolutePath }.forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { item ->
                    if (item.isDirectory) {
                        if (item.deleteRecursively()) totalDeleted++
                    } else {
                        if (item.delete()) totalDeleted++
                    }
                }
            }
        }

        // Ensure the active target directory exists empty
        val currentTarget = getDataDirectory()
        if (!currentTarget.exists()) {
            currentTarget.mkdirs()
        }

        return totalDeleted
    }

    /**
     * Download GPS and AGPS packs
     */
    suspend fun fetchAgpsPacks(
        tokenInfo: TokenInfo,
        onProgress: (String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val agpsPacks = listOf("AGPS_ALM", "AGPSZIP", "LLE", "AGPS", "EPO", "LTO")
        val agpsFileNames = listOf("cep_1week.zip", "cep_7days.zip", "lle_1week.zip", "cep_pak.bin", "epo.zip", "lto.zip")
        val servers = listOf("api-mifit-us2", "api-mifit-de2", "api-mifit-cn2", "api-mifit-sg2", "api-mifit")

        val workDir = getDataDirectory()

        // Backup existing files to subfolder with yyyyMMdd_HHmmss timestamp
        val existingFiles = workDir.listFiles()?.filter { it.isFile } ?: emptyList()
        var backupDir: File? = null

        if (existingFiles.isNotEmpty()) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempBackupDir = File(workDir, "backup_$timeStamp")
            if (tempBackupDir.mkdirs()) {
                backupDir = tempBackupDir
                onProgress("Created backup folder: ${tempBackupDir.name}")
                existingFiles.forEach { oldFile ->
                    val dest = File(tempBackupDir, oldFile.name)
                    oldFile.renameTo(dest)
                }
            }
        }

        var downloadedCount = 0

        try {
            for (idx in agpsPacks.indices) {
                val packName = agpsPacks[idx]
                val fileName = agpsFileNames[idx]
                onProgress("Downloading $packName ($fileName)...")

                var downloaded = false
                for (server in servers) {
                    val url = String.format(URL_AGPS, server, packName)
                    val request = Request.Builder()
                        .url(url)
                        .header("apptoken", tokenInfo.appToken)
                        .get()
                        .build()

                    try {
                        val response = defaultClient.newCall(request).execute()
                        if (response.code == 401) {
                            continue
                        }

                        if (!response.isSuccessful) continue

                        val bodyStr = response.body?.string() ?: continue
                        val jsonArr = JSONArray(bodyStr)
                        if (jsonArr.length() == 0) continue

                        val itemObj = jsonArr.getJSONObject(0)
                        val fileUrl = itemObj.optString("fileUrl", "")
                        if (fileUrl.isBlank()) continue

                        // Download actual file
                        val dlRequest = Request.Builder().url(fileUrl).get().build()
                        val dlResponse = defaultClient.newCall(dlRequest).execute()
                        if (dlResponse.isSuccessful) {
                            val fileBytes = dlResponse.body?.bytes()
                            if (fileBytes != null) {
                                val outFile = File(workDir, fileName)
                                FileOutputStream(outFile).use { fos ->
                                    fos.write(fileBytes)
                                }
                                downloaded = true
                                downloadedCount++
                                onProgress("Saved $fileName (${fileBytes.size} bytes)")

                                // Unzip non-epo zip files
                                if (fileName.endsWith(".zip") && fileName != "epo.zip") {
                                    onProgress("Unzipping $fileName...")
                                    extractZip(outFile, workDir)
                                }
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Try next server
                    }
                }

                if (!downloaded) {
                    onProgress("Warning: Could not download $packName from any server")
                }
            }

            if (backupDir != null) {
                if (downloadedCount > 0) {
                    onProgress("Download successful ($downloadedCount pack(s)). Deleting backup folder ${backupDir.name}...")
                    backupDir.deleteRecursively()
                } else {
                    onProgress("No files downloaded. Restoring backup from ${backupDir.name}...")
                    backupDir.listFiles()?.forEach { file ->
                        val restored = File(workDir, file.name)
                        file.renameTo(restored)
                    }
                    backupDir.deleteRecursively()
                }
            }

            // Clean temporary files, preserving only whitelist + files needed to build aGPS_UIHH.bin
            val cleanedTemp = cleanTemporaryFiles(workDir)
            if (cleanedTemp > 0) {
                onProgress("Cleaned $cleanedTemp temporary file(s)/folder(s).")
            }
        } catch (e: Exception) {
            if (backupDir != null) {
                onProgress("Error during download. Restoring files from backup folder ${backupDir.name}...")
                backupDir.listFiles()?.forEach { file ->
                    val restored = File(workDir, file.name)
                    file.renameTo(restored)
                }
                backupDir.deleteRecursively()
            }
            throw e
        }

        workDir
    }

    /**
     * Clean temporary files from data directory.
     * Keeps only files needed to build aGPS_UIHH.bin and the specified download whitelist:
     * - cep_1week.zip
     * - cep_7days.zip
     * - cep_pak.bin
     * - epo.zip
     * - gps_uihh.bin / aGPS_UIHH.bin
     * - lle_1week.zip
     * - lto.zip
     * - gps_alm.bin, gln_alm.bin, lle_bds.lle, lle_gps.lle, lle_glo.lle, lle_gal.lle, lle_qzss.lle
     */
    fun cleanTemporaryFiles(workDir: File = getDataDirectory()): Int {
        if (!workDir.exists()) return 0

        val allowedFiles = setOf(
            "cep_1week.zip",
            "cep_7days.zip",
            "cep_pak.bin",
            "epo.zip",
            "gps_uihh.bin",
            "aGPS_UIHH.bin",
            "lle_1week.zip",
            "lto.zip",
            "gps_alm.bin",
            "gln_alm.bin",
            "lle_bds.lle",
            "lle_gps.lle",
            "lle_glo.lle",
            "lle_gal.lle",
            "lle_qzss.lle"
        )

        var deletedCount = 0
        workDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.deleteRecursively()) deletedCount++
            } else if (file.isFile) {
                if (!allowedFiles.contains(file.name)) {
                    if (file.delete()) deletedCount++
                }
            }
        }
        return deletedCount
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(targetDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Pack extracted files into aGPS_UIHH.bin
     */
    suspend fun createUihhAgpsFile(
        onProgress: (String) -> Unit
    ): GeneratedFile = withContext(Dispatchers.IO) {
        val workDir = getDataDirectory()
        if (!workDir.exists()) {
            throw IllegalStateException("No downloaded aGPS data found. Run 'Fetch aGPS' first.")
        }

        val typeMap = mapOf(
            0x05 to "gps_alm.bin",
            0x0F to "gln_alm.bin",
            0x86 to "lle_bds.lle",
            0x87 to "lle_gps.lle",
            0x88 to "lle_glo.lle",
            0x89 to "lle_gal.lle",
            0x8A to "lle_qzss.lle"
        )

        var contentBytes = byteArrayOf()

        for ((typeId, filename) in typeMap) {
            val file = File(workDir, filename)
            if (!file.exists()) {
                onProgress("Note: File not found: $filename (skipping sub-pack)")
                continue
            }

            val fileData = file.readBytes()
            onProgress("Packing $filename (${fileData.size} bytes)...")

            val crc = CRC32()
            crc.update(fileData)
            val crcVal = crc.value

            val subHeader = byteArrayOf(
                0x01.toByte(),
                typeId.toByte()
            ) + fileData.size.toLittleEndian4Bytes() + crcVal.toLittleEndian4Bytes()

            contentBytes += subHeader + fileData
        }

        if (contentBytes.isEmpty()) {
            throw IllegalStateException("No valid aGPS component files were found in target folder.")
        }

        onProgress("Building UIHH main header...")

        val contentCrc = CRC32()
        contentCrc.update(contentBytes)
        val contentCrcVal = contentCrc.value

        val headerPrefix = "UIHH".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01) +
                contentCrcVal.toLittleEndian4Bytes() +
                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00) +
                contentBytes.size.toLittleEndian4Bytes() +
                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        val finalBinData = headerPrefix + contentBytes

        val outputFile = File(workDir, "aGPS_UIHH.bin")
        FileOutputStream(outputFile).use { fos ->
            fos.write(finalBinData)
        }

        val lowerOutputFile = File(workDir, "gps_uihh.bin")
        FileOutputStream(lowerOutputFile).use { fos ->
            fos.write(finalBinData)
        }

        cleanTemporaryFiles(workDir)

        onProgress("Successfully created aGPS_UIHH.bin & gps_uihh.bin (${outputFile.length()} bytes)")

        GeneratedFile(
            name = outputFile.name,
            path = outputFile.absolutePath,
            sizeBytes = outputFile.length(),
            lastModified = outputFile.lastModified()
        )
    }

    private fun Int.toLittleEndian4Bytes(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte()
    )

    private fun Long.toLittleEndian4Bytes(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte()
    )
}
