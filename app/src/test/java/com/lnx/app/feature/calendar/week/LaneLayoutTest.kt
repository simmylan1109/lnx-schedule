package com.lnx.app.feature.calendar.week

import org.junit.Assert.assertEquals
import org.junit.Test

class LaneLayoutTest {
    private fun span(id: String, start: Float, end: Float) = TimeSpan(id, start, end)

    @Test
    fun `不重叠各占满宽`() {
        val r = LaneLayout.assign(
            listOf(span("a", 540f, 600f), span("b", 660f, 720f)),
        )
        assertEquals(listOf(0, 0), r.map { it.lane })
        assertEquals(listOf(1, 1), r.map { it.lanes })
    }

    @Test
    fun `两个重叠对半分`() {
        val r = LaneLayout.assign(
            listOf(span("a", 540f, 660f), span("b", 600f, 720f)),
        )
        assertEquals(setOf(0, 1), r.map { it.lane }.toSet())
        assertEquals(listOf(2, 2), r.map { it.lanes })
    }

    @Test
    fun `传递重叠同属一组但峰值仍为2`() {
        // a 与 b 重叠、b 与 c 重叠,但 a 与 c 不重叠:
        // 三者属同一连通分量,车道数取组内峰值并发(2),不是 3
        val r = LaneLayout.assign(
            listOf(
                span("a", 540f, 660f),
                span("b", 560f, 700f),
                span("c", 680f, 800f),
            ),
        )
        assertEquals(listOf(2, 2, 2), r.map { it.lanes })
        // 车道按左到右分配:a 占最左,c 回到最左,b 次之
        assertEquals(listOf(0, 1, 0), r.map { it.lane })
    }

    @Test
    fun `相邻贴边不算重叠`() {
        val r = LaneLayout.assign(
            listOf(span("a", 540f, 600f), span("b", 600f, 660f)),
        )
        assertEquals(listOf(1, 1), r.map { it.lanes })
        assertEquals(listOf(0, 0), r.map { it.lane })
    }

    @Test
    fun `零长事件不崩`() {
        val r = LaneLayout.assign(
            listOf(span("a", 600f, 600f), span("b", 500f, 550f)),
        )
        assertEquals(2, r.size)
    }

    @Test
    fun `结果顺序与输入一致`() {
        val r = LaneLayout.assign(
            listOf(
                span("c", 600f, 700f),
                span("a", 540f, 650f),
                span("b", 560f, 660f),
            ),
        )
        assertEquals(listOf("c", "a", "b"), r.map { it.id })
    }
}
