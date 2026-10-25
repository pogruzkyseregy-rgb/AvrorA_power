package com.avrora.power

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
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
        val net = cm?.activeNetwork
        val caps = if (cm != null && net != null) cm.getNetworkCapabilities(net) else null
        val mobile = caps != null &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!mobile) {
            whitelist.value = false
            return
        }
        val reachable = coroutineScope {
            hosts.map { url -> async(Dispatchers.IO) { reachable(url) } }.awaitAll().count { it }
        }
        // Without a signal, "nothing opens" just means no connection, so it does not count.
        whitelist.value = level.intValue > 0 && reachable <= 1
    }

    private fun reachable(url: String): Boolean = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.requestMethod = "HEAD"
        c.instanceFollowRedirects = false
        val code = c.responseCode
        c.disconnect()
        code > 0
    }.getOrDefault(false)
}
