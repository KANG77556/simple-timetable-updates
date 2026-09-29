package kr.hs.msji.simpletimetable

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun UpdateAction() {
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    TextButton(
        enabled = !checking,
        onClick = {
            scope.launch {
                checking = true
                when (val result = AppUpdateManager.check()) {
                    UpdateCheckResult.Latest ->
                        message = "현재 최신 버전입니다. (v${BuildConfig.VERSION_NAME})"
                    is UpdateCheckResult.Available ->
                        info = result.info
                    is UpdateCheckResult.Error ->
                        message = result.message
                }
                checking = false
            }
        }
    ) {
        Text(if (checking) "확인 중…" else "업데이트")
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
            text = {
                Text(
                    if (update.notes.isBlank()) "새 버전을 설치할 수 있습니다."
                    else update.notes
                )
            },
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
                                val result = AppUpdateManager.download(host, update)
                                result.onSuccess { apk ->
                                    AppUpdateManager.install(host, apk)
                                }.onFailure {
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
                TextButton(
                    enabled = !checking,
                    onClick = { info = null }
                ) { Text("나중에") }
            }
        )
    }
}
