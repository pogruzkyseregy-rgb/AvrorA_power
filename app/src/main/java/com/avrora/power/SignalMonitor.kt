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

    /** Confirmed network mode, shown in the UI. */
    val status = mutableStateOf(NetMode.UNKNOWN)

    /** Ping in ms to the reachable sites (average), or -1 when nothing answered. */
    val pingMs = mutableIntStateOf(-1)

    private var callback: Any? = null

    // Foreign services: normally open, closed by a white list.
    private val foreign = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
        "https://captive.apple.com/hotspot-detect.html",
        "https://www.msftconnecttest.com/connecttest.txt",
    )

    // Domestic services: white lists differ by operator, so the list is wide.
    private val domestic = listOf(
        "https://www.gosuslugi.ru",
        "https://ya.ru",
        "https://yandex.ru",
        "https://vk.com",
        "https://mail.ru",
        "https://dzen.ru",
        "https://www.sberbank.ru",
        "https://www.nalog.gov.ru",
        "https://www.mos.ru",
        "https://max.ru",
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

    /** Result of one round of checks. */
    enum class NetMode { UNKNOWN, OPEN, WHITELIST, NO_ACCESS }

    /**
     * One check round over the mobile network.
     * Foreign services that are open in normal mode fail in white-list mode.
     * Domestic sites are the ones a white list keeps. So:
     * - foreign answers → open;
     * - no foreign, but some domestic answer → white-list;
     * - anything else (no signal, no connection) → no judgement this round.
     * A change is confirmed only after two equal rounds in a row, so one glitch does not flip it.
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
        if (cellular == null || level.intValue <= 0) {
            pingMs.intValue = -1
            observe(ctx, NetMode.UNKNOWN)
            return
        }

        val results = coroutineScope {
            (foreign + domestic).map { url -> async(Dispatchers.IO) { url to timedRequest(cellular, url) } }
                .awaitAll()
        }
        val answered = results.mapNotNull { it.second }
        // Ping = average answer time of the sites that did respond.
        pingMs.intValue = if (answered.isEmpty()) -1 else answered.average().toInt()

        val foreignOk = results.count { it.first in foreign && it.second != null }
        val domesticOk = results.count { it.first in domestic && it.second != null }
        val mode = when {
            foreignOk >= 2 -> NetMode.OPEN
            foreignOk == 0 && domesticOk >= 1 -> NetMode.WHITELIST
            // Nothing answers at all: most likely a white list whose services are not in our list,
            // or no data. It cannot be told apart from outside.
            answered.isEmpty() -> NetMode.NO_ACCESS
            else -> NetMode.UNKNOWN
        }
        observe(ctx, mode)
    }

    private var candidate = NetMode.UNKNOWN
    private var streak = 0
    private var confirmed = NetMode.UNKNOWN

    private fun observe(ctx: Context, seen: NetMode) {
        if (seen == NetMode.UNKNOWN) {
            streak = 0
            return
        }
        if (seen == candidate) streak++ else { candidate = seen; streak = 1 }
        if (streak < 2 || seen == confirmed) return

        val wasKnown = confirmed != NetMode.UNKNOWN
        val wasWhitelist = confirmed == NetMode.WHITELIST
        val isWhitelist = seen == NetMode.WHITELIST
        confirmed = seen
        status.value = seen
        whitelist.value = isWhitelist
        // Announce only real transitions into or out of white-list mode.
        // The first judgement after start only sets the starting state.
        if (wasKnown && wasWhitelist != isWhitelist) {
            if (isWhitelist) {
                Alerts.post(ctx, 6, "Белые списки включены", "Мобильный интернет ограничен списком сайтов.")
            } else {
                Alerts.post(ctx, 6, "Белые списки отключены", "Обычный доступ к интернету восстановлен.")
            }
        }
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
