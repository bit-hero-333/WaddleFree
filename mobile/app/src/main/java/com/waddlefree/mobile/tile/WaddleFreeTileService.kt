package com.waddlefree.mobile.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.waddlefree.mobile.MainActivity
import com.waddlefree.mobile.R
import com.waddlefree.mobile.data.MobilePrefs
import com.waddlefree.mobile.network.PcCompanionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WaddleFreeTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusJob: Job? = null
    private var pollingJob: Job? = null
    private var actionJob: Job? = null
    private var isListening = false
    private var lastVerifiedAtMs = 0L
    private var lastActionCompletedAtMs = 0L

    override fun onTileAdded() {
        super.onTileAdded()
        val prefs = MobilePrefs(this)
        applyTileVisuals(
            prefs = prefs,
            isAwake = prefs.lastKnownAwake,
            overrideSubtitle = null
        )
        startLiveRefresh(showCheckingHint = false)
    }

    override fun onStartListening() {
        super.onStartListening()
        isListening = true
        val prefs = MobilePrefs(this)

        // Do not overwrite tile visuals if an action is currently running
        if (actionJob?.isActive == true) return

        if (!isLocalNetworkConnected()) {
            applyTileVisuals(prefs = prefs, isAwake = false, overrideSubtitle = "No Wi-Fi")
            return
        }

        val isStale = (SystemClock.elapsedRealtime() - lastVerifiedAtMs) > 2500L
        applyTileVisuals(
            prefs = prefs,
            isAwake = prefs.lastKnownAwake,
            overrideSubtitle = if (isStale && PcCompanionClient.isValidIpv4(prefs.fullIp)) "Checking…" else null
        )

        startLiveRefresh(showCheckingHint = isStale)
        startListeningPollLoop()
    }

    override fun onStopListening() {
        isListening = false
        pollingJob?.cancel()
        pollingJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val now = SystemClock.elapsedRealtime()

        // Ignore rapid successive clicks while an action is running or during the 3-second cooldown
        // so extra taps never cancel an in-flight command or flip modes back and forth
        if (actionJob?.isActive == true || (now - lastActionCompletedAtMs) < 3000L) {
            return
        }

        val prefs = MobilePrefs(this)
        val ip = prefs.fullIp
        val mac = prefs.fullMac

        val hasValidIp = PcCompanionClient.isValidIpv4(ip)
        val hasValidMac = PcCompanionClient.parseMacBytes(mac) != null

        if (!hasValidIp && !hasValidMac) {
            openMainApp()
            return
        }

        if (!isLocalNetworkConnected()) {
            applyTileVisuals(prefs = prefs, isAwake = false, overrideSubtitle = "No Wi-Fi")
            return
        }

        actionJob = serviceScope.launch {
            try {
                // Always act on a freshly verified PC status (within the last 2.5 seconds)
                val needsVerify = (SystemClock.elapsedRealtime() - lastVerifiedAtMs) > 2500L
                if (statusJob?.isActive == true) {
                    statusJob?.join()
                } else if (needsVerify && hasValidIp) {
                    applyTileVisuals(
                        prefs = prefs,
                        isAwake = prefs.lastKnownAwake,
                        overrideSubtitle = "Checking…"
                    )
                    verifyAndSaveStatus(prefs)
                }

                val isCurrentlyAwake = prefs.lastKnownAwake

                if (isCurrentlyAwake && hasValidIp) {
                    // PC is Awake -> Put PC to Sleep immediately
                    applyTileVisuals(
                        prefs = prefs,
                        isAwake = false,
                        overrideSubtitle = "Putting to sleep…"
                    )

                    val sentSleep = PcCompanionClient.sendSleepCommand(
                        ipAddress = ip,
                        macAddress = mac,
                        deviceId = prefs.deviceId,
                        cachedPort = prefs.cachedPort
                    )

                    lastActionCompletedAtMs = SystemClock.elapsedRealtime()

                    if (sentSleep) {
                        prefs.lastKnownAwake = false
                        lastVerifiedAtMs = SystemClock.elapsedRealtime()
                        applyTileVisuals(
                            prefs = prefs,
                            isAwake = false,
                            overrideSubtitle = "Sleeping • Tap to wake"
                        )
                        // Confirm PC actually entered sleep a moment later
                        delay(2500)
                        if (isListening) {
                            verifyAndSaveStatus(prefs)
                            applyTileVisuals(prefs = prefs, isAwake = prefs.lastKnownAwake, overrideSubtitle = null)
                        }
                    } else {
                        // PC didn't answer /sleep (was already asleep) -> send Wake-on-LAN instead
                        if (hasValidMac) {
                            executeWake(prefs, ip, mac)
                        } else {
                            prefs.lastKnownAwake = false
                            applyTileVisuals(prefs = prefs, isAwake = false, overrideSubtitle = null)
                        }
                    }
                } else {
                    // PC is Sleeping -> Wake PC via Magic Packet
                    if (!hasValidMac) {
                        openMainApp()
                        return@launch
                    }
                    executeWake(prefs, ip, mac)
                }
            } finally {
                lastActionCompletedAtMs = SystemClock.elapsedRealtime()
            }
        }
    }

    private suspend fun executeWake(prefs: MobilePrefs, ip: String, mac: String) {
        applyTileVisuals(
            prefs = prefs,
            isAwake = true,
            overrideSubtitle = "Waking PC…"
        )

        val packetSent = PcCompanionClient.sendWakeOnLanMagicPacket(ip, mac)
        lastActionCompletedAtMs = SystemClock.elapsedRealtime()

        if (!packetSent) {
            prefs.lastKnownAwake = false
            applyTileVisuals(
                prefs = prefs,
                isAwake = false,
                overrideSubtitle = "Invalid MAC address"
            )
            return
        }

        // Poll until Windows actually responds online; never leave lastKnownAwake=true unverified
        if (PcCompanionClient.isValidIpv4(ip)) {
            for (attempt in 1..10) {
                delay(1500)
                val status = PcCompanionClient.checkPcStatus(
                    ipAddress = ip,
                    macAddress = mac,
                    deviceId = prefs.deviceId,
                    cachedPort = prefs.cachedPort
                )
                if (status.isAwake) {
                    if (status.pcName.isNotBlank()) prefs.lastPcName = status.pcName
                    if (status.activePort in 1024..49151) prefs.cachedPort = status.activePort
                    prefs.lastKnownAwake = true
                    lastVerifiedAtMs = SystemClock.elapsedRealtime()
                    applyTileVisuals(prefs = prefs, isAwake = true, overrideSubtitle = null)
                    return
                }
            }
        }

        prefs.lastKnownAwake = false
        lastVerifiedAtMs = SystemClock.elapsedRealtime()
        applyTileVisuals(prefs = prefs, isAwake = false, overrideSubtitle = "No reply • Tap to retry")
    }

    private fun startLiveRefresh(showCheckingHint: Boolean) {
        val prefs = MobilePrefs(this)
        val ip = prefs.fullIp
        val mac = prefs.fullMac

        if (!PcCompanionClient.isValidIpv4(ip)) {
            if (PcCompanionClient.parseMacBytes(mac) == null) {
                applyTileVisuals(prefs = prefs, isAwake = false, overrideSubtitle = "Tap to set up")
            }
            return
        }

        if (statusJob?.isActive == true) return

        statusJob = serviceScope.launch {
            if (showCheckingHint && actionJob?.isActive != true) {
                applyTileVisuals(prefs = prefs, isAwake = prefs.lastKnownAwake, overrideSubtitle = "Checking…")
            }
            verifyAndSaveStatus(prefs)
            if (actionJob?.isActive != true) {
                applyTileVisuals(
                    prefs = prefs,
                    isAwake = prefs.lastKnownAwake,
                    overrideSubtitle = null
                )
            }
        }
    }

    private fun startListeningPollLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive && isListening) {
                delay(3500)
                if (!isListening || actionJob?.isActive == true) continue
                val prefs = MobilePrefs(this@WaddleFreeTileService)
                if (!isLocalNetworkConnected()) {
                    applyTileVisuals(prefs = prefs, isAwake = false, overrideSubtitle = "No Wi-Fi")
                    continue
                }
                if (PcCompanionClient.isValidIpv4(prefs.fullIp)) {
                    verifyAndSaveStatus(prefs)
                    if (actionJob?.isActive != true && isListening) {
                        applyTileVisuals(
                            prefs = prefs,
                            isAwake = prefs.lastKnownAwake,
                            overrideSubtitle = null
                        )
                    }
                }
            }
        }
    }

    private suspend fun verifyAndSaveStatus(prefs: MobilePrefs) {
        val ip = prefs.fullIp
        val mac = prefs.fullMac
        if (!PcCompanionClient.isValidIpv4(ip)) return

        var res = PcCompanionClient.checkPcStatus(
            ipAddress = ip,
            macAddress = mac,
            deviceId = prefs.deviceId,
            cachedPort = prefs.cachedPort
        )

        // If previously awake and first probe missed (e.g. Wi-Fi power-save wakeup), do 1 quick retry
        if (!res.isAwake && prefs.lastKnownAwake && isLocalNetworkConnected()) {
            delay(250)
            res = PcCompanionClient.checkPcStatus(
                ipAddress = ip,
                macAddress = mac,
                deviceId = prefs.deviceId,
                cachedPort = prefs.cachedPort
            )
        }

        if (res.isAwake) {
            if (res.pcName.isNotBlank()) prefs.lastPcName = res.pcName
            if (res.activePort in 1024..49151) prefs.cachedPort = res.activePort
        }
        prefs.lastKnownAwake = res.isAwake
        lastVerifiedAtMs = SystemClock.elapsedRealtime()
    }

    private fun isLocalNetworkConnected(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            true
        }
    }

    private fun applyTileVisuals(
        prefs: MobilePrefs,
        isAwake: Boolean,
        overrideSubtitle: String?
    ) {
        val tile = qsTile ?: return
        val hasConfig = PcCompanionClient.isValidIpv4(prefs.fullIp) ||
            PcCompanionClient.parseMacBytes(prefs.fullMac) != null

        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile)
        tile.label = "WaddleFree"

        val subtitleText = when {
            overrideSubtitle != null -> overrideSubtitle
            !hasConfig -> "Tap to set up"
            isAwake -> {
                val name = prefs.lastPcName
                if (name.isNotBlank()) "Awake • $name" else "Awake • Tap to sleep"
            }
            else -> "Sleeping • Tap to wake"
        }

        tile.state = if (isAwake && hasConfig) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleText
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = subtitleText
        }
        tile.contentDescription = "WaddleFree: $subtitleText"

        tile.updateTile()
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun requestSync(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, WaddleFreeTileService::class.java)
                )
            } catch (_: Exception) {
                // Ignore if tile is not currently added to Quick Settings
            }
        }
    }
}
