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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.recurrence.RuleDescription
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

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
    val locale = LocalLnxLocale.current

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
            Text(stringResource(R.string.rule_repeat), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = RuleDescription.of(rule, locale),
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
                    text = label(),
                    selected = rule.type == type,
                    onClick = { onChange(defaultRule(type, seriesStart).copy(end = rule.end)) },
                    tag = "rule_type_${type.name}",
                )
            }
        }

        when (rule.type) {
            RuleType.DAILY -> NumberStepper(
                label = stringResource(R.string.rule_interval),
                unit = stringResource(R.string.rule_unit_day),
                value = rule.interval,
                range = 1..30,
                onChange = { onChange(rule.copy(interval = it)) },
                tag = "rule_interval",
            )

            RuleType.WEEKLY -> {
                NumberStepper(
                    label = stringResource(R.string.rule_interval),
                    unit = stringResource(R.string.rule_unit_week),
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
                    locale = locale,
                )
            }

            RuleType.MONTHLY -> {
                NumberStepper(
                    label = stringResource(R.string.rule_interval),
                    unit = stringResource(R.string.rule_unit_month),
                    value = rule.interval,
                    range = 1..12,
                    onChange = { onChange(rule.copy(interval = it)) },
                    tag = "rule_interval",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipOption(
                        text = stringResource(R.string.rule_by_date),
                        selected = rule.monthlyMode != MonthlyMode.BY_NTH_WEEKDAY,
                        onClick = { onChange(rule.copy(monthlyMode = MonthlyMode.BY_MONTHDAY)) },
                        tag = "rule_month_mode_DAY",
                    )
                    ChipOption(
                        text = stringResource(R.string.rule_by_weekday),
                        selected = rule.monthlyMode == MonthlyMode.BY_NTH_WEEKDAY,
                        onClick = { onChange(rule.copy(monthlyMode = MonthlyMode.BY_NTH_WEEKDAY)) },
                        tag = "rule_month_mode_NTH",
                    )
                }
                if (rule.monthlyMode == MonthlyMode.BY_NTH_WEEKDAY) {
                    NumberStepper(
                        label = stringResource(R.string.rule_nth_prefix),
                        // 中文是"第 N 个",英文是序数"the 3rd" —— 后缀得按语言算,不能写死
                        unit = if (LnxLocale.isChinese(locale)) {
                            stringResource(R.string.rule_nth_suffix)
                        } else {
                            LnxLocale.ordinalSuffix(rule.monthlyNth ?: 1, locale)
                        },
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
                        locale = locale,
                    )
                } else {
                    // 中文读作"每月 5 日",英文读作"Monthly on day 5":
                    // 后半截的"日 / on day"挂在英文标签上(unit 留空),中文单独给一个单位词
                    NumberStepper(
                        label = stringResource(R.string.rule_type_monthly),
                        unit = stringResource(R.string.rule_day_unit),
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
                text = stringResource(R.string.rule_never),
                selected = rule.end is RuleEnd.Never,
                onClick = { onChange(rule.copy(end = RuleEnd.Never)) },
                tag = "rule_end_NEVER",
            )
            ChipOption(
                text = stringResource(R.string.rule_until),
                selected = rule.end is RuleEnd.Until,
                onClick = {
                    val current = (rule.end as? RuleEnd.Until)?.date ?: seriesStart.toLocalDate()
                    onChange(rule.copy(end = RuleEnd.Until(current)))
                },
                tag = "rule_end_UNTIL",
            )
            ChipOption(
                text = stringResource(R.string.rule_count),
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
                label = stringResource(R.string.rule_end_date),
                date = end.date,
                onPick = { onChange(rule.copy(end = RuleEnd.Until(it))) },
                tag = "rule_end_date",
            )

            is RuleEnd.Count -> NumberStepper(
                label = stringResource(R.string.rule_repeat),
                unit = stringResource(R.string.rule_times),
                value = end.times,
                range = 1..999,
                onChange = { onChange(rule.copy(end = RuleEnd.Count(it))) },
                tag = "rule_count",
            )

            is RuleEnd.Never -> Unit
        }
    }
}

/** 类型 chip:标签要现取,故存 `() -> String` 而不是 String */
private val TYPE_OPTIONS: List<Pair<RuleType, @Composable () -> String>> = listOf(
    RuleType.NONE to { stringResource(R.string.rule_type_none) },
    RuleType.DAILY to { stringResource(R.string.rule_type_daily) },
    RuleType.WEEKLY to { stringResource(R.string.rule_type_weekly) },
    RuleType.MONTHLY to { stringResource(R.string.rule_type_monthly) },
    RuleType.YEARLY to { stringResource(R.string.rule_type_yearly) },
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
    locale: Locale,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DayOfWeek.entries.forEach { day ->
            ChipOption(
                // ISO 星期号 1..7(周一=1),与规则里存的值一致
                text = LnxLocale.weekday(day.value, locale),
                selected = day in selected,
                onClick = { onToggle(day) },
                tag = "${tagPrefix}_${day.value}",
            )
        }
    }
}

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
        // 单位可能为空(如"每月 5"没有单位词),所以拼词时跳过空段,免得留悬空空格
        Text(
            listOf(label, value.toString(), unit).filter { it.isNotBlank() }.joinToString(" "),
            style = MaterialTheme.typography.bodyMedium,
        )
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
