package com.waddlefree.mobile.data

import android.content.Context
import java.util.UUID

class MobilePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("waddlefree_prefs", Context.MODE_PRIVATE)

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

    var themePreference: String
        get() = prefs.getString("theme_pref", "System") ?: "System"
        set(value) = prefs.edit().putString("theme_pref", value).apply()

    var textScale: Float
        get() = prefs.getFloat("text_scale", 1.0f)
        set(value) = prefs.edit().putFloat("text_scale", value).apply()

    var highContrast: Boolean
        get() = prefs.getBoolean("high_contrast", false)
        set(value) = prefs.edit().putBoolean("high_contrast", value).apply()

    var boldBodyText: Boolean
        get() = prefs.getBoolean("bold_body", false)
        set(value) = prefs.edit().putBoolean("bold_body", value).apply()

    var primaryTextColor: String
        get() = prefs.getString("primary_text_color", "Auto") ?: "Auto"
        set(value) = prefs.edit().putString("primary_text_color", value).apply()

    var secondaryTextColor: String
        get() = prefs.getString("secondary_text_color", "Auto") ?: "Auto"
        set(value) = prefs.edit().putString("secondary_text_color", value).apply()

    var autoCheckPreset: String
        get() = prefs.getString("auto_check_preset", "1 m") ?: "1 m"
        set(value) = prefs.edit().putString("auto_check_preset", value).apply()

    var customCheckParts: List<String>
        get() {
            val raw = prefs.getString("custom_check_smh", "30:00:00") ?: "30:00:00"
            val parts = raw.split(":")
            return List(3) { idx -> parts.getOrNull(idx)?.filter { it.isDigit() }?.take(2).orEmpty() }
        }
        set(value) {
            val joined = List(3) { idx -> value.getOrNull(idx)?.filter { it.isDigit() }?.take(2).orEmpty() }.joinToString(":")
            prefs.edit().putString("custom_check_smh", joined).apply()
        }
}
