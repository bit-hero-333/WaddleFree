package com.waddlefree.mobile.network

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

data class PcStatusResult(
    val isAwake: Boolean,
    val pcName: String = "",
    val activePort: Int = 0
)

object PcCompanionClient {
    private const val ZONE2_MIN_PORT = 20000
    private const val ZONE2_MAX_PORT = 49151

    /**
     * Exact match with CompanionServer.DeriveInitialZone2Port(macAddress) on the Windows PC.
     */
    fun deriveInitialZone2Port(macAddress: String): Int {
        val normalized = macAddress
            .replace(":", "")
            .replace("-", "")
            .trim()
            .uppercase()

        var hash: UInt = 2166136261u
        for (c in normalized) {
            hash = hash xor c.code.toUInt()
            hash *= 16777619u
        }

        val range = (ZONE2_MAX_PORT - ZONE2_MIN_PORT - 20).toUInt()
        return ZONE2_MIN_PORT + (hash % range).toInt()
    }

    fun getDeviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val raw = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }.trim()
        return raw.ifEmpty { "Android Phone" }.replaceFirstChar { it.uppercase() }
    }

    suspend fun checkPcStatus(
        ipAddress: String,
        macAddress: String,
        deviceId: String,
        cachedPort: Int
    ): PcStatusResult = withContext(Dispatchers.IO) {
        if (!isValidIpv4(ipAddress)) return@withContext PcStatusResult(isAwake = false)

        val basePort = deriveInitialZone2Port(macAddress)
        val candidatePorts = buildList {
            if (cachedPort in 1024..ZONE2_MAX_PORT) add(cachedPort)
            for (offset in 0..4) {
                var p = basePort + offset
                if (p > ZONE2_MAX_PORT) p = ZONE2_MIN_PORT + (p - ZONE2_MAX_PORT - 1)
                if (!contains(p)) add(p)
            }
        }

        coroutineScope {
            val deferreds = candidatePorts.map { port ->
                async {
                    queryHttpEndpoint(ipAddress, port, "/status", deviceId, connectTimeoutMs = 550, readTimeoutMs = 700)
                }
            }
            val results = deferreds.awaitAll()
            results.firstOrNull { it.isAwake } ?: PcStatusResult(isAwake = false)
        }
    }

    suspend fun sendSleepCommand(
        ipAddress: String,
        macAddress: String,
        deviceId: String,
        cachedPort: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isValidIpv4(ipAddress)) return@withContext false

        val basePort = deriveInitialZone2Port(macAddress)
        val candidatePorts = buildList {
            if (cachedPort in 1024..ZONE2_MAX_PORT) add(cachedPort)
            for (offset in 0..4) {
                var p = basePort + offset
                if (p > ZONE2_MAX_PORT) p = ZONE2_MIN_PORT + (p - ZONE2_MAX_PORT - 1)
                if (!contains(p)) add(p)
            }
        }

        coroutineScope {
            val deferreds = candidatePorts.map { port ->
                async {
                    queryHttpEndpoint(ipAddress, port, "/sleep", deviceId, connectTimeoutMs = 550, readTimeoutMs = 700)
                }
            }
            val results = deferreds.awaitAll()
            results.any { it.isAwake }
        }
    }

    private fun queryHttpEndpoint(
        ipAddress: String,
        port: Int,
        path: String,
        deviceId: String,
        connectTimeoutMs: Int = 550,
        readTimeoutMs: Int = 700
    ): PcStatusResult {
        var conn: HttpURLConnection? = null
        return try {
            val deviceName = getDeviceDisplayName()
            val encodedId = URLEncoder.encode(deviceId, "UTF-8")
            val encodedName = URLEncoder.encode(deviceName, "UTF-8")
            val url = URL("http://$ipAddress:$port$path?deviceId=$encodedId&deviceName=$encodedName")

            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = if (path == "/sleep") "POST" else "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("X-Device-Id", deviceId)
                setRequestProperty("X-Device-Name", deviceName)
            }

            val code = conn.responseCode
            if (code == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(body)
                val pcName = json.optString("pcName", "Windows PC")
                val reportedPort = json.optInt("port", port)
                PcStatusResult(isAwake = true, pcName = pcName, activePort = reportedPort)
            } else {
                PcStatusResult(isAwake = false)
            }
        } catch (_: Exception) {
            PcStatusResult(isAwake = false)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Sends standard 102-byte Wake-on-LAN Magic Packet across global broadcast,
     * subnet broadcast (e.g. 192.168.178.255), and direct target IP on UDP ports 9 & 7.
     */
    suspend fun sendWakeOnLanMagicPacket(ipAddress: String, macAddress: String): Boolean =
        withContext(Dispatchers.IO) {
            val macBytes = parseMacBytes(macAddress) ?: return@withContext false

            val packetBytes = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) {
                packetBytes[i] = 0xFF.toByte()
            }
            for (rep in 0 until 16) {
                System.arraycopy(macBytes, 0, packetBytes, 6 + rep * 6, 6)
            }

            val targets = mutableSetOf("255.255.255.255")
            if (isValidIpv4(ipAddress)) {
                targets.add(ipAddress)
                val parts = ipAddress.split(".")
                if (parts.size == 4) {
                    targets.add("${parts[0]}.${parts[1]}.${parts[2]}.255")
                }
            }

            var sentAny = false
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (targetHost in targets) {
                    for (wolPort in intArrayOf(9, 7)) {
                        try {
                            val addr = InetAddress.getByName(targetHost)
                            val dgram = DatagramPacket(packetBytes, packetBytes.size, addr, wolPort)
                            socket.send(dgram)
                            sentAny = true
                        } catch (_: Exception) {
                            // Continue trying other broadcast addresses
                        }
                    }
                }
            }
            sentAny
        }

    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull()
            n != null && n in 0..255
        }
    }

    fun parseMacBytes(mac: String): ByteArray? {
        val cleaned = mac.replace(":", "").replace("-", "").trim()
        if (cleaned.length != 12) return null
        val bytes = ByteArray(6)
        return try {
            for (i in 0 until 6) {
                bytes[i] = cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            bytes
        } catch (_: Exception) {
            null
        }
    }
}
