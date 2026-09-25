package com.launcher_control_android.helper.util

import com.launcher_control_android.Drawables

fun getVoltageImageResId(voltage: Double): Int {
    val VOLTAGE_100 = 3.8
    val VOLTAGE_75 = 3.7
    val VOLTAGE_50 = 3.6
    val VOLTAGE_25 = 3.5

    val resId = when {
        voltage >= VOLTAGE_100 -> Drawables.battery100
        voltage >= VOLTAGE_75 -> Drawables.battery75
        voltage >= VOLTAGE_50 -> Drawables.battery50
        voltage >= VOLTAGE_25 -> Drawables.battery25
        else -> Drawables.battery0
    }
    return resId
}

fun getVoltagePercentage(voltage: Double): Int {
    val VOLTAGE_100 = 3.8
    val VOLTAGE_75 = 3.7
    val VOLTAGE_50 = 3.6
    val VOLTAGE_25 = 3.5

    val percentage = when {
        voltage >= VOLTAGE_100 -> 100
        voltage >= VOLTAGE_75 -> 75
        voltage >= VOLTAGE_50 -> 50
        voltage >= VOLTAGE_25 -> 25
        else -> 0
    }
    return percentage
}