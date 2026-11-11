using System;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using Microsoft.Win32;
using WaddleFree.Desktop.Models;
using MediaColor = System.Windows.Media.Color;
using MediaColorConverter = System.Windows.Media.ColorConverter;

namespace WaddleFree.Desktop.Services
{
    public static class ThemeService
    {
        private const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
        private const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
        private const int DWMWCP_ROUND = 2;

        [DllImport("dwmapi.dll", PreserveSig = true)]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

        public static string CurrentThemePreference { get; private set; } = "System";

        public static void ApplyTheme(AppSettings settings, Window? window = null)
        {
            string themePreference = string.IsNullOrWhiteSpace(settings.ThemePreference) ? "System" : settings.ThemePreference;
            CurrentThemePreference = themePreference;

            bool isDark = themePreference.ToLowerInvariant() switch
            {
                "dark" => true,
                "light" => false,
                _ => IsWindowsInDarkMode()
            };

            bool highContrast = settings.HighContrast;
            MediaColor accent = GetWindowsAccentColor();
            var resources = System.Windows.Application.Current.Resources;

            if (isDark)
            {
                resources["WindowBackgroundBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0x0E, 0x0E, 0x11)
                    : MediaColor.FromRgb(0x16, 0x16, 0x1A));
                resources["TileBackgroundBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0x1C, 0x1C, 0x22)
                    : MediaColor.FromRgb(0x22, 0x22, 0x28));
                resources["TileHoverBackgroundBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0x28, 0x28, 0x32)
                    : MediaColor.FromRgb(0x2C, 0x2C, 0x35));
                resources["TileBorderBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromArgb(0x88, 0xFF, 0xFF, 0xFF)
                    : MediaColor.FromArgb(0x45, 0xFF, 0xFF, 0xFF));
                resources["MenuSurfaceBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0x18, 0x18, 0x1E)
                    : MediaColor.FromRgb(0x24, 0x24, 0x2B));
                resources["PopupBackgroundBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0x12, 0x12, 0x16)
                    : MediaColor.FromRgb(0x1D, 0x1D, 0x23));
                resources["FaqAnswerBackgroundBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0x0A, 0x0A, 0x0D)
                    : MediaColor.FromRgb(0x14, 0x14, 0x18));
                resources["SubtleSurfaceBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromArgb(0x2C, 0xFF, 0xFF, 0xFF)
                    : MediaColor.FromArgb(0x1A, 0xFF, 0xFF, 0xFF));
            }
            else
            {
                resources["WindowBackgroundBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0xEE, 0xF0, 0xF4)
                    : MediaColor.FromRgb(0xF3, 0xF4, 0xF7));
                resources["TileBackgroundBrush"] = CreateFrozenBrush(MediaColor.FromRgb(0xFF, 0xFF, 0xFF));
                resources["TileHoverBackgroundBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0xE2, 0xE6, 0xEE)
                    : MediaColor.FromRgb(0xEB, 0xED, 0xF3));
                resources["TileBorderBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromArgb(0x75, 0x00, 0x00, 0x00)
                    : MediaColor.FromArgb(0x2D, 0x00, 0x00, 0x00));
                resources["MenuSurfaceBrush"] = CreateFrozenBrush(MediaColor.FromRgb(0xFF, 0xFF, 0xFF));
                resources["PopupBackgroundBrush"] = CreateFrozenBrush(MediaColor.FromRgb(0xFF, 0xFF, 0xFF));
                resources["FaqAnswerBackgroundBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromRgb(0xE8, 0xEC, 0xF2)
                    : MediaColor.FromRgb(0xF2, 0xF4, 0xF8));
                resources["SubtleSurfaceBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromArgb(0x22, 0x00, 0x00, 0x00)
                    : MediaColor.FromArgb(0x14, 0x00, 0x00, 0x00));
            }

            MediaColor primaryColor = ResolveTextColor(settings.PrimaryTextColor, isPrimary: true, isDark, highContrast);
            MediaColor secondaryColor = ResolveTextColor(settings.SecondaryTextColor, isPrimary: false, isDark, highContrast);

            resources["PrimaryTextBrush"] = CreateFrozenBrush(primaryColor);
            resources["SecondaryTextBrush"] = CreateFrozenBrush(secondaryColor);

            resources["AccentBrush"] = CreateFrozenBrush(accent);
            resources["AccentSubtleBrush"] = CreateFrozenBrush(MediaColor.FromArgb(isDark ? (byte)0x42 : (byte)0x2A, accent.R, accent.G, accent.B));
            resources["AccentBorderBrush"] = CreateFrozenBrush(MediaColor.FromArgb(isDark ? (byte)0xAA : (byte)0x88, accent.R, accent.G, accent.B));
            resources["OnAccentTextBrush"] = CreateFrozenBrush(GetContrastText(accent));

            // Apply Typography Scale (50% to 200% in 25% steps) & Proportional UI Element Scaling
            double scale = Math.Clamp(settings.TextScale <= 0 ? 1.0 : settings.TextScale, 0.50, 2.00);
            double uiScale = 0.55 + (0.45 * scale); // Smooth proportional scaling for buttons, icons, and tiles

            resources["FontSizeTitle"] = Math.Round(16.5 * scale, 1);
            resources["FontSizeTileHeader"] = Math.Round(13.5 * scale, 1);
            resources["FontSizeTileValue"] = Math.Round(22.0 * scale, 1);
            resources["FontSizeBody"] = Math.Round(13.5 * scale, 1);
            resources["FontSizeSmall"] = Math.Round(12.5 * scale, 1);
            resources["FontSizeTiny"] = Math.Round(11.0 * scale, 1);
            resources["BodyFontWeight"] = settings.BoldBodyText ? FontWeights.SemiBold : FontWeights.Normal;

            double iconBtnSize = Math.Round(40.0 * uiScale);
            double portIconSize = Math.Round(18.0 * uiScale);
            double squircleSize = Math.Round(36.0 * uiScale);

            resources["IconButtonSize"] = iconBtnSize;
            resources["IconInnerSize"] = Math.Round(17.0 * uiScale);
            resources["GuideBadgeSize"] = Math.Round(22.0 * uiScale);
            resources["TButtonWidth"] = Math.Round(34.0 * uiScale);
            resources["TButtonHeight"] = Math.Round(30.0 * uiScale);
            resources["TIconSize"] = Math.Round(21.0 * uiScale);
            resources["PortIconSize"] = portIconSize;
            resources["PortIconCornerRadius"] = new CornerRadius(portIconSize / 2.0);
            resources["MenuDividerHeight"] = Math.Round(18.0 * uiScale);
            resources["TileMinHeight"] = Math.Round(130.0 * uiScale);
            resources["TileMaxHeight"] = Math.Round(175.0 * uiScale);
            resources["SquircleSize"] = squircleSize;
            resources["SquircleCornerRadius"] = new CornerRadius(Math.Round(11.0 * uiScale));

            foreach (Window openWin in System.Windows.Application.Current.Windows)
            {
                ApplyWindowDwmAttributes(openWin, isDark);
            }
            if (window != null)
            {
                ApplyWindowDwmAttributes(window, isDark);
            }
        }

