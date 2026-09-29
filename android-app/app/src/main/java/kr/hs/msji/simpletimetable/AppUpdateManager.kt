package kr.hs.msji.simpletimetable

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String
)

sealed class UpdateCheckResult {
    data object Latest : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateManager {
    private const val UPDATE_INFO_URL =
        "https://raw.githubusercontent.com/KANG77556/simple-timetable-updates/main/android-latest.json"

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(UPDATE_INFO_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            if (code !in 200..299) error("업데이트 서버 응답 오류: HTTP $code")
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(json)
            val info = UpdateInfo(
                versionCode = obj.getInt("versionCode"),
                versionName = obj.getString("versionName"),
                apkUrl = obj.getString("apkUrl"),
                sha256 = obj.optString("sha256"),
                notes = obj.optString("notes")
            )
            if (info.versionCode > BuildConfig.VERSION_CODE) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.Latest
            }
        }.getOrElse { UpdateCheckResult.Error(it.message ?: "업데이트 확인에 실패했습니다.") }
    }

    suspend fun download(activity: Activity, info: UpdateInfo): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(info.apkUrl.startsWith("https://")) { "안전하지 않은 다운로드 주소입니다." }
            val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "SCERP-${info.versionName}.apk")
            val temp = File(dir, "SCERP-${info.versionName}.apk.part")
            if (temp.exists()) temp.delete()

            val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "SCERP-Android/${BuildConfig.VERSION_NAME}")
            val code = connection.responseCode
            if (code !in 200..299) error("APK 다운로드 실패: HTTP $code")

            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output, 128 * 1024)
                }
            }

            if (info.sha256.isNotBlank()) {
                val actual = sha256(temp)
                check(actual.equals(info.sha256, ignoreCase = true)) {
                    "APK 무결성 검사에 실패했습니다."
                }
            }

            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "다운로드 파일을 확정하지 못했습니다." }
            target
        }
    }

    fun canInstallPackages(activity: Activity): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            activity.packageManager.canRequestPackageInstalls()

    fun createUnknownSourcesIntent(activity: Activity): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}")
        )

    fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
