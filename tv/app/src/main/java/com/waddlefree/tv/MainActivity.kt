package com.waddlefree.tv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.waddlefree.tv.data.TvPrefs
import com.waddlefree.tv.network.PcCompanionClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = TvPrefs(applicationContext)
        setContent {
            WaddleFreeTvApp(prefs = prefs)
        }
    }
}

data class TvFaqItem(
    val question: String,
    val answer: String
)

data class AppPalette(
    val windowBg: Color,
    val tileBg: Color,
    val tileBorder: Color,
    val faqAnswerBg: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSubtle: Color,
    val subtleSurface: Color,
    val menuSurface: Color,
    val popupBg: Color
)

private val AutoCheckPresets = listOf("10 s", "30 s", "1 m", "5 m", "15 m")

@Composable
fun TvFocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    backgroundColor: Color,
    borderColor: Color,
    focusedBorderColor: Color,
    borderWidth: Dp = 1.5.dp,
    focusedBorderWidth: Dp = 3.dp,
    focusRequester: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = if (isFocused) focusedBorderWidth else borderWidth,
                color = if (isFocused) focusedBorderColor else borderColor,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment
    ) {
        content(isFocused)
    }
}

@Composable
fun WaddleFreeTvApp(prefs: TvPrefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ipOctets = remember {
        mutableStateListOf<String>().apply { addAll(prefs.ipOctets) }
    }
    val macOctets = remember {
        mutableStateListOf<String>().apply { addAll(prefs.macOctets) }
    }

    var pcName by remember { mutableStateOf(prefs.lastPcName) }
    var cachedPort by remember { mutableIntStateOf(prefs.cachedPort) }
    var isPcAwake by remember { mutableStateOf(prefs.lastKnownAwake) }
    var isChecking by remember { mutableStateOf(false) }
    var hasCompletedInitialCheck by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf("") }

    var themePref by remember { mutableStateOf(prefs.themePreference) }
    var themePreset by remember { mutableStateOf(prefs.themePreset) }
    var textScale by remember { mutableFloatStateOf(prefs.textScale) }
    var highContrast by remember { mutableStateOf(prefs.highContrast) }
    var boldBodyText by remember { mutableStateOf(prefs.boldBodyText) }
    var autoCheckPreset by remember { mutableStateOf(prefs.autoCheckPreset) }

    var isOptionsOpen by remember { mutableStateOf(false) }
    var isPortInfoOpen by remember { mutableStateOf(false) }
    var isGuideOpen by remember { mutableStateOf(false) }
    var isAppearanceOpen by remember { mutableStateOf(false) }
    val expandedFaqIndices = remember { mutableStateListOf<Int>() }

    // Active Octet Editor Modal State ("IP" or "MAC", and index)
    var editingType by remember { mutableStateOf<String?>(null) }
    var editingIndex by remember { mutableIntStateOf(0) }

    val checkStatusFocusRequester = remember { FocusRequester() }
    val ipFocusRequesters = remember { List(4) { FocusRequester() } }
    val macFocusRequesters = remember { List(6) { FocusRequester() } }

    // Handle TV Remote Back button gracefully when overlays are open
    BackHandler(enabled = isAppearanceOpen || editingType != null || isPortInfoOpen || isGuideOpen || isOptionsOpen) {
        when {
            editingType != null -> editingType = null
            isAppearanceOpen -> isAppearanceOpen = false
            isPortInfoOpen -> isPortInfoOpen = false
            isGuideOpen -> isGuideOpen = false
            isOptionsOpen -> isOptionsOpen = false
        }
    }

    val systemIsDark = isSystemInDarkTheme()
    val useDark = when (themePref.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> systemIsDark
    }

    val dynamicAccent: Color? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        null
    }

    val palette = remember(useDark, themePreset, highContrast, dynamicAccent) {
        buildAppPalette(
            useDark = useDark,
            themePreset = themePreset,
            highContrast = highContrast,
            dynamicAccent = dynamicAccent
        )
    }

    val fullIp = ipOctets.joinToString(".")
    val fullMac = macOctets.joinToString(":")
    val derivedPort = remember(fullMac, cachedPort) {
        if (cachedPort in 1024..49151) cachedPort else PcCompanionClient.deriveInitialZone2Port(fullMac)
    }

    suspend fun refreshStatus(manualTrigger: Boolean) {
        if (!PcCompanionClient.isValidIpv4(fullIp)) {
            isPcAwake = false
            prefs.lastKnownAwake = false
            hasCompletedInitialCheck = true
            if (manualTrigger) {
                feedbackBanner = "Enter all 4 IP address boxes first"
            }
            return
        }

        isChecking = true
        if (manualTrigger) feedbackBanner = ""
        val status = PcCompanionClient.checkPcStatus(
            ipAddress = fullIp,
            macAddress = fullMac,
            deviceId = prefs.deviceId,
            cachedPort = cachedPort
        )
        isPcAwake = status.isAwake
        prefs.lastKnownAwake = status.isAwake
        if (status.isAwake) {
            if (status.pcName.isNotBlank()) {
                pcName = status.pcName
                prefs.lastPcName = status.pcName
            }
            if (status.activePort > 0) {
                cachedPort = status.activePort
                prefs.cachedPort = status.activePort
            }
            if (manualTrigger) {
                feedbackBanner = "✓ Connected to ${pcName.ifBlank { "PC" }}"
            }
        } else if (manualTrigger) {
            feedbackBanner = "PC is currently sleeping or unreachable"
        }
        isChecking = false
        hasCompletedInitialCheck = true
    }

    val checkIntervalMs = remember(autoCheckPreset) {
        when (autoCheckPreset) {
            "10 s" -> 10_000L
            "30 s" -> 30_000L
            "1 m" -> 60_000L
            "5 m" -> 300_000L
            "15 m" -> 900_000L
            else -> 60_000L
        }
    }

    LaunchedEffect(fullIp, fullMac, checkIntervalMs) {
        if (PcCompanionClient.isValidIpv4(fullIp)) {
            refreshStatus(manualTrigger = false)
            while (true) {
                delay(checkIntervalMs)
                if (!isChecking) {
                    refreshStatus(manualTrigger = false)
                }
            }
        } else {
            hasCompletedInitialCheck = true
        }
    }

    // Focus the neutral "Check Status" button on startup so the remote cursor is immediately visible
    // without risking an accidental Wake/Sleep trigger.
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { checkStatusFocusRequester.requestFocus() }
    }

    val bodyWeight = if (boldBodyText) FontWeight.SemiBold else FontWeight.Normal
    val uiScale = textScale.coerceIn(0.85f, 1.25f)
    val iconBtnSize = (42f * uiScale).dp

    val faqList = remember {
        listOf(
            TvFaqItem(
                question = "How to use WaddleFree on Google TV / Android TV?",
                answer = "Use your TV remote's D-pad arrows to move between buttons and address boxes. Press the center OK button on any IP or MAC box to open the on-screen D-pad keypad and enter your PC's details."
            ),
            TvFaqItem(
                question = "How to configure Wake-on-Ethernet (WoL) on Windows 11?",
                answer = "1. On your PC, right-click Start → Device Manager → Network adapters → double-click your Ethernet adapter.\n\n" +
                    "2. Power Management tab: Check 'Allow this device to wake the computer' AND 'Only allow a magic packet to wake the computer'.\n\n" +
                    "3. Advanced tab: Enable 'Wake on Magic Packet' and disable 'Wake on Pattern Match'.\n\n" +
                    "4. In BIOS/UEFI: Enable 'Wake on PCIe / LAN'."
            ),
            TvFaqItem(
                question = "Where do I find my PC's IP and MAC address?",
                answer = "Open WaddleFree on your Windows PC—both your PC's IP address and MAC address are displayed right on the main tiles."
            ),
            TvFaqItem(
                question = "How does WaddleFree wake and sleep my PC?",
                answer = "When your PC is sleeping, selecting 'Wake PC' broadcasts a hardware Magic Packet using the MAC address so your motherboard powers up. Once Windows is awake, selecting 'Sleep PC' signals the desktop companion over your local network to put Windows back to sleep."
            ),
            TvFaqItem(
                question = "Do my TV and PC need to be on the same home network?",
                answer = "Yes. Connect your Google TV / Android TV (via Wi-Fi or Ethernet) and your PC (via Ethernet) to the same home router so local Wake-on-LAN and sleep signals reach your PC directly."
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = palette.windowBg
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // TV overscan-safe margin
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
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
                        // Left: Title + TV Badge + Status Badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "WaddleFree",
                                fontSize = (20f * textScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryText
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.subtleSurface)
                                    .border(1.dp, palette.tileBorder, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TV",
                                    fontSize = (11f * textScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accent
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.accentSubtle)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                val statusText = when {
                                    isChecking && !hasCompletedInitialCheck -> "Checking…"
                                    isPcAwake -> if (pcName.isNotBlank()) "Awake • $pcName" else "Awake • Connected"
                                    isChecking -> "Checking…"
                                    else -> "PC Sleeping / Offline"
                                }
                                Text(
                                    text = statusText,
                                    fontSize = (13f * textScale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.primaryText
                                )
                            }
                        }

                        // Right: Options Bar + Hamburger Menu Button
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedVisibility(
                                visible = isOptionsOpen,
                                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
                            ) {
                                TvOptionsMenuBar(
                                    palette = palette,
                                    textScale = textScale,
                                    uiScale = uiScale,
                                    themePref = themePref,
                                    onThemeChange = {
                                        themePref = it
                                        prefs.themePreference = it
                                    },
                                    onOpenAppearance = { isAppearanceOpen = true },
                                    autoCheckPreset = autoCheckPreset,
                                    onCycleAutoCheck = {
                                        val idx = AutoCheckPresets.indexOf(autoCheckPreset)
                                        val next = AutoCheckPresets[(idx + 1) % AutoCheckPresets.size]
                                        autoCheckPreset = next
                                        prefs.autoCheckPreset = next
                                    },
                                    derivedPort = derivedPort,
                                    onTogglePortInfo = { isPortInfoOpen = !isPortInfoOpen },
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                            }

                            TvFocusableSurface(
                                onClick = {
                                    isOptionsOpen = !isOptionsOpen
                                    if (!isOptionsOpen) isPortInfoOpen = false
                                },
                                modifier = Modifier.size(iconBtnSize),
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = palette.tileBg,
                                borderColor = palette.tileBorder,
                                focusedBorderColor = palette.accent
                            ) {
                                Column(
                                    modifier = Modifier.width((18f * uiScale).dp),
                                    verticalArrangement = Arrangement.spacedBy((4f * uiScale).dp)
                                ) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height((2.2f * uiScale).dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(palette.primaryText)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Expandable Port Info Panel
                    AnimatedVisibility(
                        visible = isOptionsOpen && isPortInfoOpen,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 520.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(palette.popupBg)
                                    .border(1.5.dp, palette.accent, RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Dynamic Zone 2 Port & Local Security",
                                        fontSize = (13.5f * textScale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryText
                                    )
                                    Text(
                                        text = "WaddleFree derives a deterministic port ($derivedPort) in Zone 2 (20000–49151) from your PC's MAC address so your TV and PC pair automatically with zero port configuration.",
                                        fontSize = (12.5f * textScale).sp,
                                        fontWeight = bodyWeight,
                                        color = palette.secondaryText
                                    )
                                }
                            }
                        }
                    }
                }

                // --- MIDDLE: THREE HORIZONTAL TILES ---
                val onWakeAction: () -> Unit = {
                    scope.launch {
                        val ok = PcCompanionClient.sendWakeOnLanMagicPacket(fullIp, fullMac)
                        if (!ok) {
                            feedbackBanner = "Enter a valid 12-character MAC address first"
                        } else {
                            feedbackBanner = "✓ Magic Packet sent! Waiting for PC to wake…"
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
                    if (!isPcAwake) {
                        feedbackBanner = "PC is already sleeping or offline"
                    } else {
                        scope.launch {
                            val ok = PcCompanionClient.sendSleepCommand(fullIp, fullMac, prefs.deviceId, cachedPort)
                            if (ok) {
                                isPcAwake = false
                                prefs.lastKnownAwake = false
                                feedbackBanner = "✓ Sleep signal sent to PC"
                            } else {
                                feedbackBanner = "Could not reach PC—check IP & network"
                                refreshStatus(manualTrigger = false)
                            }
                        }
                    }
                }

                val onRefreshAction: () -> Unit = {
                    if (!isChecking) {
                        scope.launch {
                            refreshStatus(manualTrigger = true)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvPcConnectionTile(
                        modifier = Modifier.weight(1f),
                        palette = palette,
                        textScale = textScale,
                        bodyWeight = bodyWeight,
                        pcName = pcName,
                        isPcAwake = isPcAwake,
                        isChecking = isChecking,
                        hasCompletedInitialCheck = hasCompletedInitialCheck,
                        feedbackBanner = feedbackBanner,
                        checkStatusFocusRequester = checkStatusFocusRequester,
                        onWakeClick = onWakeAction,
                        onSleepClick = onSleepAction,
                        onRefreshClick = onRefreshAction
                    )

                    TvIpAddressTile(
                        modifier = Modifier.weight(1f),
                        palette = palette,
                        textScale = textScale,
                        ipOctets = ipOctets,
                        focusRequesters = ipFocusRequesters,
                        onEditOctet = { index ->
                            editingType = "IP"
                            editingIndex = index
                        }
                    )

                    TvMacAddressTile(
                        modifier = Modifier.weight(1f),
                        palette = palette,
                        textScale = textScale,
                        macOctets = macOctets,
                        focusRequesters = macFocusRequesters,
                        onEditOctet = { index ->
                            editingType = "MAC"
                            editingIndex = index
                        }
                    )
                }

                // --- BOTTOM BAR: GUIDE PANEL + '?' BUTTON + GITHUB LINK ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = isGuideOpen,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 580.dp)
                                .fillMaxWidth()
                                .heightIn(max = 270.dp)
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.popupBg)
                                .border(1.5.dp, palette.accent, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Setup Guide & FAQ (Use D-Pad & OK to expand)",
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
                                        ) {
                                            TvFocusableSurface(
                                                onClick = {
                                                    if (isExpanded) expandedFaqIndices.remove(idx)
                                                    else expandedFaqIndices.add(idx)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(11.dp),
                                                backgroundColor = palette.tileBg,
                                                borderColor = palette.tileBorder,
                                                focusedBorderColor = palette.accent,
                                                borderWidth = 1.dp,
                                                focusedBorderWidth = 2.5.dp,
                                                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
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
                                            }

                                            AnimatedVisibility(visible = isExpanded) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(palette.faqAnswerBg)
                                                        .padding(horizontal = 13.dp, vertical = 10.dp)
                                                ) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvFocusableSurface(
                            onClick = { isGuideOpen = !isGuideOpen },
                            modifier = Modifier.size(iconBtnSize),
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = palette.tileBg,
                            borderColor = palette.tileBorder,
                            focusedBorderColor = palette.accent
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
                            TvFocusableSurface(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/bit-hero-333/WaddleFree")
                                        )
                                        context.startActivity(intent)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                backgroundColor = Color.Transparent,
                                borderColor = Color.Transparent,
                                focusedBorderColor = palette.accent,
                                borderWidth = 0.dp,
                                focusedBorderWidth = 2.dp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Canvas(modifier = Modifier.size((15f * textScale).dp)) {
                                        val scaleFactor = size.width / 16f
                                        withTransform({
                                            scale(scaleX = scaleFactor, scaleY = scaleFactor, pivot = Offset.Zero)
                                        }) {
                                            drawPath(path = githubIconPath, color = palette.secondaryText)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "github.com/bit-hero-333/WaddleFree",
                                        fontSize = (12.5f * textScale).sp,
                                        fontWeight = bodyWeight,
                                        color = palette.secondaryText,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(iconBtnSize))
                    }
                }
            }

            // --- D-PAD OCTET EDITOR MODAL (IP OR MAC) ---
            if (editingType != null) {
                TvOctetKeypadDialog(
                    palette = palette,
                    textScale = textScale,
                    isIp = editingType == "IP",
                    activeIndex = editingIndex,
                    ipOctets = ipOctets,
                    macOctets = macOctets,
                    onIndexChange = { editingIndex = it },
                    onValuesChanged = {
                        if (editingType == "IP") {
                            prefs.ipOctets = ipOctets.toList()
                        } else {
                            prefs.macOctets = macOctets.toList()
                        }
                    },
                    onDismiss = {
                        val lastType = editingType
                        val lastIdx = editingIndex
                        editingType = null
                        scope.launch {
                            delay(80)
                            runCatching {
                                if (lastType == "IP") ipFocusRequesters.getOrNull(lastIdx)?.requestFocus()
                                else if (lastType == "MAC") macFocusRequesters.getOrNull(lastIdx)?.requestFocus()
                            }
                            if (PcCompanionClient.isValidIpv4(ipOctets.joinToString("."))) {
                                refreshStatus(manualTrigger = false)
                            }
                        }
                    }
                )
            }

            // --- APPEARANCE & THEME DIALOG (100% D-PAD REMOTE CONTROLLED) ---
            if (isAppearanceOpen) {
                TvAppearanceDialog(
                    palette = palette,
                    textScale = textScale,
                    themePreset = themePreset,
                    highContrast = highContrast,
                    boldBodyText = boldBodyText,
                    onSelectPreset = {
                        themePreset = it
                        prefs.themePreset = it
                    },
                    onToggleHighContrast = {
                        highContrast = !highContrast
                        prefs.highContrast = highContrast
                    },
                    onToggleBoldText = {
                        boldBodyText = !boldBodyText
                        prefs.boldBodyText = boldBodyText
                    },
                    onSelectTextScale = {
                        textScale = it
                        prefs.textScale = it
                    },
                    onDismiss = { isAppearanceOpen = false }
                )
            }
        }
    }
}

@Composable
private fun TvPcConnectionTile(
    modifier: Modifier,
    palette: AppPalette,
    textScale: Float,
    bodyWeight: FontWeight,
    pcName: String,
    isPcAwake: Boolean,
    isChecking: Boolean,
    hasCompletedInitialCheck: Boolean,
    feedbackBanner: String,
    checkStatusFocusRequester: FocusRequester,
    onWakeClick: () -> Unit,
    onSleepClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.tileBg)
            .border(1.5.dp, palette.tileBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Connected PC",
                fontSize = (14f * textScale).sp,
                fontWeight = FontWeight.Medium,
                color = palette.secondaryText
            )

            TvFocusableSurface(
                onClick = onRefreshClick,
                focusRequester = checkStatusFocusRequester,
                shape = RoundedCornerShape(8.dp),
                backgroundColor = palette.subtleSurface,
                borderColor = palette.tileBorder,
                focusedBorderColor = palette.accent,
                borderWidth = 1.dp,
                focusedBorderWidth = 2.5.dp,
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isChecking) "Checking…" else "Check Status",
                    fontSize = (12f * textScale).sp,
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
                fontSize = (20f * textScale).sp,
                fontWeight = FontWeight.Bold,
                color = if (isPcAwake) palette.accent else palette.primaryText
            )
            if (isChecking) {
                Spacer(modifier = Modifier.width(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent
                )
            }
        }

        // Both buttons stay focusable at all times so D-pad focus is never lost when PC status flips
        val wakeActive = !isPcAwake
        val sleepActive = isPcAwake

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TvFocusableSurface(
                onClick = onWakeClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = if (wakeActive) palette.accent else palette.subtleSurface,
                borderColor = if (wakeActive) palette.accent else palette.tileBorder,
                focusedBorderColor = if (wakeActive) Color.White else palette.accent,
                borderWidth = 1.5.dp,
                focusedBorderWidth = 3.dp,
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = "Wake PC",
                    fontSize = (13.5f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = if (wakeActive) palette.onAccent else palette.secondaryText.copy(alpha = 0.65f)
                )
            }

            TvFocusableSurface(
                onClick = onSleepClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = if (sleepActive) palette.accent else palette.subtleSurface,
                borderColor = if (sleepActive) palette.accent else palette.tileBorder,
                focusedBorderColor = if (sleepActive) Color.White else palette.accent,
                borderWidth = 1.5.dp,
                focusedBorderWidth = 3.dp,
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = "Sleep PC",
                    fontSize = (13.5f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sleepActive) palette.onAccent else palette.secondaryText.copy(alpha = 0.65f)
                )
            }
        }

        val bottomStatusLine = when {
            feedbackBanner.isNotBlank() -> feedbackBanner
            !hasCompletedInitialCheck && isChecking -> "Checking connection to PC…"
            isPcAwake -> "Connected • Ready to put PC to sleep"
            else -> "PC is sleeping or offline • Ready to wake"
        }

        Text(
            text = bottomStatusLine,
            fontSize = (12.5f * textScale).sp,
            fontWeight = bodyWeight,
            color = palette.secondaryText
        )
    }
}

