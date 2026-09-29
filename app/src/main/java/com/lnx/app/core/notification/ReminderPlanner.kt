package com.lnx.app.core.notification

import com.lnx.app.core.domain.EventRepository
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 粘合层:读未来窗口的发生 → [NextReminderCalculator] 算清单 → [ReminderScheduler] 排闹钟。
 * 供五处复用:App 进程启动、事件保存、事件删除、闹钟到点后链式补排、开机/升级/改时区。
 *
 * **必须串行化**([mutex]):`ReminderScheduler.schedule` 是"先全量取消、再按新清单重排",
 * 不是原子操作。五个入口可以并发进入(典型:冷启动重排读到旧库的同时,用户保存了事件)。
 * 坏交错是 A 的 cancelAll 落在 B 排完之后,把 B 刚排的提醒全清掉,新事件的提醒就此丢失,
 * 要等下次冷启动才自愈。
 *
 * **窗口不能是链子的终点**:重排只看未来 [LOOKAHEAD_DAYS] 天,若这之后的提醒排不上,
 * 而队列又已排空,就没有任何闹钟在跑,链条自断(今天的事件提醒会响,十天后的差旅永远不响)。
 * 所以窗口末尾还排一个"续排"闹钟,到点只触发重排不弹通知,直到把未来的提醒都排完为止。
 */
@Singleton
class ReminderPlanner @Inject constructor(
    private val repository: EventRepository,
    private val scheduler: ReminderAlarmSink,
) {
    private val mutex = Mutex()

    suspend fun reschedule(now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        val windowEnd = now.plusDays(LOOKAHEAD_DAYS)
        val occurrences = repository.observeOccurrences(now, windowEnd).first()
        val reminders = NextReminderCalculator.calculate(occurrences, now)
        // 续排闹钟**无条件**排到窗口末端:这是链条不断的关键。
        // 只在"窗口内有提醒"时才排是错的 —— 窗口内一条提醒都没有、而 30 天外有一条
        // (建了个 40 天后的差旅),清单为空 → 不排续排 → 没有任何闹钟在跑 → 那条提醒永远排不上。
        // 代价是每 30 天多一次空唤醒(无事件时也只是查一下库就返回),可接受。
        scheduler.schedule(reminders, windowEnd)
    }

    companion object {
        const val LOOKAHEAD_DAYS = 30L
    }
}
