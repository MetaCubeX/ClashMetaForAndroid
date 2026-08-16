package com.github.kr328.clash.service.model

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.EnumMap
import java.util.TimeZone

@Serializable
enum class AutoSwitchStrategyType {
    None,
    Weekly,
}

@Serializable
enum class WeekDay(val calendarValue: Int) {
    Sunday(Calendar.SUNDAY),
    Monday(Calendar.MONDAY),
    Tuesday(Calendar.TUESDAY),
    Wednesday(Calendar.WEDNESDAY),
    Thursday(Calendar.THURSDAY),
    Friday(Calendar.FRIDAY),
    Saturday(Calendar.SATURDAY);

    companion object {
        fun fromCalendar(value: Int): WeekDay {
            return values().firstOrNull { it.calendarValue == value } ?: Sunday
        }
    }
}

@Serializable
data class DailyAutoSwitchSchedule(
    val startMinutes: Int? = null,
    val stopMinutes: Int? = null,
)

@Serializable
data class WeeklyAutoSwitchSchedule(
    val entries: Map<WeekDay, DailyAutoSwitchSchedule> = defaultEntries(),
) {
    fun get(day: WeekDay): DailyAutoSwitchSchedule {
        return entries[day] ?: DailyAutoSwitchSchedule()
    }

    fun update(
        day: WeekDay,
        transform: (DailyAutoSwitchSchedule) -> DailyAutoSwitchSchedule,
    ): WeeklyAutoSwitchSchedule {
        val map = EnumMap<WeekDay, DailyAutoSwitchSchedule>(WeekDay::class.java)
        map.putAll(entries)
        map[day] = transform(map[day] ?: DailyAutoSwitchSchedule())
        return WeeklyAutoSwitchSchedule(map)
    }

    fun next(nowMillis: Long, zone: TimeZone): ScheduledAutoSwitch? {
        var best: ScheduledAutoSwitch? = null

        for (offset in 0..7) {
            val dayCalendar = Calendar.getInstance(zone).apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val day = WeekDay.fromCalendar(dayCalendar.get(Calendar.DAY_OF_WEEK))
            val schedule = get(day)

            schedule.startMinutes?.let { minutes ->
                val triggerAt = triggerTime(dayCalendar, minutes)
                if (triggerAt > nowMillis) {
                    val candidate = ScheduledAutoSwitch(triggerAt, AutoSwitchAction.Start)
                    best = best?.takeIf { it.triggerAtMillis <= candidate.triggerAtMillis } ?: candidate
                }
            }

            schedule.stopMinutes?.let { minutes ->
                val triggerAt = triggerTime(dayCalendar, minutes)
                if (triggerAt > nowMillis) {
                    val candidate = ScheduledAutoSwitch(triggerAt, AutoSwitchAction.Stop)
                    best = best?.takeIf { it.triggerAtMillis <= candidate.triggerAtMillis } ?: candidate
                }
            }
        }

        return best
    }

    companion object {
        private fun defaultEntries(): Map<WeekDay, DailyAutoSwitchSchedule> {
            val map = EnumMap<WeekDay, DailyAutoSwitchSchedule>(WeekDay::class.java)
            WeekDay.values().forEach { map[it] = DailyAutoSwitchSchedule() }
            return map
        }

        private fun triggerTime(base: Calendar, minutes: Int): Long {
            val calendar = base.clone() as Calendar
            calendar.set(Calendar.HOUR_OF_DAY, minutes / 60)
            calendar.set(Calendar.MINUTE, minutes % 60)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
    }
}

data class ScheduledAutoSwitch(
    val triggerAtMillis: Long,
    val action: AutoSwitchAction,
)

enum class AutoSwitchAction {
    Start,
    Stop,
}
