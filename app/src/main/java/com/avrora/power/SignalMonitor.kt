package com.avrora.power

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mobile signal level (0–4 bars) and a check for "белые списки" mode.
 * White-list mode: the mobile network is up, but almost none of the usual sites open.
 */
object SignalMonitor {
    /** 0–4 bars, or -1 when unknown (no permission yet, or no signal). */
    val level = mutableIntStateOf(-1)

    /** True while the mobile network looks limited to a white list. */
    val whitelist = mutableStateOf(false)

    /** Ping in ms to the reachable sites (average), or -1 when nothing answered. */
    val pingMs = mutableIntStateOf(-1)

    private var callback: Any? = null

    private val hosts = listOf(
        "https://www.google.com/generate_204",
        "https://ya.ru",
        "https://vk.com",
        "https://www.youtube.com",
        "https://telegram.org",
    )

    fun hasPhonePermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts listening to signal changes. Safe to call repeatedly; it retries until the permission is granted. */
    @Suppress("DEPRECATION")
    fun ensureListening(ctx: Context) {
        if (callback != null || !hasPhonePermission(ctx)) return
        val tm = ctx.getSystemService(TelephonyManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        level.intValue = signalStrength.level
                    }
                }
                tm.registerTelephonyCallback(ctx.mainExecutor, cb)
                callback = cb
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                        level.intValue = signalStrength?.level ?: -1
                    }
                }
                tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                callback = listener
            }
        }
    }

    @Suppress("DEPRECATION")
    fun stop(ctx: Context) {
        val tm = ctx.getSystemService(TelephonyManager::class.java) ?: return
        runCatching {
            when (val cb = callback) {
                is TelephonyCallback -> tm.unregisterTelephonyCallback(cb)
                is PhoneStateListener -> tm.listen(cb, PhoneStateListener.LISTEN_NONE)
            }
        }
        callback = null
    }

    /**
     * One check. Only the mobile network is checked. If it has a signal but almost none of
     * the usual sites answer, white-list mode is flagged. This is a heuristic, not an exact test.
     */
    suspend fun probe(ctx: Context) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        // With a VPN on, the default network is the VPN, so find the cellular network itself.
        val cellular: Network? = if (cm == null) null else cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net)
            caps != null &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        if (cellular == null) {
            whitelist.value = false
            pingMs.intValue = -1
            return
        }
        val times = coroutineScope {
            hosts.map { url -> async(Dispatchers.IO) { timedRequest(cellular, url) } }.awaitAll()
        }.filterNotNull()
        // Ping = average answer time of the sites that did respond.
        pingMs.intValue = if (times.isEmpty()) -1 else times.average().toInt()
        // Without a signal, "nothing opens" just means no connection, so it does not count.
        whitelist.value = level.intValue > 0 && times.size <= 1
    }

    // Bound to the cellular network, so the request goes out there even when a VPN is active.
    // Returns the answer time in ms, or null if the site did not answer.
    private fun timedRequest(network: Network, url: String): Long? = runCatching {
        val t0 = System.nanoTime()
        val c = network.openConnection(URL(url)) as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.requestMethod = "HEAD"
        c.instanceFollowRedirects = false
        val code = c.responseCode
        c.disconnect()
        if (code > 0) (System.nanoTime() - t0) / 1_000_000 else null
    }.getOrNull()
}
