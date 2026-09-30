package com.lnx.app.core.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 备份文件的落盘与读取(spec §3.13)。
 *
 * 导出写进 `cacheDir/backup/` 再用 FileProvider 分享 —— 直接给系统分享面板一个
 * `file://` 路径会被 `FileUriExposedException` 拒掉,必须换成 content:// 授权 URI。
 * 放 cacheDir 而不是 filesDir:备份是一次性产物,用户存到网盘后本机留着没意义。
 */
@Singleton
class BackupFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun write(fileName: String, text: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(text)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * 读用户选的文件。用 `contentResolver.openInputStream` 而不是 File 路径 ——
     * 选文件器给的是 content:// URI,不一定有对应的真实文件(云盘/文档提供器)。
     */
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("打不开所选文件")
    }

    companion object {
        /** 与 res/xml/backup_paths.xml 里的 cache-path 对应 */
        const val AUTHORITY_SUFFIX = ".fileprovider"
        private const val DIR = "backup"
    }
}
