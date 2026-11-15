package com.waddlefree.tv.data

import android.content.Context
import java.util.UUID

class TvPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("waddlefree_tv_prefs", Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            var id = prefs.getString("device_id", null)
            if (id.isNullOrBlank()) {
                id = UUID.randomUUID().toString().take(12)
                prefs.edit().putString("device_id", id).apply()
            }
            return id
        }

    var ipOctets: List<String>
        get() {
            val raw = prefs.getString("ip_octets", "192.168.178.") ?: "192.168.178."
            val parts = raw.split(".")
            return List(4) { idx -> parts.getOrNull(idx)?.take(3).orEmpty() }
        }
        set(value) {
            val joined = List(4) { idx -> value.getOrNull(idx)?.take(3).orEmpty() }.joinToString(".")
            prefs.edit().putString("ip_octets", joined).apply()
        }

    var macOctets: List<String>
        get() {
            val raw = prefs.getString("mac_octets", ":::::") ?: ":::::"
            val parts = raw.split(":")
            return List(6) { idx -> parts.getOrNull(idx)?.take(2)?.uppercase().orEmpty() }
        }
        set(value) {
            val joined = List(6) { idx -> value.getOrNull(idx)?.take(2)?.uppercase().orEmpty() }.joinToString(":")
            prefs.edit().putString("mac_octets", joined).apply()
        }

    var lastPcName: String
        get() = prefs.getString("last_pc_name", "") ?: ""
        set(value) = prefs.edit().putString("last_pc_name", value).apply()

    var cachedPort: Int
        get() = prefs.getInt("cached_port", 0)
        set(value) = prefs.edit().putInt("cached_port", value).apply()

    var lastKnownAwake: Boolean
        get() = prefs.getBoolean("last_known_awake", false)
        set(value) = prefs.edit().putBoolean("last_known_awake", value).apply()

    val fullIp: String
        get() = ipOctets.joinToString(".")

    val fullMac: String
        get() = macOctets.joinToString(":")

    var themePreference: String
        get() = prefs.getString("theme_pref", "Dark") ?: "Dark"
        set(value) = prefs.edit().putString("theme_pref", value).apply()

    var themePreset: String
        get() = prefs.getString("theme_preset", "Default") ?: "Default"
        set(value) = prefs.edit().putString("theme_preset", value).apply()

    var textScale: Float
        get() = prefs.getFloat("text_scale", 1.0f)
        set(value) = prefs.edit().putFloat("text_scale", value).apply()

    var highContrast: Boolean
        get() = prefs.getBoolean("high_contrast", false)
        set(value) = prefs.edit().putBoolean("high_contrast", value).apply()

    var boldBodyText: Boolean
        get() = prefs.getBoolean("bold_body", false)
        set(value) = prefs.edit().putBoolean("bold_body", value).apply()

    var autoCheckPreset: String
        get() = prefs.getString("auto_check_preset", "1 m") ?: "1 m"
        set(value) = prefs.edit().putString("auto_check_preset", value).apply()
}
