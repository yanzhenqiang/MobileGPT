package org.autojs.autojs.ui.floating

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Toast
import butterknife.ButterKnife
import butterknife.OnClick
import butterknife.Optional
import com.afollestad.materialdialogs.MaterialDialog
import com.makeramen.roundedimageview.RoundedImageView
import com.stardust.app.DialogUtils
import com.stardust.enhancedfloaty.FloatyService
import com.stardust.enhancedfloaty.FloatyWindow
import com.stardust.view.accessibility.AccessibilityService.Companion.instance
import com.stardust.view.accessibility.LayoutInspector.CaptureAvailableListener
import com.stardust.view.accessibility.NodeInfo
import org.autojs.autoxjs.R
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.theme.dialog.ThemeColorMaterialDialogBuilder
import org.autojs.autojs.tool.AccessibilityServiceTool
import org.autojs.autojs.ui.common.OperationDialogBuilder
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.main.MainActivity
import org.greenrobot.eventbus.EventBus
import org.jdeferred.Deferred
import org.jdeferred.impl.DeferredObject

/**
 * Created by Stardust on 2017/10/18.
 */
@SuppressLint("NonConstantResourceId")
class CircularMenu(context: Context?) : CaptureAvailableListener {
    class StateChangeEvent(val currentState: Int, val previousState: Int)

    private var mWindow: CircularMenuWindow? = null
    private var mState = 0
    private var mActionViewIcon: RoundedImageView? = null
    private val mContext: Context
    private var mSettingsDialog: MaterialDialog? = null
    private var mLayoutInspectDialog: MaterialDialog? = null
    private var mRunningPackage: String? = null
    private var mRunningActivity: String? = null
    private var mCaptureDeferred: Deferred<NodeInfo?, Void, Void>? = null
    private fun setupListeners() {
        mWindow?.setOnActionViewClickListener {
            if (mWindow?.isExpanded == true) {
                mWindow?.collapse()
            } else {
                mCaptureDeferred = DeferredObject()
                AutoJs.getInstance().layoutInspector.captureCurrentWindow()
                mWindow?.expand()
            }
        }
    }

    private fun initFloaty() {
        mWindow = CircularMenuWindow(mContext, object : CircularMenuFloaty {
            override fun inflateActionView(
                service: FloatyService,
                window: CircularMenuWindow
            ): View {
                val actionView = View.inflate(service, R.layout.circular_action_view, null)
                mActionViewIcon = actionView.findViewById(R.id.icon)
                return actionView
            }

            override fun inflateMenuItems(
                service: FloatyService,
                window: CircularMenuWindow
            ): CircularActionMenu {
                val menu = View.inflate(
                    ContextThemeWrapper(service, R.style.AppTheme),
                    R.layout.circular_action_menu,
                    null
                ) as CircularActionMenu
                ButterKnife.bind(this@CircularMenu, menu)
                return menu
            }
        })
        mWindow?.setKeepToSideHiddenWidthRadio(0.25f)
        FloatyService.addWindow(mWindow)
    }

    private fun setState(state: Int) {
        val previousState = mState
        mState = state
        mActionViewIcon?.setImageResource(IC_ACTION_VIEW)
        mActionViewIcon?.setBackgroundResource(R.drawable.circle_white)
        val padding =
            mContext.resources.getDimension(R.dimen.padding_circular_menu_normal)
                .toInt()
        mActionViewIcon?.setPadding(padding, padding, padding, padding)
        EventBus.getDefault().post(StateChangeEvent(mState, previousState))
    }

    @Optional
    @OnClick(R.id.layout_inspect)
    fun inspectLayout() {
        mWindow?.collapse()
        mLayoutInspectDialog = OperationDialogBuilder(mContext)
            .item(
                R.id.layout_bounds,
                R.drawable.ic_circular_menu_bounds,
                R.string.text_inspect_layout_bounds
            )
            .item(
                R.id.layout_hierarchy, R.drawable.ic_layout_hierarchy,
                R.string.text_inspect_layout_hierarchy
            )
            .bindItemClick(this)
            .title(R.string.text_inspect_layout)
            .build()
        DialogUtils.showDialog(mLayoutInspectDialog)
    }

    @Optional
    @OnClick(R.id.layout_bounds)
    fun showLayoutBounds() {
        inspectLayout { rootNode -> rootNode?.let { LayoutBoundsFloatyWindow(it) } }
    }

    @Optional
    @OnClick(R.id.layout_hierarchy)
    fun showLayoutHierarchy() {
        inspectLayout { mRootNode -> mRootNode?.let { LayoutHierarchyFloatyWindow(it) } }
    }

    private fun inspectLayout(windowCreator: (NodeInfo?) -> FloatyWindow?) {
        mLayoutInspectDialog?.dismiss()
        mLayoutInspectDialog = null
        if (instance == null) {
            Toast.makeText(
                mContext,
                R.string.text_no_accessibility_permission_to_capture,
                Toast.LENGTH_SHORT
            ).show()
            AccessibilityServiceTool.goToAccessibilitySetting()
            return
        }
        val progress = DialogUtils.showDialog(
            ThemeColorMaterialDialogBuilder(mContext)
                .content(R.string.text_layout_inspector_is_dumping)
                .canceledOnTouchOutside(false)
                .progress(true, 0)
                .build()
        )
        mCaptureDeferred?.promise()
            ?.then({ capture ->
                mActionViewIcon?.post {
                    if (!progress.isCancelled) {
                        progress.dismiss()
                        windowCreator.invoke(capture)?.let { FloatyService.addWindow(it) }
                    }
                }
            }) { mActionViewIcon?.post { progress.dismiss() } }
    }

    private fun dismissSettingsDialog() {
        mSettingsDialog?.dismiss()
        mSettingsDialog = null
    }

    @Optional
    @OnClick(R.id.open_launcher)
    fun openLauncher() {
        mWindow?.collapse()
        dismissSettingsDialog()
        val intent = Intent(mContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        mContext.startActivity(intent)
    }

    @Optional
    @OnClick(R.id.exit_floating_window)
    fun exitFloatingWindow() {
        mWindow?.collapse()
        dismissSettingsDialog()
        try {
            mWindow?.close()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } finally {
            EventBus.getDefault().post(StateChangeEvent(STATE_CLOSED, mState))
            mState = STATE_CLOSED
        }
        AutoJs.getInstance().layoutInspector.removeCaptureAvailableListener(this)
    }

    override fun onCaptureAvailable(capture: NodeInfo?) {
        if (mCaptureDeferred != null && mCaptureDeferred!!.isPending) mCaptureDeferred!!.resolve(
            capture
        )
    }

    companion object {
        const val STATE_CLOSED = -1
        private const val IC_ACTION_VIEW = R.drawable.ic_android_eat_js
    }

    init {
        mContext = ContextThemeWrapper(context, R.style.AppTheme)
        initFloaty()
        setupListeners()
        AutoJs.getInstance().layoutInspector.addCaptureAvailableListener(this)
    }
}