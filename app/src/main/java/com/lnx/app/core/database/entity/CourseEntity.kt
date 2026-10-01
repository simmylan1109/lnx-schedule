package com.lnx.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程表(v0.2 课表)。一门课一学期一条 —— 「高等数学(上)」「高等数学(下)」是两条。
 *
 * **课表的数据不进日/周/月视图**(用户拍板"独立一套"),两张视图各查各的表,
 * 所以在日历里改一条事件不会影响课表,删掉课表也不会动日历。
 */
@Entity(
    tableName = "courses",
    indices = [Index(value = ["termId"])],
)
data class CourseEntity(
    @PrimaryKey val id: String,
    val termId: String,
    val name: String,
    val teacher: String?,
    val location: String?,
    /** 0..7 色位(spec §5.4),存编号而非具体颜色,换主题时自动跟随 —— 与事件同一套规矩 */
    val colorSlot: Int,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

/**
 * 排课(v0.2 课表):一条 = 「**高等数学** 周三 第 3–4 节 第 1–16 周 教三 401」。
 *
 * 课程名/老师/地点来自 [CourseEntity],时间由 [PeriodEntity] 拼出来,这里只存
 * **在周几、第几节、第几周到第几周**。
 *
 * **连堂课(1–2 节连着上)存成两条**,周几/周次相同、`periodId` 不同,靠 `periodIndex`
 * 相邻判断要不要合并显示。没做"一条存 periodFrom/periodTo"是为了让"某一节课被调了"
 * 能只改一行 —— 连堂拆成两条之后,调掉第 2 节不影响第 1 节。
 */
@Entity(
    tableName = "course_sessions",
    indices = [Index(value = ["courseId"])],
)
data class CourseSessionEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    /** ISO 星期值 1=周一..7=周日 —— 与 `events.ruleWeekdays` 同一个约定 */
    val dayOfWeek: Int,
    val periodId: String,
    /** 第几周到第几周,均从 1 开始;只上单周时 from == to */
    val weekFrom: Int,
    val weekTo: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)
