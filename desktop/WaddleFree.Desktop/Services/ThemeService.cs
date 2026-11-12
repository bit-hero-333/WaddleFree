using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Imaging;
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

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool DestroyIcon(IntPtr hIcon);

        public static string CurrentThemePreference { get; private set; } = "System";

        private static Bitmap? _cachedBaseIconMask;

        public record PresetPalette(
            MediaColor WindowBg,
            MediaColor TileBg,
            MediaColor TileHoverBg,
            MediaColor MenuSurface,
            MediaColor PopupBg,
            MediaColor FaqAnswerBg,
            MediaColor PrimaryText,
            MediaColor SecondaryText,
            MediaColor? AccentOverride);

        public static bool IsEffectiveDarkMode(AppSettings settings)
        {
            string themePreference = string.IsNullOrWhiteSpace(settings.ThemePreference) ? "System" : settings.ThemePreference;
            return themePreference.ToLowerInvariant() switch
            {
                "dark" => true,
                "light" => false,
                _ => IsWindowsInDarkMode()
            };
        }

        public static void ApplyTheme(AppSettings settings, Window? window = null)
        {
            string themePreference = string.IsNullOrWhiteSpace(settings.ThemePreference) ? "System" : settings.ThemePreference;
            CurrentThemePreference = themePreference;

            bool isDark = IsEffectiveDarkMode(settings);
            bool highContrast = settings.HighContrast;
            var preset = GetPresetPalette(settings.ThemePreset, isDark, highContrast);

            MediaColor windowBg = TryParseColor(settings.WindowBgColor) ?? preset.WindowBg;
            MediaColor tileBg = TryParseColor(settings.TileBgColor) ?? preset.TileBg;
            MediaColor accent = TryParseColor(settings.AccentColor) ?? preset.AccentOverride ?? GetWindowsAccentColor();

            bool customTile = TryParseColor(settings.TileBgColor).HasValue;
            bool customWin = TryParseColor(settings.WindowBgColor).HasValue;

            MediaColor tileHover = customTile ? ShiftBrightness(tileBg, isDark ? 14 : -12) : preset.TileHoverBg;
            MediaColor menuSurface = customTile ? ShiftBrightness(tileBg, isDark ? 4 : 0) : preset.MenuSurface;
            MediaColor popupBg = customTile ? ShiftBrightness(tileBg, isDark ? -6 : 0) : preset.PopupBg;
            MediaColor faqAnswerBg = (customTile || customWin) ? ShiftBrightness(windowBg, isDark ? -6 : -5) : preset.FaqAnswerBg;

            var resources = System.Windows.Application.Current.Resources;

            resources["WindowBackgroundBrush"] = CreateFrozenBrush(windowBg);
            resources["TileBackgroundBrush"] = CreateFrozenBrush(tileBg);
            resources["TileHoverBackgroundBrush"] = CreateFrozenBrush(tileHover);
            resources["MenuSurfaceBrush"] = CreateFrozenBrush(menuSurface);
            resources["PopupBackgroundBrush"] = CreateFrozenBrush(popupBg);
            resources["FaqAnswerBackgroundBrush"] = CreateFrozenBrush(faqAnswerBg);

            if (isDark)
            {
                resources["TileBorderBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromArgb(0x88, 0xFF, 0xFF, 0xFF)
                    : MediaColor.FromArgb(0x45, 0xFF, 0xFF, 0xFF));
                resources["SubtleSurfaceBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromArgb(0x2C, 0xFF, 0xFF, 0xFF)
                    : MediaColor.FromArgb(0x1A, 0xFF, 0xFF, 0xFF));
            }
            else
            {
                resources["TileBorderBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromArgb(0x75, 0x00, 0x00, 0x00)
                    : MediaColor.FromArgb(0x2D, 0x00, 0x00, 0x00));
                resources["SubtleSurfaceBrush"] = CreateFrozenBrush(highContrast
                    ? MediaColor.FromArgb(0x22, 0x00, 0x00, 0x00)
                    : MediaColor.FromArgb(0x14, 0x00, 0x00, 0x00));
            }

            MediaColor primaryColor = TryParseColor(settings.PrimaryTextColor) ?? preset.PrimaryText;
            MediaColor secondaryColor = TryParseColor(settings.SecondaryTextColor) ?? preset.SecondaryText;

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
            resources["TIconSize"] = Math.Round(20.0 * uiScale);
            resources["PortIconSize"] = portIconSize;
            resources["PortIconCornerRadius"] = new CornerRadius(portIconSize / 2.0);
            resources["MenuDividerHeight"] = Math.Round(18.0 * uiScale);
            resources["TileMinHeight"] = Math.Round(130.0 * uiScale);
            resources["TileMaxHeight"] = Math.Round(175.0 * uiScale);
            resources["SquircleSize"] = squircleSize;
            resources["SquircleCornerRadius"] = new CornerRadius(Math.Round(11.0 * uiScale));

            ImageSource? customWindowIcon = CreateWindowIconSource(settings.IconBgColor, settings.IconFgColor, 128);

            foreach (Window openWin in System.Windows.Application.Current.Windows)
            {
                ApplyWindowDwmAttributes(openWin, isDark);
                if (customWindowIcon != null)
                {
                    openWin.Icon = customWindowIcon;
                }
            }
            if (window != null)
            {
                ApplyWindowDwmAttributes(window, isDark);
                if (customWindowIcon != null)
                {
                    window.Icon = customWindowIcon;
                }
            }
        }

        public static PresetPalette GetPresetPalette(string? presetKey, bool isDark, bool highContrast)
        {
            string key = (presetKey ?? "Default").Trim().ToLowerInvariant();

            if (isDark)
            {
                return key switch
                {
                    "ocean" => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0x09, 0x10, 0x1E) : MediaColor.FromRgb(0x0F, 0x17, 0x2A),
                        TileBg: highContrast ? MediaColor.FromRgb(0x15, 0x22, 0x38) : MediaColor.FromRgb(0x1E, 0x29, 0x3B),
                        TileHoverBg: MediaColor.FromRgb(0x28, 0x36, 0x4D),
                        MenuSurface: MediaColor.FromRgb(0x1E, 0x29, 0x3B),
                        PopupBg: MediaColor.FromRgb(0x16, 0x20, 0x32),
                        FaqAnswerBg: MediaColor.FromRgb(0x0B, 0x12, 0x20),
                        PrimaryText: MediaColor.FromRgb(0xF8, 0xFA, 0xFC),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0xE2, 0xE8, 0xF0) : MediaColor.FromRgb(0xBD, 0xCC, 0xDF),
                        AccentOverride: MediaColor.FromRgb(0x38, 0xBD, 0xF8)),

                    "amethyst" => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0x10, 0x0B, 0x1C) : MediaColor.FromRgb(0x16, 0x11, 0x24),
                        TileBg: highContrast ? MediaColor.FromRgb(0x1D, 0x15, 0x30) : MediaColor.FromRgb(0x23, 0x1B, 0x38),
                        TileHoverBg: MediaColor.FromRgb(0x2F, 0x24, 0x4A),
                        MenuSurface: MediaColor.FromRgb(0x25, 0x1C, 0x3B),
                        PopupBg: MediaColor.FromRgb(0x1C, 0x15, 0x2E),
                        FaqAnswerBg: MediaColor.FromRgb(0x12, 0x0D, 0x1E),
                        PrimaryText: MediaColor.FromRgb(0xFA, 0xF5, 0xFF),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0xED, 0xE9, 0xFE) : MediaColor.FromRgb(0xD8, 0xCE, 0xF2),
                        AccentOverride: MediaColor.FromRgb(0xA8, 0x55, 0xF7)),

                    "emerald" => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0x07, 0x12, 0x0F) : MediaColor.FromRgb(0x0C, 0x19, 0x15),
                        TileBg: highContrast ? MediaColor.FromRgb(0x11, 0x22, 0x1D) : MediaColor.FromRgb(0x15, 0x28, 0x22),
                        TileHoverBg: MediaColor.FromRgb(0x1E, 0x38, 0x30),
                        MenuSurface: MediaColor.FromRgb(0x17, 0x2B, 0x25),
                        PopupBg: MediaColor.FromRgb(0x11, 0x21, 0x1C),
                        FaqAnswerBg: MediaColor.FromRgb(0x09, 0x14, 0x11),
                        PrimaryText: MediaColor.FromRgb(0xEC, 0xFD, 0xF5),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0xD1, 0xFA, 0xE5) : MediaColor.FromRgb(0xBA, 0xE6, 0xD4),
                        AccentOverride: MediaColor.FromRgb(0x10, 0xB9, 0x81)),

                    "ember" => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0x14, 0x0D, 0x0B) : MediaColor.FromRgb(0x1C, 0x13, 0x11),
                        TileBg: highContrast ? MediaColor.FromRgb(0x24, 0x17, 0x14) : MediaColor.FromRgb(0x2B, 0x1D, 0x1A),
                        TileHoverBg: MediaColor.FromRgb(0x3A, 0x27, 0x23),
                        MenuSurface: MediaColor.FromRgb(0x2E, 0x1F, 0x1C),
                        PopupBg: MediaColor.FromRgb(0x23, 0x17, 0x15),
                        FaqAnswerBg: MediaColor.FromRgb(0x16, 0x0F, 0x0D),
                        PrimaryText: MediaColor.FromRgb(0xFF, 0xF7, 0xED),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0xFF, 0xED, 0xD5) : MediaColor.FromRgb(0xEB, 0xCE, 0xBE),
                        AccentOverride: MediaColor.FromRgb(0xF9, 0x73, 0x16)),

                    _ => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0x0E, 0x0E, 0x11) : MediaColor.FromRgb(0x16, 0x16, 0x1A),
                        TileBg: highContrast ? MediaColor.FromRgb(0x1C, 0x1C, 0x22) : MediaColor.FromRgb(0x22, 0x22, 0x28),
                        TileHoverBg: highContrast ? MediaColor.FromRgb(0x28, 0x28, 0x32) : MediaColor.FromRgb(0x2C, 0x2C, 0x35),
                        MenuSurface: highContrast ? MediaColor.FromRgb(0x18, 0x18, 0x1E) : MediaColor.FromRgb(0x24, 0x24, 0x2B),
                        PopupBg: highContrast ? MediaColor.FromRgb(0x12, 0x12, 0x16) : MediaColor.FromRgb(0x1D, 0x1D, 0x23),
                        FaqAnswerBg: highContrast ? MediaColor.FromRgb(0x0A, 0x0A, 0x0D) : MediaColor.FromRgb(0x14, 0x14, 0x18),
                        PrimaryText: highContrast ? MediaColor.FromRgb(0xFF, 0xFF, 0xFF) : MediaColor.FromRgb(0xFA, 0xFA, 0xFC),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0xF8, 0xF9, 0xFA) : MediaColor.FromRgb(0xDB, 0xDD, 0xE4),
                        AccentOverride: null)
                };
            }
            else
            {
                return key switch
                {
                    "ocean" => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0xE2, 0xEE, 0xFF) : MediaColor.FromRgb(0xED, 0xF5, 0xFF),
                        TileBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        TileHoverBg: MediaColor.FromRgb(0xDF, 0xEC, 0xFF),
                        MenuSurface: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        PopupBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        FaqAnswerBg: MediaColor.FromRgb(0xE4, 0xF0, 0xFF),
                        PrimaryText: MediaColor.FromRgb(0x0F, 0x17, 0x2A),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0x1E, 0x29, 0x3B) : MediaColor.FromRgb(0x33, 0x41, 0x55),
                        AccentOverride: MediaColor.FromRgb(0x25, 0x63, 0xEB)),

                    "amethyst" => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0xED, 0xE5, 0xFF) : MediaColor.FromRgb(0xF5, 0xF0, 0xFF),
                        TileBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        TileHoverBg: MediaColor.FromRgb(0xEB, 0xE0, 0xFF),
                        MenuSurface: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        PopupBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        FaqAnswerBg: MediaColor.FromRgb(0xEE, 0xE6, 0xFF),
                        PrimaryText: MediaColor.FromRgb(0x1E, 0x10, 0x35),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0x2E, 0x10, 0x65) : MediaColor.FromRgb(0x4C, 0x1D, 0x95),
                        AccentOverride: MediaColor.FromRgb(0x7C, 0x3A, 0xED)),

                    "emerald" => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0xE0, 0xF2, 0xEA) : MediaColor.FromRgb(0xEC, 0xF7, 0xF2),
                        TileBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        TileHoverBg: MediaColor.FromRgb(0xDC, 0xF2, 0xE8),
                        MenuSurface: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        PopupBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        FaqAnswerBg: MediaColor.FromRgb(0xE3, 0xF3, 0xEC),
                        PrimaryText: MediaColor.FromRgb(0x06, 0x28, 0x1E),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0x06, 0x4E, 0x3B) : MediaColor.FromRgb(0x1E, 0x46, 0x38),
                        AccentOverride: MediaColor.FromRgb(0x05, 0x96, 0x69)),

                    "ember" => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0xFA, 0xEC, 0xDE) : MediaColor.FromRgb(0xFD, 0xF5, 0xEC),
                        TileBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        TileHoverBg: MediaColor.FromRgb(0xFA, 0xE6, 0xD4),
                        MenuSurface: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        PopupBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        FaqAnswerBg: MediaColor.FromRgb(0xFA, 0xEB, 0xDC),
                        PrimaryText: MediaColor.FromRgb(0x27, 0x15, 0x0B),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0x43, 0x14, 0x07) : MediaColor.FromRgb(0x5C, 0x34, 0x1D),
                        AccentOverride: MediaColor.FromRgb(0xEA, 0x58, 0x0C)),

                    _ => new PresetPalette(
                        WindowBg: highContrast ? MediaColor.FromRgb(0xEE, 0xF0, 0xF4) : MediaColor.FromRgb(0xF3, 0xF4, 0xF7),
                        TileBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        TileHoverBg: highContrast ? MediaColor.FromRgb(0xE2, 0xE6, 0xEE) : MediaColor.FromRgb(0xEB, 0xED, 0xF3),
                        MenuSurface: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        PopupBg: MediaColor.FromRgb(0xFF, 0xFF, 0xFF),
                        FaqAnswerBg: highContrast ? MediaColor.FromRgb(0xE8, 0xEC, 0xF2) : MediaColor.FromRgb(0xF2, 0xF4, 0xF8),
                        PrimaryText: highContrast ? MediaColor.FromRgb(0x00, 0x00, 0x00) : MediaColor.FromRgb(0x14, 0x14, 0x17),
                        SecondaryText: highContrast ? MediaColor.FromRgb(0x14, 0x15, 0x18) : MediaColor.FromRgb(0x38, 0x3A, 0x42),
                        AccentOverride: null)
                };
            }
        }

        public static MediaColor? TryParseColor(string? value)
        {
            if (string.IsNullOrWhiteSpace(value) || value.Equals("Auto", StringComparison.OrdinalIgnoreCase))
                return null;

            try
            {
                if (MediaColorConverter.ConvertFromString(value.Trim()) is MediaColor parsed)
                    return parsed;
            }
            catch
            {
                // Ignore invalid hex
            }
            return null;
        }

        private static MediaColor ShiftBrightness(MediaColor c, int delta)
        {
            byte r = (byte)Math.Clamp(c.R + delta, 0, 255);
            byte g = (byte)Math.Clamp(c.G + delta, 0, 255);
            byte b = (byte)Math.Clamp(c.B + delta, 0, 255);
            return MediaColor.FromRgb(r, g, b);
        }

        // --- Dynamic Program Icon Rendering (Desktop Window, Taskbar, Tray & Live Preview) ---
        private static Bitmap? GetBaseWhiteIconBitmap()
        {
            if (_cachedBaseIconMask != null)
                return _cachedBaseIconMask;

            try
            {
                var streamInfo = System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/Assets/icon_square_white.png"));
                if (streamInfo?.Stream != null)
                {
                    using var s = streamInfo.Stream;
                    _cachedBaseIconMask = new Bitmap(s);
                    return _cachedBaseIconMask;
                }
            }
            catch
            {
                // Ignore if resource cannot be loaded
            }
            return null;
        }

        public static Bitmap RenderCustomAppIconGdiBitmap(string? bgHex, string? fgHex, int size)
        {
            MediaColor bgMedia = TryParseColor(bgHex) ?? MediaColor.FromRgb(0x18, 0x18, 0x1B);
            MediaColor fgMedia = TryParseColor(fgHex) ?? MediaColor.FromRgb(0xFF, 0xFF, 0xFF);

            var bmp = new Bitmap(size, size, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
            using var g = Graphics.FromImage(bmp);
            g.SmoothingMode = SmoothingMode.HighQuality;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.Clear(System.Drawing.Color.Transparent);

            float cornerRadius = size * 0.225f;
            using (var path = CreateRoundedRectPath(0, 0, size, size, cornerRadius))
            using (var bgBrush = new SolidBrush(System.Drawing.Color.FromArgb(255, bgMedia.R, bgMedia.G, bgMedia.B)))
            {
                g.FillPath(bgBrush, path);
            }

            var baseMask = GetBaseWhiteIconBitmap();
            if (baseMask != null)
            {
                // Artwork bounds in icon_square_white.png (1016x1024): X=100..870 (770), Y=278..842 (564), center=(485, 560)
                // Draw centered with clean padding inside the rounded square
                float scale = (size * 0.72f) / 770.0f;
                float drawW = baseMask.Width * scale;
                float drawH = baseMask.Height * scale;
                float drawX = (size / 2.0f) - (485.0f * scale);
                float drawY = (size / 2.0f) - (560.0f * scale);

                float r = fgMedia.R / 255f;
                float gr = fgMedia.G / 255f;
                float b = fgMedia.B / 255f;

                var colorMatrix = new ColorMatrix(new float[][]
                {
                    new float[] { 0, 0, 0, 0, 0 },
                    new float[] { 0, 0, 0, 0, 0 },
                    new float[] { 0, 0, 0, 0, 0 },
                    new float[] { 0, 0, 0, 1, 0 },
                    new float[] { r, gr, b, 0, 1 }
                });

                using var ia = new ImageAttributes();
                ia.SetColorMatrix(colorMatrix, ColorMatrixFlag.Default, ColorAdjustType.Bitmap);

                g.DrawImage(
                    baseMask,
                    new Rectangle((int)Math.Round(drawX), (int)Math.Round(drawY), (int)Math.Round(drawW), (int)Math.Round(drawH)),
                    0, 0, baseMask.Width, baseMask.Height,
                    GraphicsUnit.Pixel,
                    ia);
            }

            return bmp;
        }

        public static ImageSource? CreateWindowIconSource(string? bgHex, string? fgHex, int size = 128)
        {
            try
            {
                using var gdiBmp = RenderCustomAppIconGdiBitmap(bgHex, fgHex, size);
                var rect = new Rectangle(0, 0, gdiBmp.Width, gdiBmp.Height);
                var bmpData = gdiBmp.LockBits(rect, ImageLockMode.ReadOnly, System.Drawing.Imaging.PixelFormat.Format32bppPArgb);
                try
                {
                    var source = BitmapSource.Create(
                        gdiBmp.Width,
                        gdiBmp.Height,
                        96,
                        96,
                        PixelFormats.Pbgra32,
                        null,
                        bmpData.Scan0,
                        bmpData.Stride * gdiBmp.Height,
                        bmpData.Stride);
                    source.Freeze();
                    return source;
                }
                finally
                {
                    gdiBmp.UnlockBits(bmpData);
                }
            }
            catch
            {
                return null;
            }
        }

        public static System.Drawing.Icon CreateCustomTrayIcon(AppSettings settings)
        {
            try
            {
                using var gdiBmp = RenderCustomAppIconGdiBitmap(settings.IconBgColor, settings.IconFgColor, 32);
                IntPtr hIcon = gdiBmp.GetHicon();
                try
                {
                    using var tempIcon = System.Drawing.Icon.FromHandle(hIcon);
                    return (System.Drawing.Icon)tempIcon.Clone();
                }
                finally
                {
                    DestroyIcon(hIcon);
                }
            }
            catch
            {
                var streamInfo = System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/Assets/app.ico"));
                if (streamInfo?.Stream != null)
                {
                    using var stream = streamInfo.Stream;
                    return new System.Drawing.Icon(stream, new System.Drawing.Size(32, 32));
                }
                return SystemIcons.Application;
            }
        }

        private static GraphicsPath CreateRoundedRectPath(float x, float y, float width, float height, float radius)
        {
            var path = new GraphicsPath();
            float d = radius * 2f;
            path.AddArc(x, y, d, d, 180, 90);
            path.AddArc(x + width - d, y, d, d, 270, 90);
            path.AddArc(x + width - d, y + height - d, d, d, 0, 90);
            path.AddArc(x, y + height - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
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
