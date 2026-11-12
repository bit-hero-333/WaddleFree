package com.waddlefree.mobile

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.waddlefree.mobile.data.MobilePrefs
import com.waddlefree.mobile.network.PcCompanionClient
import com.waddlefree.mobile.tile.WaddleFreeTileService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = MobilePrefs(this)
        setContent {
            WaddleFreeMobileApp(prefs)
        }
    }
}

data class MobileFaqItem(
    val question: String,
    val answer: String
)

data class AppPalette(
    val windowBg: Color,
    val tileBg: Color,
    val tileBorder: Color,
    val menuSurface: Color,
    val popupBg: Color,
    val faqAnswerBg: Color,
    val subtleSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSubtle: Color
)

private val AllowedTextScales = floatArrayOf(0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 1.75f, 2.00f)

private val AutoCheckPresets = listOf(
    "None",
    "30 s",
    "1 m",
    "2 m",
    "5 m",
    "10 m",
    "30 m",
    "45 m",
    "1 h",
    "Custom"
)

private fun computeAutoCheckIntervalMillis(preset: String, customParts: List<String>): Long {
    return when (preset) {
        "None" -> 0L
        "30 s" -> 30_000L
        "1 m" -> 60_000L
        "2 m" -> 120_000L
        "5 m" -> 300_000L
        "10 m" -> 600_000L
        "30 m" -> 1_800_000L
        "45 m" -> 2_700_000L
        "1 h" -> 3_600_000L
        "Custom" -> {
            // Left-to-right: hh : mm : ss
            val hh = customParts.getOrNull(0)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val mm = customParts.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val ss = customParts.getOrNull(2)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val totalSeconds = (hh * 3600L) + (mm * 60L) + ss
            if (totalSeconds <= 0L) 0L else totalSeconds * 1000L
        }
        else -> 60_000L
    }
}

/**
 * Converts any overflow in seconds or minutes (e.g. 120s -> 00:02:00, 140s -> 00:02:20)
 * into clean 2-digit hh:mm:ss slots when confirmed.
 */
