package com.waddlefree.mobile

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun WaddleFreeMobileApp(prefs: MobilePrefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var themePref by remember { mutableStateOf(prefs.themePreference) }
    var textScale by remember { mutableFloatStateOf(prefs.textScale.coerceIn(0.50f, 2.00f)) }
    var highContrast by remember { mutableStateOf(prefs.highContrast) }
    var boldBodyText by remember { mutableStateOf(prefs.boldBodyText) }
    var primaryColorSetting by remember { mutableStateOf(prefs.primaryTextColor) }
    var secondaryColorSetting by remember { mutableStateOf(prefs.secondaryTextColor) }

    val ipOctets = remember {
        val initial = prefs.ipOctets
        mutableStateListOf(initial[0], initial[1], initial[2], initial[3])
    }
    val macOctets = remember {
        val initial = prefs.macOctets
        mutableStateListOf(initial[0], initial[1], initial[2], initial[3], initial[4], initial[5])
    }

    var pcName by remember { mutableStateOf(prefs.lastPcName) }
    var cachedPort by remember { mutableIntStateOf(prefs.cachedPort) }
    var isPcAwake by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
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

    val palette = remember(isDark, highContrast, primaryColorSetting, secondaryColorSetting, dynamicAccent) {
        buildPalette(
            isDark = isDark,
            highContrast = highContrast,
            primarySetting = primaryColorSetting,
            secondarySetting = secondaryColorSetting,
            accent = dynamicAccent
        )
    }

    val uiScale = 0.55f + (0.45f * textScale)
    val bodyWeight = if (boldBodyText) FontWeight.SemiBold else FontWeight.Normal

    val fullIp = ipOctets.joinToString(".")
    val fullMac = macOctets.joinToString(":")
    val derivedPort = remember(fullMac, cachedPort) {
        if (cachedPort in 1024..49151) cachedPort else PcCompanionClient.deriveInitialZone2Port(fullMac)
    }

    suspend fun refreshStatus() {
        if (!PcCompanionClient.isValidIpv4(fullIp)) {
            isPcAwake = false
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
        isPcAwake = res.isAwake
        if (res.isAwake) {
            if (res.pcName.isNotBlank()) {
                pcName = res.pcName
                prefs.lastPcName = res.pcName
            }
            if (res.activePort in 1024..49151) {
                cachedPort = res.activePort
                prefs.cachedPort = res.activePort
            }
        }
    }

    // Periodic status check while app is open
    LaunchedEffect(fullIp, fullMac) {
        if (PcCompanionClient.isValidIpv4(fullIp)) {
            refreshStatus()
        }
        while (true) {
            delay(5000)
            if (PcCompanionClient.isValidIpv4(fullIp) && !isChecking) {
                refreshStatus()
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
                answer = "When your PC is sleeping, tapping 'Wake PC' broadcasts a hardware Magic Packet using the MAC address so your motherboard powers up. Once Windows is awake, tapping 'Put PC to Sleep' signals the desktop companion over your local Wi-Fi to put Windows back to sleep."
            ),
            MobileFaqItem(
                question = "Why don't I have to type dots or colons in the IP and MAC boxes?",
                answer = "The dots (.) for the IP address and colons (:) for the MAC address are permanently built into the UI boxes. As you type each segment, the cursor advances automatically—or you can paste a full IP or MAC address directly into the first box."
            ),
            MobileFaqItem(
                question = "Do my phone and PC need to be on the same home network?",
                answer = "Yes. Connect your phone to your home Wi-Fi and your PC via Ethernet to the same router so local Wake-on-LAN and sleep signals can reach your PC directly."
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
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
                                    isChecking -> "Checking…"
                                    isPcAwake -> if (pcName.isNotBlank()) "Awake • $pcName" else "Awake • Connected"
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
                                derivedPort = derivedPort,
                                onTogglePortInfo = { isPortInfoOpen = !isPortInfoOpen },
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
                                feedbackBanner = feedbackBanner,
                                onWakeClick = {
                                    scope.launch {
                                        val ok = PcCompanionClient.sendWakeOnLanMagicPacket(fullIp, fullMac)
                                        feedbackBanner = if (ok) "✓ Magic Packet sent! Waking PC…" else "Enter a valid 12-character MAC address first"
                                        delay(2200)
                                        refreshStatus()
                                    }
                                },
                                onSleepClick = {
                                    scope.launch {
                                        val ok = PcCompanionClient.sendSleepCommand(fullIp, fullMac, prefs.deviceId, cachedPort)
                                        if (ok) {
                                            isPcAwake = false
                                            feedbackBanner = "✓ Sleep signal sent to PC"
                                        } else {
                                            feedbackBanner = "Could not reach PC—check IP & Wi-Fi"
                                        }
                                    }
                                },
                                onRefreshClick = {
                                    scope.launch {
                                        refreshStatus()
                                        feedbackBanner = if (isPcAwake) "✓ PC is Awake" else "PC is currently sleeping or unreachable"
                                    }
                                }
                            )

                            IpAddressSegmentedTile(
                                modifier = Modifier.weight(1f),
                                palette = palette,
                                textScale = textScale,
                                bodyWeight = bodyWeight,
                                ipOctets = ipOctets,
                                onOctetsChanged = {
                                    prefs.ipOctets = ipOctets.toList()
                                }
                            )

                            MacAddressSegmentedTile(
                                modifier = Modifier.weight(1f),
                                palette = palette,
                                textScale = textScale,
                                bodyWeight = bodyWeight,
                                macOctets = macOctets,
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
                                feedbackBanner = feedbackBanner,
                                onWakeClick = {
                                    scope.launch {
                                        val ok = PcCompanionClient.sendWakeOnLanMagicPacket(fullIp, fullMac)
                                        feedbackBanner = if (ok) "✓ Magic Packet sent! Waking PC…" else "Enter a valid 12-character MAC address first"
                                        delay(2200)
                                        refreshStatus()
                                    }
                                },
                                onSleepClick = {
                                    scope.launch {
                                        val ok = PcCompanionClient.sendSleepCommand(fullIp, fullMac, prefs.deviceId, cachedPort)
                                        if (ok) {
                                            isPcAwake = false
                                            feedbackBanner = "✓ Sleep signal sent to PC"
                                        } else {
                                            feedbackBanner = "Could not reach PC—check IP & Wi-Fi"
                                        }
                                    }
                                },
                                onRefreshClick = {
                                    scope.launch {
                                        refreshStatus()
                                        feedbackBanner = if (isPcAwake) "✓ PC is Awake" else "PC is currently sleeping or unreachable"
                                    }
                                }
                            )

                            IpAddressSegmentedTile(
                                modifier = Modifier.fillMaxWidth(),
                                palette = palette,
                                textScale = textScale,
                                bodyWeight = bodyWeight,
                                ipOctets = ipOctets,
                                onOctetsChanged = {
                                    prefs.ipOctets = ipOctets.toList()
                                }
                            )

                            MacAddressSegmentedTile(
                                modifier = Modifier.fillMaxWidth(),
                                palette = palette,
                                textScale = textScale,
                                bodyWeight = bodyWeight,
                                macOctets = macOctets,
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
                                .clickable { isGuideOpen = !isGuideOpen },
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

                        SelectionContainer(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "Connected locally over Wi-Fi • Dots and colons are built into the IP & MAC fields.",
                                fontSize = (11.5f * textScale).sp,
                                fontWeight = bodyWeight,
                                color = palette.secondaryText,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.width(iconBtnSize))
                    }
                }
            }
        }
    }

    // --- TEXT & READABILITY SETTINGS MODAL DIALOG ---
    if (isTextSettingsOpen) {
        TextSettingsDialog(
            palette = palette,
            textScale = textScale,
            uiScale = uiScale,
            highContrast = highContrast,
            boldBodyText = boldBodyText,
            primaryColorSetting = primaryColorSetting,
            secondaryColorSetting = secondaryColorSetting,
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
            onPrimaryColorChange = {
                primaryColorSetting = it
                prefs.primaryTextColor = it
            },
            onSecondaryColorChange = {
                secondaryColorSetting = it
                prefs.secondaryTextColor = it
            },
            onReset = {
                textScale = 1.0f
                highContrast = false
                boldBodyText = false
                primaryColorSetting = "Auto"
                secondaryColorSetting = "Auto"
                prefs.textScale = 1.0f
                prefs.highContrast = false
                prefs.boldBodyText = false
                prefs.primaryTextColor = "Auto"
                prefs.secondaryTextColor = "Auto"
            },
            onDismiss = { isTextSettingsOpen = false }
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
    derivedPort: Int,
    onTogglePortInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.menuSurface)
            .border(1.dp, palette.tileBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            // 'T' with slightly overlaid Vector Accent Gear Button
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
                    val s = size.width / 22f
                    // Vector 'T'
                    val tPath = Path().apply {
                        moveTo(1.5f * s, 2.5f * s)
                        lineTo(15.5f * s, 2.5f * s)
                        lineTo(15.5f * s, 5.3f * s)
                        lineTo(10.0f * s, 5.3f * s)
                        lineTo(10.0f * s, 18.0f * s)
                        lineTo(7.0f * s, 18.0f * s)
                        lineTo(7.0f * s, 5.3f * s)
                        lineTo(1.5f * s, 5.3f * s)
                        close()
                    }
                    drawPath(tPath, color = palette.primaryText)

                    // Halo cutout for gear overlap
                    val gearCenter = Offset(14.2f * s, 14.8f * s)
                    drawCircle(
                        color = palette.menuSurface,
                        radius = 6.8f * s,
                        center = gearCenter
                    )

                    // 8-toothed Vector Gear with hollow center
                    val gearPath = Path().apply {
                        fillType = PathFillType.EvenOdd
                        val outerR = 5.8f * s
                        val innerR = 4.3f * s
                        val teeth = 8
                        val totalSteps = teeth * 4
                        for (i in 0 until totalSteps) {
                            val angle = (i * 2.0 * Math.PI) / totalSteps
                            val r = if (i % 4 == 0 || i % 4 == 1) outerR else innerR
                            val px = gearCenter.x + (r * cos(angle)).toFloat()
                            val py = gearCenter.y + (r * sin(angle)).toFloat()
                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                        // Center hole
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                gearCenter.x - 2.0f * s,
                                gearCenter.y - 2.0f * s,
                                gearCenter.x + 2.0f * s,
                                gearCenter.y + 2.0f * s
                            )
                        )
                    }
                    drawPath(gearPath, color = palette.accent)
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height((18f * uiScale).dp)
                    .background(palette.tileBorder)
            )

            // Port + Encircled 'i' Icon
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
                    .clickable { onRefreshClick() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isChecking) "Pinging…" else "Check Status",
                    fontSize = (11.5f * textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val displayPc = when {
                isPcAwake && pcName.isNotBlank() -> pcName
                pcName.isNotBlank() -> "$pcName (Sleeping)"
                isPcAwake -> "PC Online"
                else -> "Sleeping / Ready to Wake"
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

        // Primary Wake / Sleep Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onWakeClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isPcAwake) palette.accent else palette.subtleSurface,
                    contentColor = if (!isPcAwake) palette.onAccent else palette.primaryText
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
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPcAwake) palette.accent else palette.subtleSurface,
                    contentColor = if (isPcAwake) palette.onAccent else palette.primaryText
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

        if (feedbackBanner.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = feedbackBanner,
                    fontSize = (12f * textScale).sp,
                    fontWeight = bodyWeight,
                    color = palette.secondaryText
                )
            }
        }
    }
}

