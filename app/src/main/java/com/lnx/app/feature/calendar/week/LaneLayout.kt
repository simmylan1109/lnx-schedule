package com.lnx.app.feature.calendar.week

/**
 * 周视图中一个事件的纵向占位(单位:从当日 00:00 起的分钟数)。
 * 半开语义:两个事件在 endMinute == startMinute 处不算重叠。
 */
data class TimeSpan(val id: String, val startMinute: Float, val endMinute: Float)

/**
 * 车道分配结果:该事件占第 lane 条竖向车道(0 起),本重叠簇共 lanes 条。
 * 调用方据此算出块宽 = 列宽 / lanes,左边距 = lane × 块宽(spec §3.2 并排错开)。
 */
data class LaneSlot(val id: String, val lane: Int, val lanes: Int)

/**
 * 贪心车道分配(spec §3.2:同时段多个事件并排错开,不重叠)。
 *
 * 规则:
 * 1. 按 (start, end, id) 排序扫描,每个事件放进**最左**一条已释放的车道(车道末值 <= start);
 * 2. 事件按重叠关系传递地聚成簇(相邻事件 start < 簇内最大 end 即同簇);
 * 3. 簇的 lanes = 簇内**最大并发数**,不是簇的大小 —— 09:00–11:00 / 09:20–11:40 / 11:20–13:20
 *    这类"传递重叠"应占 2 道而不是 3 道。
 *
 * 返回顺序与输入顺序一致,便于调用方按输入渲染。
 */
object LaneLayout {
    fun assign(spans: List<TimeSpan>): List<LaneSlot> {
        if (spans.isEmpty()) return emptyList()

        val ordered = spans.sortedWith(compareBy({ it.startMinute }, { it.endMinute }, { it.id }))
        val laneEnds = mutableListOf<Float>()          // lane → 该车道最后一个事件的结束
        val laneOf = mutableMapOf<String, Int>()       // 车道必须在扫描时就定,不能事后重算
        val clusterOf = mutableMapOf<String, Int>()
        val clusterPeak = mutableListOf<Int>()          // 每簇的峰值并发
        var clusterMaxEnd = Float.NEGATIVE_INFINITY
        var current = -1

        ordered.forEach { span ->
            val start = span.startMinute
            val end = maxOf(span.endMinute, start)     // 零长/负长退化处理

            if (current == -1 || start >= clusterMaxEnd) {
                current += 1
                clusterPeak.add(0)
                clusterMaxEnd = end
            } else {
                clusterMaxEnd = maxOf(clusterMaxEnd, end)
            }
            clusterOf[span.id] = current

            val lane = laneEnds.indexOfFirst { it <= start }.let { if (it == -1) laneEnds.size else it }
            if (laneEnds.size <= lane) laneEnds.add(end) else laneEnds[lane] = end
            laneOf[span.id] = lane

            val concurrent = laneEnds.count { it > start }
            clusterPeak[current] = maxOf(clusterPeak[current], concurrent)
        }

        return spans.map { span ->
            val cluster = clusterOf.getValue(span.id)
            LaneSlot(
                id = span.id,
                lane = laneOf.getValue(span.id),
                lanes = clusterPeak[cluster],
            )
        }
    }
}
