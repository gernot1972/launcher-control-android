package com.launcher_control_android.main.ui.home

import com.launcher_control_android.Colors
import com.launcher_control_android.Drawables
import com.launcher_control_android.data.model.response.UnitModel
import com.launcher_control_android.main.common.FetchedChannelModel

data class LauncherControlUIStateModel(
    val isGatewaySetup: Boolean,
    val isUnitSetup: Boolean,
    val gatewayConnectionStatus: GatewayConnectionStatus,
    val selectedUnit: UnitModel? = null,
    val fetchedUnitModel: FetchedChannelModel? = null
) {
    fun hasGatewayAndUnitSet(): Boolean {
        return isGatewaySetup && isUnitSetup
    }

    fun launcherImageResId(): Int {
        return if (getNextAvailableChannel() != null) Drawables.ic_red_freesbi else Drawables.ic_green_freesbi
    }

    fun isGatewayNotConnected(): Boolean = gatewayConnectionStatus == GatewayConnectionStatus.NOT_CONNECTED
    fun isGatewayConnecting(): Boolean = gatewayConnectionStatus == GatewayConnectionStatus.CONNECTING
    fun isGatewayConnected(): Boolean = gatewayConnectionStatus == GatewayConnectionStatus.CONNECTED

    fun hasUnitSelected(): Boolean = selectedUnit != null
    fun hasUnitDataFetched(): Boolean = fetchedUnitModel != null


    fun isThisUnitSelected(unitNumber: Int): Boolean {
        return selectedUnit?.unitNumber == unitNumber
    }

    fun isUnitServoON(): Boolean {
        return selectedUnit?.isServoVersion == true
    }

    fun canShowFireChannel(): Boolean {
        return selectedUnit?.isChannelAdded() == true && fetchedUnitModel != null
    }

    fun getNextAvailableChannel(): Int? {
        val nextAvailableChannel = fetchedUnitModel?.getNextChannel()
        val noOfChannelAdded = selectedUnit?.noOfChannel ?: 0
        return if (nextAvailableChannel != null && nextAvailableChannel <= noOfChannelAdded) {
            nextAvailableChannel
        } else {
            null
        }
    }

    fun getUnitBackground(unitNumber: Int): Int {
        return if (fetchedUnitModel?.getUnit()?.toIntOrNull() == unitNumber && getNextAvailableChannel() != null) {
            Drawables.shape_rect_rounded_10_red
        } else if (selectedUnit?.unitNumber == unitNumber) {
            Drawables.shape_rect_rounded_10
        } else {
            Drawables.shape_rect_rounded_border_10
        }
    }

    fun getUnitTextColor(unitNumber: Int): Int {
        return if (selectedUnit?.unitNumber == unitNumber) {
            Colors.black
        } else {
            Colors.white
        }
    }
}
