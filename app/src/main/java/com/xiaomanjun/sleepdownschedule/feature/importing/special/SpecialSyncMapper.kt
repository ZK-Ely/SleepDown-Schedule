package com.xiaomanjun.sleepdownschedule.feature.importing.special

import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 教务 kbList → SleepDown ImportDraft 适配。
 *
 * 教务返回的是"一次授课"记录（某周·某天·某节段·某教室），这里按
 * 课程名+教师+地点+星期+节段 聚合成周期课程，周次取并集；
 * 节次时间直接采用教务返回的 qssj/jssj（缺失节次用默认作息补齐），
 * 并由 skrq 反推第一周周一作为 termStartDate，供自动计算当前周使用。
 */
object SpecialSyncMapper {

    private val DateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun buildDraft(
        items: List<SpecialSyncCourseItem>,
        scheduleId: Int
    ): ImportDraft {
        val usable = items.filter { it.kcmc.isNotBlank() && it.zc in 1..30 && it.xq in 1..7 }
        require(usable.isNotEmpty()) { "没有可导入的课程数据" }

        data class GroupKey(
            val name: String,
            val teacher: String,
            val location: String,
            val weekday: Int,
            val sectionStart: Int,
            val sectionEnd: Int
        )

        val groups = usable.groupBy {
            GroupKey(
                name = it.kcmc.trim(),
                teacher = it.teaxms.trim(),
                location = (it.jxcdmc.ifBlank { it.szxqmc }).trim(),
                weekday = it.xq,
                sectionStart = it.sectionStart,
                sectionEnd = it.sectionEnd
            )
        }
        val courses = groups.map { (key, list) ->
            CourseEntity(
                name = key.name,
                teacher = key.teacher.ifBlank { null },
                location = key.location.ifBlank { null },
                weekday = key.weekday,
                periods = (key.sectionStart..key.sectionEnd).toList(),
                weeks = list.map { it.zc }.distinct().sorted(),
                weekParity = WeekParity.ALL,
                note = list.first().sknrjj.trim().ifBlank { null },
                scheduleId = scheduleId
            )
        }

        val totalWeeks = usable.maxOf { it.zc }.coerceAtLeast(20)
        val maxSection = usable.maxOf { it.sectionEnd }.coerceIn(1, 20)

        // 节次时间：优先教务真实时间（统一规范化为 HH:mm，教务可能返回带秒的 "08:30:00"）
        val observed = sortedMapOf<Int, Pair<String, String>>()
        usable.sortedBy { it.sectionStart }.forEach {
            if (it.sectionStart !in observed) {
                val start = normalizeHhmm(it.qssj)
                val end = normalizeHhmm(it.jssj)
                if (start != null && end != null) observed[it.sectionStart] = start to end
            }
        }
        val periods = (1..maxSection).map { index ->
            val time = observed[index]
            if (time != null && time.first.contains(':') && time.second.contains(':')) {
                PeriodEntity(index, time.first, time.second, scheduleId)
            } else {
                val fallback = fallbackPeriodTime(observed, index)
                PeriodEntity(index, fallback.first, fallback.second, scheduleId)
            }
        }

        val termStartDate = detectTermStartDate(usable)
        val config = defaultConfig(scheduleId).copy(
            totalWeeks = totalWeeks,
            currentWeek = 1,
            termStartDate = termStartDate,
            autoCurrentWeek = termStartDate != null
        )

        return ImportDraft(
            config = config,
            periods = periods,
            courses = courses
        )
    }

    /** "08:30:00" / "8:30" → "08:30"；非法返回 null */
    private fun normalizeHhmm(raw: String): String? {
        val m = Regex("(\\d{1,2}):(\\d{2})").find(raw.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return null
        return "%02d:%02d".format(h, min)
    }

    /** 缺失节次：优先用上一节结束时间推 45 分钟制；否则用项目默认作息 */
    private fun fallbackPeriodTime(
        observed: Map<Int, Pair<String, String>>,
        index: Int
    ): Pair<String, String> {
        for (probe in index - 1 downTo 1) {
            val prev = observed[probe]
            if (prev != null) {
                val start = runCatching {
                    LocalTime.parse(prev.second, DateTimeFormatter.ofPattern("HH:mm")).plusMinutes(10)
                }.getOrNull()
                if (start != null) {
                    val end = start.plusMinutes(45)
                    val fmt = DateTimeFormatter.ofPattern("HH:mm")
                    return start.format(fmt) to end.format(fmt)
                }
                break
            }
        }
        val defaults = DefaultSectionTimes.getOrNull(index - 1) ?: ("08:00" to "08:45")
        return defaults
    }

    /** 找 zc==1 的记录，其 skrq 往前推到周一即为第一周周一 */
    private fun detectTermStartDate(items: List<SpecialSyncCourseItem>): String? {
        val anchor = items.filter { it.zc == 1 }.minByOrNull { it.skrq } ?: return null
        val date = runCatching { LocalDate.parse(anchor.skrq, DateFormat) }.getOrNull() ?: return null
        val monday = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.format(DateFormat)
    }

    // 与 SleepDown 默认作息（defaultPeriods）保持一致的兜底时间
    private val DefaultSectionTimes = listOf(
        "08:00" to "08:45", "08:55" to "09:40",
        "10:00" to "10:45", "10:55" to "11:40",
        "14:00" to "14:45", "14:55" to "15:40",
        "16:00" to "16:45", "16:55" to "17:40",
        "19:00" to "19:45", "19:55" to "20:40",
        "20:50" to "21:35", "21:45" to "22:30"
    )
}
