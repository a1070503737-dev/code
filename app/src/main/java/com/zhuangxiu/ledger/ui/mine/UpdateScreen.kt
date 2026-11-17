package com.zhuangxiu.ledger.ui.mine

import android.content.Intent
import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.zhuangxiu.ledger.BuildConfig
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.update.UpdateSource
import java.util.Locale

@Composable
fun UpdateScreen(onBack: () -> Unit, viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showAccess by remember { mutableStateOf(false) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) viewModel.requestInstall()
        else viewModel.showError("尚未允许安装更新，安装包已保留，可再次点击“安装更新”")
    }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.installs.collect { file ->
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    installer.launch(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (_: Exception) {
                    viewModel.showError("无法打开系统安装界面，请检查安装权限后重试")
                }
            }
        }
    }

    if (!showAccess) UpdateContent(state, onBack,
        onCheck = viewModel::check,
        onDownload = viewModel::download, onCancel = viewModel::cancelDownload,
        onInstall = {
            try {
                if (context.packageManager.canRequestPackageInstalls()) viewModel.requestInstall()
                else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
            } catch (_: Exception) { viewModel.showError("请在系统设置中允许装修记账安装应用，再返回重试") }
        },
        onOpenReleases = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, UpdateSource.RELEASES_URL.toUri())) }
            catch (_: Exception) { viewModel.showError("无法打开浏览器，请稍后重试") }
        }, onConfigureAccess = { showAccess = true })

    if (showAccess) {
        GitHubAccessScreen(state, onDismiss = { showAccess = false },
            onSave = { token -> viewModel.configureAccess(token) { showAccess = false } },
            onClear = { viewModel.clearAccess(); showAccess = false },
            onCreateToken = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        "https://github.com/settings/personal-access-tokens/new".toUri()))
                } catch (_: Exception) { viewModel.showError("无法打开浏览器，请稍后重试") }
            })
    }
}

@Composable
fun UpdateContent(
    state: UpdateUiState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleases: () -> Unit,
    onConfigureAccess: () -> Unit = {},
) {
    SubPageScaffold(title = "检查更新", onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LedgerCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("让记账，越来越顺手", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            LedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val title = when (state.stage) {
                        UpdateStage.IDLE -> "检查应用更新"
                        UpdateStage.CHECKING -> "正在检查更新…"
                        UpdateStage.CURRENT -> "当前已是最新版本"
                        UpdateStage.AVAILABLE -> "新版本 ${state.release?.version} 已发布"
                        UpdateStage.DOWNLOADING -> "正在下载更新…"
                        UpdateStage.VERIFYING -> "正在校验安装包…"
                        UpdateStage.READY -> "下载完成，可以安装"
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    when (state.stage) {
                        UpdateStage.CHECKING, UpdateStage.VERIFYING -> CircularProgressIndicator(Modifier.size(28.dp))
                        UpdateStage.DOWNLOADING -> {
                            val total = state.release?.size ?: 1L
                            val progress = (state.downloaded.toFloat() / total).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("${(progress * 100).toInt()}% · ${megabytes(state.downloaded)} / ${megabytes(total)} MB", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onCancel) { Text("取消下载") }
                        }
                        UpdateStage.AVAILABLE -> {
                            Text("安装包 ${megabytes(state.release?.size ?: 0)} MB", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("下载更新") }
                        }
                        UpdateStage.READY -> {
                            Text("安装包已校验，可覆盖当前版本并保留账本。", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("安装更新") }
                        }
                        else -> Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("检查新版本") }
                    }
                    if (state.stage == UpdateStage.IDLE) Text("从 GitHub 获取正式发行版，下载后在手机上安装。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                }
            }

            state.release?.let { release ->
                LedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("更新内容 · ${release.version}", style = MaterialTheme.typography.titleMedium)
                        Text(release.notes.ifBlank { "改善使用体验，修复已知问题。" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (state.release != null && !state.busy) {
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("重新检查") }
            }
            Text("首次安装需允许本应用安装更新，并在 Android 安装界面确认。更新前建议备份账本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth()) { Text("查看 GitHub 发行版") }
            LedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GitHub 访问", style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        state.bundledAccess -> "优先访问公开发行版，无需填写令牌。私有访问使用内置授权。"
                        state.accessConfigured -> "优先访问公开发行版，私有访问使用本机配置。"
                        else -> "公开发行版无需配置即可更新。仅私有仓库访问需要只读令牌。"
                    }, style = MaterialTheme.typography.bodyMedium)
                    if (!state.bundledAccess) TextButton(onClick = onConfigureAccess, enabled = !state.busy) { Text(if (state.accessConfigured) "管理访问设置" else "设置 GitHub 访问") }
                }
            }
            Text("仅检查更新和下载安装包时连接 GitHub，账本与凭证不会上传。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GitHubAccessScreen(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onCreateToken: () -> Unit,
) {
    // Deliberately not rememberSaveable: do not put plaintext credentials in saved instance state.
    var token by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    }
    DisposableEffect(activity) {
        val alreadySecure = ((activity?.window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler { if (!state.busy) onDismiss() }
    SubPageScaffold(title = "连接私有 GitHub 仓库", onBack = { if (!state.busy) onDismiss() }) { modifier ->
            Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("创建 Fine-grained token，只选择 a1070503737-dev/code 仓库，将 Contents 权限设为 Read-only，无需写入权限。")
                TextButton(onClick = onCreateToken, enabled = !state.busy) { Text("前往 GitHub 创建只读令牌") }
                OutlinedTextField(value = token, onValueChange = { token = it }, enabled = !state.busy,
                    label = { Text("GitHub 访问令牌") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
                Text("令牌只在这台手机上加密保存，不会写入安装包、账本备份或上传到仓库。", style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.busy) CircularProgressIndicator(Modifier.size(24.dp))
                Button(onClick = { onSave(token) }, enabled = token.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("保存并检查") }
                OutlinedButton(onClick = onDismiss, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                if (state.accessConfigured) TextButton(onClick = onClear, enabled = !state.busy) { Text("移除本机访问设置") }
            }
    }
}

private fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f", bytes / 1_048_576.0)
