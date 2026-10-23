package com.github.kr328.clash.design

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import android.view.View
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.databinding.PreferenceCategoryBinding
import com.github.kr328.clash.design.preference.ClickablePreference
import com.github.kr328.clash.design.preference.OnChangedListener
import com.github.kr328.clash.design.preference.Preference
import com.github.kr328.clash.design.preference.PreferenceScreen
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.selectableList
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.AutoSwitchReceiver
import com.github.kr328.clash.service.model.AutoSwitchStrategyType
import com.github.kr328.clash.service.model.DailyAutoSwitchSchedule
import com.github.kr328.clash.service.model.WeekDay
import com.github.kr328.clash.service.model.WeeklyAutoSwitchSchedule
import com.github.kr328.clash.service.store.ServiceStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.EnumMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AutoSwitchSettingsDesign(
    context: Context,
    private val serviceStore: ServiceStore,
) : Design<Unit>(context) {
    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private var currentStrategy: AutoSwitchStrategyType = AutoSwitchStrategyType.None
    private var weeklySchedule: WeeklyAutoSwitchSchedule = WeeklyAutoSwitchSchedule()

    private val dayPreferences = EnumMap<WeekDay, ClickablePreference>(WeekDay::class.java)
    private lateinit var weeklyHeader: Preference

    init {
        binding.surface = surface
        binding.activityBarLayout.applyFrom(context)
        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            category(R.string.auto_switch_strategy_category)

            selectableList(
                value = serviceStore::autoSwitchStrategy,
                values = AutoSwitchStrategyType.values(),
                valuesText = arrayOf(
                    R.string.auto_switch_strategy_none,
                    R.string.auto_switch_strategy_weekly,
                ),
                title = R.string.auto_switch_strategy_title,
                icon = R.drawable.ic_baseline_schedule,
            ) {
                listener = OnChangedListener {
                    refreshStrategy()
                }
            }

            weeklyHeader = addCategory(R.string.auto_switch_weekly_category)

            WeekDay.values().forEach { day ->
                val preference = clickable(
                    title = R.string.auto_switch_day_placeholder,
                ) {
                    launch(Dispatchers.Main) {
                        showDayOptions(day)
                    }
                }

                preference.title = day.displayName(context)
                dayPreferences[day] = preference
            }
        }

        binding.content.addView(screen.root)

        launch(Dispatchers.Main) {
            val (strategy, schedule) = withContext(Dispatchers.IO) {
                serviceStore.autoSwitchStrategy to serviceStore.autoSwitchWeeklySchedule
            }

            currentStrategy = strategy
            weeklySchedule = schedule

            WeekDay.values().forEach(::updateDaySummary)
            updateStrategyVisibility()
        }
    }

    private fun refreshStrategy() {
        launch(Dispatchers.Main) {
            val strategy = withContext(Dispatchers.IO) {
                serviceStore.autoSwitchStrategy.also {
                    AutoSwitchReceiver.requestReschedule(context)
                }
            }

            currentStrategy = strategy
            updateStrategyVisibility()
        }
    }

    private fun updateStrategyVisibility() {
        val visible = currentStrategy == AutoSwitchStrategyType.Weekly
        val visibility = if (visible) View.VISIBLE else View.GONE

        weeklyHeader.view.visibility = visibility
        dayPreferences.values.forEach { preference ->
            preference.view.visibility = visibility
        }
    }

    private fun updateDaySummary(day: WeekDay) {
        val preference = dayPreferences[day] ?: return
        val schedule = weeklySchedule.get(day)
        val startText = formatTime(schedule.startMinutes)
        val stopText = formatTime(schedule.stopMinutes)

        preference.summary = context.getString(
            R.string.auto_switch_day_summary,
            startText,
            stopText,
        )
    }

    private suspend fun requestTime(initial: Int?): Int? = suspendCancellableCoroutine { cont ->
        val is24Hour = DateFormat.is24HourFormat(context)
        val initialHour = initial?.div(60) ?: 0
        val initialMinute = initial?.rem(60) ?: 0
        var resumed = false

        val dialog = TimePickerDialog(
            context,
            { _, hour, minute ->
                if (!resumed && cont.isActive) {
                    resumed = true
                    cont.resume(hour * 60 + minute)
                }
            },
            initialHour,
            initialMinute,
            is24Hour,
        )

        dialog.setOnCancelListener {
            if (!resumed && cont.isActive) {
                resumed = true
                cont.resume(null)
            }
        }
        dialog.setOnDismissListener {
            if (!resumed && cont.isActive) {
                resumed = true
                cont.resume(null)
            }
        }
        dialog.setButton(TimePickerDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel)) { _, _ ->
            if (!resumed && cont.isActive) {
                resumed = true
                cont.resume(null)
            }
        }

        dialog.show()

        cont.invokeOnCancellation {
            dialog.dismiss()
        }
    }

    private fun showDayOptions(day: WeekDay) {
        val schedule = weeklySchedule.get(day)

        val items = arrayOf(
            context.getString(R.string.auto_switch_set_start_time),
            context.getString(R.string.auto_switch_clear_start_time),
            context.getString(R.string.auto_switch_set_stop_time),
            context.getString(R.string.auto_switch_clear_stop_time),
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(day.displayName(context))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> launch(Dispatchers.Main) {
                        val minutes = requestTime(schedule.startMinutes)
                        if (minutes != null) {
                            updateSchedule(day) { it.copy(startMinutes = minutes) }
                        }
                    }

                    1 -> updateSchedule(day) { it.copy(startMinutes = null) }

                    2 -> launch(Dispatchers.Main) {
                        val minutes = requestTime(schedule.stopMinutes)
                        if (minutes != null) {
                            updateSchedule(day) { it.copy(stopMinutes = minutes) }
                        }
                    }

                    3 -> updateSchedule(day) { it.copy(stopMinutes = null) }
                }
            }
            .show()
    }

    private fun updateSchedule(
        day: WeekDay,
        transform: (DailyAutoSwitchSchedule) -> DailyAutoSwitchSchedule,
    ) {
        launch(Dispatchers.Main) {
            val updated = withContext(Dispatchers.IO) {
                val schedule = serviceStore.autoSwitchWeeklySchedule
                val newSchedule = schedule.update(day) { transform(it) }
                serviceStore.autoSwitchWeeklySchedule = newSchedule
                AutoSwitchReceiver.requestReschedule(context)
                newSchedule
            }

            weeklySchedule = updated
            updateDaySummary(day)
        }
    }

    private fun formatTime(minutes: Int?): String {
        if (minutes == null) {
            return context.getString(R.string.auto_switch_time_not_set)
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return DateFormat.getTimeFormat(context).format(calendar.time)
    }

    private fun WeekDay.displayName(context: Context): String {
        val names = DateFormatSymbols.getInstance().weekdays
        return names.getOrNull(calendarValue) ?: name
    }

    private fun PreferenceScreen.addCategory(text: Int): Preference {
        val binding = PreferenceCategoryBinding.inflate(context.layoutInflater, root, false)
        binding.textView.text = context.getString(text)

        val preference = object : Preference {
            override val view: View
                get() = binding.root
        }

        addElement(preference)
        return preference
    }
}
