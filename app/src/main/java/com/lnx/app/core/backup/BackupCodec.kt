package com.lnx.app.core.backup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * 解析结果(spec §3.13:导入失败时提示错误,**不改动现有数据**)。
 * 刻意不用异常:界面要按"格式不符 / 未来版本 / 文件坏了"分别给不同提示,
 * 抛异常再 catch 会把这些分支散到调用方。
 */
sealed interface BackupParse {
    data class Ok(val backup: Backup) : BackupParse

    /** 不是 lnx 备份(别的 App 的 json、随手改名的文件) */
    data object NotLnxBackup : BackupParse

    /** 认得出是 lnx 备份,但版本比当前 App 新 —— 不能瞎读,否则会丢字段 */
    data class UnsupportedVersion(val version: Int) : BackupParse

    /** 文件损坏 / 不是合法 JSON;[detail] 只进日志,不直接给用户看 */
    data class Corrupted(val detail: String) : BackupParse
}

/**
 * 备份文件的读写(spec §3.13)。纯逻辑,可在 JVM 单测里跑,不碰 Android。
 *
 * JSON 策略:
 * - `ignoreUnknownKeys`:新版本多写的字段,老版本读得进去(向前兼容);
 * - `encodeDefaults = false`:默认值不落盘,文件小一半,老版本也认得;
 * - `prettyPrint`:用户可能自己打开看,别压成一坨。
 */
@Singleton
class BackupCodec @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = true
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): BackupParse {
        val parsed = runCatching { json.decodeFromString(Backup.serializer(), text) }
            .getOrElse { return BackupParse.Corrupted(it.message ?: it::class.java.simpleName) }
        if (parsed.format != Backup.FORMAT) return BackupParse.NotLnxBackup
        if (parsed.version > Backup.FORMAT_VERSION) {
            return BackupParse.UnsupportedVersion(parsed.version)
        }
        return BackupParse.Ok(parsed)
    }

    /** spec §3.13 的文件名:`lnx-backup-YYYYMMDD.json` */
    fun fileName(date: java.time.LocalDate): String =
        "lnx-backup-%04d%02d%02d.json".format(date.year, date.monthValue, date.dayOfMonth)
}
