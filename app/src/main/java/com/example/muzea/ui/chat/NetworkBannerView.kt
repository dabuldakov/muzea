package com.example.muzea.ui.chat

import android.view.View
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.utils.NetworkQuality

/** Плашка «нет сети / слабая сеть» над лентой. */
class NetworkBannerView(
    private val banner: TextView,
    private val onBackOnline: () -> Unit
) {

    fun render(quality: NetworkQuality) {
        val show = quality != NetworkQuality.GOOD
        banner.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            banner.setText(
                if (quality == NetworkQuality.OFFLINE) R.string.network_offline_banner
                else R.string.network_slow_banner
            )
        } else {
            onBackOnline()
        }
    }
}
