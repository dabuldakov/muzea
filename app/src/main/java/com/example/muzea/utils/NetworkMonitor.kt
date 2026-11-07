package com.example.muzea.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Насколько канал пригоден для сетевых операций. */
enum class NetworkQuality { GOOD, WEAK, OFFLINE }

data class NetworkStatus(
    val quality: NetworkQuality,
    val metered: Boolean
) {
    val online: Boolean get() = quality != NetworkQuality.OFFLINE
}

/**
 * Единая точка правды о сети.
 *
 * Нужна, чтобы на слабом канале не грузить медиа и не долбить сервер
 * частым поллингом. Качество определяется по validated-сети и оценке
 * пропускной способности; при неизвестной оценке считаем канал хорошим.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(read())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val pollScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    init {
        // Страховка: некоторые прошивки не присылают onLost для default-сети.
        // Раз в 10 секунд перечитываем состояние сами.
        pollScope.launch {
            while (true) {
                refresh()
                delay(10_000)
            }
        }
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = refresh()
                    override fun onLost(network: Network) = refresh()
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities
                    ) = refresh()
                }
            )
        }
    }

    private fun refresh() {
        val status = read()
        _status.value = status
        MediaPolicy.status = status
    }

    private fun read(): NetworkStatus {
        val network = connectivityManager.activeNetwork
            ?: return NetworkStatus(NetworkQuality.OFFLINE, metered = false)
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkStatus(NetworkQuality.OFFLINE, metered = false)

        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!validated) {
            return NetworkStatus(NetworkQuality.OFFLINE, metered = false)
        }

        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val downKbps = caps.linkDownstreamBandwidthKbps
        val quality = when {
            downKbps in 1..MIN_GOOD_KBPS -> NetworkQuality.WEAK
            else -> NetworkQuality.GOOD
        }
        return NetworkStatus(quality, metered)
    }

    private companion object {
        /** Ниже ~400 кбит/с считаем канал слабым: хватит на текст, не на медиа. */
        const val MIN_GOOD_KBPS = 400
    }
}
