package com.zhuangxiu.ledger.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.update.AppRelease
import com.zhuangxiu.ledger.update.AppUpdateRepository
import com.zhuangxiu.ledger.update.UpdateCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

enum class UpdateStage { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, VERIFYING, READY }

data class UpdateUiState(
    val stage: UpdateStage = UpdateStage.IDLE,
    val release: AppRelease? = null,
    val downloaded: Long = 0,
    val error: String? = null,
    val bundledAccess: Boolean = UpdateCredentials.bundled,
    val accessConfigured: Boolean = bundledAccess,
) {
    val busy get() = stage in setOf(UpdateStage.CHECKING, UpdateStage.DOWNLOADING, UpdateStage.VERIFYING)
}

@HiltViewModel
class UpdateViewModel @Inject constructor(private val repository: AppUpdateRepository) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state = _state.asStateFlow()
    private var downloadJob: Job? = null
    private var readyFile: File? = null
    private val installEvents = Channel<File>(Channel.BUFFERED)
    val installs = installEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                val configured = repository.accessConfigured()
                _state.update { it.copy(accessConfigured = configured) }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(message(e)) }
        }
    }

    fun check() {
        if (_state.value.busy) return
        readyFile = null
        _state.value = UpdateUiState(stage = UpdateStage.CHECKING, accessConfigured = _state.value.accessConfigured)
        viewModelScope.launch {
            try {
                val release = repository.check()
                checked(release)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.value = UpdateUiState(error = message(e), accessConfigured = _state.value.accessConfigured)
            }
        }
    }

    fun download() {
        if (_state.value.busy) return
        val release = _state.value.release ?: return
        _state.update { it.copy(stage = UpdateStage.DOWNLOADING, downloaded = 0, error = null) }
        downloadJob = viewModelScope.launch {
            try {
                readyFile = repository.download(release,
                    onProgress = { bytes -> _state.update { it.copy(downloaded = bytes) } },
                    onVerifying = { _state.update { it.copy(stage = UpdateStage.VERIFYING) } })
                _state.update { it.copy(stage = UpdateStage.READY) }
            } catch (e: CancellationException) {
                _state.update { it.copy(stage = UpdateStage.AVAILABLE, error = "下载已取消，可以重新下载") }
                throw e
            } catch (e: Exception) {
                readyFile = null
                _state.update { it.copy(stage = UpdateStage.AVAILABLE, error = message(e)) }
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    fun requestInstall() {
        if (_state.value.busy) return
        val file = readyFile ?: return
        val release = _state.value.release ?: return
        _state.update { it.copy(stage = UpdateStage.VERIFYING, error = null) }
        viewModelScope.launch {
            try {
                repository.verifyForInstall(file, release)
                _state.update { it.copy(stage = UpdateStage.READY) }
                installEvents.send(file)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                readyFile = null
                _state.update { it.copy(stage = UpdateStage.AVAILABLE, error = message(e)) }
            }
        }
    }

    fun showError(text: String) { _state.update { it.copy(error = text) } }

    fun configureAccess(token: String, onSaved: () -> Unit) {
        if (_state.value.busy) return
        readyFile = null
        _state.update { it.copy(stage = UpdateStage.CHECKING, error = null, release = null) }
        viewModelScope.launch {
            try {
                val release = repository.configureAccess(token)
                _state.update { it.copy(accessConfigured = true) }
                checked(release)
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(stage = UpdateStage.IDLE, error = message(e)) } }
        }
    }

    fun clearAccess() {
        if (_state.value.busy) return
        _state.update { it.copy(stage = UpdateStage.CHECKING, error = null) }
        viewModelScope.launch {
            try {
                repository.clearAccess()
                readyFile = null
                _state.value = UpdateUiState()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(stage = UpdateStage.IDLE, error = message(e)) } }
        }
    }

    private fun checked(release: AppRelease?) {
        _state.value = UpdateUiState(stage = if (release == null) UpdateStage.CURRENT else UpdateStage.AVAILABLE,
            release = release, accessConfigured = _state.value.accessConfigured)
    }

    private fun message(e: Exception): String = when (e) {
        is UnknownHostException -> "暂时无法连接 GitHub，请检查网络后重试"
        is SocketTimeoutException -> "连接 GitHub 超时，请稍后重试"
        is IOException -> e.message ?: "下载失败，请检查网络和剩余存储空间"
        else -> "暂时无法完成更新，请稍后重试"
    }
}
