package com.lnx.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.backup.Backup
import com.lnx.app.core.backup.BackupCodec
import com.lnx.app.core.backup.BackupFiles
import com.lnx.app.core.backup.BackupParse
import com.lnx.app.core.backup.BackupRepository
import com.lnx.app.core.backup.ImportMode
import com.lnx.app.core.backup.ImportSummary
import com.lnx.app.core.notification.ReminderPlanner
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导入出错的原因,界面按它挑文案(不把异常原文丢给用户看) */
enum class BackupError {
    NOT_LNX_BACKUP,
    UNSUPPORTED_VERSION,

    /** 文件损坏 / 不是合法 JSON */
    CORRUPTED,

    /** 读不出文件(打不开、没权限、不是文件) */
    FILE_IO,

    /**
     * 写库失败。与 [FILE_IO] 分开:一个是"文件的问题",一个是"数据库的问题",
     * 混在一起报会让排查的人往错的方向找(终审 P2)。
     */
    DATABASE,
}

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
    private val files: BackupFiles,
    private val reminderPlanner: ReminderPlanner,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /**
     * 导出:拍快照 → 写文件。UI 拿到 URI 后弹系统分享面板。
     *
     * **读库与写文件分开兜底**:库坏了(文件损坏/磁盘满)和写不出文件是两回事,
     * 合成一个 runCatching 就会把前者报成"读不出这个文件" —— 恰好是这个 P2 要消灭的误导。
     */
    fun export(onReady: (Uri, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val name = codec.fileName(LocalDate.now())
            val snapshot = runCatching { repository.snapshot(System.currentTimeMillis()) }
                .getOrElse {
                    _state.update { s -> s.copy(busy = false, error = BackupError.DATABASE) }
                    return@launch
                }
            runCatching { files.write(name, codec.encode(snapshot)) }
                .onSuccess { uri ->
                    _state.update { it.copy(busy = false, message = BackupMessage.Exported(name)) }
                    // 只在真的落盘成功后才记"上次备份";失败不算(v0.2 补欠账 ②)
                    settingsRepository.setLastExportAt(System.currentTimeMillis())
                    onReady(uri, name)
                }
                .onFailure {
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
                    // 解析成功之后出错就是库的问题,不是文件的问题
                    val summary = runCatching {
                        repository.summarize(parsed.backup, ImportMode.MERGE)
                    }.getOrNull()
                    if (summary == null) {
                        _state.update { it.copy(busy = false, error = BackupError.DATABASE) }
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
        if (_state.value.pending == null) return
        viewModelScope.launch {
            // 警告里的 N 必须**此刻**数一遍:从选文件到点"覆盖"之间用户可能又建了几条,
            // 用选文件时的快照会吓唬错人(或吓唬不够)
            val current = runCatching { repository.currentEventCount() }
                .getOrDefault(_state.value.pending?.summary?.currentCount ?: 0)
            _state.update { it.copy(overwriteWarning = OverwriteWarning(current)) }
        }
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
                    // 走到这里说明文件已经读出来、也解析通过了,失败只可能出在写库
                    _state.update { it.copy(busy = false, error = BackupError.DATABASE) }
                }
        }
    }
}
