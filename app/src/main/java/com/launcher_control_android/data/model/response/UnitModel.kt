package com.launcher_control_android.data.model.response

import java.io.Serializable

data class UnitModel(
    var unitNumber: Int,
    var noOfChannel: Int,
    var isSoundOptionInstalled: Boolean,
    var isServoVersion: Boolean,
    var selectedSound: Int,
    var selectedPressure: Int
): Serializable {
    fun testHexCode(): String? {
        return when (unitNumber) {
            1 -> "1F"
            2 -> "2F"
            3 -> "3F"
            4 -> "4F"
            else -> null
        }
    }

    fun reloadHexCode(): String? {
        return when (unitNumber) {
            1 -> "10"
            2 -> "20"
            3 -> "30"
            4 -> "40"
            else -> null
        }
    }

    fun fetchDataHexCode(): String? {
        return when(unitNumber) {
            1 -> "1E"
            2 -> "2E"
            3 -> "3E"
            4 -> "4E"
            else -> null
        }
    }

    fun unitChannelHexCode(unit: String?, channel: Int?): String? {
        val channelHex = when(channel) {
            1 -> "1"
            2 -> "2"
            3 -> "3"
            4 -> "4"
            5 -> "5"
            6 -> "6"
            7 -> "7"
            8 -> "8"
            9 -> "9"
            10 -> "a"
            11 -> "b"
            12 -> "c"
            else -> null
        } ?: return null
        return "$unit$channelHex"
    }

    fun soundHexCode(soundIndex: Int? = null): String? {
        return if (soundIndex != null) {
            when(unitNumber) {
                1 -> "A${soundIndex + 1}"
                2 -> "B${soundIndex + 1}"
                3 -> "C${soundIndex + 1}"
                4 -> "D${soundIndex + 1}"
                else -> null
            }
        } else {
            when(unitNumber) {
                1 -> "A${selectedSound + 1}"
                2 -> "B${selectedSound + 1}"
                3 -> "C${selectedSound + 1}"
                4 -> "D${selectedSound + 1}"
                else -> null
            }
        }
    }

    fun pressureHaxCode(pressureIndex: Int?): String? {
        val lastCode = when(pressureIndex) {
            0 -> "A"
            1 -> "B"
            2 -> "C"
            3 -> "D"
            4 -> "E"
            else -> "F"
        }
        return when(unitNumber) {
            1 -> "A$lastCode"
            2 -> "B$lastCode"
            3 -> "C$lastCode"
            4 -> "D$lastCode"
            else -> null
        }
    }

    fun isReloadCommand(command: String?) : Boolean {
        return command == reloadHexCode()
    }

    fun isSoundCommand(command: String?): Boolean {
        return command?.getOrNull(0) in 'A'..'D' && command?.getOrNull(1) in '1'..'6'
    }

    fun isPressureCommand(command: String?): Boolean {
        return command?.getOrNull(0) in 'A'..'D' && command?.getOrNull(1) in 'A'..'F'
    }

    fun isVisible(): Boolean {
        return noOfChannel > 0 || isSoundOptionInstalled
    }

    fun isOnlySoundInstalled(): Boolean {
        return noOfChannel == 0 && isSoundOptionInstalled
    }

    fun isChannelAdded(): Boolean {
        return noOfChannel > 0
    }
}
