package com.launcher_control_android.main.ui.channel_list.model

import androidx.lifecycle.MutableLiveData
import com.launcher_control_android.AppConstants.App.listOfPressure
import com.launcher_control_android.AppConstants.App.listOfSound
import com.launcher_control_android.data.model.response.UnitModel
import com.launcher_control_android.helper.util.PrefUtil
import com.launcher_control_android.main.base.BaseVM
import com.launcher_control_android.main.common.FetchedChannelModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChannelListActVM @Inject constructor(private val prefs: PrefUtil) : BaseVM() {

    var selectedUnitModel = MutableLiveData<UnitModel>()
    var fetchedUnitModel: FetchedChannelModel? = null
    var fetchedUnitVoltage: String? = null
    var fetchedUnitFirmware: String? = null
    val savedBluetoothDevice = MutableLiveData(prefs.savedBluetoothDevice)
    var autoupdatePressureEnable = MutableLiveData(prefs.autoupdatePressureEnable)

    var fireChannelNo: Int = -1
    var isAutoUpdateOnPause = false
    var fetchDataFromAutoupdate: Boolean = false

    fun getPrefUtil() = prefs

    fun setVoltageResponse(voltageStr: String?) {
        val arr = voltageStr?.replace("V", "", ignoreCase = true)?.split("-fw")
        fetchedUnitVoltage = arr?.getOrNull(0)
        fetchedUnitFirmware = arr?.getOrNull(1)
    }

    fun resetFetchedUnit() {
        fireChannelNo = -1
        fetchedUnitModel = null
        fetchedUnitVoltage = null
        fetchedUnitFirmware = null
    }

    fun noOfAvailableChannel(): Int {
        var noOfAvailableChannel = 0
        for (n in 1..(selectedUnitModel.value?.noOfChannel ?: 0)) {
            if (fetchedUnitModel?.isChannelAvailable(n) == true) noOfAvailableChannel++
        }
        return noOfAvailableChannel
    }

    fun noOfAddedChannel(): Int {
        return selectedUnitModel.value?.noOfChannel ?: 0
    }

    fun getPressure(): String {
        return if (selectedUnitModel.value?.isServoVersion != true) {
            if (fetchedUnitModel?.isBarFail() == true) "Fail" else "${fetchedUnitModel?.getBar()?.toString()} Bar"
        } else "Update"
    }

    fun getBatteryPercentText(): String {
        val hex = fetchedUnitModel?.getBatteryHex() ?: 14
        return when (hex) {
            15 -> "100%"
            14 -> "FAIL"
            13 -> "100%"
            12 -> "95%"
            11 -> "85%"
            10 -> "80%"
            9  -> "75%"
            8  -> "65%"
            7  -> "60%"
            6  -> "50%"
            5  -> "40%"
            4  -> "30%"
            3  -> "20%"
            2  -> "10%"
            1  -> "3%"
            0  -> "1%"
            else -> "FAIL"
        }
    }

    fun isCompressorActive(): Boolean {
        return fetchedUnitModel?.isCompressorActive() == true
    }

    fun setSoundForSelectedUnit(index: Int) {
        selectedUnitModel.value?.selectedSound = index
        when (selectedUnitModel.value?.unitNumber) {
            1 -> {
                val unit1 = prefs.unit1Model
                unit1.selectedSound = index
                prefs.unit1Model = unit1
            }
            2 -> {
                val unit2 = prefs.unit2Model
                unit2.selectedSound = index
                prefs.unit2Model = unit2
            }
            3 -> {
                val unit3 = prefs.unit3Model
                unit3.selectedSound = index
                prefs.unit3Model = unit3
            }
            4 -> {
                val unit4 = prefs.unit4Model
                unit4.selectedSound = index
                prefs.unit4Model = unit4
            }
        }
    }

    fun setPressureForSelectedUnit(index: Int) {
        selectedUnitModel.value?.selectedPressure = index
        when (selectedUnitModel.value?.unitNumber) {
            1 -> {
                val unit1 = prefs.unit1Model
                unit1.selectedPressure = index
                prefs.unit1Model = unit1
            }
            2 -> {
                val unit2 = prefs.unit2Model
                unit2.selectedPressure = index
                prefs.unit2Model = unit2
            }
            3 -> {
                val unit3 = prefs.unit3Model
                unit3.selectedPressure = index
                prefs.unit3Model = unit3
            }
            4 -> {
                val unit4 = prefs.unit4Model
                unit4.selectedPressure = index
                prefs.unit4Model = unit4
            }
        }
    }

    fun refreshSelectedUnit() {
        when (selectedUnitModel.value?.unitNumber) {
            1 -> {
                selectedUnitModel.postValue(prefs.unit1Model)
            }
            2 -> {
                selectedUnitModel.postValue(prefs.unit2Model)
            }
            3 -> {
                selectedUnitModel.postValue(prefs.unit3Model)
            }
            4 -> {
                selectedUnitModel.postValue(prefs.unit4Model)
            }
        }
    }

    fun setPressureInBar(bar: Int?) {
        val selectedPressureIndex = listOfPressure.indexOfFirst { it.replace("bar", "").toIntOrNull() == bar }
        if (selectedPressureIndex != -1) {
            setPressureForSelectedUnit(selectedPressureIndex)
        }
    }


    fun getSoundName(soundIndex: Int): String {
        return listOfSound.getOrNull(soundIndex) ?: ""
    }

    fun getNextAvailableChannel(): Int? {
        val nextAvailableChannel = fetchedUnitModel?.getNextChannel()
        val noOfChannelAdded = selectedUnitModel.value?.noOfChannel ?: 0
        return if (nextAvailableChannel != null && nextAvailableChannel <= noOfChannelAdded) {
            nextAvailableChannel
        } else {
            null
        }
    }

    fun getUnitModel(unitNumber: Int): UnitModel? {
        return when (unitNumber) {
            1 -> prefs.unit1Model
            2 -> prefs.unit2Model
            3 -> prefs.unit3Model
            4 -> prefs.unit4Model
            else -> null
        }
    }

    fun toggleAutoupdatePressureEnable() {
        val isEnable = !(autoupdatePressureEnable.value ?: false)
        autoupdatePressureEnable.postValue(isEnable)
        prefs.autoupdatePressureEnable = isEnable
    }
}