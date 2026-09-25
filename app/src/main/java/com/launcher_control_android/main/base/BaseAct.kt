package com.launcher_control_android.main.base

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.*
import androidx.lifecycle.lifecycleScope
import com.launcher_control_android.AppConstants.Api.ResponseCode.UNAUTHORIZED_CODE
import com.launcher_control_android.AppConstants.Communication.BundleData.IS_UNAUTHORISED
import com.launcher_control_android.BR
import com.launcher_control_android.R
import com.launcher_control_android.Strings
import com.launcher_control_android.main.ui.unit_detail.view.UnitDetailAct
import com.launcher_control_android.helper.util.*
import com.launcher_control_android.main.base.BaseRepo.ApiResultType.CANCELLED
import com.launcher_control_android.main.common.ApiRenderState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class BaseAct<binding : ViewDataBinding, VM : BaseVM>(
    @LayoutRes private val layoutId: Int
) : AppCompatActivity(), View.OnClickListener {

    @Inject
    lateinit var prefs: PrefUtil

    protected lateinit var binding: binding

    private var progress: ObservableField<Boolean>? = null

    protected abstract val vm: VM?
    protected abstract fun renderState(apiRenderState: ApiRenderState)
    protected abstract val hasProgress: Boolean
    protected abstract fun init()

    override fun onCreate(savedInstanceState: Bundle?) {
        /*fragFactory?.let {
            supportFragmentManager.fragmentFactory = it
        }*/
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        binding = DataBindingUtil.setContentView<binding>(this, layoutId).apply {
            lifecycleOwner = this@BaseAct

            vm?.let {
                setVariable(BR.vm, it)

                lifecycleScope.launch {
                    it.state().collect {
                        renderState(it)
                    }
                }

                lifecycleScope.launch {
                    it.apiError.collect {
                        if (it.resCode == UNAUTHORIZED_CODE)
                            logout(true)
                        else
                            if (it.resultType != CANCELLED) {
                                hideProgress()
                                it.error?.let {
                                    this@BaseAct.showToast(it)
                                }
                            }
                    }
                }
            }
            root.findViewById<View>(R.id.btn_back)?.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            setVariable(BR.click, this@BaseAct)

            if (hasProgress) {
                progress = ObservableField()
                setVariable(BR.showProgress, progress)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            findViewById<View>(android.R.id.content).setOnApplyWindowInsetsListener { v, insets ->
                val viewInsets: Insets = insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())

                /**
                 * add navigation bar
                 */
                val rootLayout = findViewById<ViewGroup>(android.R.id.content)
                rootLayout.getChildAt(0)?.setPadding(0, 0, 0, viewInsets.bottom)
                val navigationBarView = View(this@BaseAct)
                navigationBarView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewInsets.bottom)
                navigationBarView.setBackgroundColor(ContextCompat.getColor(this@BaseAct, R.color.black))
                when (rootLayout) {
                    is FrameLayout -> {
                        val params = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            viewInsets.bottom
                        ).apply {
                            gravity = Gravity.BOTTOM
                        }
                        navigationBarView.layoutParams = params
                    }

                    is RelativeLayout -> {
                        val params = RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            viewInsets.bottom
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                        }
                        navigationBarView.layoutParams = params
                    }

                    is ConstraintLayout -> {
                        val params = ConstraintLayout.LayoutParams(
                            ConstraintLayout.LayoutParams.MATCH_PARENT,
                            viewInsets.bottom
                        ).apply {
                            bottomToBottom =
                                ConstraintLayout.LayoutParams.PARENT_ID
                        }
                        navigationBarView.layoutParams = params
                    }

                    is CoordinatorLayout -> {
                        val params = CoordinatorLayout.LayoutParams(
                            CoordinatorLayout.LayoutParams.MATCH_PARENT,
                            viewInsets.bottom
                        ).apply {
                            gravity = Gravity.BOTTOM
                        }
                        navigationBarView.layoutParams = params
                    }
                }
                rootLayout.addView(navigationBarView)

                /**
                 * Add status bar
                 */
                (v as? ViewGroup)?.children?.firstOrNull()?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = viewInsets.top
                }
                val statusBarView = View(this@BaseAct)
                statusBarView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewInsets.top)
                statusBarView.setBackgroundColor(ContextCompat.getColor(this@BaseAct, R.color.black))
                addContentView(statusBarView, statusBarView.layoutParams)
                insets
            }
        }

        init()
    }

    fun logout(isUnauthorised: Boolean = false) {
        prefs.clearPrefs()

        startActivity(
            UnitDetailAct::class.java,
            bundleOf(IS_UNAUTHORISED to isUnauthorised),
            listOf(Intent.FLAG_ACTIVITY_CLEAR_TOP, Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    fun showToast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        ToastUtil.showToastMessage(this@BaseAct, getString(resId), duration)
    }

    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        ToastUtil.showToastMessage(this@BaseAct, message, duration)
    }

    fun showDialogFrag(dialFrag: DialogFragment, bundle: Bundle? = null) {
        bundle?.let {
            dialFrag.arguments = bundle
        }
        dialFrag.show(supportFragmentManager, "")
    }

    fun showProgress() {
        progress?.set(true)
    }

    fun hideProgress() {
        progress?.set(false)
    }

    fun popFrag() {
        supportFragmentManager.popBackStack()
    }

    fun finishAct() {
        currentFocus?.hideSoftKeyboard()
        finish()
    }

    fun delayedExecutor(millis: Long, executable: () -> Unit) {
        lifecycleScope.launch {
            delay(millis)
            executable.invoke()
        }
    }

    fun onContainerBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0)
            popFrag()
        else
            finishAct()
    }

    private fun isApplicationInBackground(): Boolean {
//        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
        val runningTasks =
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningTasks(1)
        if (runningTasks.isNotEmpty())
            return runningTasks[0].topActivity?.packageName != packageName
        return false
//        } else {
//            val runningTasks = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).appTasks
//            if (runningTasks.isNotEmpty())
//                return runningTasks[0].taskInfo.topActivity.packageName != packageName
//            return false
//        }
    }

    override fun onClick(v: View) {

    }
}