private fun normalizeCustomHmsParts(parts: MutableList<String>, onChanged: () -> Unit) {
    if (parts.size < 3) return
    val hh = parts[0].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val mm = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val ss = parts[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val totalSeconds = (hh * 3600L) + (mm * 60L) + ss
    if (totalSeconds <= 0L) {
        if (parts.any { it.isNotEmpty() }) {
            parts[0] = "00"
            parts[1] = "00"
            parts[2] = "00"
            onChanged()
        }
        return
    }
    val maxTotalSeconds = (99L * 3600L) + (59L * 60L) + 59L
    val clampedTotal = totalSeconds.coerceAtMost(maxTotalSeconds)
    val normHh = clampedTotal / 3600L
    val remAfterHours = clampedTotal % 3600L
    val normMm = remAfterHours / 60L
    val normSs = remAfterHours % 60L

    val newHh = String.format("%02d", normHh)
    val newMm = String.format("%02d", normMm)
    val newSs = String.format("%02d", normSs)
    if (parts[0] != newHh || parts[1] != newMm || parts[2] != newSs) {
        parts[0] = newHh
        parts[1] = newMm
        parts[2] = newSs
        onChanged()
    }
}

@Composable
fun WaddleFreeMobileApp(prefs: MobilePrefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    var autoCheckPreset by remember { mutableStateOf(prefs.autoCheckPreset) }
    val customCheckParts = remember {
        val init = prefs.customCheckParts
        mutableStateListOf(init[0], init[1], init[2])
    }

    // Automatically clear text field focus (removing the flashing '|' bar) and normalize hh:mm:ss whenever the keyboard is hidden
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    var wasImeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isImeVisible) {
        if (wasImeVisible && !isImeVisible) {
            normalizeCustomHmsParts(customCheckParts) {
                prefs.customCheckParts = customCheckParts.toList()
            }
            focusManager.clearFocus()
        }
        wasImeVisible = isImeVisible
    }

    var themePref by remember { mutableStateOf(prefs.themePreference) }
    var themePreset by remember { mutableStateOf(prefs.themePreset) }
    var textScale by remember { mutableFloatStateOf(prefs.textScale.coerceIn(0.50f, 2.00f)) }
    var highContrast by remember { mutableStateOf(prefs.highContrast) }
    var boldBodyText by remember { mutableStateOf(prefs.boldBodyText) }
    var accentColorSetting by remember { mutableStateOf(prefs.accentColor) }
    var windowBgColorSetting by remember { mutableStateOf(prefs.windowBgColor) }
    var tileBgColorSetting by remember { mutableStateOf(prefs.tileBgColor) }
    var primaryColorSetting by remember { mutableStateOf(prefs.primaryTextColor) }
    var secondaryColorSetting by remember { mutableStateOf(prefs.secondaryTextColor) }
    var iconBgColorSetting by remember { mutableStateOf(prefs.iconBgColor) }
    var iconFgColorSetting by remember { mutableStateOf(prefs.iconFgColor) }

    val ipOctets = remember {
        val initial = prefs.ipOctets
        mutableStateListOf(initial[0], initial[1], initial[2], initial[3])
    }
    val macOctets = remember {
        val initial = prefs.macOctets
        mutableStateListOf(initial[0], initial[1], initial[2], initial[3], initial[4], initial[5])
    }

    val fullIp = ipOctets.joinToString(".")
    val fullMac = macOctets.joinToString(":")

    var pcName by remember { mutableStateOf(prefs.lastPcName) }
    var cachedPort by remember { mutableIntStateOf(prefs.cachedPort) }
    var isPcAwake by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(PcCompanionClient.isValidIpv4(fullIp)) }
    var hasCompletedInitialCheck by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf("") }

    var isOptionsOpen by remember { mutableStateOf(false) }
    var isPortInfoOpen by remember { mutableStateOf(false) }
    var isGuideOpen by remember { mutableStateOf(false) }
    var isTextSettingsOpen by remember { mutableStateOf(false) }

    val expandedFaqIndices = remember { mutableStateListOf<Int>() }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themePref.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    val dynamicAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        Color(0xFF3B82F6)
    }

    val palette = remember(
        isDark,
        themePreset,
        highContrast,
        accentColorSetting,
        windowBgColorSetting,
        tileBgColorSetting,
        primaryColorSetting,
        secondaryColorSetting,
        dynamicAccent
    ) {
        buildPalette(
            isDark = isDark,
            themePreset = themePreset,
            highContrast = highContrast,
            accentSetting = accentColorSetting,
            windowBgSetting = windowBgColorSetting,
            tileBgSetting = tileBgColorSetting,
            primarySetting = primaryColorSetting,
            secondarySetting = secondaryColorSetting,
            systemAccent = dynamicAccent
        )
    }

    LaunchedEffect(iconBgColorSetting, iconFgColorSetting) {
        updateTaskDescriptionIcon(context, iconBgColorSetting, iconFgColorSetting)
    }

    val uiScale = 0.55f + (0.45f * textScale)
    val bodyWeight = if (boldBodyText) FontWeight.SemiBold else FontWeight.Normal

    val derivedPort = remember(fullMac, cachedPort) {
        if (cachedPort in 1024..49151) cachedPort else PcCompanionClient.deriveInitialZone2Port(fullMac)
    }

    suspend fun refreshStatus(manualTrigger: Boolean = false) {
        if (!PcCompanionClient.isValidIpv4(fullIp)) {
            isPcAwake = false
            isChecking = false
            hasCompletedInitialCheck = true
            if (manualTrigger) {
                feedbackBanner = "Enter your PC's local IPv4 address first"
            }
            return
        }
        isChecking = true
        val res = PcCompanionClient.checkPcStatus(
            ipAddress = fullIp,
            macAddress = fullMac,
            deviceId = prefs.deviceId,
            cachedPort = cachedPort
        )
        isChecking = false
        hasCompletedInitialCheck = true
        isPcAwake = res.isAwake
        prefs.lastKnownAwake = res.isAwake
        if (res.isAwake) {
            if (res.pcName.isNotBlank()) {
                pcName = res.pcName
                prefs.lastPcName = res.pcName
            }
            if (res.activePort in 1024..49151) {
                cachedPort = res.activePort
                prefs.cachedPort = res.activePort
            }
            // Immediately update status text beneath the buttons so stale sleeping/waking text never lingers
            feedbackBanner = if (manualTrigger) {
                "✓ Connected to ${pcName.ifBlank { "PC" }}"
            } else {
                ""
            }
        } else {
            if (manualTrigger) {
                feedbackBanner = "PC is currently sleeping or unreachable"
            } else if (feedbackBanner.startsWith("✓ Connected")) {
                feedbackBanner = ""
            }
        }
        WaddleFreeTileService.requestSync(context)
    }

    val customIntervalSignature = customCheckParts.joinToString(":")
    val intervalMs = remember(autoCheckPreset, customIntervalSignature) {
        computeAutoCheckIntervalMillis(autoCheckPreset, customCheckParts)
    }

    // Initial check when IP/MAC changes
    LaunchedEffect(fullIp, fullMac) {
        if (PcCompanionClient.isValidIpv4(fullIp)) {
            refreshStatus(manualTrigger = false)
        } else {
            isPcAwake = false
            isChecking = false
            hasCompletedInitialCheck = true
        }
    }

    // Configurable automatic status polling loop (respects None, 30 s .. 1 h, and Custom ss:mm:hh)
    LaunchedEffect(fullIp, fullMac, intervalMs) {
        if (intervalMs <= 0L) return@LaunchedEffect
        while (true) {
            delay(intervalMs)
            if (PcCompanionClient.isValidIpv4(fullIp) && !isChecking) {
                refreshStatus(manualTrigger = false)
            }
        }
    }

    val faqList = remember {
        listOf(
            MobileFaqItem(
                question = "How to configure Wake-on-Ethernet (WoL) on Windows 11?",
                answer = "1. On your PC, right-click Start → Device Manager → Network adapters → double-click your Ethernet adapter.\n\n" +
                    "2. Power Management tab: Check 'Allow this device to wake the computer' AND 'Only allow a magic packet to wake the computer'.\n\n" +
                    "3. Advanced tab: Enable 'Wake on Magic Packet' and disable 'Wake on Pattern Match'.\n\n" +
                    "4. In BIOS/UEFI: Enable 'Wake on PCIe / LAN'."
            ),
            MobileFaqItem(
                question = "Where do I find my PC's IP and MAC address?",
                answer = "Open WaddleFree on your Windows PC—both your PC's IP address and MAC address are displayed right on the main tiles."
            ),
            MobileFaqItem(
                question = "How does WaddleFree wake and sleep my PC?",
                answer = "When your PC is sleeping, tapping 'Wake PC' broadcasts a hardware Magic Packet using the MAC address so your motherboard powers up. Once Windows is awake, tapping 'Sleep PC' signals the desktop companion over your local network to put Windows back to sleep."
            ),
            MobileFaqItem(
                question = "How do I use the Quick Settings Tile?",
                answer = "Swipe down your notification shade, tap the pencil (Edit) button, and drag the WaddleFree tile into your active tiles (works in both Small 1×1 and Large 2×1 tile sizes). When the tile is lit up, your PC is awake (tap to sleep); when unlit, your PC is sleeping (tap to wake). Long-pressing the tile opens WaddleFree."
            ),
            MobileFaqItem(
                question = "What if my PC's local IP address changes?",
                answer = "Most home routers assign the same local IP address to your PC automatically. If your router ever changes it after a reboot, you can either update the IP here or reserve a static DHCP lease for your PC in your router settings."
            ),
            MobileFaqItem(
                question = "Do my phone and PC need to be on the same home network?",
                answer = "Yes. Connect your phone to your home Wi-Fi and your PC via Ethernet to the same router so local Wake-on-LAN and sleep signals can reach your PC directly."
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    normalizeCustomHmsParts(customCheckParts) {
                        prefs.customCheckParts = customCheckParts.toList()
                    }
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            },
        color = palette.windowBg
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            val isWideScreen = maxWidth >= 640.dp
            val iconBtnSize = (40f * uiScale).dp

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- TOP BAR ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Title + Status Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = "WaddleFree",
                                fontSize = (17f * textScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.primaryText
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.accentSubtle)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                val statusText = when {
                                    isChecking && !hasCompletedInitialCheck -> "Checking…"
                                    isPcAwake -> if (pcName.isNotBlank()) "Awake • $pcName" else "Awake • Connected"
                                    isChecking -> "Checking…"
                                    else -> "PC Sleeping / Offline"
                                }
                                Text(
                                    text = statusText,
                                    fontSize = (12f * textScale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.primaryText
                                )
                            }
                        }

                        // Right: On Wide Screens, Options Menu opens sideways to the LEFT of Hamburger Button
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isWideScreen) {
                                AnimatedVisibility(
                                    visible = isOptionsOpen,
                                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
                                ) {
                                    OptionsMenuBarContent(
                                        palette = palette,
                                        textScale = textScale,
                                        uiScale = uiScale,
                                        themePref = themePref,
                                        onThemeChange = {
                                            themePref = it
                                            prefs.themePreference = it
                                        },
                                        onOpenTextSettings = { isTextSettingsOpen = true },
                                        autoCheckPreset = autoCheckPreset,
                                        onAutoCheckPresetChange = {
                                            autoCheckPreset = it
                                            prefs.autoCheckPreset = it
                                        },
                                        customCheckParts = customCheckParts,
                                        onCustomCheckPartsChanged = {
                                            prefs.customCheckParts = customCheckParts.toList()
                                        },
                                        focusManager = focusManager,
                                        keyboardController = keyboardController,
                                        derivedPort = derivedPort,
                                        onTogglePortInfo = { isPortInfoOpen = !isPortInfoOpen },
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                }
                            }

                            // Hamburger Menu Button
                            Box(
                                modifier = Modifier
                                    .size(iconBtnSize)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.tileBg)
                                    .border(1.dp, palette.tileBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        focusManager.clearFocus()
                                        isOptionsOpen = !isOptionsOpen
                                        if (!isOptionsOpen) isPortInfoOpen = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.width((16f * uiScale).dp),
                                    verticalArrangement = Arrangement.spacedBy((4f * uiScale).dp)
                                ) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height((2f * uiScale).dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(palette.primaryText)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // On Compact / Portrait Screens, Options Menu opens downward below Top Bar
                    if (!isWideScreen) {
                        AnimatedVisibility(
                            visible = isOptionsOpen,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            OptionsMenuBarContent(
                                palette = palette,
                                textScale = textScale,
                                uiScale = uiScale,
                                themePref = themePref,
                                onThemeChange = {
                                    themePref = it
                                    prefs.themePreference = it
                                },
                                onOpenTextSettings = { isTextSettingsOpen = true },
                                autoCheckPreset = autoCheckPreset,
                                onAutoCheckPresetChange = {
                                    autoCheckPreset = it
                                    prefs.autoCheckPreset = it
                                },
                                customCheckParts = customCheckParts,
                                onCustomCheckPartsChanged = {
                                    prefs.customCheckParts = customCheckParts.toList()
                                },
                                focusManager = focusManager,
                                keyboardController = keyboardController,
                                derivedPort = derivedPort,
                                onTogglePortInfo = { isPortInfoOpen = !isPortInfoOpen },
                                isCompact = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            )
                        }
                    }

                    // Port Info Explanation Box (with clickable Wikipedia link & selectable text)
                    AnimatedVisibility(
                        visible = isPortInfoOpen && isOptionsOpen,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.popupBg)
                                .border(1.5.dp, palette.accent, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            SelectionContainer {
                                Column {
                                    Text(
                                        text = "What is this port?",
                                        fontSize = (13.5f * textScale).sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.primaryText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "An internal local communication channel automatically matched with your PC's MAC address so your phone can check status and send sleep signals locally without clashing with other apps. No router setup is required.",
                                        fontSize = (12.5f * textScale).sp,
                                        fontWeight = bodyWeight,
                                        color = palette.secondaryText
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Learn more about network ports on Wikipedia ↗",
                                        fontSize = (12.5f * textScale).sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.accent,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://en.wikipedia.org/wiki/Port_(computer_networking)")
                                            )
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- MIDDLE: THREE TILES (Side-by-side on wide screens, vertical scroll on portrait phones) ---
                val centerScrollState = rememberScrollState()
                val onWakeAction: () -> Unit = {
                    focusManager.clearFocus()
                    scope.launch {
                        val ok = PcCompanionClient.sendWakeOnLanMagicPacket(fullIp, fullMac)
                        if (!ok) {
                            feedbackBanner = "Enter a valid 12-character MAC address first"
                        } else {
                            feedbackBanner = "✓ Magic Packet sent! Waiting for PC to wake…"
                            // Poll every 2 seconds for up to 16 seconds so the moment the PC wakes up, the UI updates immediately
                            for (attempt in 1..8) {
                                delay(2000)
                                refreshStatus(manualTrigger = false)
                                if (isPcAwake) {
                                    feedbackBanner = "✓ Connected to ${pcName.ifBlank { "PC" }}"
                                    break
                                }
                            }
                        }
                    }
                }

                val onSleepAction: () -> Unit = {
                    focusManager.clearFocus()
                    scope.launch {
                        val ok = PcCompanionClient.sendSleepCommand(fullIp, fullMac, prefs.deviceId, cachedPort)
                        if (ok) {
                            isPcAwake = false
                            prefs.lastKnownAwake = false
                            WaddleFreeTileService.requestSync(context)
                            feedbackBanner = "✓ Sleep signal sent to PC"
                        } else {
                            feedbackBanner = "Could not reach PC—check IP & Wi-Fi"
                            refreshStatus(manualTrigger = false)
                        }
                    }
                }

                val onRefreshAction: () -> Unit = {
                    focusManager.clearFocus()
                    scope.launch {
                        refreshStatus(manualTrigger = true)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(centerScrollState),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PcConnectionTile(
                                modifier = Modifier.weight(1f),
                                palette = palette,
                                textScale = textScale,
                                bodyWeight = bodyWeight,
                                pcName = pcName,
                                isPcAwake = isPcAwake,
                                isChecking = isChecking,
                                hasCompletedInitialCheck = hasCompletedInitialCheck,
                                feedbackBanner = feedbackBanner,
                                onWakeClick = onWakeAction,
                                onSleepClick = onSleepAction,
                                onRefreshClick = onRefreshAction
                            )

                            IpAddressSegmentedTile(
                                modifier = Modifier.weight(1f),
                                palette = palette,
                                textScale = textScale,
                                ipOctets = ipOctets,
                                focusManager = focusManager,
                                keyboardController = keyboardController,
                                onOctetsChanged = {
                                    prefs.ipOctets = ipOctets.toList()
                                }
                            )

                            MacAddressSegmentedTile(
                                modifier = Modifier.weight(1f),
                                palette = palette,
                                textScale = textScale,
                                macOctets = macOctets,
                                focusManager = focusManager,
                                keyboardController = keyboardController,
                                onOctetsChanged = {
                                    prefs.macOctets = macOctets.toList()
                                }
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(centerScrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PcConnectionTile(
                                modifier = Modifier.fillMaxWidth(),
                                palette = palette,
                                textScale = textScale,
                                bodyWeight = bodyWeight,
                                pcName = pcName,
                                isPcAwake = isPcAwake,
                                isChecking = isChecking,
                                hasCompletedInitialCheck = hasCompletedInitialCheck,
                                feedbackBanner = feedbackBanner,
                                onWakeClick = onWakeAction,
                                onSleepClick = onSleepAction,
                                onRefreshClick = onRefreshAction
                            )

                            IpAddressSegmentedTile(
                                modifier = Modifier.fillMaxWidth(),
                                palette = palette,
                                textScale = textScale,
                                ipOctets = ipOctets,
                                focusManager = focusManager,
                                keyboardController = keyboardController,
                                onOctetsChanged = {
                                    prefs.ipOctets = ipOctets.toList()
                                }
                            )

                            MacAddressSegmentedTile(
                                modifier = Modifier.fillMaxWidth(),
                                palette = palette,
                                textScale = textScale,
                                macOctets = macOctets,
                                focusManager = focusManager,
                                keyboardController = keyboardController,
                                onOctetsChanged = {
                                    prefs.macOctets = macOctets.toList()
                                }
                            )
                        }
                    }
                }

                // --- BOTTOM AREA: UPWARD GUIDE / FAQ PANEL + BOTTOM BAR ('?' Button on Left) ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = isGuideOpen,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 480.dp)
                                .fillMaxWidth()
                                .heightIn(max = 310.dp)
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.popupBg)
                                .border(1.5.dp, palette.accent, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Setup Guide & FAQ",
                                    fontSize = (13.5f * textScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryText,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    faqList.forEachIndexed { idx, item ->
                                        val isExpanded = expandedFaqIndices.contains(idx)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(palette.tileBg)
                                                .border(1.dp, palette.tileBorder, RoundedCornerShape(11.dp))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (isExpanded) expandedFaqIndices.remove(idx)
                                                        else expandedFaqIndices.add(idx)
                                                    }
                                                    .padding(horizontal = 13.dp, vertical = 11.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.question,
                                                    fontSize = (13f * textScale).sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = palette.primaryText,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 8.dp)
                                                )
                                                Text(
                                                    text = if (isExpanded) "▴" else "▾",
                                                    fontSize = (13f * textScale).sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.accent
                                                )
                                            }

                                            AnimatedVisibility(visible = isExpanded) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(palette.faqAnswerBg)
                                                        .padding(horizontal = 13.dp, vertical = 11.dp)
                                                ) {
                                                    SelectionContainer {
                                                        Text(
                                                            text = item.answer,
                                                            fontSize = (12.5f * textScale).sp,
                                                            fontWeight = bodyWeight,
                                                            color = palette.secondaryText,
                                                            lineHeight = (18f * textScale).sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Row: Encircled '?' Guide Button on Bottom-Left + Hint Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(iconBtnSize)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.tileBg)
                                .border(1.dp, palette.tileBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    isGuideOpen = !isGuideOpen
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((22f * uiScale).dp)
                                    .border(1.7.dp, palette.primaryText, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "?",
                                    fontSize = (13f * uiScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryText
                                )
                            }
                        }

                        val githubIconPath = remember {
                            PathParser().parsePathString(
                                "M 8 0 C 3.58 0 0 3.58 0 8 c 0 3.54 2.29 6.53 5.47 7.59 " +
                                    "c 0.4 0.07 0.55 -0.17 0.55 -0.38 c 0 -0.19 -0.01 -0.82 -0.01 -1.49 " +
                                    "c -2.01 0.37 -2.53 -0.49 -2.69 -0.94 c -0.09 -0.23 -0.48 -0.94 -0.82 -1.13 " +
                                    "c -0.28 -0.15 -0.68 -0.52 -0.01 -0.53 c 0.63 -0.01 1.08 0.58 1.23 0.82 " +
                                    "c 0.72 1.21 1.87 0.87 2.33 0.66 c 0.07 -0.52 0.28 -0.87 0.51 -1.07 " +
                                    "c -1.78 -0.2 -3.64 -0.89 -3.64 -3.95 c 0 -0.87 0.31 -1.59 0.82 -2.15 " +
                                    "c -0.08 -0.2 -0.36 -1.02 0.08 -2.12 c 0 0 0.67 -0.21 2.2 0.82 " +
                                    "c 0.64 -0.18 1.32 -0.27 2 -0.27 c 0.68 0 1.36 0.09 2 0.27 " +
                                    "c 1.53 -1.04 2.2 -0.82 2.2 -0.82 c 0.44 1.1 0.16 1.92 0.08 2.12 " +
                                    "c 0.51 0.56 0.82 1.27 0.82 2.15 c 0 3.07 -1.87 3.75 -3.65 3.95 " +
                                    "c 0.29 0.25 0.54 0.73 0.54 1.48 c 0 1.07 -0.01 1.93 -0.01 2.2 " +
                                    "c 0 0.21 0.15 0.46 0.55 0.38 C 13.71 14.53 16 11.53 16 8 C 16 3.58 12.42 0 8 0 Z"
                            ).toPath()
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/bit-hero-333/WaddleFree")
                                        )
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Canvas(modifier = Modifier.size((16.5f * uiScale).dp)) {
                                    val s = size.width / 17f
                                    withTransform({
                                        translate(left = 0.5f * s, top = 0.5f * s)
                                        scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
                                    }) {
                                        drawPath(path = githubIconPath, color = palette.accent)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "github.com/bit-hero-333/WaddleFree",
                                    fontSize = (12f * textScale).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.accent,
                                    textDecoration = TextDecoration.Underline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(iconBtnSize))
                    }
                }
            }
        }
    }

    // --- THEME & APPEARANCE MODAL DIALOG ---
    if (isTextSettingsOpen) {
        TextSettingsDialog(
            isDark = isDark,
            palette = palette,
            themePreset = themePreset,
            textScale = textScale,
            uiScale = uiScale,
            highContrast = highContrast,
            boldBodyText = boldBodyText,
            accentColorSetting = accentColorSetting,
            windowBgColorSetting = windowBgColorSetting,
            tileBgColorSetting = tileBgColorSetting,
            primaryColorSetting = primaryColorSetting,
            secondaryColorSetting = secondaryColorSetting,
            iconBgColorSetting = iconBgColorSetting,
            iconFgColorSetting = iconFgColorSetting,
            onPresetChange = { presetKey ->
                themePreset = presetKey
                accentColorSetting = "Auto"
                windowBgColorSetting = "Auto"
                tileBgColorSetting = "Auto"
                primaryColorSetting = "Auto"
                secondaryColorSetting = "Auto"
                prefs.themePreset = presetKey
                prefs.accentColor = "Auto"
                prefs.windowBgColor = "Auto"
                prefs.tileBgColor = "Auto"
                prefs.primaryTextColor = "Auto"
                prefs.secondaryTextColor = "Auto"
            },
            onScaleChange = {
                textScale = it
                prefs.textScale = it
            },
            onHighContrastToggle = {
                highContrast = !highContrast
                prefs.highContrast = highContrast
            },
            onBoldToggle = {
                boldBodyText = !boldBodyText
                prefs.boldBodyText = boldBodyText
            },
            onAccentColorChange = {
                accentColorSetting = it
                prefs.accentColor = it
            },
            onWindowBgColorChange = {
                windowBgColorSetting = it
                prefs.windowBgColor = it
            },
            onTileBgColorChange = {
                tileBgColorSetting = it
                prefs.tileBgColor = it
            },
            onPrimaryColorChange = {
                primaryColorSetting = it
                prefs.primaryTextColor = it
            },
            onSecondaryColorChange = {
                secondaryColorSetting = it
                prefs.secondaryTextColor = it
            },
            onIconBgColorChange = {
                iconBgColorSetting = it
                prefs.iconBgColor = it
            },
            onIconFgColorChange = {
                iconFgColorSetting = it
                prefs.iconFgColor = it
            },
            onReset = {
                themePreset = "Default"
                textScale = 1.0f
                highContrast = false
                boldBodyText = false
                accentColorSetting = "Auto"
                windowBgColorSetting = "Auto"
                tileBgColorSetting = "Auto"
                primaryColorSetting = "Auto"
                secondaryColorSetting = "Auto"
                iconBgColorSetting = "#18181B"
                iconFgColorSetting = "#FFFFFF"
                prefs.themePreset = "Default"
                prefs.textScale = 1.0f
                prefs.highContrast = false
                prefs.boldBodyText = false
                prefs.accentColor = "Auto"
                prefs.windowBgColor = "Auto"
                prefs.tileBgColor = "Auto"
                prefs.primaryTextColor = "Auto"
                prefs.secondaryTextColor = "Auto"
                prefs.iconBgColor = "#18181B"
                prefs.iconFgColor = "#FFFFFF"
                syncLauncherIconAlias(context, prefs, "#18181B", "#FFFFFF")
            },
            onDismiss = {
                isTextSettingsOpen = false
                syncLauncherIconAlias(context, prefs, iconBgColorSetting, iconFgColorSetting)
            }
        )
    }
}

@Composable
private fun OptionsMenuBarContent(
    palette: AppPalette,
    textScale: Float,
    uiScale: Float,
    themePref: String,
    onThemeChange: (String) -> Unit,
    onOpenTextSettings: () -> Unit,
    autoCheckPreset: String,
    onAutoCheckPresetChange: (String) -> Unit,
    customCheckParts: MutableList<String>,
    onCustomCheckPartsChanged: () -> Unit,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    derivedPort: Int,
    onTogglePortInfo: () -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isIntervalDropdownExpanded by remember { mutableStateOf(false) }
    val customFocusRequesters = remember { List(3) { FocusRequester() } }
    val artistPalettePath = remember {
        PathParser().parsePathString(
            "M 12.0 2.2 C 6.4 2.2 1.8 6.5 1.8 11.8 C 1.8 17.1 6.4 21.5 11.9 21.5 " +
                "C 13.5 21.5 14.7 20.3 14.7 18.7 C 14.7 18.0 14.4 17.4 14.0 16.9 " +
                "C 13.6 16.5 13.4 15.9 13.4 15.3 C 13.4 13.9 14.5 12.8 15.9 12.8 " +
                "L 17.7 12.8 C 20.7 12.8 23.0 10.4 23.0 7.5 C 23.0 4.3 18.1 2.2 12.0 2.2 Z"
        ).toPath()
    }

    @Composable
    fun ThemeAndTextControls() {
        Text(
            text = "Theme",
            fontSize = (12f * textScale).sp,
            fontWeight = FontWeight.Medium,
            color = palette.secondaryText,
            modifier = Modifier.padding(end = 6.dp)
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(palette.subtleSurface)
                .padding(2.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Dark" to "Dark", "Light" to "Light", "System" to "Device").forEach { (key, label) ->
                val active = themePref.equals(key, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) palette.accent else Color.Transparent)
                        .clickable { onThemeChange(key) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = (12f * textScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) palette.onAccent else palette.secondaryText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Artist Palette Button for Theme & Appearance
        Box(
            modifier = Modifier
                .size(width = (34f * uiScale).dp, height = (30f * uiScale).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.subtleSurface)
                .border(1.dp, palette.tileBorder, RoundedCornerShape(8.dp))
                .clickable { onOpenTextSettings() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size((20f * uiScale).dp)) {
                val s = size.width / 24f
                withTransform({
                    scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
                }) {
                    drawPath(path = artistPalettePath, color = palette.primaryText)
                    // Thumbhole cutout
                    drawCircle(color = palette.menuSurface, radius = 1.75f, center = Offset(9.75f, 15.8f))
                    // 4 Colorful Paint Dabs
                    drawCircle(color = palette.menuSurface, radius = 2.1f, center = Offset(6.4f, 11.4f))
                    drawCircle(color = Color(0xFFF87171), radius = 1.6f, center = Offset(6.4f, 11.4f))

                    drawCircle(color = palette.menuSurface, radius = 2.1f, center = Offset(9.3f, 7.0f))
                    drawCircle(color = Color(0xFFFBBF24), radius = 1.6f, center = Offset(9.3f, 7.0f))

                    drawCircle(color = palette.menuSurface, radius = 2.1f, center = Offset(14.3f, 6.8f))
                    drawCircle(color = Color(0xFF4ADE80), radius = 1.6f, center = Offset(14.3f, 6.8f))

                    drawCircle(color = palette.menuSurface, radius = 2.1f, center = Offset(18.3f, 9.5f))
                    drawCircle(color = palette.accent, radius = 1.6f, center = Offset(18.3f, 9.5f))
                }
            }
        }
    }

    @Composable
    fun AutoCheckControls() {
        Text(
            text = "Auto-Check",
            fontSize = (12f * textScale).sp,
            fontWeight = FontWeight.Medium,
            color = palette.secondaryText,
            modifier = Modifier.padding(end = 6.dp)
        )

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.subtleSurface)
                    .border(1.dp, palette.tileBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        focusManager.clearFocus()
                        isIntervalDropdownExpanded = true
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = autoCheckPreset,
                    fontSize = (12f * textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryText
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "▾",
                    fontSize = (11f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent
                )
            }

            DropdownMenu(
                expanded = isIntervalDropdownExpanded,
                onDismissRequest = { isIntervalDropdownExpanded = false },
                modifier = Modifier
                    .background(palette.popupBg)
                    .border(1.dp, palette.tileBorder, RoundedCornerShape(8.dp))
            ) {
                AutoCheckPresets.forEach { preset ->
                    val isSelected = autoCheckPreset == preset
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (preset == "Custom") "Custom (hh:mm:ss)" else preset,
                                fontSize = (12.5f * textScale).sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) palette.accent else palette.primaryText
                            )
                        },
                        onClick = {
                            onAutoCheckPresetChange(preset)
                            isIntervalDropdownExpanded = false
                        }
                    )
                }
            }
        }

        if (autoCheckPreset == "Custom") {
            Spacer(modifier = Modifier.width(6.dp))
            val placeholders = listOf("hh", "mm", "ss")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.subtleSurface)
                    .border(1.dp, palette.tileBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                for (idx in 0 until 3) {
                    BasicTextField(
                        value = customCheckParts[idx],
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            val cleaned = digits.trimStart('0').ifEmpty {
                                if (digits.isNotEmpty()) "0" else ""
                            }.take(4)
                            customCheckParts[idx] = cleaned
                            onCustomCheckPartsChanged()
                        },
                        modifier = Modifier
                            .width((30f * uiScale).dp)
                            .focusRequester(customFocusRequesters[idx])
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette.faqAnswerBg)
                            .padding(vertical = 3.dp, horizontal = 2.dp),
                        textStyle = TextStyle(
                            fontSize = (11.5f * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryText,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (idx < 2) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                normalizeCustomHmsParts(customCheckParts, onCustomCheckPartsChanged)
                                if (idx < 2) customFocusRequesters[idx + 1].requestFocus()
                            },
                            onDone = {
                                normalizeCustomHmsParts(customCheckParts, onCustomCheckPartsChanged)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (customCheckParts[idx].isEmpty()) {
                                    Text(
                                        text = placeholders[idx],
                                        fontSize = (10.5f * textScale).sp,
                                        color = palette.secondaryText.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (idx < 2) {
                        Text(
                            text = ":",
                            fontSize = (12f * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun PortControls() {
        Text(
            text = "Port: $derivedPort",
            fontSize = (12f * textScale).sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.primaryText
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size((19f * uiScale).dp)
                .clip(CircleShape)
                .border(1.5.dp, palette.accent, CircleShape)
                .clickable { onTogglePortInfo() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "i",
                fontSize = (11f * uiScale).sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                color = palette.accent
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.menuSurface)
            .border(1.dp, palette.tileBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThemeAndTextControls()
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .width(1.dp)
                            .height((18f * uiScale).dp)
                            .background(palette.tileBorder)
                    )
                    PortControls()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(palette.tileBorder.copy(alpha = 0.5f))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutoCheckControls()
                }
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeAndTextControls()
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height((18f * uiScale).dp)
                        .background(palette.tileBorder)
                )
                AutoCheckControls()
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height((18f * uiScale).dp)
                        .background(palette.tileBorder)
                )
                PortControls()
            }
        }
    }
}

@Composable
private fun PcConnectionTile(
    modifier: Modifier,
    palette: AppPalette,
    textScale: Float,
    bodyWeight: FontWeight,
    pcName: String,
    isPcAwake: Boolean,
    isChecking: Boolean,
    hasCompletedInitialCheck: Boolean,
    feedbackBanner: String,
    onWakeClick: () -> Unit,
    onSleepClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.tileBg)
            .border(1.5.dp, palette.tileBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Connected PC",
                fontSize = (13.5f * textScale).sp,
                fontWeight = FontWeight.Medium,
                color = palette.secondaryText
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.subtleSurface)
                    .clickable(enabled = !isChecking) { onRefreshClick() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isChecking) "Checking…" else "Check Status",
                    fontSize = (11.5f * textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val displayPc = when {
                !hasCompletedInitialCheck && isChecking -> "Checking PC…"
                isPcAwake && pcName.isNotBlank() -> pcName
                isPcAwake -> "PC Online"
                pcName.isNotBlank() -> "$pcName (Sleeping)"
                else -> "Sleeping / Offline"
            }
            Text(
                text = displayPc,
                fontSize = (19f * textScale).sp,
                fontWeight = FontWeight.Bold,
                color = if (isPcAwake) palette.accent else palette.primaryText
            )
            if (isChecking) {
                Spacer(modifier = Modifier.width(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent
                )
            }
        }

        // Primary Wake / Sleep Action Buttons:
        // When PC is awake -> Wake PC is non-interactable (uncolored) & Sleep PC is interactable (accent colored), and vice versa.
        val wakeEnabled = !isPcAwake
        val sleepEnabled = isPcAwake

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onWakeClick,
                enabled = wakeEnabled,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.subtleSurface,
                    disabledContentColor = palette.secondaryText.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = "Wake PC",
                    fontSize = (13f * textScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onSleepClick,
                enabled = sleepEnabled,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.subtleSurface,
                    disabledContentColor = palette.secondaryText.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = "Sleep PC",
                    fontSize = (13f * textScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Dynamic status text beneath buttons that updates immediately on connection state changes
        val bottomStatusLine = when {
            feedbackBanner.isNotBlank() -> feedbackBanner
            !hasCompletedInitialCheck && isChecking -> "Checking connection to PC…"
            isPcAwake -> "Connected • Ready to put PC to sleep"
            else -> "PC is sleeping or offline • Ready to wake"
        }

        SelectionContainer {
            Text(
                text = bottomStatusLine,
                fontSize = (12f * textScale).sp,
                fontWeight = bodyWeight,
                color = palette.secondaryText
            )
        }
    }
}

@Composable
private fun IpAddressSegmentedTile(
    modifier: Modifier,
    palette: AppPalette,
    textScale: Float,
    ipOctets: MutableList<String>,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onOctetsChanged: () -> Unit
) {
    val focusRequesters = remember { List(4) { FocusRequester() } }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.tileBg)
            .border(1.5.dp, palette.tileBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "IP address of PC",
            fontSize = (13.5f * textScale).sp,
            fontWeight = FontWeight.Medium,
            color = palette.secondaryText
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0 until 4) {
                BasicTextField(
                    value = ipOctets[i],
                    onValueChange = { rawInput ->
                        // Support pasting a full IP like "192.168.178.52"
                        if (rawInput.contains(".")) {
                            val split = rawInput.split(".")
                            if (split.size == 4) {
                                for (k in 0 until 4) {
                                    ipOctets[k] = split[k].filter { it.isDigit() }.take(3)
                                }
                                onOctetsChanged()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                return@BasicTextField
                            }
                        }

                        val digits = rawInput.filter { it.isDigit() }.take(3)
                        val num = digits.toIntOrNull()
                        val clamped = if (num != null && num > 255) "255" else digits
                        ipOctets[i] = clamped
                        onOctetsChanged()

                        if (clamped.length == 3) {
                            if (i < 3) {
                                focusRequesters[i + 1].requestFocus()
                            } else {
                                // Final character inputted -> dismiss keyboard and remove flashing cursor
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequesters[i])
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.faqAnswerBg)
                        .border(1.dp, palette.tileBorder, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    textStyle = TextStyle(
                        fontSize = (16f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (i < 3) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            if (i < 3) focusRequesters[i + 1].requestFocus()
                        },
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                )

                if (i < 3) {
                    Text(
                        text = ".",
                        fontSize = (22f * textScale).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MacAddressSegmentedTile(
    modifier: Modifier,
    palette: AppPalette,
    textScale: Float,
    macOctets: MutableList<String>,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onOctetsChanged: () -> Unit
) {
    val focusRequesters = remember { List(6) { FocusRequester() } }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.tileBg)
            .border(1.5.dp, palette.tileBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "MAC address of PC",
            fontSize = (13.5f * textScale).sp,
            fontWeight = FontWeight.Medium,
            color = palette.secondaryText
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0 until 6) {
                BasicTextField(
                    value = macOctets[i],
                    onValueChange = { rawInput ->
                        // Support pasting full MAC like "D8:43:AE:4D:EB:DB"
                        val hexOnly = rawInput.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
                        if (hexOnly.length >= 12) {
                            for (k in 0 until 6) {
                                macOctets[k] = hexOnly.substring(k * 2, k * 2 + 2)
                            }
                            onOctetsChanged()
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            return@BasicTextField
                        }

                        val pair = hexOnly.take(2)
                        macOctets[i] = pair
                        onOctetsChanged()

                        if (pair.length == 2) {
                            if (i < 5) {
                                focusRequesters[i + 1].requestFocus()
                            } else {
                                // Final character inputted -> dismiss keyboard and remove flashing cursor
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequesters[i])
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.faqAnswerBg)
                        .border(1.dp, palette.tileBorder, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    textStyle = TextStyle(
                        fontSize = (15f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = if (i < 5) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            if (i < 5) focusRequesters[i + 1].requestFocus()
                        },
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                )

                if (i < 5) {
                    Text(
                        text = ":",
                        fontSize = (19f * textScale).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.accent,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSettingsDialog(
    isDark: Boolean,
    palette: AppPalette,
    themePreset: String,
    textScale: Float,
    uiScale: Float,
    highContrast: Boolean,
    boldBodyText: Boolean,
    accentColorSetting: String,
    windowBgColorSetting: String,
    tileBgColorSetting: String,
    primaryColorSetting: String,
    secondaryColorSetting: String,
    iconBgColorSetting: String,
    iconFgColorSetting: String,
    onPresetChange: (String) -> Unit,
    onScaleChange: (Float) -> Unit,
    onHighContrastToggle: () -> Unit,
    onBoldToggle: () -> Unit,
    onAccentColorChange: (String) -> Unit,
    onWindowBgColorChange: (String) -> Unit,
    onTileBgColorChange: (String) -> Unit,
    onPrimaryColorChange: (String) -> Unit,
    onSecondaryColorChange: (String) -> Unit,
    onIconBgColorChange: (String) -> Unit,
    onIconFgColorChange: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeWheelTarget by remember { mutableStateOf<String?>(null) }
    var wheelHue by remember { mutableFloatStateOf(0f) }
    var wheelSat by remember { mutableFloatStateOf(0f) }
    var wheelVal by remember { mutableFloatStateOf(1f) }

    val squircleWheelBitmap = remember { createHsvWheelBitmap(96, circularMask = false).asImageBitmap() }
    val circularWheelBitmap = remember { createHsvWheelBitmap(220, circularMask = true).asImageBitmap() }
    val iconPreviewBitmap = remember(iconBgColorSetting, iconFgColorSetting) {
        createCustomAppIconBitmap(context, iconBgColorSetting, iconFgColorSetting, 112).asImageBitmap()
    }

    val squircleSize = (36f * uiScale).dp
    val squircleShape = RoundedCornerShape((11f * uiScale).dp)

    fun applyWheelHex(hex: String) {
        when (activeWheelTarget) {
            "Accent" -> onAccentColorChange(hex)
            "WinBg" -> onWindowBgColorChange(hex)
            "TileBg" -> onTileBgColorChange(hex)
            "Primary" -> onPrimaryColorChange(hex)
            "Secondary" -> onSecondaryColorChange(hex)
            "IconBg" -> onIconBgColorChange(hex)
            "IconFg" -> onIconFgColorChange(hex)
        }
    }

    @Composable
    fun InlineWheelPicker(targetKey: String, label: String) {
        AnimatedVisibility(visible = activeWheelTarget == targetKey) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.subtleSurface)
                    .border(1.5.dp, palette.accent, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = (12f * textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .pointerInput(targetKey) {
                            detectTapGestures { offset ->
                                val r = size.width / 2f
                                val dx = offset.x - r
                                val dy = offset.y - r
                                val dist = min(hypot(dx, dy), r)
                                var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                if (deg < 0f) deg += 360f
                                wheelHue = deg
                                wheelSat = (dist / r).coerceIn(0f, 1f)
                                applyWheelHex(hsvToHex(wheelHue, wheelSat, wheelVal))
                            }
                        }
                        .pointerInput(targetKey) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val r = size.width / 2f
                                val dx = change.position.x - r
                                val dy = change.position.y - r
                                val dist = min(hypot(dx, dy), r)
                                var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                if (deg < 0f) deg += 360f
                                wheelHue = deg
                                wheelSat = (dist / r).coerceIn(0f, 1f)
                                applyWheelHex(hsvToHex(wheelHue, wheelSat, wheelVal))
                            }
                        }
                ) {
                    Image(
                        bitmap = circularWheelBitmap,
                        contentDescription = "Color Wheel",
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.width / 2f
                        val rad = Math.toRadians((wheelHue - 90f).toDouble())
                        val cx = r + (wheelSat * r * cos(rad)).toFloat()
                        val cy = r + (wheelSat * r * sin(rad)).toFloat()
                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.2.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Brightness",
                    fontSize = (11f * textScale).sp,
                    color = palette.secondaryText
                )
                Slider(
                    value = wheelVal,
                    onValueChange = {
                        wheelVal = it
                        applyWheelHex(hsvToHex(wheelHue, wheelSat, wheelVal))
                    },
                    valueRange = 0.08f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent
                    )
                )
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(palette.windowBg)
                .border(1.5.dp, palette.tileBorder, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Theme & Appearance",
                    fontSize = (17f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryText
                )

                // 1. Named Theme Presets (Names & Palettes adapt dynamically to Dark vs Light mode)
                val presetItems = remember(isDark) {
                    if (isDark) {
                        listOf(
                            "Default" to "Default",
                            "Ocean" to "Midnight",
                            "Amethyst" to "Amethyst",
                            "Emerald" to "Emerald",
                            "Ember" to "Ember"
                        )
                    } else {
                        listOf(
                            "Default" to "Default",
                            "Ocean" to "Daybreak",
                            "Amethyst" to "Lavender",
                            "Emerald" to "Sage",
                            "Ember" to "Warm Sand"
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.tileBg)
                        .border(1.dp, palette.tileBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Theme Presets",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                        Text(
                            text = if (isDark) "Dark Mode Palettes" else "Light Mode Palettes",
                            fontSize = (11f * textScale).sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.accent
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.subtleSurface)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        presetItems.forEach { (key, displayName) ->
                            val active = themePreset.equals(key, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) palette.accent else Color.Transparent)
                                    .clickable { onPresetChange(key) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayName,
                                    fontSize = (12f * textScale).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) palette.onAccent else palette.secondaryText
                                )
                            }
                        }
                    }
                }

                // 2. Accent & Highlight Color
                ColorSquircleSection(
                    title = "Accent & Highlight Color",
                    presets = listOf("#38BDF8", "#A855F7", "#10B981", "#F97316", "#F43F5E"),
                    currentSetting = accentColorSetting,
                    squircleSize = squircleSize,
                    squircleShape = squircleShape,
                    squircleWheelBitmap = squircleWheelBitmap,
                    palette = palette,
                    textScale = textScale,
                    onPresetClick = onAccentColorChange,
                    onWheelSquircleClick = {
                        activeWheelTarget = if (activeWheelTarget == "Accent") null else "Accent"
                    },
                    wheelContent = { InlineWheelPicker("Accent", "Custom Accent Color") }
                )

                // 3. Window Background & Card Surface Colors
                ColorSquircleSection(
                    title = "Window Background Color",
                    presets = listOf("#16161A", "#0F172A", "#161124", "#F3F4F7", "#FDF5EC"),
                    currentSetting = windowBgColorSetting,
                    squircleSize = squircleSize,
                    squircleShape = squircleShape,
                    squircleWheelBitmap = squircleWheelBitmap,
                    palette = palette,
                    textScale = textScale,
                    onPresetClick = onWindowBgColorChange,
                    onWheelSquircleClick = {
                        activeWheelTarget = if (activeWheelTarget == "WinBg") null else "WinBg"
                    },
                    wheelContent = { InlineWheelPicker("WinBg", "Custom Window Background") }
                )

                ColorSquircleSection(
                    title = "Card & Menu Surface Color",
                    presets = listOf("#222228", "#1E293B", "#231B38", "#FFFFFF", "#F8FAFC"),
                    currentSetting = tileBgColorSetting,
                    squircleSize = squircleSize,
                    squircleShape = squircleShape,
                    squircleWheelBitmap = squircleWheelBitmap,
                    palette = palette,
                    textScale = textScale,
                    onPresetClick = onTileBgColorChange,
                    onWheelSquircleClick = {
                        activeWheelTarget = if (activeWheelTarget == "TileBg") null else "TileBg"
                    },
                    wheelContent = { InlineWheelPicker("TileBg", "Custom Card & Surface Color") }
                )

                // 4. Primary Text Color Squircles
                ColorSquircleSection(
                    title = "Main Heading & Value Text Color",
                    presets = listOf("#FFFFFF", "#18181B", "#F87171", "#4ADE80", "#60A5FA"),
                    currentSetting = primaryColorSetting,
                    squircleSize = squircleSize,
                    squircleShape = squircleShape,
                    squircleWheelBitmap = squircleWheelBitmap,
                    palette = palette,
                    textScale = textScale,
                    onPresetClick = onPrimaryColorChange,
                    onWheelSquircleClick = {
                        activeWheelTarget = if (activeWheelTarget == "Primary") null else "Primary"
                    },
                    wheelContent = { InlineWheelPicker("Primary", "Custom Main Heading Color") }
                )

                // 5. Secondary Text Color Squircles
                ColorSquircleSection(
                    title = "Descriptions, FAQ Answers & Hint Text Color",
                    presets = listOf("#DBDDE4", "#FFFFFF", "#FBBF24", "#34D399", "#38BDF8"),
                    currentSetting = secondaryColorSetting,
                    squircleSize = squircleSize,
                    squircleShape = squircleShape,
                    squircleWheelBitmap = squircleWheelBitmap,
                    palette = palette,
                    textScale = textScale,
                    onPresetClick = onSecondaryColorChange,
                    onWheelSquircleClick = {
                        activeWheelTarget = if (activeWheelTarget == "Secondary") null else "Secondary"
                    },
                    wheelContent = { InlineWheelPicker("Secondary", "Custom Description / FAQ Color") }
                )

                // 6. Program Icon Colors (Background & Icon Lines + Live Preview)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.tileBg)
                        .border(1.dp, palette.tileBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Program Icon Colors",
                                fontSize = (12.5f * textScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.primaryText
                            )
                            Text(
                                text = "Updates app icon (Quick Settings tile stays unchanged)",
                                fontSize = (11f * textScale).sp,
                                color = palette.secondaryText
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .border(1.dp, palette.tileBorder, RoundedCornerShape(11.dp))
                        ) {
                            Image(
                                bitmap = iconPreviewBitmap,
                                contentDescription = "App Icon Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Text(
                        text = "Icon Background Color",
                        fontSize = (11.5f * textScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.secondaryText,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                    SquircleRow(
                        presets = listOf("#18181B", "#1E3A8A", "#4C1D95", "#065F46", "#F4F4F6"),
                        currentSetting = iconBgColorSetting,
                        squircleSize = squircleSize,
                        squircleShape = squircleShape,
                        squircleWheelBitmap = squircleWheelBitmap,
                        palette = palette,
                        onPresetClick = onIconBgColorChange,
                        onWheelSquircleClick = {
                            activeWheelTarget = if (activeWheelTarget == "IconBg") null else "IconBg"
                        }
                    )
                    InlineWheelPicker("IconBg", "Custom Icon Background")

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Icon Lines Color",
                        fontSize = (11.5f * textScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.secondaryText,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                    SquircleRow(
                        presets = listOf("#FFFFFF", "#18181B", "#38BDF8", "#4ADE80", "#FBBF24"),
                        currentSetting = iconFgColorSetting,
                        squircleSize = squircleSize,
                        squircleShape = squircleShape,
                        squircleWheelBitmap = squircleWheelBitmap,
                        palette = palette,
                        onPresetClick = onIconFgColorChange,
                        onWheelSquircleClick = {
                            activeWheelTarget = if (activeWheelTarget == "IconFg") null else "IconFg"
                        }
                    )
                    InlineWheelPicker("IconFg", "Custom Icon Lines Color")
                }

                // 7. Stepped Dot Slider (50% to 200% in 25% steps; 100% is at the 3rd dot)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.tileBg)
                        .border(1.dp, palette.tileBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    val activeIndex = AllowedTextScales.indices.minByOrNull {
                        kotlin.math.abs(AllowedTextScales[it] - textScale)
                    } ?: 2

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Text & UI Scale",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                        Text(
                            text = "${(AllowedTextScales[activeIndex] * 100).roundToInt()}%",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val pad = 10.dp.toPx()
                                    val usable = (size.width - pad * 2).coerceAtLeast(1f)
                                    val frac = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                                    val idx = (frac * (AllowedTextScales.size - 1)).roundToInt()
                                        .coerceIn(0, AllowedTextScales.size - 1)
                                    onScaleChange(AllowedTextScales[idx])
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val pad = 10.dp.toPx()
                                    val usable = (size.width - pad * 2).coerceAtLeast(1f)
                                    val frac = ((change.position.x - pad) / usable).coerceIn(0f, 1f)
                                    val idx = (frac * (AllowedTextScales.size - 1)).roundToInt()
                                        .coerceIn(0, AllowedTextScales.size - 1)
                                    onScaleChange(AllowedTextScales[idx])
                                }
                            }
                    ) {
                        val pad = 10.dp.toPx()
                        val usable = (size.width - pad * 2).coerceAtLeast(1f)
                        val cy = size.height / 2f
                        val activeFrac = activeIndex.toFloat() / (AllowedTextScales.size - 1)
                        val activeX = pad + usable * activeFrac

                        drawLine(
                            color = palette.subtleSurface,
                            start = Offset(pad, cy),
                            end = Offset(pad + usable, cy),
                            strokeWidth = 6.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        drawLine(
                            color = palette.accent,
                            start = Offset(pad, cy),
                            end = Offset(activeX, cy),
                            strokeWidth = 6.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        for (i in AllowedTextScales.indices) {
                            val dotFrac = i.toFloat() / (AllowedTextScales.size - 1)
                            val dotX = pad + usable * dotFrac
                            drawCircle(
                                color = if (i <= activeIndex) palette.accent else palette.primaryText.copy(alpha = 0.55f),
                                radius = 3.6.dp.toPx(),
                                center = Offset(dotX, cy)
                            )
                        }
                        drawCircle(
                            color = palette.windowBg,
                            radius = 9.5.dp.toPx(),
                            center = Offset(activeX, cy)
                        )
                        drawCircle(
                            color = palette.accent,
                            radius = 7.5.dp.toPx(),
                            center = Offset(activeX, cy)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("50%", fontSize = (10.5f * textScale).sp, color = palette.secondaryText)
                        Text("100%", fontSize = (10.5f * textScale).sp, fontWeight = FontWeight.SemiBold, color = palette.secondaryText)
                        Text("150%", fontSize = (10.5f * textScale).sp, color = palette.secondaryText)
                        Text("200%", fontSize = (10.5f * textScale).sp, color = palette.secondaryText)
                    }
                }

                // 8. Contrast & Weight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.tileBg)
                        .border(1.dp, palette.tileBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Contrast & Weight",
                        fontSize = (12.5f * textScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.subtleSurface)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (highContrast) palette.accent else Color.Transparent)
                                .clickable { onHighContrastToggle() }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "High Contrast",
                                fontSize = (12f * textScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (highContrast) palette.onAccent else palette.secondaryText
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (boldBodyText) palette.accent else Color.Transparent)
                                .clickable { onBoldToggle() }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bold Body Text",
                                fontSize = (12f * textScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (boldBodyText) palette.onAccent else palette.secondaryText
                            )
                        }
                    }
                }

                // Bottom Reset & Done Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reset to Defaults",
                        fontSize = (12.5f * textScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.secondaryText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                activeWheelTarget = null
                                onReset()
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent
                        )
                    ) {
                        Text(
                            text = "Done",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSquircleSection(
    title: String,
    presets: List<String>,
    currentSetting: String,
    squircleSize: Dp,
    squircleShape: RoundedCornerShape,
    squircleWheelBitmap: androidx.compose.ui.graphics.ImageBitmap,
    palette: AppPalette,
    textScale: Float,
    onPresetClick: (String) -> Unit,
    onWheelSquircleClick: () -> Unit,
    wheelContent: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.tileBg)
            .border(1.dp, palette.tileBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            fontSize = (12.5f * textScale).sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.primaryText,
            modifier = Modifier.padding(bottom = 9.dp)
        )

        SquircleRow(
            presets = presets,
            currentSetting = currentSetting,
            squircleSize = squircleSize,
            squircleShape = squircleShape,
            squircleWheelBitmap = squircleWheelBitmap,
            palette = palette,
            onPresetClick = onPresetClick,
            onWheelSquircleClick = onWheelSquircleClick
        )

        wheelContent()
    }
}

@Composable
private fun SquircleRow(
    presets: List<String>,
    currentSetting: String,
    squircleSize: Dp,
    squircleShape: RoundedCornerShape,
    squircleWheelBitmap: androidx.compose.ui.graphics.ImageBitmap,
    palette: AppPalette,
    onPresetClick: (String) -> Unit,
    onWheelSquircleClick: () -> Unit
) {
    val matchedPreset = presets.any { it.equals(currentSetting, ignoreCase = true) }
    val isCustomWheel = !matchedPreset && !currentSetting.equals("Auto", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { hex ->
            val isSelected = currentSetting.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(squircleSize)
                    .clip(squircleShape)
                    .background(parseHexColor(hex, Color.White))
                    .border(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        color = if (isSelected) palette.accent else palette.tileBorder,
                        shape = squircleShape
                    )
                    .clickable { onPresetClick(hex) }
            )
        }

        // 6th Squircle: Color Wheel clipped strictly inside the squircle shape and underneath the border
        Box(
            modifier = Modifier
                .size(squircleSize)
                .clip(squircleShape)
                .border(
                    width = if (isCustomWheel) 3.dp else 1.5.dp,
                    color = if (isCustomWheel) palette.accent else palette.tileBorder,
                    shape = squircleShape
                )
                .clickable { onWheelSquircleClick() }
        ) {
            Image(
                bitmap = squircleWheelBitmap,
                contentDescription = "Custom Color Wheel",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(squircleShape)
            )
        }
    }
}

private data class PresetColors(
    val windowBg: Color,
    val tileBg: Color,
    val menuSurface: Color,
    val popupBg: Color,
    val faqAnswerBg: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accentOverride: Color?
)

private fun getPresetColors(presetKey: String, isDark: Boolean, highContrast: Boolean): PresetColors {
    val key = presetKey.trim().lowercase()
    return if (isDark) {
        when (key) {
            "ocean" -> PresetColors(
                windowBg = if (highContrast) Color(0xFF09101E) else Color(0xFF0F172A),
                tileBg = if (highContrast) Color(0xFF152238) else Color(0xFF1E293B),
                menuSurface = Color(0xFF1E293B),
                popupBg = Color(0xFF162032),
                faqAnswerBg = Color(0xFF0B1220),
                primaryText = Color(0xFFF8FAFC),
                secondaryText = if (highContrast) Color(0xFFE2E8F0) else Color(0xFFBDCCDF),
                accentOverride = Color(0xFF38BDF8)
            )
            "amethyst" -> PresetColors(
                windowBg = if (highContrast) Color(0xFF100B1C) else Color(0xFF161124),
                tileBg = if (highContrast) Color(0xFF1D1530) else Color(0xFF231B38),
                menuSurface = Color(0xFF251C3B),
                popupBg = Color(0xFF1C152E),
                faqAnswerBg = Color(0xFF120D1E),
                primaryText = Color(0xFFFAF5FF),
                secondaryText = if (highContrast) Color(0xFFEDE9FE) else Color(0xFFD8CEF2),
                accentOverride = Color(0xFFA855F7)
            )
            "emerald" -> PresetColors(
                windowBg = if (highContrast) Color(0xFF07120F) else Color(0xFF0C1915),
                tileBg = if (highContrast) Color(0xFF11221D) else Color(0xFF152822),
                menuSurface = Color(0xFF172B25),
                popupBg = Color(0xFF11211C),
                faqAnswerBg = Color(0xFF091411),
                primaryText = Color(0xFFECFDF5),
                secondaryText = if (highContrast) Color(0xFFD1FAE5) else Color(0xFFBAE6D4),
                accentOverride = Color(0xFF10B981)
            )
            "ember" -> PresetColors(
                windowBg = if (highContrast) Color(0xFF140D0B) else Color(0xFF1C1311),
                tileBg = if (highContrast) Color(0xFF241714) else Color(0xFF2B1D1A),
                menuSurface = Color(0xFF2E1F1C),
                popupBg = Color(0xFF231715),
                faqAnswerBg = Color(0xFF160F0D),
                primaryText = Color(0xFFFFF7ED),
                secondaryText = if (highContrast) Color(0xFFFFEDD5) else Color(0xFFEBCEBE),
                accentOverride = Color(0xFFF97316)
            )
            else -> PresetColors(
                windowBg = if (highContrast) Color(0xFF0E0E11) else Color(0xFF16161A),
                tileBg = if (highContrast) Color(0xFF1C1C22) else Color(0xFF222228),
                menuSurface = if (highContrast) Color(0xFF18181E) else Color(0xFF24242B),
                popupBg = if (highContrast) Color(0xFF121216) else Color(0xFF1D1D23),
                faqAnswerBg = if (highContrast) Color(0xFF0A0A0D) else Color(0xFF141418),
                primaryText = if (highContrast) Color(0xFFFFFFFF) else Color(0xFFFAFAFC),
                secondaryText = if (highContrast) Color(0xFFF8F9FA) else Color(0xFFDBDDE4),
                accentOverride = null
            )
        }
    } else {
        when (key) {
            "ocean" -> PresetColors(
                windowBg = if (highContrast) Color(0xFFE2EEFF) else Color(0xFFEDF5FF),
                tileBg = Color(0xFFFFFFFF),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF),
                faqAnswerBg = Color(0xFFE4F0FF),
                primaryText = Color(0xFF0F172A),
                secondaryText = if (highContrast) Color(0xFF1E293B) else Color(0xFF334155),
                accentOverride = Color(0xFF2563EB)
            )
            "amethyst" -> PresetColors(
                windowBg = if (highContrast) Color(0xFFEDE5FF) else Color(0xFFF5F0FF),
                tileBg = Color(0xFFFFFFFF),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF),
                faqAnswerBg = Color(0xFFEEE6FF),
                primaryText = Color(0xFF1E1035),
                secondaryText = if (highContrast) Color(0xFF2E1065) else Color(0xFF4C1D95),
                accentOverride = Color(0xFF7C3AED)
            )
            "emerald" -> PresetColors(
                windowBg = if (highContrast) Color(0xFFE0F2EA) else Color(0xFFECF7F2),
                tileBg = Color(0xFFFFFFFF),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF),
                faqAnswerBg = Color(0xFFE3F3EC),
                primaryText = Color(0xFF06281E),
                secondaryText = if (highContrast) Color(0xFF064E3B) else Color(0xFF1E4638),
                accentOverride = Color(0xFF059669)
            )
            "ember" -> PresetColors(
                windowBg = if (highContrast) Color(0xFFFAECDE) else Color(0xFFFDF5EC),
                tileBg = Color(0xFFFFFFFF),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF),
                faqAnswerBg = Color(0xFFFAEBDC),
                primaryText = Color(0xFF27150B),
                secondaryText = if (highContrast) Color(0xFF431407) else Color(0xFF5C341D),
                accentOverride = Color(0xFFEA580C)
            )
            else -> PresetColors(
                windowBg = if (highContrast) Color(0xFFEEF0F4) else Color(0xFFF3F4F7),
                tileBg = Color(0xFFFFFFFF),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF),
                faqAnswerBg = if (highContrast) Color(0xFFE8ECF2) else Color(0xFFF2F4F8),
                primaryText = if (highContrast) Color(0xFF000000) else Color(0xFF141417),
                secondaryText = if (highContrast) Color(0xFF141518) else Color(0xFF383A42),
                accentOverride = null
            )
        }
    }
}

private fun shiftColor(c: Color, delta: Float): Color {
    return Color(
        red = (c.red + delta).coerceIn(0f, 1f),
        green = (c.green + delta).coerceIn(0f, 1f),
        blue = (c.blue + delta).coerceIn(0f, 1f),
        alpha = c.alpha
    )
}

private fun buildPalette(
    isDark: Boolean,
    themePreset: String,
    highContrast: Boolean,
    accentSetting: String,
    windowBgSetting: String,
    tileBgSetting: String,
    primarySetting: String,
    secondarySetting: String,
    systemAccent: Color
): AppPalette {
    val preset = getPresetColors(themePreset, isDark, highContrast)

    val customWin = !windowBgSetting.equals("Auto", ignoreCase = true)
    val customTile = !tileBgSetting.equals("Auto", ignoreCase = true)

    val windowBg = if (customWin) parseHexColor(windowBgSetting, preset.windowBg) else preset.windowBg
    val tileBg = if (customTile) parseHexColor(tileBgSetting, preset.tileBg) else preset.tileBg
    val accent = if (!accentSetting.equals("Auto", ignoreCase = true)) {
        parseHexColor(accentSetting, preset.accentOverride ?: systemAccent)
    } else {
        preset.accentOverride ?: systemAccent
    }

    val menuSurface = if (customTile) shiftColor(tileBg, if (isDark) 0.02f else 0f) else preset.menuSurface
    val popupBg = if (customTile) shiftColor(tileBg, if (isDark) -0.02f else 0f) else preset.popupBg
    val faqAnswerBg = if (customTile || customWin) shiftColor(windowBg, -0.02f) else preset.faqAnswerBg

    val primaryText = if (primarySetting.equals("Auto", ignoreCase = true)) {
        preset.primaryText
    } else {
        parseHexColor(primarySetting, preset.primaryText)
    }

    val secondaryText = if (secondarySetting.equals("Auto", ignoreCase = true)) {
        preset.secondaryText
    } else {
        parseHexColor(secondarySetting, preset.secondaryText)
    }

    val luminance = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    val onAccent = if (luminance > 0.62f) Color(0xFF121214) else Color(0xFFFFFFFF)

    return AppPalette(
        windowBg = windowBg,
        tileBg = tileBg,
        tileBorder = if (isDark) {
            if (highContrast) Color(0x88FFFFFF) else Color(0x45FFFFFF)
        } else {
            if (highContrast) Color(0x75000000) else Color(0x2D000000)
        },
        menuSurface = menuSurface,
        popupBg = popupBg,
        faqAnswerBg = faqAnswerBg,
        subtleSurface = if (isDark) {
            if (highContrast) Color(0x2CFFFFFF) else Color(0x1AFFFFFF)
        } else {
            if (highContrast) Color(0x22000000) else Color(0x14000000)
        },
        primaryText = primaryText,
        secondaryText = secondaryText,
        accent = accent,
        onAccent = onAccent,
        accentSubtle = accent.copy(alpha = if (isDark) 0.26f else 0.18f)
    )
}

// --- Android Program Icon Rendering, Recents TaskDescription & Home Screen Alias Switcher ---
private fun createCustomAppIconBitmap(context: Context, bgHex: String, fgHex: String, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = try {
            android.graphics.Color.parseColor(bgHex.trim())
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#18181B")
        }
    }
    val radius = size * 0.22f
    canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, bgPaint)

    try {
        val fgBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground_img)
        if (fgBitmap != null) {
            val fgColor = try {
                android.graphics.Color.parseColor(fgHex.trim())
            } catch (_: Exception) {
                android.graphics.Color.WHITE
            }
            val fgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(fgColor, PorterDuff.Mode.SRC_IN)
            }
            val dstRect = android.graphics.Rect(0, 0, size, size)
            canvas.drawBitmap(fgBitmap, null, dstRect, fgPaint)
        }
    } catch (_: Exception) {
        // Ignore if foreground bitmap decode fails
    }

    return bmp
}

@Suppress("DEPRECATION")
private fun updateTaskDescriptionIcon(context: Context, bgHex: String, fgHex: String) {
    val activity = context as? Activity ?: return
    try {
        val iconBmp = createCustomAppIconBitmap(context, bgHex, fgHex, 128)
        val primaryRgb = try {
            android.graphics.Color.parseColor(bgHex.trim())
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#18181B")
        }
        activity.setTaskDescription(ActivityManager.TaskDescription("WaddleFree", iconBmp, primaryRgb))
    } catch (_: Exception) {
        // Ignore on unsupported OEM ROMs
    }
}

private val LauncherBgAliases = listOf(
    "dark" to "#18181B",
    "blue" to "#1E3A8A",
    "purple" to "#4C1D95",
    "green" to "#065F46",
    "light" to "#F4F4F6",
    "orange" to "#9A3412",
    "rose" to "#881337"
)

private val LauncherFgAliases = listOf(
    "white" to "#FFFFFF",
    "black" to "#18181B",
    "sky" to "#38BDF8",
    "mint" to "#4ADE80",
    "gold" to "#FBBF24",
    "coral" to "#F87171",
    "violet" to "#C084FC"
)

private fun closestAliasKey(targetHex: String, candidates: List<Pair<String, String>>, defaultKey: String): String {
    val targetRgb = try {
        android.graphics.Color.parseColor(targetHex.trim())
    } catch (_: Exception) {
        return defaultKey
    }
    val tr = (targetRgb shr 16) and 0xFF
    val tg = (targetRgb shr 8) and 0xFF
    val tb = targetRgb and 0xFF

    var bestKey = defaultKey
    var bestDist = Int.MAX_VALUE
    for ((key, hex) in candidates) {
        val cRgb = android.graphics.Color.parseColor(hex)
        val cr = (cRgb shr 16) and 0xFF
        val cg = (cRgb shr 8) and 0xFF
        val cb = cRgb and 0xFF
        val dist = (tr - cr) * (tr - cr) + (tg - cg) * (tg - cg) + (tb - cb) * (tb - cb)
        if (dist < bestDist) {
            bestDist = dist
            bestKey = key
        }
    }
    return bestKey
}

private fun syncLauncherIconAlias(context: Context, prefs: MobilePrefs, bgHex: String, fgHex: String) {
    try {
        val bgKey = closestAliasKey(bgHex, LauncherBgAliases, "dark")
        val fgKey = closestAliasKey(fgHex, LauncherFgAliases, "white")
        val desiredAlias = if (bgKey == "dark" && fgKey == "white") {
            ".LauncherDefault"
        } else {
            ".Launcher_${bgKey}_${fgKey}"
        }

        val currentAlias = prefs.activeLauncherAlias
        if (currentAlias == desiredAlias) return

        val pm = context.packageManager
        val pkg = context.packageName

        // Enable the new alias first so the app always has an active LAUNCHER entry
        pm.setComponentEnabledSetting(
            ComponentName(pkg, "$pkg$desiredAlias"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // Disable the previous alias
        pm.setComponentEnabledSetting(
            ComponentName(pkg, "$pkg$currentAlias"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        prefs.activeLauncherAlias = desiredAlias
    } catch (_: Exception) {
        // Ignore if launcher blocks dynamic alias toggling
    }
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: Exception) {
        fallback
    }
}

private fun hsvToHex(h: Float, s: Float, v: Float): String {
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun createHsvWheelBitmap(size: Int, circularMask: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val center = (size - 1) / 2f
    val radius = size / 2f
    val maxRadius = if (circularMask) radius - 1f else radius * 1.42f
    val hsv = FloatArray(3)
    hsv[2] = 1f

    for (y in 0 until size) {
        for (x in 0 until size) {
            val dx = x - center
            val dy = y - center
            val dist = hypot(dx, dy)
            if (circularMask && dist > radius) {
                bmp.setPixel(x, y, 0x00000000)
                continue
            }
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
            if (deg < 0f) deg += 360f
            hsv[0] = deg
            hsv[1] = (dist / maxRadius).coerceIn(0f, 1f)
            val color = android.graphics.Color.HSVToColor(hsv)
            bmp.setPixel(x, y, color)
        }
    }
    return bmp
}