@Composable
private fun TvIpAddressTile(
    modifier: Modifier,
    palette: AppPalette,
    textScale: Float,
    ipOctets: List<String>,
    focusRequesters: List<FocusRequester>,
    onEditOctet: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.tileBg)
            .border(1.5.dp, palette.tileBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IP address of PC",
                fontSize = (14f * textScale).sp,
                fontWeight = FontWeight.Medium,
                color = palette.secondaryText
            )
            Text(
                text = "Press OK to edit",
                fontSize = (11f * textScale).sp,
                color = palette.secondaryText.copy(alpha = 0.75f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0 until 4) {
                TvFocusableSurface(
                    onClick = { onEditOctet(i) },
                    modifier = Modifier.weight(1f),
                    focusRequester = focusRequesters[i],
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = palette.faqAnswerBg,
                    borderColor = palette.tileBorder,
                    focusedBorderColor = palette.accent,
                    borderWidth = 1.dp,
                    focusedBorderWidth = 3.dp,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = ipOctets[i].ifEmpty { "—" },
                        fontSize = (16.5f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ipOctets[i].isNotEmpty()) palette.primaryText else palette.secondaryText.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                }

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
private fun TvMacAddressTile(
    modifier: Modifier,
    palette: AppPalette,
    textScale: Float,
    macOctets: List<String>,
    focusRequesters: List<FocusRequester>,
    onEditOctet: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.tileBg)
            .border(1.5.dp, palette.tileBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MAC address of PC",
                fontSize = (14f * textScale).sp,
                fontWeight = FontWeight.Medium,
                color = palette.secondaryText
            )
            Text(
                text = "Press OK to edit",
                fontSize = (11f * textScale).sp,
                color = palette.secondaryText.copy(alpha = 0.75f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0 until 6) {
                TvFocusableSurface(
                    onClick = { onEditOctet(i) },
                    modifier = Modifier.weight(1f),
                    focusRequester = focusRequesters[i],
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = palette.faqAnswerBg,
                    borderColor = palette.tileBorder,
                    focusedBorderColor = palette.accent,
                    borderWidth = 1.dp,
                    focusedBorderWidth = 3.dp,
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = macOctets[i].ifEmpty { "--" },
                        fontSize = (15.5f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = if (macOctets[i].isNotEmpty()) palette.primaryText else palette.secondaryText.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                }

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
private fun TvOptionsMenuBar(
    palette: AppPalette,
    textScale: Float,
    uiScale: Float,
    themePref: String,
    onThemeChange: (String) -> Unit,
    onOpenAppearance: () -> Unit,
    autoCheckPreset: String,
    onCycleAutoCheck: () -> Unit,
    derivedPort: Int,
    onTogglePortInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artistPalettePath = remember {
        PathParser().parsePathString(
            "M 12.0 2.2 C 6.4 2.2 1.8 6.5 1.8 11.8 C 1.8 17.1 6.4 21.5 11.9 21.5 " +
                "C 13.5 21.5 14.7 20.3 14.7 18.7 C 14.7 18.0 14.4 17.4 14.0 16.9 " +
                "C 13.6 16.5 13.4 15.9 13.4 15.3 C 13.4 13.9 14.5 12.8 15.9 12.8 " +
                "L 17.7 12.8 C 20.7 12.8 23.0 10.4 23.0 7.5 C 23.0 4.3 18.1 2.2 12.0 2.2 Z"
        ).toPath()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.menuSurface)
            .border(1.dp, palette.tileBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf("Dark" to "Dark", "Light" to "Light", "System" to "Device").forEach { (key, label) ->
                    val active = themePref.equals(key, ignoreCase = true)
                    TvFocusableSurface(
                        onClick = { onThemeChange(key) },
                        shape = RoundedCornerShape(7.dp),
                        backgroundColor = if (active) palette.accent else Color.Transparent,
                        borderColor = Color.Transparent,
                        focusedBorderColor = if (active) Color.White else palette.accent,
                        borderWidth = 0.dp,
                        focusedBorderWidth = 2.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
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

            Spacer(modifier = Modifier.width(8.dp))

            // Palette Button
            TvFocusableSurface(
                onClick = onOpenAppearance,
                modifier = Modifier.size(width = (36f * uiScale).dp, height = (32f * uiScale).dp),
                shape = RoundedCornerShape(8.dp),
                backgroundColor = palette.subtleSurface,
                borderColor = palette.tileBorder,
                focusedBorderColor = palette.accent,
                borderWidth = 1.dp,
                focusedBorderWidth = 2.5.dp
            ) {
                Canvas(modifier = Modifier.size((20f * uiScale).dp)) {
                    val s = size.width / 24f
                    withTransform({
                        scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
                    }) {
                        drawPath(path = artistPalettePath, color = palette.primaryText)
                        drawCircle(color = palette.menuSurface, radius = 1.75f, center = Offset(9.75f, 15.8f))
                        drawCircle(color = Color(0xFFF87171), radius = 1.6f, center = Offset(6.4f, 11.4f))
                        drawCircle(color = Color(0xFFFBBF24), radius = 1.6f, center = Offset(9.3f, 7.0f))
                        drawCircle(color = Color(0xFF4ADE80), radius = 1.6f, center = Offset(14.3f, 6.8f))
                        drawCircle(color = palette.accent, radius = 1.6f, center = Offset(18.3f, 9.5f))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height((18f * uiScale).dp)
                    .background(palette.tileBorder)
            )

            Text(
                text = "Auto-Check",
                fontSize = (12f * textScale).sp,
                fontWeight = FontWeight.Medium,
                color = palette.secondaryText,
                modifier = Modifier.padding(end = 6.dp)
            )

            TvFocusableSurface(
                onClick = onCycleAutoCheck,
                shape = RoundedCornerShape(8.dp),
                backgroundColor = palette.subtleSurface,
                borderColor = palette.tileBorder,
                focusedBorderColor = palette.accent,
                borderWidth = 1.dp,
                focusedBorderWidth = 2.5.dp,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = autoCheckPreset,
                        fontSize = (12f * textScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "⟳",
                        fontSize = (11f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height((18f * uiScale).dp)
                    .background(palette.tileBorder)
            )

            Text(
                text = "Port: $derivedPort",
                fontSize = (12f * textScale).sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.primaryText
            )
            Spacer(modifier = Modifier.width(6.dp))
            TvFocusableSurface(
                onClick = onTogglePortInfo,
                modifier = Modifier.size((22f * uiScale).dp),
                shape = RoundedCornerShape(50),
                backgroundColor = Color.Transparent,
                borderColor = palette.accent,
                focusedBorderColor = Color.White,
                borderWidth = 1.5.dp,
                focusedBorderWidth = 2.5.dp
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
    }
}

@Composable
private fun TvOctetKeypadDialog(
    palette: AppPalette,
    textScale: Float,
    isIp: Boolean,
    activeIndex: Int,
    ipOctets: MutableList<String>,
    macOctets: MutableList<String>,
    onIndexChange: (Int) -> Unit,
    onValuesChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxOctets = if (isIp) 4 else 6
    val maxChars = if (isIp) 3 else 2
    val firstKeyFocusRequester = remember { FocusRequester() }

    fun appendChar(ch: String) {
        if (isIp) {
            val current = ipOctets[activeIndex]
            val candidate = (current + ch).take(3)
            val num = candidate.toIntOrNull() ?: 0
            val clamped = if (num > 255) "255" else candidate
            ipOctets[activeIndex] = clamped
            onValuesChanged()
            if (clamped.length == 3 && activeIndex < 3) {
                onIndexChange(activeIndex + 1)
            }
        } else {
            val current = macOctets[activeIndex]
            val candidate = (current + ch).uppercase().take(2)
            macOctets[activeIndex] = candidate
            onValuesChanged()
            if (candidate.length == 2 && activeIndex < 5) {
                onIndexChange(activeIndex + 1)
            }
        }
    }

    fun backspace() {
        if (isIp) {
            val cur = ipOctets[activeIndex]
            if (cur.isNotEmpty()) {
                ipOctets[activeIndex] = cur.dropLast(1)
                onValuesChanged()
            } else if (activeIndex > 0) {
                onIndexChange(activeIndex - 1)
            }
        } else {
            val cur = macOctets[activeIndex]
            if (cur.isNotEmpty()) {
                macOctets[activeIndex] = cur.dropLast(1)
                onValuesChanged()
            } else if (activeIndex > 0) {
                onIndexChange(activeIndex - 1)
            }
        }
    }

    fun clearCurrent() {
        if (isIp) ipOctets[activeIndex] = "" else macOctets[activeIndex] = ""
        onValuesChanged()
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstKeyFocusRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.popupBg)
                .border(2.dp, palette.accent, RoundedCornerShape(20.dp))
                .padding(22.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isIp) "Edit PC IP Address" else "Edit PC MAC Address",
                    fontSize = (16f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryText
                )

                // Live Preview Row of all Octets (can also click any box to jump to it)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until maxOctets) {
                        val isSelected = i == activeIndex
                        val value = if (isIp) ipOctets[i] else macOctets[i]
                        TvFocusableSurface(
                            onClick = { onIndexChange(i) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(9.dp),
                            backgroundColor = if (isSelected) palette.accentSubtle else palette.faqAnswerBg,
                            borderColor = if (isSelected) palette.accent else palette.tileBorder,
                            focusedBorderColor = Color.White,
                            borderWidth = if (isSelected) 2.dp else 1.dp,
                            focusedBorderWidth = 2.5.dp,
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = value.ifEmpty { if (isIp) "—" else "--" },
                                fontSize = (15f * textScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) palette.accent else palette.primaryText,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (i < maxOctets - 1) {
                            Text(
                                text = if (isIp) "." else ":",
                                fontSize = (18f * textScale).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.accent,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                // On-screen D-Pad Keypad
                val rows = if (isIp) {
                    listOf(
                        listOf("1", "2", "3", "4", "5"),
                        listOf("6", "7", "8", "9", "0")
                    )
                } else {
                    listOf(
                        listOf("0", "1", "2", "3", "4", "5", "6", "7"),
                        listOf("8", "9", "A", "B", "C", "D", "E", "F")
                    )
                }

                rows.forEachIndexed { rIdx, rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowKeys.forEachIndexed { cIdx, keyLabel ->
                            TvFocusableSurface(
                                onClick = { appendChar(keyLabel) },
                                modifier = Modifier.weight(1f),
                                focusRequester = if (rIdx == 0 && cIdx == 0) firstKeyFocusRequester else null,
                                shape = RoundedCornerShape(10.dp),
                                backgroundColor = palette.tileBg,
                                borderColor = palette.tileBorder,
                                focusedBorderColor = palette.accent,
                                borderWidth = 1.dp,
                                focusedBorderWidth = 2.5.dp,
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Text(
                                    text = keyLabel,
                                    fontSize = (16f * textScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryText
                                )
                            }
                        }
                    }
                }

                // Action Row: Prev Box | Next Box | Backspace | Clear | Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvFocusableSurface(
                        onClick = { if (activeIndex > 0) onIndexChange(activeIndex - 1) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = palette.subtleSurface,
                        borderColor = palette.tileBorder,
                        focusedBorderColor = palette.accent,
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "◀ Box",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                    }

                    TvFocusableSurface(
                        onClick = { if (activeIndex < maxOctets - 1) onIndexChange(activeIndex + 1) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = palette.subtleSurface,
                        borderColor = palette.tileBorder,
                        focusedBorderColor = palette.accent,
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "Box ▶",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                    }

                    TvFocusableSurface(
                        onClick = { backspace() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = palette.subtleSurface,
                        borderColor = palette.tileBorder,
                        focusedBorderColor = palette.accent,
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "⌫ Del",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                    }

                    TvFocusableSurface(
                        onClick = { clearCurrent() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = palette.subtleSurface,
                        borderColor = palette.tileBorder,
                        focusedBorderColor = palette.accent,
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "Clear",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                    }

                    TvFocusableSurface(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = palette.accent,
                        borderColor = palette.accent,
                        focusedBorderColor = Color.White,
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = "✓ Done",
                            fontSize = (13f * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvAppearanceDialog(
    palette: AppPalette,
    textScale: Float,
    themePreset: String,
    highContrast: Boolean,
    boldBodyText: Boolean,
    onSelectPreset: (String) -> Unit,
    onToggleHighContrast: () -> Unit,
    onToggleBoldText: () -> Unit,
    onSelectTextScale: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val firstPresetFocus = remember { FocusRequester() }
    val presets = listOf("Default", "Violet", "Emerald", "Sunset", "AMOLED", "Monochrome")
    val scales = listOf(0.9f to "90%", 1.0f to "100%", 1.1f to "110%", 1.2f to "120%")

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstPresetFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.popupBg)
                .border(2.dp, palette.accent, RoundedCornerShape(20.dp))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Theme & Appearance",
                    fontSize = (16f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryText
                )

                Text(
                    text = "Color Palette Preset",
                    fontSize = (12.5f * textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.secondaryText
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.chunked(3).forEachIndexed { rowIdx, chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEachIndexed { colIdx, preset ->
                                val selected = themePreset.equals(preset, ignoreCase = true)
                                TvFocusableSurface(
                                    onClick = { onSelectPreset(preset) },
                                    modifier = Modifier.weight(1f),
                                    focusRequester = if (rowIdx == 0 && colIdx == 0) firstPresetFocus else null,
                                    shape = RoundedCornerShape(10.dp),
                                    backgroundColor = if (selected) palette.accent else palette.tileBg,
                                    borderColor = if (selected) palette.accent else palette.tileBorder,
                                    focusedBorderColor = if (selected) Color.White else palette.accent,
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = (12.5f * textScale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) palette.onAccent else palette.primaryText
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "UI & Text Scale",
                    fontSize = (12.5f * textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.secondaryText
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scales.forEach { (scaleVal, label) ->
                        val selected = kotlin.math.abs(textScale - scaleVal) < 0.04f
                        TvFocusableSurface(
                            onClick = { onSelectTextScale(scaleVal) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = if (selected) palette.accent else palette.tileBg,
                            borderColor = if (selected) palette.accent else palette.tileBorder,
                            focusedBorderColor = if (selected) Color.White else palette.accent,
                            contentPadding = PaddingValues(vertical = 9.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = (12.5f * textScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) palette.onAccent else palette.primaryText
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TvFocusableSurface(
                        onClick = onToggleHighContrast,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = if (highContrast) palette.accentSubtle else palette.tileBg,
                        borderColor = if (highContrast) palette.accent else palette.tileBorder,
                        focusedBorderColor = palette.accent,
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = if (highContrast) "✓ High Contrast: ON" else "High Contrast: OFF",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                    }

                    TvFocusableSurface(
                        onClick = onToggleBoldText,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = if (boldBodyText) palette.accentSubtle else palette.tileBg,
                        borderColor = if (boldBodyText) palette.accent else palette.tileBorder,
                        focusedBorderColor = palette.accent,
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = if (boldBodyText) "✓ Bold Text: ON" else "Bold Text: OFF",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                    }
                }

                TvFocusableSurface(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = palette.accent,
                    borderColor = palette.accent,
                    focusedBorderColor = Color.White,
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = "Close",
                        fontSize = (13.5f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onAccent
                    )
                }
            }
        }
    }
}

private fun buildAppPalette(
    useDark: Boolean,
    themePreset: String,
    highContrast: Boolean,
    dynamicAccent: Color?
): AppPalette {
    val defaultAccent = dynamicAccent ?: if (useDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    return if (useDark) {
        when (themePreset) {
            "Violet" -> AppPalette(
                windowBg = Color(0xFF0F0B1A),
                tileBg = Color(0xFF18122B),
                tileBorder = if (highContrast) Color(0xFFA78BFA) else Color(0xFF2D2250),
                faqAnswerBg = Color(0xFF120D1E),
                primaryText = Color(0xFFF5F3FF),
                secondaryText = Color(0xFFC4B5FD),
                accent = Color(0xFFA78BFA),
                onAccent = Color(0xFF0F0B1A),
                accentSubtle = Color(0x33A78BFA),
                subtleSurface = Color(0xFF231A3E),
                menuSurface = Color(0xFF161026),
                popupBg = Color(0xFF1B1430)
            )
            "Emerald" -> AppPalette(
                windowBg = Color(0xFF07120F),
                tileBg = Color(0xFF0D1F1A),
                tileBorder = if (highContrast) Color(0xFF34D399) else Color(0xFF1B3B32),
                faqAnswerBg = Color(0xFF091411),
                primaryText = Color(0xFFECFDF5),
                secondaryText = Color(0xFFA7F3D0),
                accent = Color(0xFF34D399),
                onAccent = Color(0xFF06100D),
                accentSubtle = Color(0x3334D399),
                subtleSurface = Color(0xFF142E27),
                menuSurface = Color(0xFF0B1A16),
                popupBg = Color(0xFF10241E)
            )
            "Sunset" -> AppPalette(
                windowBg = Color(0xFF140C0A),
                tileBg = Color(0xFF221411),
                tileBorder = if (highContrast) Color(0xFFFB923C) else Color(0xFF432620),
                faqAnswerBg = Color(0xFF160F0D),
                primaryText = Color(0xFFFFF7ED),
                secondaryText = Color(0xFFFED7AA),
                accent = Color(0xFFFB923C),
                onAccent = Color(0xFF180C06),
                accentSubtle = Color(0x33FB923C),
                subtleSurface = Color(0xFF311D18),
                menuSurface = Color(0xFF1C100E),
                popupBg = Color(0xFF271714)
            )
            "AMOLED" -> AppPalette(
                windowBg = Color(0xFF000000),
                tileBg = Color(0xFF0A0A0C),
                tileBorder = if (highContrast) Color(0xFFFFFFFF) else Color(0xFF27272A),
                faqAnswerBg = Color(0xFF000000),
                primaryText = Color(0xFFFFFFFF),
                secondaryText = Color(0xFFA1A1AA),
                accent = defaultAccent,
                onAccent = Color(0xFF050811),
                accentSubtle = defaultAccent.copy(alpha = 0.22f),
                subtleSurface = Color(0xFF18181B),
                menuSurface = Color(0xFF09090B),
                popupBg = Color(0xFF121215)
            )
            "Monochrome" -> AppPalette(
                windowBg = Color(0xFF0B0D13),
                tileBg = Color(0xFF131722),
                tileBorder = if (highContrast) Color(0xFFFFFFFF) else Color(0xFF2B3245),
                faqAnswerBg = Color(0xFF0B1220),
                primaryText = Color(0xFFF8FAFC),
                secondaryText = Color(0xFFCBD5E1),
                accent = Color(0xFFE2E8F0),
                onAccent = Color(0xFF0F172A),
                accentSubtle = Color(0x33E2E8F0),
                subtleSurface = Color(0xFF1E2436),
                menuSurface = Color(0xFF111520),
                popupBg = Color(0xFF171C29)
            )
            else -> AppPalette(
                windowBg = Color(0xFF090D16),
                tileBg = Color(0xFF111827),
                tileBorder = if (highContrast) defaultAccent else Color(0xFF243049),
                faqAnswerBg = Color(0xFF0B1220),
                primaryText = Color(0xFFF8FAFC),
                secondaryText = Color(0xFF94A3B8),
                accent = defaultAccent,
                onAccent = Color(0xFF06101E),
                accentSubtle = defaultAccent.copy(alpha = 0.20f),
                subtleSurface = Color(0xFF1E293B),
                menuSurface = Color(0xFF0F172A),
                popupBg = Color(0xFF162033)
            )
        }
    } else {
        when (themePreset) {
            "Violet" -> AppPalette(
                windowBg = Color(0xFFF5F3FF),
                tileBg = Color(0xFFFFFFFF),
                tileBorder = if (highContrast) Color(0xFF6D28D9) else Color(0xFFDDD6FE),
                faqAnswerBg = Color(0xFFEEE6FF),
                primaryText = Color(0xFF1E1B4B),
                secondaryText = Color(0xFF5B21B6),
                accent = Color(0xFF7C3AED),
                onAccent = Color(0xFFFFFFFF),
                accentSubtle = Color(0x267C3AED),
                subtleSurface = Color(0xFFEDE9FE),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF)
            )
            "Emerald" -> AppPalette(
                windowBg = Color(0xFFECFDF5),
                tileBg = Color(0xFFFFFFFF),
                tileBorder = if (highContrast) Color(0xFF047857) else Color(0xFFA7F3D0),
                faqAnswerBg = Color(0xFFE3F3EC),
                primaryText = Color(0xFF064E3B),
                secondaryText = Color(0xFF047857),
                accent = Color(0xFF059669),
                onAccent = Color(0xFFFFFFFF),
                accentSubtle = Color(0x26059669),
                subtleSurface = Color(0xFFD1FAE5),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF)
            )
            "Sunset" -> AppPalette(
                windowBg = Color(0xFFFFF7ED),
                tileBg = Color(0xFFFFFFFF),
                tileBorder = if (highContrast) Color(0xFFC2410C) else Color(0xFFFED7AA),
                faqAnswerBg = Color(0xFFFAEBDC),
                primaryText = Color(0xFF431407),
                secondaryText = Color(0xFF9A3412),
                accent = Color(0xFFEA580C),
                onAccent = Color(0xFFFFFFFF),
                accentSubtle = Color(0x26EA580C),
                subtleSurface = Color(0xFFFFEDD5),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF)
            )
            else -> AppPalette(
                windowBg = Color(0xFFF1F5F9),
                tileBg = Color(0xFFFFFFFF),
                tileBorder = if (highContrast) defaultAccent else Color(0xFFCBD5E1),
                faqAnswerBg = Color(0xFFE4F0FF),
                primaryText = Color(0xFF0F172A),
                secondaryText = Color(0xFF475569),
                accent = defaultAccent,
                onAccent = Color(0xFFFFFFFF),
                accentSubtle = defaultAccent.copy(alpha = 0.16f),
                subtleSurface = Color(0xFFE2E8F0),
                menuSurface = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF)
            )
        }
    }
}
