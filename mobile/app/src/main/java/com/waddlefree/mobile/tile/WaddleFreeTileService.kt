package com.waddlefree.mobile.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
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
import kotlinx.coroutines.launch

class WaddleFreeTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusJob: Job? = null
    private var actionJob: Job? = null
    private var hasVerifiedStatusThisSession = false
    private var lastActionCompletedAtMs = 0L

    override fun onTileAdded() {
        super.onTileAdded()
        val prefs = MobilePrefs(this)
        applyTileVisuals(
            prefs = prefs,
            isAwake = prefs.lastKnownAwake,
            overrideSubtitle = null
        )
        refreshLiveStatus()
    }

    override fun onStartListening() {
        super.onStartListening()
        val prefs = MobilePrefs(this)
        hasVerifiedStatusThisSession = false

        // Do not overwrite tile visuals if an action is currently running
        if (actionJob?.isActive == true) return

        applyTileVisuals(
            prefs = prefs,
            isAwake = prefs.lastKnownAwake,
            overrideSubtitle = null
        )
        refreshLiveStatus()
    }

    override fun onStopListening() {
        statusJob?.cancel()
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

        actionJob = serviceScope.launch {
            try {
                // If the initial status check from pulling down the shade is still in flight,
                // wait for it (or verify now) so the very first tap always acts on the true PC state
                if (statusJob?.isActive == true) {
                    statusJob?.join()
                } else if (!hasVerifiedStatusThisSession && hasValidIp) {
                    val live = PcCompanionClient.checkPcStatus(ip, mac, prefs.deviceId, prefs.cachedPort)
                    if (live.isAwake) {
                        if (live.pcName.isNotBlank()) prefs.lastPcName = live.pcName
                        if (live.activePort in 1024..49151) prefs.cachedPort = live.activePort
                    }
                    prefs.lastKnownAwake = live.isAwake
                    hasVerifiedStatusThisSession = true
                }

                val isCurrentlyAwake = prefs.lastKnownAwake

                if (isCurrentlyAwake && hasValidIp) {
                    // PC is Awake -> Put PC to Sleep immediately
                    prefs.lastKnownAwake = false
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
                        applyTileVisuals(
                            prefs = prefs,
                            isAwake = false,
                            overrideSubtitle = "Sleeping • Tap to wake"
                        )
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
        prefs.lastKnownAwake = true
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

        // Poll in background after waking so the subtitle updates once Windows is online
        if (PcCompanionClient.isValidIpv4(ip)) {
            for (attempt in 1..6) {
                delay(2000)
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
                    hasVerifiedStatusThisSession = true
                    applyTileVisuals(prefs = prefs, isAwake = true, overrideSubtitle = null)
                    return
                }
            }
        }

        applyTileVisuals(prefs = prefs, isAwake = true, overrideSubtitle = "Magic Packet sent")
    }

    private fun refreshLiveStatus() {
        val prefs = MobilePrefs(this)
        val ip = prefs.fullIp
        val mac = prefs.fullMac

        if (!PcCompanionClient.isValidIpv4(ip)) {
            if (PcCompanionClient.parseMacBytes(mac) == null) {
                applyTileVisuals(prefs = prefs, isAwake = false, overrideSubtitle = "Tap to set up")
            }
            return
        }

        statusJob?.cancel()
        statusJob = serviceScope.launch {
            val res = PcCompanionClient.checkPcStatus(
                ipAddress = ip,
                macAddress = mac,
                deviceId = prefs.deviceId,
                cachedPort = prefs.cachedPort
            )
            // Do not overwrite if user started an action while status check was running
            if (actionJob?.isActive == true) return@launch

            if (res.isAwake) {
                if (res.pcName.isNotBlank()) prefs.lastPcName = res.pcName
                if (res.activePort in 1024..49151) prefs.cachedPort = res.activePort
            }
            prefs.lastKnownAwake = res.isAwake
            hasVerifiedStatusThisSession = true
            applyTileVisuals(
                prefs = prefs,
                isAwake = res.isAwake,
                overrideSubtitle = null
            )
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
