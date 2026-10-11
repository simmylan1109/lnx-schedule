package com.lnx.app.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnx.app.core.di.SyncStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 同步的**位置与状态**(v0.3 A4),与设置(settings)分开存:
 * 游标/镜像/上次同步时间是"同步机制的账本",不是用户偏好,不该混进设置流。
 *
 * - `cursor`:服务端权威的同步位置。**只在整次同步成功后落库**——中途失败不推进,
 *   下次从旧位置重拉,靠 LWW 幂等,顶多多下几遍已见过的行。
 * - `mirror`:本地行内容的镜像(`"entity:id" -> payload JSON 字符串`)。
 *   脏检测 = 当前库与镜像的差集,**不比时钟** —— 两台手机的时钟不齐,
 *   比时间戳会静默漏推,镜像比内容永远不会。
 * - `lastSyncAt` / `lastError`:给状态指示(A5)用的。
 */
@Singleton
class SyncStateStore @Inject constructor(
    @SyncStore private val dataStore: DataStore<Preferences>,
) {

    @Serializable
    data class SyncState(
        val cursor: Long = 0L,
        val lastSyncAt: Long = 0L,
        val lastError: String? = null,
    )

    val state: Flow<SyncState> = dataStore.data.map { prefs ->
        SyncState(
            cursor = prefs[KEY_CURSOR] ?: 0L,
            lastSyncAt = prefs[KEY_LAST_SYNC_AT] ?: 0L,
            lastError = prefs[KEY_LAST_ERROR],
        )
    }

    suspend fun cursorOnce(): Long = dataStore.data.first()[KEY_CURSOR] ?: 0L

    /** 镜像的当前内容;key = "entity:id",value = 上次同步时的 payload JSON 文本 */
    suspend fun mirrorOnce(): Map<String, String> {
        val raw = dataStore.data.first()[KEY_MIRROR] ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    /**
     * 整次同步成功的收尾:游标推进、镜像换成本次同步后的样子、错误清空。
     * **三个一起写**——半提交的账本比没有账本更难查。
     */
    suspend fun finishSync(cursor: Long, lastSyncAt: Long, mirror: Map<String, String>) {
        dataStore.edit { prefs ->
            prefs[KEY_CURSOR] = cursor
            prefs[KEY_LAST_SYNC_AT] = lastSyncAt
            prefs[KEY_MIRROR] = json.encodeToString(MirrorMapSerializer, mirror)
            prefs.remove(KEY_LAST_ERROR)
        }
    }

    suspend fun setLastError(message: String?) {
        dataStore.edit { prefs ->
            if (message == null) prefs.remove(KEY_LAST_ERROR) else prefs[KEY_LAST_ERROR] = message
        }
    }

    private companion object {
        val KEY_CURSOR = longPreferencesKey("cursor")
        val KEY_LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val KEY_LAST_ERROR = stringPreferencesKey("last_error")
        val KEY_MIRROR = stringPreferencesKey("mirror")
        val MirrorMapSerializer = MapSerializer(String.serializer(), String.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}