package com.lnx.app.feature.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.recurrence.RuleDescription
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 重复规则编辑区(spec §3.7):折叠行显示中文描述,点开展开编辑。
 * 采用**内联展开**而不是对话框:参数多(chips + 步进器 + 日期),内联不与键盘/滚动打架。
 * 切换类型时按系列起点预填合理的默认值(spec §3.5 编辑页字段序中"重复"位)。
 */
@Composable
fun RuleEditorSection(
    rule: EventRule,
    seriesStart: LocalDateTime,
    onChange: (EventRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(rule.type != RuleType.NONE) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp)
                .testTag("rule_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("重复", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = RuleDescription.of(rule),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("rule_summary"),
            )
        }

        if (!expanded) return@Column

        // —— 类型 ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TYPE_OPTIONS.forEach { (type, label) ->
                ChipOption(
                    text = label,
                    selected = rule.type == type,
                    onClick = { onChange(defaultRule(type, seriesStart).copy(end = rule.end)) },
                    tag = "rule_type_${type.name}",
                )
            }
        }

        when (rule.type) {
            RuleType.DAILY -> NumberStepper(
                label = "间隔",
                unit = "天",
                value = rule.interval,
                range = 1..30,
                onChange = { onChange(rule.copy(interval = it)) },
                tag = "rule_interval",
            )

            RuleType.WEEKLY -> {
                NumberStepper(
                    label = "间隔",
                    unit = "周",
                    value = rule.interval,
                    range = 1..52,
                    onChange = { onChange(rule.copy(interval = it)) },
                    tag = "rule_interval",
                )
                WeekdayChips(
                    selected = rule.weekdays,
                    multiSelect = true,
                    onToggle = { day ->
                        val next = rule.weekdays.toMutableSet()
                        // 至少留一天:否则会存出一条"永不发生"的重复事件(引擎只能回退猜一天)
                        if (day in next && next.size == 1) return@WeekdayChips
                        if (!next.add(day)) next.remove(day)
                        onChange(rule.copy(weekdays = next))
                    },
                    tagPrefix = "rule_weekday",
                )
            }

            RuleType.MONTHLY -> {
                NumberStepper(
                    label = "间隔",
                    unit = "个月",
                    value = rule.interval,
                    range = 1..12,
                    onChange = { onChange(rule.copy(interval = it)) },
                    tag = "rule_interval",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipOption(
                        text = "按日期",
                        selected = rule.monthlyMode != MonthlyMode.BY_NTH_WEEKDAY,
                        onClick = { onChange(rule.copy(monthlyMode = MonthlyMode.BY_MONTHDAY)) },
                        tag = "rule_month_mode_DAY",
                    )
                    ChipOption(
                        text = "按星期",
                        selected = rule.monthlyMode == MonthlyMode.BY_NTH_WEEKDAY,
                        onClick = { onChange(rule.copy(monthlyMode = MonthlyMode.BY_NTH_WEEKDAY)) },
                        tag = "rule_month_mode_NTH",
                    )
                }
                if (rule.monthlyMode == MonthlyMode.BY_NTH_WEEKDAY) {
                    NumberStepper(
                        label = "第",
                        unit = "个",
                        value = rule.monthlyNth ?: 1,
                        range = 1..5,
                        onChange = { onChange(rule.copy(monthlyNth = it)) },
                        tag = "rule_nth",
                    )
                    WeekdayChips(
                        selected = setOfNotNull(rule.monthlyWeekday),
                        multiSelect = false,
                        onToggle = { day -> onChange(rule.copy(monthlyWeekday = day)) },
                        tagPrefix = "rule_nth_weekday",
                    )
                } else {
                    NumberStepper(
                        label = "每月",
                        unit = "日",
                        value = rule.monthlyDay ?: seriesStart.dayOfMonth,
                        range = 1..31,
                        onChange = { onChange(rule.copy(monthlyDay = it)) },
                        tag = "rule_monthly_day",
                    )
                }
            }

            // 每年固定 1 年(spec §3.7),无参数可调
            RuleType.YEARLY -> Unit
            RuleType.NONE -> Unit
        }

        // —— 结束条件 ——
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipOption(
                text = "永不结束",
                selected = rule.end is RuleEnd.Never,
                onClick = { onChange(rule.copy(end = RuleEnd.Never)) },
                tag = "rule_end_NEVER",
            )
            ChipOption(
                text = "到日期",
                selected = rule.end is RuleEnd.Until,
                onClick = {
                    val current = (rule.end as? RuleEnd.Until)?.date ?: seriesStart.toLocalDate()
                    onChange(rule.copy(end = RuleEnd.Until(current)))
                },
                tag = "rule_end_UNTIL",
            )
            ChipOption(
                text = "重复次数",
                selected = rule.end is RuleEnd.Count,
                onClick = {
                    val current = (rule.end as? RuleEnd.Count)?.times ?: 10
                    onChange(rule.copy(end = RuleEnd.Count(current)))
                },
                tag = "rule_end_COUNT",
            )
        }
        when (val end = rule.end) {
            is RuleEnd.Until -> DateField(
                label = "结束日期",
                date = end.date,
                onPick = { onChange(rule.copy(end = RuleEnd.Until(it))) },
                tag = "rule_end_date",
            )

            is RuleEnd.Count -> NumberStepper(
                label = "重复",
                unit = "次",
                value = end.times,
                range = 1..999,
                onChange = { onChange(rule.copy(end = RuleEnd.Count(it))) },
                tag = "rule_count",
            )

            is RuleEnd.Never -> Unit
        }
    }
}

