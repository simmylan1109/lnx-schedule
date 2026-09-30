package com.lnx.app.core.common

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * App 语言与日期/星期文案(spec §10 国际化)。
 *
 * **纯函数,不带 Android 依赖**:界面文案走 `strings.xml`,这里管"程序生成的文字"
 * (日期、星期、重复规则描述)—— 它们的形状由语言决定,中文 `9月29日 周二` 与英文
 * `Sep 29, Tue` 连词序都不同,不是替换几个词能解决的。
 *
 * 两种语言的中文星期是 `周二`(不带"期"),java 的 `zh` locale 用 `EEEE` 会得到 `星期二`,
 * 所以中文走表、英文走 `EEE`,别混用。
 */
object LnxLocale {

    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"

    private val DOW_ZH = mapOf(
        DayOfWeek.MONDAY to "周一",
        DayOfWeek.TUESDAY to "周二",
        DayOfWeek.WEDNESDAY to "周三",
        DayOfWeek.THURSDAY to "周四",
        DayOfWeek.FRIDAY to "周五",
        DayOfWeek.SATURDAY to "周六",
        DayOfWeek.SUNDAY to "周日",
    )

    /**
     * 设置里的语言 → Locale。
     *
     * 「跟随系统」只有两种结果:系统是中文就中文,否则**按英文渲染** ——
     * v0.1 只备了中英两套资源(默认中文 + `values-en`),系统是法文时 Android 只能回落到
     * 默认那套(中文),日期再按法文格式化就成了"法文日期 + 中文界面"。统一按英文走,
     * 至少两边一致。
     */
    fun resolve(language: String): Locale = when {
        language == ZH -> Locale.SIMPLIFIED_CHINESE
        language == EN -> Locale.ENGLISH
        // 跟随系统:用无主语 when,`when(language) { 条件 -> }` 只认常量条件,不接受布尔表达式
        Locale.getDefault().language == ZH -> Locale.SIMPLIFIED_CHINESE
        else -> Locale.ENGLISH
    }

    fun isChinese(locale: Locale): Boolean = locale.language == ZH

    // —— 星期 ——

    fun weekday(date: LocalDate, locale: Locale): String = weekday(date.dayOfWeek, locale)

    fun weekday(dow: DayOfWeek, locale: Locale): String =
        if (isChinese(locale)) DOW_ZH.getValue(dow) else fmt(locale, "E", "EEE").format(dow)

    /** ISO 星期号 1..7(重复规则里存的就是它) */
    fun weekday(value: Int, locale: Locale): String =
        weekday(DayOfWeek.of(value.coerceIn(1, 7)), locale)

    // —— 日期与时间 ——

    /** 顶栏大标题:`9月 · 29日 周二` / `Sep 29, Tue` */
    fun title(date: LocalDate, locale: Locale): String = fmt(locale, "M月 · d日 E", "MMM d, EEE").format(date)

    /** 日期 + 星期,不带时间:`9月29日 周二` / `Sep 29, Tue` */
    fun dateWithWeekday(date: LocalDate, locale: Locale): String =
        fmt(locale, "M月d日 E", "MMM d, EEE").format(date)

    /** 只到日:`9月29日` / `Sep 29` */
    fun monthDay(date: LocalDate, locale: Locale): String = fmt(locale, "M月d日", "MMM d").format(date)

    /** 日期 + 时间:`9月29日 周二 14:30` / `Sep 29, Tue 2:30 PM` */
    fun dateTime(value: LocalDateTime, locale: Locale): String =
        fmt(locale, "M月d日 E HH:mm", "MMM d, EEE h:mm a").format(value)

    /** 只到分:`14:30` / `2:30 PM` */
    fun time(value: LocalTime, locale: Locale): String = fmt(locale, "HH:mm", "h:mm a").format(value)

    // formatter 构造不算免费,而这些方法在重组时会被反复调,按 (语言,格式) 缓存
    private val cache = HashMap<String, DateTimeFormatter>()

    @Synchronized
    private fun fmt(locale: Locale, zh: String, en: String): DateTimeFormatter {
        val chinese = isChinese(locale)
        val key = (if (chinese) "zh:" else "en:") + if (chinese) zh else en
        return cache.getOrPut(key) {
            DateTimeFormatter.ofPattern(if (chinese) zh else en, locale)
        }
    }
}