@Composable
private fun IpAddressSegmentedTile(
    modifier: Modifier,
    palette: AppPalette,
    textScale: Float,
    bodyWeight: FontWeight,
    ipOctets: MutableList<String>,
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

        // 4 Octet Inputs with the 3 dots (.) permanently part of the UI
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
                                focusRequesters[3].requestFocus()
                                return@BasicTextField
                            }
                        }

                        val digits = rawInput.filter { it.isDigit() }.take(3)
                        val num = digits.toIntOrNull()
                        val clamped = if (num != null && num > 255) "255" else digits
                        ipOctets[i] = clamped
                        onOctetsChanged()

                        if (clamped.length == 3 && i < 3) {
                            focusRequesters[i + 1].requestFocus()
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

        Text(
            text = "Dots are built in • Type digits or paste full IP",
            fontSize = (12f * textScale).sp,
            fontWeight = bodyWeight,
            color = palette.secondaryText
        )
    }
}

@Composable
private fun MacAddressSegmentedTile(
    modifier: Modifier,
    palette: AppPalette,
    textScale: Float,
    bodyWeight: FontWeight,
    macOctets: MutableList<String>,
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

        // 6 Hex Pairs with the 5 colons (:) permanently part of the UI
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
                            focusRequesters[5].requestFocus()
                            return@BasicTextField
                        }

                        val pair = hexOnly.take(2)
                        macOctets[i] = pair
                        onOctetsChanged()

                        if (pair.length == 2 && i < 5) {
                            focusRequesters[i + 1].requestFocus()
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

        Text(
            text = "Colons are built in • Auto-advances every 2 chars",
            fontSize = (12f * textScale).sp,
            fontWeight = bodyWeight,
            color = palette.secondaryText
        )
    }
}