private val TYPE_OPTIONS = listOf(
    RuleType.NONE to "不重复",
    RuleType.DAILY to "每天",
    RuleType.WEEKLY to "每周",
    RuleType.MONTHLY to "每月",
    RuleType.YEARLY to "每年",
)

/** 切换类型时的默认参数:贴着系列起点给合理值(周三的事件默认就"每周三") */
private fun defaultRule(type: RuleType, seriesStart: LocalDateTime): EventRule = EventRule(
    type = type,
    interval = 1,
    weekdays = if (type == RuleType.WEEKLY) setOf(seriesStart.dayOfWeek) else emptySet(),
    monthlyMode = if (type == RuleType.MONTHLY) MonthlyMode.BY_MONTHDAY else null,
    monthlyDay = if (type == RuleType.MONTHLY) seriesStart.dayOfMonth else null,
    monthlyNth = if (type == RuleType.MONTHLY) nthOfDay(seriesStart.dayOfMonth) else null,
    monthlyWeekday = if (type == RuleType.MONTHLY) seriesStart.dayOfWeek else null,
    end = RuleEnd.Never,
)

/** 15 号是当月第 3 个"任意日"所在的序号位;仅作默认档位提示,用户可改 */
private fun nthOfDay(dayOfMonth: Int): Int = ((dayOfMonth - 1) / 7 + 1).coerceIn(1, 5)

@Composable
private fun WeekdayChips(
    selected: Set<DayOfWeek>,
    multiSelect: Boolean,
    onToggle: (DayOfWeek) -> Unit,
    tagPrefix: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DayOfWeek.entries.forEach { day ->
            ChipOption(
                text = WEEKDAY_SHORT[day.value - 1],
                selected = day in selected,
                onClick = { onToggle(day) },
                tag = "${tagPrefix}_${day.value}",
            )
        }
    }
}

private val WEEKDAY_SHORT = listOf("一", "二", "三", "四", "五", "六", "日")

/** 数字步进器:− 与 + 各包 48dp 触达,值超界直接钳制(比置灰更好按) */
@Composable
private fun NumberStepper(
    label: String,
    unit: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label$value $unit", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        ChipOption(
            text = "−",
            selected = false,
            onClick = { onChange((value - 1).coerceIn(range)) },
            tag = "${tag}_down",
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .testTag("${tag}_value"),
        )
        ChipOption(
            text = "＋",
            selected = false,
            onClick = { onChange((value + 1).coerceIn(range)) },
            tag = "${tag}_up",
        )
    }
}
