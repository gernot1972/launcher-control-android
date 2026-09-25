package com.launcher_control_android.main.ui.home.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.graphics.Color
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.databinding.ObservableField
import androidx.lifecycle.lifecycleScope
import com.launcher_control_android.AppConstants
import com.launcher_control_android.AppConstants.Command.listOfFetchDataCommand
import com.launcher_control_android.AppConstants.Command.listOfTestCommand
import com.launcher_control_android.Layouts
import com.launcher_control_android.R
import com.launcher_control_android.Strings
import com.launcher_control_android.data.model.response.UnitModel
import com.launcher_control_android.databinding.ActLauncherControlBinding
import com.launcher_control_android.helper.bluetooth.communication.BluetoothCommunicationAct
import com.launcher_control_android.helper.util.animateHorizontalFlip
import com.launcher_control_android.helper.util.animateRotate
import com.launcher_control_android.helper.util.animateWave
import com.launcher_control_android.helper.util.getVoltageImageResId
import com.launcher_control_android.helper.util.logE
import com.launcher_control_android.helper.util.startActivity
import com.launcher_control_android.helper.util.startActivityForResult
import com.launcher_control_android.helper.util.triggerHaptic
import com.launcher_control_android.main.common.ApiRenderState
import com.launcher_control_android.main.common.FetchedChannelModel
import com.launcher_control_android.main.ui.channel_list.view.ChannelListAct
import com.launcher_control_android.main.ui.connection_config.view.ConnectionConfigAct
import com.launcher_control_android.main.ui.home.model.LauncherControlVM
import com.launcher_control_android.main.ui.main_configuration.view.MainConfigurationAct
import com.launcher_control_android.main.ui.unit_detail.view.SoundOptionsBsd
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@AndroidEntryPoint
class LauncherControlAct :
    BluetoothCommunicationAct<ActLauncherControlBinding, LauncherControlVM>(Layouts.act_launcher_control) {

    private val showSecondaryProgress = ObservableField(false)
    private var tvConnectingBlinkAnimator: ObjectAnimator? = null
    private var reloadBtnBlinkAnimator: ObjectAnimator? = null
    private var updateBtnBlinkAnimator: ObjectAnimator? = null
    private var animatorSet: AnimatorSet? = null

    private var showGatewayInfoJob: Job? = null
    private var stayArmedTimerJob: Job? = null
    private var autoupdateJob: Job? = null

    override val vm: LauncherControlVM by viewModels()

    override val hasProgress: Boolean = false

    override fun init() {
        binding.showSecondaryProgress = showSecondaryProgress
        deviceAddress = prefs.savedBluetoothDevice?.address ?: ""
        setListener()
        setObserver()
        setupBlinkAnimation()
        connectGateway()

        /**
         * Set minimum autoupdatePressureDelay to 5 if in previous app version it set as less then 5 because in previous version minimum value is 3
         */
        if (prefs.autoupdatePressureDelay < 5) {
            prefs.autoupdatePressureDelay = 5
        }
    }

    private fun setListener() {
        binding.btnSound.setOnLongClickListener {
            it.triggerHaptic()
            showDialogFrag(
                SoundOptionsBsd.newInstance(vm.uiState.value?.selectedUnit?.selectedSound ?: 0) {
                    vm.setSoundForSelectedUnit(it)
                },
            )
            true
        }
        binding.btnUpdate.setOnLongClickListener {
            it.triggerHaptic()
            if (vm.uiState.value?.selectedUnit?.isServoVersion != true) {
                showDialogFrag(
                    PressureOptionsBsd.newInstance(vm.uiState.value?.selectedUnit?.selectedPressure ?: 0) {
                        vm.setPressureForSelectedUnit(it)
                        vm.uiState.value?.selectedUnit?.pressureHaxCode(it)?.let { hexCode ->
                            sendCommand(hexCode)
                        }
                    },
                )
            }
            true
        }

        binding.ivLauncher.setOnLongClickListener {
            it.triggerHaptic()
            if (vm.uiState.value?.isGatewayConnected() == true && vm.uiState.value?.hasUnitDataFetched() != true && vm.hasStayArmedActive()) {
                bluetoothService?.disconnectBluetoothDevice()
                setGatewayDisconnected()
            } else {
                if (vm.hasStayArmedActive()) {
                    fireSoundAndChannel(vm.uiState.value?.selectedUnit?.isChannelAdded() == true)
                } else {
                    vm.setUnitDisarmed(false)
                }
            }
            true
        }

        binding.tv1.setOnLongClickListener {
            it.triggerHaptic()
            handleLongUnitClick(1, prefs.unit1Model)
            true
        }

        binding.tv2.setOnLongClickListener {
            it.triggerHaptic()
            handleLongUnitClick(2, prefs.unit2Model)
            true
        }

        binding.tv3.setOnLongClickListener {
            it.triggerHaptic()
            handleLongUnitClick(3, prefs.unit3Model)
            true
        }

        binding.tv4.setOnLongClickListener {
            it.triggerHaptic()
            handleLongUnitClick(4, prefs.unit4Model)
            true
        }
    }

    private fun setObserver() {
        vm.uiState.observe(this@LauncherControlAct) { state ->
            if (vm.uiState.value?.fetchedUnitModel != null) {
                if (state.getNextAvailableChannel() != null) {
                    cancelUpdateBtnBlinkAnimation()
                    cancelReloadBtnBlinkAnimator()
                    startBlinkAnimation()
                } else {
                    stayArmedTimerJob?.cancel()
                    cancelUpdateBtnBlinkAnimation()
                    stopBlinkAnimation()
                    startReloadBtnAnimation()
                }

                if (state.selectedUnit?.isChannelAdded() == true) {
                    autoupdateJob?.cancel()
                    if (prefs.autoupdatePressureEnable) {
                        autoupdateJob = lifecycleScope.launch {
                            delay((prefs.autoupdatePressureDelay * 1000L))
                            if (vm.hasStayArmedActive() &&
                                !vm.isAutoUpdateOnPause &&
                                vm.uiState.value?.hasUnitSelected() == true &&
                                vm.uiState.value?.selectedUnit?.isServoVersion != true &&
                                vm.uiState.value?.selectedUnit?.isChannelAdded() == true &&
                                vm.uiState.value?.getNextAvailableChannel() != null) {
                                fetchUnitData(vm.uiState.value?.selectedUnit, true)
                            }
                        }
                    }
                } else {
                    autoupdateJob?.cancel()
                }

                if (state.isGatewayConnecting()) {
                    startConnectingBlinkAnimation()
                } else {
                    cancelConnectingBlinkAnimation()
                }
            }
            binding.invalidateAll()
        }
        /*vm.fetchedUnitModel.observe(this@LauncherControlAct){
            if (it != null) {
                showUnitDataFetchedUI()
                if (vm.selectedUnitModel.value?.isOnlySoundInstalled() != true) {
                    autoupdateJob?.cancel()
                    if (prefs.autoupdatePressureEnable && vm.stayArmedActive) {
                        autoupdateJob = lifecycleScope.launch {
                            delay((prefs.autoupdatePressureDelay * 1000L))
                            fetchUnitData(vm.selectedUnitModel.value, true)
                        }
                    }
                }
            }
        }*/
        /*lifecycleScope.launch {
            vm.uiState().collect {
                when (it) {
                    is LauncherControlUIState.NoGatewayOrUnitSetup -> {
                        binding.llSetupMessages.isVisible = !it.isGatewaySetup || !it.isUnitSetup
                        binding.tvSaveGatewayInfo.isVisible = !it.isGatewaySetup
                        binding.tvSetUpUnitInfo.isVisible = !it.isUnitSetup
                        binding.toolbar.showSelectDevice = true
                        binding.toolbar.isGatewaySetup = it.isGatewaySetup

                        binding.tv1.isClickable = false
                        binding.tv2.isClickable = false
                        binding.tv3.isClickable = false
                        binding.tv4.isClickable = false

                        binding.tvNoChannelAvailable.isVisible = false
                        binding.tvNoUnitSelected.isVisible = false
                    }
                    is LauncherControlUIState.GatewayAndUnitSetup -> {
                        binding.llSetupMessages.isVisible = false
                        binding.tvSaveGatewayInfo.isVisible = false
                        binding.tvSetUpUnitInfo.isVisible = false
                        binding.ivLauncher.isVisible = true
                        binding.tv1.isVisible = vm.getPrefUtil().unit1Model.isVisible()
                        binding.tv2.isVisible = vm.getPrefUtil().unit2Model.isVisible()
                        binding.tv3.isVisible = vm.getPrefUtil().unit3Model.isVisible()
                        binding.tv4.isVisible = vm.getPrefUtil().unit4Model.isVisible()

                        val isGatewayConnected = it.gatewayConnectionStatus == GatewayConnectionStatus.CONNECTED
                        val isGatewayConnecting = it.gatewayConnectionStatus == GatewayConnectionStatus.CONNECTING
                        val isGatewayNotConnected = it.gatewayConnectionStatus == GatewayConnectionStatus.NOT_CONNECTED

                        binding.ivLauncher.alpha = 0.1f
                        binding.ivLauncher.isClickable = false
                        setGrayScale(binding.ivLauncher, !isGatewayConnected)
                        setLauncherButtonImage(false)
                        binding.tv1.alpha = if (isGatewayConnected) 1.0f else 0.1f
                        binding.tv2.alpha = if (isGatewayConnected) 1.0f else 0.1f
                        binding.tv3.alpha = if (isGatewayConnected) 1.0f else 0.1f
                        binding.tv4.alpha = if (isGatewayConnected) 1.0f else 0.1f
                        binding.tv1.isClickable = isGatewayConnected
                        binding.tv2.isClickable = isGatewayConnected
                        binding.tv3.isClickable = isGatewayConnected
                        binding.tv4.isClickable = isGatewayConnected

                        binding.tvConnecting.isVisible = isGatewayConnecting
                        binding.tvGatewayNotConnected.isVisible = isGatewayNotConnected
                        binding.tvConnectedToGateway.isVisible = isGatewayConnected
                        binding.tvNoUnitSelected.isVisible = isGatewayConnected

                        binding.toolbar.showSelectDevice = !isGatewayConnected
                        binding.toolbar.showNetworkSignal = isGatewayConnected
                        binding.toolbar.signalStrength = prefs.savedBluetoothDevice?.signalStrength ?: 0
                        binding.toolbar.btnNetwork.imageTintList = ColorStateList.valueOf(Color.WHITE)

                        if (isGatewayConnecting) {
                            tvConnectingBlinkAnimator = ObjectAnimator.ofFloat(binding.tvConnecting, "alpha", 1f, 0f)
                            tvConnectingBlinkAnimator?.duration = 500
                            tvConnectingBlinkAnimator?.repeatMode = ValueAnimator.REVERSE
                            tvConnectingBlinkAnimator?.repeatCount = ValueAnimator.INFINITE
                            tvConnectingBlinkAnimator?.start()
                        } else {
                            tvConnectingBlinkAnimator?.cancel()
                            binding.tvConnecting.alpha = 1f
                        }
                    }
                }
            }
        }*/
    }

    private fun connectGateway() {
        if (vm.uiState.value?.hasGatewayAndUnitSet() == true && vm.uiState.value?.isGatewayNotConnected() == true) {
            checkBluetooth {
                vm.setGatewayConnecting()
                startConnectingBlinkAnimation()
                startBluetoothService {
                    setGatewayConnected()
                }
            }
        }
    }

    override fun onDeviceConnectionChanged(gatt: BluetoothGatt?, isConnected: Boolean) {
        super.onDeviceConnectionChanged(gatt, isConnected)
        runOnUiThread {
            if (isConnected) {
                setGatewayConnected()
            } else {
                setGatewayDisconnected()
            }
            binding.invalidateAll()
            binding.executePendingBindings()
        }
    }

    override fun renderState(apiRenderState: ApiRenderState) {

    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        runOnUiThread {
            vm.gatewaySignalStrength.value = rssi
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        value: ByteArray
    ) {
        super.onCharacteristicChanged(gatt, characteristic, value)
        runOnUiThread {
            val response = characteristic?.value?.decodeToString()?.lowercase()
            val command = bluetoothService?.waitingForRes
            val unit = getWaitingForResUnit() ?: return@runOnUiThread
            "DEBUG 1: response: $response, command: $command, unit: $unit".logE()
            if (response?.startsWith("U", ignoreCase = true) == true) {
                val fetchedChannelModel = FetchedChannelModel(response)
                vm.setPressureInBar(fetchedChannelModel.getPressure())
                vm.setFetchedUnitData(fetchedChannelModel)
                if (!vm.fetchDataFromAutoupdate) {
                    setStayArmed()
                }
            } else if (response?.startsWith("V", ignoreCase = true) == true) {
                vm.setVoltageResponse(response)
                setVoltage()
                setVoltageVisibility()
            } else if (vm.uiState.value?.selectedUnit?.isSoundCommand(bluetoothService?.waitingForRes) == true) {
//                showToast(String.format(getString(Strings.signal_sent_to_unit), vm.uiState.value?.selectedUnit?.unitNumber))
            } else if (vm.uiState.value?.selectedUnit?.isPressureCommand(bluetoothService?.waitingForRes) == true) {
                showToast(String.format(getString(Strings.signal_sent_to_unit), vm.uiState.value?.selectedUnit?.unitNumber))
            } else {
                if (response == AppConstants.CommandResponse.NO_RESPONSE || response == AppConstants.CommandResponse.NO_REPLY) {
                    /*showDialog(
                        title = getString(Strings.no_response),
                        message = String.format(getString(Strings.no_response_msg), unit),
                        onPositiveClick = {
                            reloadUnitData()
                        }
                    )*/
                    if (vm.uiState.value?.fetchedUnitModel == null) {
                        val unitNumber = vm.uiState.value?.selectedUnit?.unitNumber ?: return@runOnUiThread
                        val fetchedChannelModel = FetchedChannelModel("U${unitNumber}")
                        vm.setFetchedUnitData(fetchedChannelModel)
                    }
                    stayArmedTimerJob?.cancel()
                    cancelReloadBtnBlinkAnimator()
                    stopBlinkAnimation()
                    startUpdateBtnBlinkAnimation()
                } else if (response == AppConstants.CommandResponse.GOT_IT) {
                    if (vm.uiState.value?.selectedUnit?.isReloadCommand(command) == true) {
                        cancelReloadBtnBlinkAnimator()
                        if (vm.uiState.value?.fetchedUnitModel == null) {
                            val unitNumber = vm.uiState.value?.selectedUnit?.unitNumber ?: return@runOnUiThread
                            val fetchedChannelModel = FetchedChannelModel("U${unitNumber}-000000000000")
                            vm.setFetchedUnitData(fetchedChannelModel)
                        } else {
                            vm.uiState.value?.fetchedUnitModel?.doReload()
                            vm.setFetchedUnitData(vm.uiState.value?.fetchedUnitModel)
                        }
                        setStayArmed()
                        showToast(Strings.unit_successfully_initialized)
                    } else {
                        val channel = vm.currentFiredChannel ?: return@runOnUiThread
                        "DEBUG 1: channel: ${vm.currentFiredChannel} ?: ${channel}".logE()
                        if (channel != null) {
                            vm.uiState.value?.fetchedUnitModel?.markChannelAsFire(channel)
                            showToast(String.format(getString(Strings.channel_fired_successfully), channel))
                            vm.setFetchedUnitData(vm.uiState.value?.fetchedUnitModel)
                        }
                        vm.currentFiredChannel = null
                    }
                }
            }
        }
    }

    override fun onCharacteristicChangedTimeout() {
        super.onCharacteristicChangedTimeout()
        when(bluetoothService?.waitingForRes) {
            in listOfTestCommand -> {

            }
            in listOfFetchDataCommand -> {
                startUpdateBtnBlinkAnimation()
                cancelReloadBtnBlinkAnimator()
            }
        }
    }

    private fun fireSelectedChannel() {
        if (vm.uiState.value?.getNextAvailableChannel() != null) {
            val unit = vm.uiState.value?.fetchedUnitModel?.getUnit()
            val channel = vm.uiState.value?.getNextAvailableChannel()
            "DEBUG 1: Fire channel: $channel, unit: $unit".logE()
            val haxCode = vm.uiState.value?.selectedUnit?.unitChannelHexCode(
                unit = unit,
                channel = channel
            ) ?: return
            if (vm.hasStayArmedActive()) {
                vm.currentFiredChannel = channel
                sendCommand(haxCode)
                setStayArmed()
            } else {
                vm.setUnitDisarmed(false)
            }
        }
    }

    private fun setStayArmed() {
        stayArmedTimerJob?.cancel()
        stayArmedTimerJob = lifecycleScope.launch(Dispatchers.IO) {
            delay((prefs.armedInterval * 1000L))
            withContext(Dispatchers.Main) { vm.setUnitDisarmed(true) }
        }
    }

    private fun fireSoundCommand(soundIndex: Int? = null) {
        val haxCode = vm.uiState.value?.selectedUnit?.soundHexCode(soundIndex) ?: return
        sendCommand(haxCode)
    }

    private fun fireSoundAndChannel(isChannelAdded: Boolean = false) {
        vm.isAutoUpdateOnPause = true
        vm.isDisarmPause = true
        lifecycleScope.launch {
            if (prefs.randomizedSoundAndFireEnable) {
                fireSoundAndChannelRandomly(isChannelAdded)
            } else {
                fireSoundAndChannelStandard(isChannelAdded)
            }
            vm.isAutoUpdateOnPause = false
            vm.isDisarmPause = false
        }
    }

    private suspend fun fireSoundAndChannelStandard(isChannelAdded: Boolean = false) {
        if (vm.uiState.value?.fetchedUnitModel != null) {
            val soundIndex = if (vm.uiState.value?.selectedUnit?.isOnlySoundInstalled() == true) vm.selectedSoundIndex else null
            withContext(Dispatchers.Main) {
                showSecondaryProgress.set(true)
            }

            if (prefs.soundCount > 0) {
                withContext(Dispatchers.Main) { fireSoundCommand(soundIndex) }
            }

            for (i in 2..prefs.soundCount) {
                delay((prefs.soundDelay * 1000L))
                withContext(Dispatchers.Main) { fireSoundCommand(soundIndex) }
            }

            if (isChannelAdded && vm.uiState.value?.getNextAvailableChannel() != null) {
                delay((prefs.fireDelay * 1000L))
                withContext(Dispatchers.Main) { fireSelectedChannel() }
            }

            if (prefs.gunshotAfterLaunchEnable) {
                delay(1000L)
                withContext(Dispatchers.Main) { fireSoundCommand(4) }// Fire gunshot sound
            }

            withContext(Dispatchers.Main) {
                showSecondaryProgress.set(false)
            }
        }
    }

    private suspend fun fireSoundAndChannelRandomly(isChannelAdded: Boolean = false) {
        if (vm.uiState.value?.fetchedUnitModel != null) {
            val soundIndex = if (prefs.randomizedSoundEnable) {
                prefs.selectedSoundForRandomize.randomOrNull()?.toIntOrNull()
            } else {
                if (vm.uiState.value?.selectedUnit?.isOnlySoundInstalled() == true) vm.selectedSoundIndex else null
            }
            withContext(Dispatchers.Main) {
                showSecondaryProgress.set(true)
            }

            val soundCount = if (prefs.randomSoundCountEnable) Random.nextInt(prefs.randomSoundCountMin, prefs.randomSoundCountMax + 1) else prefs.soundCount
            if (soundCount > 0) {
                withContext(Dispatchers.Main) { fireSoundCommand(soundIndex) }
            }

            val soundDelay = if (prefs.randomSoundDelayEnable) Random.nextInt(prefs.randomSoundDelayMin, prefs.randomSoundDelayMax + 1) else prefs.soundDelay
            for (i in 2..soundCount) {
                val nextSoundIndex = if (prefs.randomizedSoundEnable && !prefs.canSameSoundForMultipleSounds) {
                    prefs.selectedSoundForRandomize.randomOrNull()?.toIntOrNull()
                } else {
                    soundIndex
                }
                delay((soundDelay * 1000L))
                withContext(Dispatchers.Main) { fireSoundCommand(nextSoundIndex) }
            }

            val fireDelay = if (prefs.randomFireDelayEnable) Random.nextInt(prefs.randomFireDelayMin, prefs.randomFireDelayMax + 1) else prefs.fireDelay
            if (isChannelAdded && vm.uiState.value?.getNextAvailableChannel() != null) {
                delay((fireDelay * 1000L))
                withContext(Dispatchers.Main) { fireSelectedChannel() }
            }

            if (prefs.gunshotAfterLaunchEnable) {
                delay(1000L)
                withContext(Dispatchers.Main) { fireSoundCommand(4) }// Fire gunshot sound
            }

            withContext(Dispatchers.Main) {
                showSecondaryProgress.set(false)
            }
        }
    }

    private fun setVoltage() {
        binding.tvVoltage.text = "${vm.fetchedUnitVoltage}V"
        vm.fetchedUnitVoltage?.toDoubleOrNull()?.let {
            binding.ivVoltage.setImageResource(getVoltageImageResId(it) )
        }
    }

    private fun setVoltageVisibility(iconVisible: Boolean = true) {
        val isVoltageVisible = !vm.fetchedUnitVoltage.isNullOrBlank()
        binding.tvVoltage.isVisible = !iconVisible && isVoltageVisible
        vm.fetchedUnitVoltage?.toDoubleOrNull()?.let {
            binding.ivVoltage.isVisible = iconVisible && isVoltageVisible
        }
    }

    private fun showGatewayInfo() {
        showGatewayInfoJob?.cancel()
        showGatewayInfoJob = lifecycleScope.launch(Dispatchers.Main) {
            binding.cvGatewayInfo.isVisible = true
            delay(2000)
            binding.cvGatewayInfo.isVisible = false
        }
    }

    private fun fetchUnitData(unitModel: UnitModel?, autoupdate: Boolean = false) {
        if (!autoupdate) {
            vm.resetFetchedUnit()
        }
        vm.fetchDataFromAutoupdate = autoupdate
        val haxCode = unitModel?.fetchDataHexCode() ?: return
        sendCommand(haxCode)
    }

    private fun reloadUnitData() {
        if (vm.uiState.value?.selectedUnit == null) return
        vm.currentFiredChannel = null
        val haxCode = vm.uiState.value?.selectedUnit?.reloadHexCode() ?: return
        sendCommand(haxCode)
    }

    private fun setupBlinkAnimation() {
        val anim1 = ObjectAnimator.ofArgb(binding.tvFire, "textColor", Color.BLACK, Color.RED)
        val anim2 = ObjectAnimator.ofArgb(binding.tvChannel, "textColor", Color.BLACK, Color.RED)
        val anim3 = ObjectAnimator.ofArgb(binding.tvChannelNo, "textColor", Color.BLACK, Color.RED)

        anim1.repeatCount = ObjectAnimator.INFINITE
        anim2.repeatCount = ObjectAnimator.INFINITE
        anim3.repeatCount = ObjectAnimator.INFINITE

        anim1.repeatMode = ObjectAnimator.REVERSE
        anim2.repeatMode = ObjectAnimator.REVERSE
        anim3.repeatMode = ObjectAnimator.REVERSE

        anim1.setDuration(500)
        anim2.setDuration(500)
        anim3.setDuration(500)

        animatorSet = AnimatorSet()
        animatorSet?.playTogether(anim1, anim2, anim3)
    }

    private fun startBlinkAnimation() {
        if (animatorSet != null && !animatorSet!!.isStarted) {
            animatorSet!!.start()
        }
    }

    private fun stopBlinkAnimation() {
        if (animatorSet != null && animatorSet!!.isStarted) {
            animatorSet!!.cancel()
            binding.tvFire.setTextColor(Color.BLACK)
            binding.tvChannel.setTextColor(Color.BLACK)
            binding.tvChannelNo.setTextColor(Color.BLACK)
        }
    }

    private fun startConnectingBlinkAnimation() {
        tvConnectingBlinkAnimator =
            ObjectAnimator.ofFloat(binding.tvConnecting, "alpha", 1f, 0f)
        tvConnectingBlinkAnimator?.duration = 500
        tvConnectingBlinkAnimator?.repeatMode = ValueAnimator.REVERSE
        tvConnectingBlinkAnimator?.repeatCount = ValueAnimator.INFINITE
        tvConnectingBlinkAnimator?.start()
    }

    private fun cancelConnectingBlinkAnimation() {
        tvConnectingBlinkAnimator?.cancel()
        binding.tvConnecting.alpha = 1.0f
    }

    private fun startReloadBtnAnimation() {
        reloadBtnBlinkAnimator?.cancel()
        reloadBtnBlinkAnimator = ObjectAnimator.ofFloat(binding.btnReloadContainer, "alpha", 1f, 0f, 1f)
        reloadBtnBlinkAnimator?.duration = 1000
        reloadBtnBlinkAnimator?.repeatCount = ObjectAnimator.INFINITE
        reloadBtnBlinkAnimator?.repeatMode = ObjectAnimator.RESTART
        reloadBtnBlinkAnimator?.interpolator = AccelerateDecelerateInterpolator()
        reloadBtnBlinkAnimator?.start()
    }

    private fun cancelReloadBtnBlinkAnimator() {
        reloadBtnBlinkAnimator?.cancel()
        binding.btnReloadContainer.alpha = 1.0f
    }

    private fun startUpdateBtnBlinkAnimation() {
        updateBtnBlinkAnimator?.cancel()
        updateBtnBlinkAnimator = ObjectAnimator.ofFloat(binding.btnUpdateContainer, "alpha", 1f, 0f, 1f)
        updateBtnBlinkAnimator?.duration = 1000
        updateBtnBlinkAnimator?.repeatCount = ObjectAnimator.INFINITE
        updateBtnBlinkAnimator?.repeatMode = ObjectAnimator.RESTART
        updateBtnBlinkAnimator?.interpolator = AccelerateDecelerateInterpolator()
        updateBtnBlinkAnimator?.start()
    }

    private fun cancelUpdateBtnBlinkAnimation() {
        updateBtnBlinkAnimator?.cancel()
        binding.btnUpdateContainer.alpha = 1.0f
    }

    /*private fun showUnitDataFetchedUI() {
        //        binding.bgChannelConnected.isVisible = true
        binding.tvDisarmedUnit.isVisible = false
        setLauncherButtonImage(true)
        if (vm.uiState.value?.selectedUnit?.isOnlySoundInstalled() == true) {
            binding.tvSound.isVisible = true
            binding.tvFire.isVisible = false
            binding.tvChannel.isVisible = false
            binding.tvChannelNo.isVisible = false
        } else {
            binding.tvFire.isVisible = true
            binding.tvChannel.isVisible = true
            binding.tvChannelNo.isVisible = true
            binding.tvSound.isVisible = false
            binding.tvBar.isVisible = vm.selectedUnitModel.value?.isServoVersion != true
            binding.tvBarNo.isVisible = vm.selectedUnitModel.value?.isServoVersion != true
            binding.tvBarNo.text = vm.fetchedUnitModel.value?.getBar()

            listOfUnitTvIds.forEach { textViewId ->
                val view = binding.root.findViewById<TextView>(textViewId) ?: return@forEach
                if (view.id == vm.selectedUnitTvId) {
                    view.setTextColor(getColor(Colors.black))
                    view.setBackgroundResource(Drawables.shape_rect_rounded_10_red)
//                view.backgroundTintList = ColorStateList.valueOf(getColor(Colors.colorRed))
                } else {
                    view.setTextColor(getColor(Colors.white))
                    view.setBackgroundResource(Drawables.shape_rect_rounded_border_10)
//                view.backgroundTintList = null
                }
            }
        }
        startBlinkAnimation()
    }

    private fun hideUnitDataFetchedUI() {
//        binding.bgChannelConnected.isVisible = false
        setLauncherButtonImage(false)
        binding.ivLauncher.alpha = 0.1f
        binding.ivLauncher.isClickable = false

        binding.tvFire.isVisible = false
        binding.tvChannel.isVisible = false
        binding.tvChannelNo.isVisible = false
        binding.tvSound.isVisible = false
        binding.tvBar.isVisible = false
        binding.tvBarNo.isVisible = false
        binding.tvVoltage.isVisible = false
        binding.ivVoltage.isVisible = false
        stopBlinkAnimation()

        listOfUnitTvIds.forEach { textViewId ->
            val view = binding.root.findViewById<TextView>(textViewId) ?: return
            if (view.id == vm.selectedUnitTvId) {
                view.setTextColor(getColor(Colors.black))
                view.setBackgroundResource(Drawables.shape_rect_rounded_10)
            } else {
                view.setTextColor(getColor(Colors.white))
                view.setBackgroundResource(Drawables.shape_rect_rounded_border_10)
            }
        }
    }*/

    private fun selectNextChannel() {
        val nextAvailableChannel = vm.uiState.value?.fetchedUnitModel?.getNextChannel()
        val noOfChannelAdded = vm.uiState.value?.selectedUnit?.noOfChannel ?: 0
        if (vm.uiState.value?.getNextAvailableChannel() != null) {
            startBlinkAnimation()
        }
        if (nextAvailableChannel == null || nextAvailableChannel > noOfChannelAdded) {

            stopBlinkAnimation()
        }
    }

    private fun handleUnitClick(unitNo: Int, unitModel: UnitModel?) {
        if (prefs.advancedControlIsStandardEnable) {
            resetSelectUnit()
            val bundle =
                bundleOf(AppConstants.Communication.BundleData.INTENT_UNIT_MODEL to unitModel)
            startActivity(ChannelListAct::class.java, bundle = bundle)
        } else {
            selectUnit(unitNo, unitModel)
        }
    }

    private fun handleLongUnitClick(unitNo: Int, unitModel: UnitModel?) {
        if (prefs.advancedControlIsStandardEnable) {
            selectUnit(unitNo, unitModel)
        } else {
            resetSelectUnit()
            val bundle =
                bundleOf(AppConstants.Communication.BundleData.INTENT_UNIT_MODEL to unitModel)
            startActivity(ChannelListAct::class.java, bundle = bundle)
        }
    }

    private fun selectUnit(unitNo: Int, unitModel: UnitModel?) {
        if (vm.uiState.value?.isThisUnitSelected(unitNo) == true && vm.uiState.value?.getNextAvailableChannel() != null) {
            vm.setUnitDisarmed(true)
        } else if (vm.uiState.value?.isThisUnitSelected(unitNo) == true) {
            resetSelectUnit()
        } else {
            vm.setSelectedUnit(unitModel)
            fetchUnitData(unitModel)
        }
    }

    private fun resetSelectUnit() {
        vm.setSelectedUnit(null)
        vm.setFetchedUnitData(null)
    }

    /*private fun setLauncherButtonImage(isConnected: Boolean) {
        if (isConnected) {
            binding.ivLauncher.setImageResource(Drawables.ic_red_freesbi)
        } else {
            if (vm.selectedUnitModel.value?.isOnlySoundInstalled() == true) {
                binding.ivLauncher.setImageResource(Drawables.ic_green_freesbi_with_speaker)
            } else {
                binding.ivLauncher.setImageResource(Drawables.ic_green_freesbi)
            }
        }
    }*/

    private val settingActResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        vm.reloadState()
        connectGateway()
    }

    override fun onClick(v: View) {
        super.onClick(v)
        v.triggerHaptic()
        when(v.id) {
            R.id.iv_launcher -> {
                when {
                    vm.uiState.value?.isGatewayNotConnected() == true -> connectGateway()
                    vm.uiState.value?.isGatewayConnected() == true -> {
                        if (vm.uiState.value?.selectedUnit != null) {
                            if (vm.uiState.value?.selectedUnit?.isOnlySoundInstalled() == true) {
                                binding.ivSpeaker.animateWave()
                                fireSoundCommand(vm.selectedSoundIndex)
                            } else {
                                fireSelectedChannel()
                            }
                        } else {
                            showGatewayInfo()
                        }
                    }
                    else -> {

                    }
                }
            }

            R.id.iv_voltage -> {
                lifecycleScope.launch {
                    setVoltageVisibility(false)
                    delay(2000)
                    setVoltageVisibility(true)
                }
            }

            R.id.btn_available_devices, R.id.btn_network, R.id.tv_save_gateway_info -> {
                startActivityForResult(ConnectionConfigAct::class.java, settingActResultLauncher)
            }

            R.id.btn_setting, R.id.tv_set_up_unit_info -> {
                startActivityForResult(MainConfigurationAct::class.java, settingActResultLauncher)
            }

            R.id.tv_1 -> {
                handleUnitClick(1, prefs.unit1Model)
            }

            R.id.tv_2 -> {
                handleUnitClick(2, prefs.unit2Model)
            }

            R.id.tv_3 -> {
                handleUnitClick(3, prefs.unit3Model)
            }

            R.id.tv_4 -> {
                handleUnitClick(4, prefs.unit4Model)
            }

            R.id.tv_disarmed_unit -> {
                vm.setUnitDisarmed(false)
                setStayArmed()
            }

            R.id.btn_reload -> {
                v.animateRotate()
                reloadUnitData()
            }

            R.id.btn_update -> {
                v.animateHorizontalFlip()
                fetchUnitData(vm.uiState.value?.selectedUnit)
            }

            R.id.btn_sound -> {
                v.animateWave()
                fireSoundCommand()
            }

            R.id.btn_sound_duck -> {
                selectSound(0)
            }

            R.id.btn_sound_pheasant -> {
                selectSound(1)
            }

            R.id.btn_sound_goose -> {
                selectSound(2)
            }

            R.id.btn_sound_brrr -> {
                selectSound(3)
            }

            R.id.btn_sound_gunshot -> {
                selectSound(4)
            }

            R.id.btn_sound_magpie -> {
                selectSound(5)
            }
        }
    }

    private fun selectSound(index: Int) {
        vm.selectedSoundIndex = index
        binding.invalidateAll()
    }

    private fun setGatewayConnected() {
        startGatewaySignalStrengthUpdate()
        vm.setGatewayConnected()
    }

    private fun setGatewayDisconnected() {
        stopGatewaySignalStrengthUpdate()
        vm.setGatewayDisconnected()
    }

    override fun onPause() {
        super.onPause()
        stopBlinkAnimation()
    }

    override fun onResume() {
        super.onResume()
        startBlinkAnimation()

        /**
         * Reload unit
         */
        val unitNumber = vm.uiState.value?.selectedUnit?.unitNumber ?: -1
        vm.setSelectedUnit(vm.getUnitModel(unitNumber))
    }
}