        public static MediaColor ResolveTextColor(string? value, bool isPrimary, bool isDark, bool highContrast)
        {
            if (!string.IsNullOrWhiteSpace(value) && !value.Equals("Auto", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    if (MediaColorConverter.ConvertFromString(value) is MediaColor parsed)
                        return parsed;
                }
                catch
                {
                    // Fall back to default below
                }
            }

            if (isPrimary)
            {
                return isDark
                    ? (highContrast ? MediaColor.FromRgb(0xFF, 0xFF, 0xFF) : MediaColor.FromRgb(0xFA, 0xFA, 0xFC))
                    : (highContrast ? MediaColor.FromRgb(0x00, 0x00, 0x00) : MediaColor.FromRgb(0x14, 0x14, 0x17));
            }
            else
            {
                return isDark
                    ? (highContrast ? MediaColor.FromRgb(0xF8, 0xF9, 0xFA) : MediaColor.FromRgb(0xDB, 0xDD, 0xE4))
                    : (highContrast ? MediaColor.FromRgb(0x14, 0x15, 0x18) : MediaColor.FromRgb(0x38, 0x3A, 0x42));
            }
        }

        public static void ApplyWindowDwmAttributes(Window window, bool? forceDark = null)
        {
            try
            {
                IntPtr hwnd = new WindowInteropHelper(window).Handle;
                if (hwnd == IntPtr.Zero) return;

                bool isDark = forceDark ?? (CurrentThemePreference.ToLowerInvariant() switch
                {
                    "dark" => true,
                    "light" => false,
                    _ => IsWindowsInDarkMode()
                });

                int darkModeValue = isDark ? 1 : 0;
                DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref darkModeValue, sizeof(int));

                int cornerPref = DWMWCP_ROUND;
                DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref cornerPref, sizeof(int));
            }
            catch
            {
                // Ignore on older OS builds
            }
        }

        public static bool IsWindowsInDarkMode()
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
                if (key?.GetValue("AppsUseLightTheme") is int value)
                {
                    return value == 0;
                }
            }
            catch
            {
                // Default to dark
            }
            return true;
        }

        public static MediaColor GetWindowsAccentColor()
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\DWM");
                if (key?.GetValue("AccentColor") is int abgr)
                {
                    byte r = (byte)(abgr & 0xFF);
                    byte g = (byte)((abgr >> 8) & 0xFF);
                    byte b = (byte)((abgr >> 16) & 0xFF);
                    return MediaColor.FromRgb(r, g, b);
                }
            }
            catch
            {
                // Fallback below
            }

            try
            {
                return SystemParameters.WindowGlassColor;
            }
            catch
            {
                return MediaColor.FromRgb(0x00, 0x78, 0xD4);
            }
        }

        private static MediaColor GetContrastText(MediaColor bg)
        {
            double luminance = (0.299 * bg.R + 0.587 * bg.G + 0.114 * bg.B) / 255.0;
            return luminance > 0.62 ? MediaColor.FromRgb(0x12, 0x12, 0x14) : MediaColor.FromRgb(0xFF, 0xFF, 0xFF);
        }

        private static SolidColorBrush CreateFrozenBrush(MediaColor color)
        {
            var brush = new SolidColorBrush(color);
            brush.Freeze();
            return brush;
        }
    }
}
