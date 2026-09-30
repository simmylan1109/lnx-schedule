package com.lnx.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.backup.Backup
import com.lnx.app.core.backup.BackupCodec
import com.lnx.app.core.backup.BackupFileStore
import com.lnx.app.core.backup.BackupParse
import com.lnx.app.core.backup.BackupRepository
import com.lnx.app.core.backup.ImportMode
import com.lnx.app.core.backup.ImportSummary
import com.lnx.app.core.notification.ReminderPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导入出错的原因,界面按它挑文案(不把异常原文丢给用户看) */
enum class BackupError { NOT_LNX_BACKUP, UNSUPPORTED_VERSION, CORRUPTED, FILE_IO }

/** 覆盖导入前的二次警告(spec §3.13:覆盖模式需二次警告) */
data class OverwriteWarning(val currentCount: Int)

/** 待确认的导入:摘要 + 真正要落库的内容 */
data class PendingImport(val backup: Backup, val summary: ImportSummary)

sealed interface BackupMessage {
    data class Exported(val fileName: String) : BackupMessage
    data class Imported(
        val mode: ImportMode,
        val added: Int,
        val skipped: Int,
        val replaced: Int,
    ) : BackupMessage
}

data class BackupUiState(
    val busy: Boolean = false,
    /**
     * 已解析出内容、等着用户选合并还是覆盖。**内容一直留着**,直到真的落库或用户取消 ——
     * 覆盖确认弹在它之上,点"返回"要能退回摘要而不是丢掉文件。
     */
    val pending: PendingImport? = null,
    val overwriteWarning: OverwriteWarning? = null,
    val error: BackupError? = null,
    /** 一次性的结果反馈(导出成功 / 导入完成),界面弹完即清 */
    val message: BackupMessage? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    private val codec: BackupCodec,
    private val files: BackupFileStore,
    private val reminderPlanner: ReminderPlanner,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** 导出:拍快照 → 写文件。UI 拿到 URI 后弹系统分享面板。 */
    fun export(onReady: (Uri, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                val name = codec.fileName(LocalDate.now())
                val uri = files.write(name, codec.encode(repository.snapshot(System.currentTimeMillis())))
                name to uri
            }.onSuccess { (name, uri) ->
                _state.update { it.copy(busy = false, message = BackupMessage.Exported(name)) }
                onReady(uri, name)
            }.onFailure {
                _state.update { it.copy(busy = false, error = BackupError.FILE_IO) }
            }
        }
    }

    /**
     * 选完文件后的第一段:解析 + 算摘要,**不落库**(spec §3.13「点击确认才执行」)。
     * 解析失败一律报错且不动现有数据。
     */
    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val text = runCatching { files.read(uri) }.getOrNull()
            if (text == null) {
                _state.update { it.copy(busy = false, error = BackupError.FILE_IO) }
                return@launch
            }
            when (val parsed = codec.decode(text)) {
                is BackupParse.Corrupted ->
                    _state.update { it.copy(busy = false, error = BackupError.CORRUPTED) }
                BackupParse.NotLnxBackup ->
                    _state.update { it.copy(busy = false, error = BackupError.NOT_LNX_BACKUP) }
                is BackupParse.UnsupportedVersion ->
                    _state.update { it.copy(busy = false, error = BackupError.UNSUPPORTED_VERSION) }
                is BackupParse.Ok -> {
                    val summary = runCatching {
                        repository.summarize(parsed.backup, ImportMode.MERGE)
                    }.getOrNull()
                    if (summary == null) {
                        _state.update { it.copy(busy = false, error = BackupError.FILE_IO) }
                    } else {
                        _state.update {
                            it.copy(busy = false, pending = PendingImport(parsed.backup, summary))
                        }
                    }
                }
            }
        }
    }

    fun chooseMerge() {
        val backup = _state.value.pending?.backup ?: return
        apply(backup, ImportMode.MERGE)
    }

    /** 覆盖不是一步到位:先弹二次警告,确认后才真删 */
    fun chooseOverwrite() {
        val summary = _state.value.pending?.summary ?: return
        _state.update { it.copy(overwriteWarning = OverwriteWarning(summary.currentCount)) }
    }

    fun confirmOverwrite() {
        val backup = _state.value.pending?.backup ?: return
        apply(backup, ImportMode.OVERWRITE)
    }

    /** 二次警告上按返回:退回摘要那一步,内容还留着,不用重新选文件 */
    fun cancelOverwrite() {
        _state.update { it.copy(overwriteWarning = null) }
    }

    fun dismissPending() {
        _state.update { it.copy(pending = null, overwriteWarning = null) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun apply(backup: Backup, mode: ImportMode) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, pending = null, overwriteWarning = null, error = null) }
            runCatching { repository.import(backup, mode) }
                .onSuccess { result ->
                    // 导入改了事件集合,已排的闹钟得跟着重排(spec §3.8)
                    reminderPlanner.reschedule()
                    _state.update {
                        it.copy(
                            busy = false,
                            message = BackupMessage.Imported(
                                mode = mode,
                                added = result.eventsAdded,
                                skipped = result.eventsSkipped,
                                replaced = result.eventsReplaced,
                            ),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(busy = false, error = BackupError.FILE_IO) }
                }
        }
    }
}
