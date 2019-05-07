package org.tvheadend.tvhclient.ui.base

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import org.tvheadend.tvhclient.MainApplication
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.domain.entity.ServerStatus
import org.tvheadend.tvhclient.ui.common.MenuUtils
import org.tvheadend.tvhclient.ui.common.callbacks.NetworkStatusListener
import org.tvheadend.tvhclient.ui.common.callbacks.ToolbarInterface
import org.tvheadend.tvhclient.ui.common.gone
import org.tvheadend.tvhclient.ui.common.visible
import org.tvheadend.tvhclient.ui.features.MainViewModel

abstract class BaseFragment : Fragment(), NetworkStatusListener {

    protected lateinit var sharedPreferences: SharedPreferences
    protected lateinit var toolbarInterface: ToolbarInterface
    protected var isDualPane: Boolean = false
    protected var isUnlocked: Boolean = false
    protected var isNetworkAvailable: Boolean = false

    protected lateinit var mainViewModel: MainViewModel
    protected lateinit var menuUtils: MenuUtils
    protected lateinit var serverStatus: ServerStatus

    private var mainFrameLayout: FrameLayout? = null
    private var detailsFrameLayout: FrameLayout? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (activity is ToolbarInterface) {
            toolbarInterface = activity as ToolbarInterface
        }

        if (activity is NetworkStatusListener) {
            isNetworkAvailable = (activity as NetworkStatusListener).onNetworkIsAvailable()
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        mainViewModel = ViewModelProviders.of(activity as BaseActivity).get(MainViewModel::class.java)

        menuUtils = MenuUtils(activity!!)
        mainFrameLayout = activity?.findViewById(R.id.main)
        detailsFrameLayout = activity?.findViewById(R.id.details)

        serverStatus = mainViewModel.activeServerStatus
        isUnlocked = MainApplication.getInstance().isUnlocked

        // Check if we have a frame in which to embed the details fragment.
        // Make the frame layout visible and set the weights again in case
        // it was hidden by the call to forceSingleScreenLayout()
        isDualPane = detailsFrameLayout != null
        if (isDualPane) {
            detailsFrameLayout?.visible()
            val param = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0.65f
            )
            mainFrameLayout?.layoutParams = param
        }

        setHasOptionsMenu(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                activity?.finish()
                return true
            }
            R.id.menu_refresh ->
                return menuUtils.handleMenuReconnectSelection()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNetworkStatusChanged(isAvailable: Boolean) {
        this.isNetworkAvailable = isAvailable
    }

    override fun onNetworkIsAvailable(): Boolean {
        return this.isNetworkAvailable
    }

    protected fun forceSingleScreenLayout() {
        if (detailsFrameLayout != null) {
            detailsFrameLayout?.gone()
            val param = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1.0f
            )
            mainFrameLayout?.layoutParams = param
        }
    }
}
