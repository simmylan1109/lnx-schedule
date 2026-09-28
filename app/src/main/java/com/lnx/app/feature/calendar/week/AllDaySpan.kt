package com.lnx.app.feature.calendar.week

import com.lnx.app.core.domain.model.Occurrence
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 全天/跨天事件在周视图 AllDayStrip 的条带布局(spec §3.3/§3.4)。
 * 列区间为 [startCol, endColExclusive);跨周的事件裁剪到本周;重叠条带按"最早开始、
 * 同列者优先最长"的顺序贪心分配到第一个全空闲的行。
 */
object AllDaySpan {

    data class AllDayBar(
        val occurrence: Occurrence,
        val startCol: Int,
        /** 排他 */
        val endColExclusive: Int,
        val row: Int,
    )

    private const val DAYS_PER_WEEK = 7

    fun layout(occurrences: List<Occurrence>, weekStart: LocalDate): List<AllDayBar> {
        val weekEndInclusive = weekStart.plusDays(DAYS_PER_WEEK - 1L)

        val candidates = occurrences
            .asSequence()
            .filter { it.event.allDay || it.start.toLocalDate() != it.end.toLocalDate() }
            .mapNotNull { occ ->
                // 结束为零点 = 排他存储,不占结束日当天;否则(定时跨零点)占用结束日
                val lastCovered =
                    if (occ.end.toLocalTime() == LocalTime.MIDNIGHT) {
                        occ.end.toLocalDate().minusDays(1)
                    } else {
                        occ.end.toLocalDate()
                    }
                val firstClamped = maxOf(occ.start.toLocalDate(), weekStart)
                val lastClamped = minOf(lastCovered, weekEndInclusive)
                if (lastClamped < firstClamped) return@mapNotNull null
                val startCol = ChronoUnit.DAYS.between(weekStart, firstClamped).toInt()
                val endColExclusive = (ChronoUnit.DAYS.between(weekStart, lastClamped) + 1)
                    .toInt()
                    .coerceAtMost(DAYS_PER_WEEK)
                Triple(occ, startCol, endColExclusive)
            }
            .sortedWith(
                compareBy({ it.second }, { -(it.third - it.second) }, { it.first.event.id }),
            )
            .toList()

        val rows = mutableListOf<BooleanArray>()
        val bars = mutableListOf<AllDayBar>()
        candidates.forEach { (occ, startCol, endColExclusive) ->
            var row = 0
            while (true) {
                val cells = rows.getOrNull(row) ?: BooleanArray(DAYS_PER_WEEK).also { rows.add(it) }
                val free = (startCol until endColExclusive).all { !cells[it] }
                if (free) {
                    (startCol until endColExclusive).forEach { cells[it] = true }
                    bars.add(AllDayBar(occ, startCol, endColExclusive, row))
                    break
                }
                row++
            }
        }
        return bars
    }
}