@Composable
private fun TextSettingsDialog(
    palette: AppPalette,
    textScale: Float,
    uiScale: Float,
    highContrast: Boolean,
    boldBodyText: Boolean,
    primaryColorSetting: String,
    secondaryColorSetting: String,
    onScaleChange: (Float) -> Unit,
    onHighContrastToggle: () -> Unit,
    onBoldToggle: () -> Unit,
    onPrimaryColorChange: (String) -> Unit,
    onSecondaryColorChange: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeWheelTarget by remember { mutableStateOf<String?>(null) } // "Primary" or "Secondary"
    var wheelHue by remember { mutableFloatStateOf(0f) }
    var wheelSat by remember { mutableFloatStateOf(0f) }
    var wheelVal by remember { mutableFloatStateOf(1f) }

    val squircleWheelBitmap = remember { createHsvWheelBitmap(96, circularMask = false).asImageBitmap() }
    val circularWheelBitmap = remember { createHsvWheelBitmap(220, circularMask = true).asImageBitmap() }

    val squircleSize = (36f * uiScale).dp
    val squircleShape = RoundedCornerShape((11f * uiScale).dp)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                    text = "Text & Readability",
                    fontSize = (17f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryText
                )

                // 1. Stepped Dot Slider (50% to 200% in 25% steps; 100% is at the 3rd dot, not center)
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
                            text = "Text Size",
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

                    // Interactive 7-dot stepped slider canvas
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

                        // Track background
                        drawLine(
                            color = palette.subtleSurface,
                            start = Offset(pad, cy),
                            end = Offset(pad + usable, cy),
                            strokeWidth = 6.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        // Active track fill
                        drawLine(
                            color = palette.accent,
                            start = Offset(pad, cy),
                            end = Offset(activeX, cy),
                            strokeWidth = 6.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        // 7 Step Dots
                        for (i in AllowedTextScales.indices) {
                            val dotFrac = i.toFloat() / (AllowedTextScales.size - 1)
                            val dotX = pad + usable * dotFrac
                            drawCircle(
                                color = if (i <= activeIndex) palette.accent else palette.primaryText.copy(alpha = 0.55f),
                                radius = 3.6.dp.toPx(),
                                center = Offset(dotX, cy)
                            )
                        }
                        // Active Thumb
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

                // 2. Contrast & Weight
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

                // 3. Primary Text Color Squircles (5 Presets + 6th Fitted Color Wheel Squircle)
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
                    }
                )

                // 4. Secondary Text Color Squircles (5 Presets + 6th Fitted Color Wheel Squircle)
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
                    }
                )

                // Interactive Circular HSV Color Wheel Picker
                AnimatedVisibility(visible = activeWheelTarget != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.tileBg)
                            .border(1.5.dp, palette.accent, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (activeWheelTarget == "Primary") "Custom Main Heading Color" else "Custom Description / FAQ Color",
                            fontSize = (12.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .pointerInput(activeWheelTarget) {
                                    detectTapGestures { offset ->
                                        val r = size.width / 2f
                                        val dx = offset.x - r
                                        val dy = offset.y - r
                                        val dist = min(hypot(dx, dy), r)
                                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                        if (deg < 0f) deg += 360f
                                        wheelHue = deg
                                        wheelSat = (dist / r).coerceIn(0f, 1f)
                                        val hex = hsvToHex(wheelHue, wheelSat, wheelVal)
                                        if (activeWheelTarget == "Primary") onPrimaryColorChange(hex)
                                        else onSecondaryColorChange(hex)
                                    }
                                }
                                .pointerInput(activeWheelTarget) {
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
                                        val hex = hsvToHex(wheelHue, wheelSat, wheelVal)
                                        if (activeWheelTarget == "Primary") onPrimaryColorChange(hex)
                                        else onSecondaryColorChange(hex)
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
                                val hex = hsvToHex(wheelHue, wheelSat, wheelVal)
                                if (activeWheelTarget == "Primary") onPrimaryColorChange(hex)
                                else onSecondaryColorChange(hex)
                            },
                            valueRange = 0.2f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accent,
                                activeTrackColor = palette.accent
                            )
                        )
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
    onWheelSquircleClick: () -> Unit
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

        val matchedPreset = presets.any { it.equals(currentSetting, ignoreCase = true) }
        val isCustomWheel = !matchedPreset && !currentSetting.equals("Auto", ignoreCase = true)

        Row(
            modifier = Modifier.fillMaxWidth(),
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
}

