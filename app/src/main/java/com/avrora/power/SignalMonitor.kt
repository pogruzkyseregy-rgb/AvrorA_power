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
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Mobile signal level (0–4 bars), ping, and a check for "белые списки" mode.
 */
object SignalMonitor {
    /** Result of one round of checks. */
    enum class NetMode { UNKNOWN, OPEN, WHITELIST, NO_ACCESS }

    /** What happened when one site was checked over the mobile network. */
    data class Probe(
        val url: String,
        /** Answer time in ms, or null if the site did not answer. */
        val ms: Long?,
        /** The name resolved to an address on the mobile network. */
        val dns: Boolean,
        /** The connection timed out (as opposed to being refused or failing at once). */
        val timeout: Boolean,
    )

    /** 0–4 bars, or -1 when unknown (no permission yet, or no signal). */
    val level = mutableIntStateOf(-1)

    /** Ping in ms to the reachable sites (average), or -1 when nothing answered. */
    val pingMs = mutableIntStateOf(-1)

    /** True while white-list mode is in effect (automatic result or the manual setting). */
    val whitelist = mutableStateOf(false)

    /** Confirmed automatic result, shown in the UI. */
    val status = mutableStateOf(NetMode.UNKNOWN)

    /** -1 = automatic, 0 = forced off, 1 = forced on. */
    val manual = mutableIntStateOf(-1)

    /** Last round: each site and what happened. */
    val lastResults = mutableStateOf<List<Probe>>(emptyList())

    private var callback: Any? = null
    private var candidate = NetMode.UNKNOWN
    private var streak = 0
    private var confirmed = NetMode.UNKNOWN

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

    fun loadManual(ctx: Context) {
        manual.intValue = ctx.getSharedPreferences("avrora", Context.MODE_PRIVATE).getInt("wlManual", -1)
        applyEffective()
    }

    /** Cycles automatic → on → off → automatic. */
    fun cycleManual(ctx: Context) {
        val next = when (manual.intValue) {
            -1 -> 1
            1 -> 0
            else -> -1
        }
        manual.intValue = next
        ctx.getSharedPreferences("avrora", Context.MODE_PRIVATE).edit().putInt("wlManual", next).apply()
        applyEffective()
    }

    // The manual setting wins over the automatic result.
    private fun applyEffective() {
        whitelist.value = when (manual.intValue) {
            1 -> true
            0 -> false
            else -> confirmed == NetMode.WHITELIST
        }
    }

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
     * One check round over the mobile network.
     * - Foreign services answer → open.
     * - Foreign ones do not, but domestic ones do → white list.
     * - Nothing answers, but the names resolve and connections time out → white list
     *   (the usual picture when a white list is on and our services are not in it).
     * - Nothing resolves at all → no access.
     * A change is confirmed only after two equal rounds in a row.
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
            (foreign + domestic).map { url -> async(Dispatchers.IO) { checkHost(cellular, url) } }.awaitAll()
        }
        lastResults.value = results

        val answered = results.mapNotNull { it.ms }
        // Ping = average answer time of the sites that did respond.
        pingMs.intValue = if (answered.isEmpty()) -1 else answered.average().toInt()

        val foreignOk = results.count { it.url in foreign && it.ms != null }
        val domesticOk = results.count { it.url in domestic && it.ms != null }
        val resolved = results.count { it.dns }
        val timeouts = results.count { it.timeout }
        val half = results.size / 2
        val mode = when {
            foreignOk >= 2 -> NetMode.OPEN
            foreignOk == 0 && domesticOk >= 1 -> NetMode.WHITELIST
            // Names resolve, but connections hang: the typical picture of a white list.
            answered.isEmpty() && resolved >= half && timeouts >= half -> NetMode.WHITELIST
            answered.isEmpty() -> NetMode.NO_ACCESS
            else -> NetMode.UNKNOWN
        }
        observe(ctx, mode)
    }

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
        applyEffective()
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

    // Bound to the cellular network, so requests go out there even when a VPN is active.
    private fun checkHost(network: Network, url: String): Probe {
        val host = URL(url).host
        val resolved = runCatching { network.getAllByName(host).isNotEmpty() }.getOrDefault(false)
        if (!resolved) return Probe(url, null, dns = false, timeout = false)

        val t0 = System.nanoTime()
        return try {
            val c = network.openConnection(URL(url)) as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.requestMethod = "HEAD"
            c.instanceFollowRedirects = false
            val code = c.responseCode
            c.disconnect()
            val ms = if (code > 0) (System.nanoTime() - t0) / 1_000_000 else null
            Probe(url, ms, dns = true, timeout = false)
        } catch (e: SocketTimeoutException) {
            Probe(url, null, dns = true, timeout = true)
        } catch (e: Exception) {
            Probe(url, null, dns = true, timeout = false)
        }
    }
}
