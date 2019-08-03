package org.tvheadend.tvhclient.ui.features.information

import android.os.Bundle
import org.tvheadend.tvhclient.R

class HelpAndSupportFragment : WebViewFragment() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        website = "help_and_support"

        toolbarInterface.setTitle(getString(R.string.help_and_support))
        toolbarInterface.setSubtitle("")
    }
}