private fun buildPalette(
    isDark: Boolean,
    highContrast: Boolean,
    primarySetting: String,
    secondarySetting: String,
    accent: Color
): AppPalette {
    val defaultPrimary = if (isDark) {
        if (highContrast) Color(0xFFFFFFFF) else Color(0xFFFAFAFC)
    } else {
        if (highContrast) Color(0xFF000000) else Color(0xFF141417)
    }

    val defaultSecondary = if (isDark) {
        if (highContrast) Color(0xFFF8F9FA) else Color(0xFFDBDDE4)
    } else {
        if (highContrast) Color(0xFF141518) else Color(0xFF383A42)
    }

    val primaryText = if (primarySetting.equals("Auto", ignoreCase = true)) {
        defaultPrimary
    } else {
        parseHexColor(primarySetting, defaultPrimary)
    }

    val secondaryText = if (secondarySetting.equals("Auto", ignoreCase = true)) {
        defaultSecondary
    } else {
        parseHexColor(secondarySetting, defaultSecondary)
    }

    val luminance = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    val onAccent = if (luminance > 0.62f) Color(0xFF121214) else Color(0xFFFFFFFF)

    return if (isDark) {
        AppPalette(
            windowBg = if (highContrast) Color(0xFF0E0E11) else Color(0xFF16161A),
            tileBg = if (highContrast) Color(0xFF1C1C22) else Color(0xFF222228),
            tileBorder = if (highContrast) Color(0x88FFFFFF) else Color(0x45FFFFFF),
            menuSurface = if (highContrast) Color(0xFF18181E) else Color(0xFF24242B),
            popupBg = if (highContrast) Color(0xFF121216) else Color(0xFF1D1D23),
            faqAnswerBg = if (highContrast) Color(0xFF0A0A0D) else Color(0xFF141418),
            subtleSurface = if (highContrast) Color(0x2CFFFFFF) else Color(0x1AFFFFFF),
            primaryText = primaryText,
            secondaryText = secondaryText,
            accent = accent,
            onAccent = onAccent,
            accentSubtle = accent.copy(alpha = 0.26f)
        )
    } else {
        AppPalette(
            windowBg = if (highContrast) Color(0xFFEEF0F4) else Color(0xFFF3F4F7),
            tileBg = Color(0xFFFFFFFF),
            tileBorder = if (highContrast) Color(0x75000000) else Color(0x2D000000),
            menuSurface = Color(0xFFFFFFFF),
            popupBg = Color(0xFFFFFFFF),
            faqAnswerBg = if (highContrast) Color(0xFFE8ECF2) else Color(0xFFF2F4F8),
            subtleSurface = if (highContrast) Color(0x22000000) else Color(0x14000000),
            primaryText = primaryText,
            secondaryText = secondaryText,
            accent = accent,
            onAccent = onAccent,
            accentSubtle = accent.copy(alpha = 0.18f)
        )
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
