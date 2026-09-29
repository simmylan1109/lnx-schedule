package com.lnx.app.core.notification

import com.lnx.app.core.domain.EventRepository
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 粘合层:读未来窗口的发生 → [NextReminderCalculator] 算清单 → [ReminderScheduler] 排闹钟。
 * 供三处复用:App 内保存/删除事件后、闹钟到点后链式补排、开机后重排。
 *
 * 窗口取未来 [LOOKAHEAD_DAYS] 天:够长的重复系列不会一次排爆(计算器本身也封顶 50 条),
 * 又保证"每响一次就补排下一次",链条能一直走下去。
 */
@Singleton
class ReminderPlanner @Inject constructor(
    private val repository: EventRepository,
    private val scheduler: ReminderAlarmSink,
) {
    suspend fun reschedule(now: LocalDateTime = LocalDateTime.now()) {
        val occurrences = repository
            .observeOccurrences(now, now.plusDays(LOOKAHEAD_DAYS))
            .first()
        scheduler.schedule(NextReminderCalculator.calculate(occurrences, now))
    }

    companion object {
        const val LOOKAHEAD_DAYS = 7L
    }
}
