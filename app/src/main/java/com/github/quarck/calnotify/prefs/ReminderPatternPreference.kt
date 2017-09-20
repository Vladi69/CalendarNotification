//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.prefs

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.ui.TimeIntervalPickerController
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.isMarshmallowOrAbove

class ReminderPatternPreference(context: Context, attrs: AttributeSet)
    : DialogPreference(context, attrs)
    , AdapterView.OnItemSelectedListener
{
    var SecondsIndex = -1
    var MinutesIndex = 0
    var HoursIndex = 1
    var DaysIndex = 2

    internal var timeValueSeconds = 0

    internal lateinit var view: View
    internal var maxIntervalMilliseconds = 0L
    internal var allowSubMinuteIntervals = false
    internal lateinit var numberPicker: NumberPicker
    internal lateinit var timeUnitsSpinners: Spinner

    init {
        dialogLayoutResource = R.layout.dialog_reminder_interval_configuration
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
        dialogIcon = null
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        allowSubMinuteIntervals = Settings(this.context).enableSubMinuteReminders

        numberPicker = view.find<NumberPicker>(R.id.numberPickerTimeInterval)
        timeUnitsSpinners = view.find<Spinner>(R.id.spinnerTimeIntervalUnit)

        if (allowSubMinuteIntervals) {
            timeUnitsSpinners.adapter =
                    ArrayAdapter(
                            view.context,
                            android.R.layout.simple_list_item_1,
                            view.context.resources.getStringArray(R.array.time_units_plurals_with_seconds)
                    )
            SecondsIndex = 0
            MinutesIndex = 1
            HoursIndex = 2
            DaysIndex = 3
        }
        else {
            timeUnitsSpinners.adapter =
                    ArrayAdapter(
                            view.context,
                            android.R.layout.simple_list_item_1,
                            view.context.resources.getStringArray(R.array.time_units_plurals)
                    )

            SecondsIndex = -1
            MinutesIndex = 0
            HoursIndex = 1
            DaysIndex = 2
        }

        timeUnitsSpinners.onItemSelectedListener = this

        timeUnitsSpinners.setSelection(MinutesIndex)

        timeUnitsSpinners.onItemSelectedListener = this

        numberPicker.minValue = 1
        numberPicker.maxValue = 100

        intervalSeconds = timeValueSeconds
    }

    override fun onClick() {
        super.onClick()
        clearFocus()
    }

    fun clearFocus() {
        numberPicker.clearFocus()
        timeUnitsSpinners.clearFocus()
    }

    override fun onDialogClosed(positiveResult: Boolean) {

        if (positiveResult) {
            clearFocus()

            timeValueSeconds = intervalSeconds

            if (timeValueSeconds == 0) {
                timeValueSeconds = 60
                val msg = context.resources.getString(R.string.invalid_reminder_interval)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }

            persistString(PreferenceUtils.formatPattern(longArrayOf(timeValueSeconds*1000L)))

            val settings = Settings(context)

            if (isMarshmallowOrAbove &&
                    timeValueSeconds * 1000L < Consts.MARSHMALLOW_MIN_REMINDER_INTERVAL_USEC &&
                    !settings.dontShowMarshmallowWarningInSettings) {

                AlertDialog.Builder(context)
                        .setMessage(context.resources.getString(R.string.reminders_not_accurate_again))
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) {
                            _, _ ->
                        }
                        .setNegativeButton(R.string.never_show_again) {
                            _, _ ->
                            Settings(context).dontShowMarshmallowWarningInSettings = true
                        }
                        .create()
                        .show()
            }
        }
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            // Restore existing state

            val strValue = this.getPersistedString("10m")

            val value = PreferenceUtils.parseSnoozePresets(strValue)

            if (value != null && value.size == 1) {
                timeValueSeconds = (value[0] / 1000).toInt()
            }
        }
        else if (defaultValue != null && defaultValue is String) {
            // Set default state from the XML attribute

            val value = PreferenceUtils.parseSnoozePresets(defaultValue)

            if (value != null && value.size == 1) {
                timeValueSeconds = (value[0] / 1000).toInt()
            }
            persistString(defaultValue)
        }
    }

    @Suppress("UseExpressionBody")
    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        val ret = a.getString(index)
        return ret ?: "10m"
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

        if (maxIntervalMilliseconds == 0L) {
            if (position == SecondsIndex) {
                numberPicker.minValue = Consts.MIN_REMINDER_INTERVAL_SECONDS
                numberPicker.maxValue = 60
            } else {
                numberPicker.minValue = 1
                numberPicker.maxValue = 100
            }
            return
        }

        val maxValue =
                when (timeUnitsSpinners.selectedItemPosition) {
                    SecondsIndex ->
                        maxIntervalMilliseconds / 1000L
                    MinutesIndex ->
                        maxIntervalMilliseconds / Consts.MINUTE_IN_MILLISECONDS
                    HoursIndex ->
                        maxIntervalMilliseconds / Consts.HOUR_IN_MILLISECONDS
                    DaysIndex ->
                        maxIntervalMilliseconds / Consts.DAY_IN_MILLISECONDS
                    else ->
                        throw Exception("Unknown time unit")
                }

        numberPicker.maxValue = Math.min(maxValue.toInt(), 100)
    }

    override fun onNothingSelected(parent: AdapterView<*>) {

    }

    private var intervalSeconds: Int
        get() {
            clearFocus()

            val number = numberPicker.value

            val multiplier =
                    when (timeUnitsSpinners.selectedItemPosition) {
                        SecondsIndex ->
                            1
                        MinutesIndex ->
                            60
                        HoursIndex ->
                            60 * 60
                        DaysIndex ->
                            24 * 60 * 60
                        else ->
                            throw Exception("Unknown time unit")
                    }

            return (number * multiplier).toInt()
        }
        set(value) {

            if (allowSubMinuteIntervals) {
                var number = value
                var units = SecondsIndex

                if ((number % 60) == 0) {
                    units = MinutesIndex
                    number /= 60 // to minutes
                }

                if ((number % 60) == 0) {
                    units = HoursIndex
                    number /= 60 // to hours
                }

                if ((number % 24) == 0) {
                    units = DaysIndex
                    number /= 24 // to days
                }

                timeUnitsSpinners.setSelection(units)
                numberPicker.value = number.toInt()

            }
            else {
                var number = value / 60 // convert to minutes
                var units = MinutesIndex

                if ((number % 60) == 0) {
                    units = HoursIndex
                    number /= 60 // to hours
                }

                if ((number % 24) == 0) {
                    units = DaysIndex
                    number /= 24 // to days
                }

                timeUnitsSpinners.setSelection(units)
                numberPicker.value = number.toInt()
            }
        }

    companion object {
        private const val LOG_TAG = "TimePickerPreference"
    }
}