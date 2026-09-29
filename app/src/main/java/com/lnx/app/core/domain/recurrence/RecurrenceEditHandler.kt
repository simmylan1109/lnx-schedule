package com.lnx.app.core.domain.recurrence

import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.RuleEnd
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 重复事件编辑/删除的作用范围(spec §3.7/§4.4 三选一) */
enum class EditScope { THIS_ONLY, THIS_AND_FUTURE, ALL }

/**
 * 三选一语义落库(spec §4.4):
 * - `THIS_ONLY`(仅本次):写/取消一条例外;单次例外**不改规则**(编辑器里规则区只读);
 * - `ALL`(全部):改/删母事件(删除时把它的例外一并清掉);已有的单次例外保留;
 * - `THIS_AND_FUTURE`(本次及以后):母事件在本次之前截止(结束条件取更近者,
 *   Count 统计截止日前的发生次数),剪断日起的例外清掉,改的时候再建一条新母事件。
 *
 * `edited = null` 表示删除。纯落库逻辑,不动 UI;数据库写入全部走 [EventRepository]。
 */
@Singleton
class RecurrenceEditHandler @Inject constructor(
    private val repository: EventRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun apply(
        master: Event,
        originalDate: LocalDate,
        edited: Event?,
        scope: EditScope,
    ) {
        when (scope) {
            EditScope.THIS_ONLY -> if (edited == null) {
                repository.cancelOccurrence(master.id, originalDate)
            } else {
                repository.upsertException(
                    EventException(
                        masterId = master.id,
                        originalDate = originalDate,
                        override = edited.copy(id = master.id, rule = master.rule, createdAt = master.createdAt),
                    ),
                )
            }

            EditScope.ALL -> if (edited == null) {
                repository.delete(master.id)
                repository.deleteExceptionsFor(master.id)
            } else {
                repository.save(edited.copy(id = master.id, createdAt = master.createdAt))
            }

            EditScope.THIS_AND_FUTURE -> {
                cutMaster(master, originalDate)
                repository.deleteExceptionsFrom(master.id, originalDate)
                if (edited != null) {
                    // 新母事件的 id 由调用方指定时沿用(它要把标签挂到这个新 id 上);
                    // 没指定则新生成,createdAt 留 0 由仓库盖当前时间
                    val targetId = edited.id.takeIf { it.isNotBlank() && it != master.id } ?: newId()
                    repository.save(edited.copy(id = targetId, createdAt = 0L, updatedAt = 0L))
                }
            }
        }
    }

    /**
     * 母事件截止到剪断日之前(spec §4.4)。
     * - Never → Until(前一日);
     * - Until → 取更近者;
     * - Count → 数出剪断日前有几次,改成"数满即停"(0 次时退化为 Until,因为 Count(0)
     *   在引擎里会被当成非法值而变成"永不结束",必须避开)。
     */
    private suspend fun cutMaster(master: Event, from: LocalDate) {
        val rule = master.rule
        val duration = Duration.between(master.start, master.end)
        val before = RecurrenceEngine.expand(rule, master.start, duration, master.start, from.atStartOfDay()).size
        val newEnd: RuleEnd = when (val end = rule.end) {
            is RuleEnd.Until -> RuleEnd.Until(minOf(end.date, from.minusDays(1)))
            is RuleEnd.Count -> if (before == 0) RuleEnd.Until(from.minusDays(1)) else RuleEnd.Count(before)
            is RuleEnd.Never -> RuleEnd.Until(from.minusDays(1))
        }
        repository.save(master.copy(rule = rule.copy(end = newEnd), updatedAt = 0L))
    }
}
