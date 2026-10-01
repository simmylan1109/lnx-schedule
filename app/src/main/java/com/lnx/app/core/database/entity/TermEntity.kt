package com.lnx.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学期表(v0.2 课表)。课表的一切都挂在学期下面 —— 没有学期就没有"第几周"这个概念,
 * 而"第几周"恰恰是课表和日历最本质的区别:日历按日期,课表按**教学周**。
 *
 * 学期由用户手动新建并填开学日期,**不去猜校历** —— 猜错的代价比让用户多填一个日期大得多。
 */
@Entity(
    tableName = "terms",
    // 当前学期是设置项(`current_term_id`),但"按学期列课表"要按 startDate 排
    indices = [Index(value = ["startDate"])],
)
data class TermEntity(
    @PrimaryKey val id: String,
    /** 显示用,如「2026 秋」「2027 春」 */
    val name: String,
    /**
     * 第 1 周的**周一**,epochDay。
     *
     * 建学期时必须把用户填的开学日期对齐到周一(往前找),否则第 1 周只覆盖半周,
     * 而"现在是第几周"是整个课表的锚点,错一天整屏课表都会错位。
     */
    val startDate: Long,
    /** 本学期共几周(教学周,不含假期) */
    val weekCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

/**
 * 节次定义(v0.2 课表):「第 3 节 = 10:00–10:45」。
 *
 * 每学期一套,因为不同学校的节次划分不一样(有的是 45 分钟一节,有的是 40 分钟)。
 * 存"节次 → 时刻"的映射而不是把时刻抄进排课表,是为了改一次节次时间所有课一起变。
 */
@Entity(
    tableName = "periods",
    indices = [Index(value = ["termId"])],
)
data class PeriodEntity(
    @PrimaryKey val id: String,
    val termId: String,
    /**
     * 第几节,从 1 开始。
     *
     * 列名不叫 `index`:`INDEX` 是 SQLite 的关键字,建表语句要额外加引号,
     * 迁移时抄 SQL 极容易抄漏一个字符(而迁移 SQL 必须与 Room 生成的 createSql 逐字一致,
     * 差一个字符就是别人手机上打开 App 直接崩)。名字里多个词,彻底躲开这个坑。
     */
    val periodIndex: Int,
    /** 当天 00:00 起的分钟数,如 10:00 → 600 */
    val startMinute: Int,
    val endMinute: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)
