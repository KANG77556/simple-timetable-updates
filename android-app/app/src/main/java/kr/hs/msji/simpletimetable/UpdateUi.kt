package kr.hs.msji.simpletimetable

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateAction() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("app_update", Activity.MODE_PRIVATE)
    }

    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingApk by remember { mutableStateOf<File?>(null) }

    suspend fun checkUpdate(showLatestMessage: Boolean, showErrors: Boolean) {
        if (checking) return
        checking = true
        when (val result = AppUpdateManager.check()) {
            UpdateCheckResult.Latest -> {
                info = null
                if (showLatestMessage) {
                    message = "현재 최신 버전입니다. (v${BuildConfig.VERSION_NAME})"
                }
            }
            is UpdateCheckResult.Available -> info = result.info
            is UpdateCheckResult.Error -> {
                if (showErrors) message = result.message
            }
        }
        prefs.edit().putLong("last_check_ms", System.currentTimeMillis()).apply()
        checking = false
    }

    LaunchedEffect(Unit) {
        val last = prefs.getLong("last_check_ms", 0L)
        val elapsed = System.currentTimeMillis() - last
        if (last == 0L || elapsed >= 24L * 60L * 60L * 1000L) {
            checkUpdate(showLatestMessage = false, showErrors = false)
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val host = activity
        val apk = pendingApk
        if (host != null && apk != null) {
            if (AppUpdateManager.canInstallPackages(host)) {
                AppUpdateManager.install(host, apk)
                pendingApk = null
            } else {
                message = "설치를 계속하려면 이 앱의 '알 수 없는 앱 설치' 권한을 허용해 주세요."
            }
        }
    }

    BadgedBox(
        badge = {
            if (info != null) {
                Badge { Text("NEW") }
            }
        }
    ) {
        TextButton(
            enabled = !checking,
            onClick = {
                val available = info
                if (available != null) {
                    info = available
                } else {
                    scope.launch {
                        checkUpdate(showLatestMessage = true, showErrors = true)
                    }
                }
            }
        ) {
            Text(
                when {
                    checking -> "확인 중…"
                    info != null -> "업데이트 v${info!!.versionName}"
                    else -> "업데이트"
                }
            )
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("업데이트") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("확인") }
            }
        )
    }

    info?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!checking) info = null },
            title = { Text("새 버전 v${update.versionName}") },
            text = { Text(update.notes.ifBlank { "새 버전을 설치할 수 있습니다." }) },
            confirmButton = {
                TextButton(
                    enabled = !checking,
                    onClick = {
                        val host = activity
                        if (host == null) {
                            message = "설치 화면을 열 수 없습니다."
                            info = null
                        } else {
                            scope.launch {
                                checking = true
                                AppUpdateManager.download(host, update)
                                    .onSuccess { apk ->
                                        if (AppUpdateManager.canInstallPackages(host)) {
                                            AppUpdateManager.install(host, apk)
                                        } else {
                                            pendingApk = apk
                                            installPermissionLauncher.launch(
                                                AppUpdateManager.createUnknownSourcesIntent(host)
                                            )
                                        }
                                    }
                                    .onFailure {
                                        message = it.message ?: "업데이트 다운로드에 실패했습니다."
                                    }
                                checking = false
                                info = null
                            }
                        }
                    }
                ) {
                    Text(if (checking) "다운로드 중…" else "다운로드 및 설치")
                }
            },
            dismissButton = {
                TextButton(enabled = !checking, onClick = { info = null }) {
                    Text("나중에")
                }
            }
        )
    }
}
