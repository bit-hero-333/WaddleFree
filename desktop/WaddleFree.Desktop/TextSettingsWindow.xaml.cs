using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using WaddleFree.Desktop.Models;
using WaddleFree.Desktop.Services;
using MediaColor = System.Windows.Media.Color;

namespace WaddleFree.Desktop
{
    public partial class TextSettingsWindow : Window
    {
        private static readonly double[] AllowedScales = { 0.50, 0.75, 1.00, 1.25, 1.50, 1.75, 2.00 };

        private readonly AppSettings _settings;
        private readonly Action _onSettingsChanged;

        private bool _isDraggingSizeSlider;
        private bool _isDraggingColorWheel;
        private string? _activeWheelTarget; // "Accent", "WinBg", "TileBg", "Primary", "Secondary", "IconBg", "IconFg"
        private double _wheelHue = 0;
        private double _wheelSat = 0;

        public TextSettingsWindow(AppSettings settings, Action onSettingsChanged)
        {
            _settings = settings;
            _onSettingsChanged = onSettingsChanged;

            InitializeComponent();

            // Paint the color wheel directly as the Background ImageBrush of every 6th squircle
            var squircleWheelBmp = CreateColorWheelBitmap(72, circularMask: false);
            var wheelSquircleBrush = new ImageBrush(squircleWheelBmp) { Stretch = Stretch.Fill };
            wheelSquircleBrush.Freeze();

            AccentWheelSquircle.Background = wheelSquircleBrush;
            WinBgWheelSquircle.Background = wheelSquircleBrush;
            TileBgWheelSquircle.Background = wheelSquircleBrush;
            PrimWheelSquircle.Background = wheelSquircleBrush;
            SecWheelSquircle.Background = wheelSquircleBrush;
            IconBgWheelSquircle.Background = wheelSquircleBrush;
            IconFgWheelSquircle.Background = wheelSquircleBrush;

            LargeColorWheelImage.Source = CreateColorWheelBitmap(180, circularMask: true);

            RefreshButtonsVisualState();
        }

        private void Window_SourceInitialized(object sender, EventArgs e)
        {
            ThemeService.ApplyWindowDwmAttributes(this);
        }

        private void ApplyAndRefresh()
        {
            _settings.Save();
            _onSettingsChanged();
            RefreshButtonsVisualState();
        }

        public void RefreshButtonsVisualState()
        {
            bool isDark = ThemeService.IsEffectiveDarkMode(_settings);

            // Dynamically change theme preset names based on Dark vs Light mode
            // OLED is only available when Dark mode is active
            PresetModeLabel.Text = isDark ? "Dark Mode Palettes" : "Light Mode Palettes";
            PresetDefaultBtn.Content = "Default";
            PresetOledBtn.Content = "OLED";
            PresetOledBtn.Visibility = isDark ? Visibility.Visible : Visibility.Collapsed;
            PresetOceanBtn.Content = isDark ? "Midnight" : "Daybreak";
            PresetAmethystBtn.Content = isDark ? "Amethyst" : "Lavender";
            PresetEmeraldBtn.Content = isDark ? "Emerald" : "Sage";
            PresetEmberBtn.Content = isDark ? "Ember" : "Warm Sand";

            string preset = (_settings.ThemePreset ?? "Default").Trim();
            string effectivePreset = (!isDark && string.Equals(preset, "OLED", StringComparison.OrdinalIgnoreCase))
                ? "Default"
                : preset;

            StylePill(PresetDefaultBtn, string.Equals(effectivePreset, "Default", StringComparison.OrdinalIgnoreCase));
            StylePill(PresetOledBtn, isDark && string.Equals(effectivePreset, "OLED", StringComparison.OrdinalIgnoreCase));
            StylePill(PresetOceanBtn, string.Equals(effectivePreset, "Ocean", StringComparison.OrdinalIgnoreCase));
            StylePill(PresetAmethystBtn, string.Equals(effectivePreset, "Amethyst", StringComparison.OrdinalIgnoreCase));
            StylePill(PresetEmeraldBtn, string.Equals(effectivePreset, "Emerald", StringComparison.OrdinalIgnoreCase));
            StylePill(PresetEmberBtn, string.Equals(effectivePreset, "Ember", StringComparison.OrdinalIgnoreCase));

            RenderSteppedSizeSlider();

            StylePill(HighContrastBtn, _settings.HighContrast);
            StylePill(BoldTextBtn, _settings.BoldBodyText);

            HighlightActiveSquircle(
                _settings.AccentColor,
                new[] { AccentSquircle1, AccentSquircle2, AccentSquircle3, AccentSquircle4, AccentSquircle5 },
                AccentWheelSquircle);

            HighlightActiveSquircle(
                _settings.WindowBgColor,
                new[] { WinBgSquircle1, WinBgSquircle2, WinBgSquircle3, WinBgSquircle4, WinBgSquircle5 },
                WinBgWheelSquircle);

            HighlightActiveSquircle(
                _settings.TileBgColor,
                new[] { TileBgSquircle1, TileBgSquircle2, TileBgSquircle3, TileBgSquircle4, TileBgSquircle5 },
                TileBgWheelSquircle);

            HighlightActiveSquircle(
                _settings.PrimaryTextColor,
                new[] { PrimSquircle1, PrimSquircle2, PrimSquircle3, PrimSquircle4, PrimSquircle5 },
                PrimWheelSquircle);

            HighlightActiveSquircle(
                _settings.SecondaryTextColor,
                new[] { SecSquircle1, SecSquircle2, SecSquircle3, SecSquircle4, SecSquircle5 },
                SecWheelSquircle);

            HighlightActiveSquircle(
                _settings.IconBgColor,
                new[] { IconBgSquircle1, IconBgSquircle2, IconBgSquircle3, IconBgSquircle4, IconBgSquircle5 },
                IconBgWheelSquircle);

            HighlightActiveSquircle(
                _settings.IconFgColor,
                new[] { IconFgSquircle1, IconFgSquircle2, IconFgSquircle3, IconFgSquircle4, IconFgSquircle5 },
                IconFgWheelSquircle);

            IconLivePreviewImage.Source = ThemeService.CreateWindowIconSource(_settings.IconBgColor, _settings.IconFgColor, 96);
        }

        // --- Theme Preset Buttons ---
        private void PresetBtn_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button btn && btn.Tag is string presetKey)
            {
                _settings.ThemePreset = presetKey;
                // Clear individual element overrides so the preset's cohesive palette applies immediately
                _settings.AccentColor = "Auto";
                _settings.WindowBgColor = "Auto";
                _settings.TileBgColor = "Auto";
                _settings.PrimaryTextColor = "Auto";
                _settings.SecondaryTextColor = "Auto";
                ApplyAndRefresh();
            }
        }

        // --- 7-Dot Stepped Slider (50%, 75%, 100%, 125%, 150%, 175%, 200%) ---
        private void SizeSliderCanvas_SizeChanged(object sender, SizeChangedEventArgs e)
        {
            RenderSteppedSizeSlider();
        }

        private void RenderSteppedSizeSlider()
        {
            double width = SizeSliderCanvas.ActualWidth;
            if (width <= 24) return;

            const double pad = 9.0; // Half of 18px thumb width
            double usableWidth = width - (pad * 2);

            SliderTrackBg.Width = usableWidth;
            Canvas.SetLeft(SliderTrackBg, pad);

            int activeIndex = GetClosestStepIndex(_settings.TextScale);
            double activeFraction = activeIndex / (double)(AllowedScales.Length - 1);
            double activeX = pad + (usableWidth * activeFraction);

            SliderTrackFill.Width = Math.Max(0, activeX - pad);
            Canvas.SetLeft(SliderTrackFill, pad);
            Canvas.SetLeft(SliderThumb, activeX - pad);

            int pct = (int)Math.Round(AllowedScales[activeIndex] * 100);
            TextSizePercentLabel.Text = $"{pct}%";

            // Render the 7 step dots along the slider track
            SliderDotsLayer.Children.Clear();
            var res = System.Windows.Application.Current.Resources;
            var accentBrush = (System.Windows.Media.Brush)res["AccentBrush"];
            var inactiveDotBrush = (System.Windows.Media.Brush)res["PrimaryTextBrush"];

            for (int i = 0; i < AllowedScales.Length; i++)
            {
                double frac = i / (double)(AllowedScales.Length - 1);
                double dotX = pad + (usableWidth * frac);

                var dot = new Ellipse
                {
                    Width = 7,
                    Height = 7,
                    Fill = i <= activeIndex ? accentBrush : inactiveDotBrush,
                    Opacity = i <= activeIndex ? 1.0 : 0.55,
                    IsHitTestVisible = false
                };

                Canvas.SetLeft(dot, dotX - 3.5);
                Canvas.SetTop(dot, 10.5);
                SliderDotsLayer.Children.Add(dot);
            }
        }

        private static int GetClosestStepIndex(double scale)
        {
            int bestIndex = 2; // Default 100% (index 2)
            double bestDist = double.MaxValue;
            for (int i = 0; i < AllowedScales.Length; i++)
            {
                double d = Math.Abs(AllowedScales[i] - scale);
                if (d < bestDist)
                {
                    bestDist = d;
                    bestIndex = i;
                }
            }
            return bestIndex;
        }

        private void SizeSliderCanvas_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            _isDraggingSizeSlider = true;
            SizeSliderCanvas.CaptureMouse();
            UpdateScaleFromMouseX(e.GetPosition(SizeSliderCanvas).X);
        }

        private void SizeSliderCanvas_MouseMove(object sender, System.Windows.Input.MouseEventArgs e)
        {
            if (_isDraggingSizeSlider)
            {
                UpdateScaleFromMouseX(e.GetPosition(SizeSliderCanvas).X);
            }
        }

        private void SizeSliderCanvas_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            if (_isDraggingSizeSlider)
            {
                _isDraggingSizeSlider = false;
                SizeSliderCanvas.ReleaseMouseCapture();
            }
        }

        private void UpdateScaleFromMouseX(double mouseX)
        {
            double width = SizeSliderCanvas.ActualWidth;
            if (width <= 24) return;

            const double pad = 9.0;
            double usableWidth = width - (pad * 2);
            double fraction = Math.Clamp((mouseX - pad) / usableWidth, 0.0, 1.0);

            int stepIndex = (int)Math.Round(fraction * (AllowedScales.Length - 1));
            stepIndex = Math.Clamp(stepIndex, 0, AllowedScales.Length - 1);

            double newScale = AllowedScales[stepIndex];
            if (Math.Abs(_settings.TextScale - newScale) > 0.01)
            {
                _settings.TextScale = newScale;
                ApplyAndRefresh();
            }
            else
            {
                RenderSteppedSizeSlider();
            }
        }

        // --- Squircles & HSV Color Wheel ---
        private static void HighlightActiveSquircle(
            string? currentColorSetting,
            System.Windows.Controls.Button[] presetButtons,
            System.Windows.Controls.Button wheelButton)
        {
            var res = System.Windows.Application.Current.Resources;
            var accentBrush = (System.Windows.Media.Brush)res["AccentBrush"];
            var normalBorder = (System.Windows.Media.Brush)res["TileBorderBrush"];

            string current = (currentColorSetting ?? "Auto").Trim();
            bool matchedPreset = false;

            foreach (var btn in presetButtons)
            {
                string tag = (btn.Tag as string) ?? string.Empty;
                bool isMatch = string.Equals(current, tag, StringComparison.OrdinalIgnoreCase);
                if (isMatch) matchedPreset = true;

                btn.BorderBrush = isMatch ? accentBrush : normalBorder;
                btn.BorderThickness = isMatch ? new Thickness(3) : new Thickness(1.5);
            }

            bool isCustomWheel = !matchedPreset && !string.Equals(current, "Auto", StringComparison.OrdinalIgnoreCase);
            wheelButton.BorderBrush = isCustomWheel ? accentBrush : normalBorder;
            wheelButton.BorderThickness = isCustomWheel ? new Thickness(3) : new Thickness(1.5);
        }

        private void AccentPresetSquircle_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button btn && btn.Tag is string hex)
            {
                _settings.AccentColor = hex;
                ApplyAndRefresh();
            }
        }

        private void WinBgPresetSquircle_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button btn && btn.Tag is string hex)
            {
                _settings.WindowBgColor = hex;
                ApplyAndRefresh();
            }
        }

        private void TileBgPresetSquircle_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button btn && btn.Tag is string hex)
            {
                _settings.TileBgColor = hex;
                ApplyAndRefresh();
            }
        }

        private void PrimPresetSquircle_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button btn && btn.Tag is string hex)
            {
                _settings.PrimaryTextColor = hex;
                ApplyAndRefresh();
            }
        }

        private void SecPresetSquircle_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button btn && btn.Tag is string hex)
            {
                _settings.SecondaryTextColor = hex;
                ApplyAndRefresh();
            }
        }

        private void IconBgPresetSquircle_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button btn && btn.Tag is string hex)
            {
                _settings.IconBgColor = hex;
                ApplyAndRefresh();
            }
        }

        private void IconFgPresetSquircle_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button btn && btn.Tag is string hex)
            {
                _settings.IconFgColor = hex;
                ApplyAndRefresh();
            }
        }

        private void AccentWheelSquircle_Click(object sender, RoutedEventArgs e) => ToggleColorWheelForTarget("Accent", AccentSectionHost);
        private void WinBgWheelSquircle_Click(object sender, RoutedEventArgs e) => ToggleColorWheelForTarget("WinBg", WinBgSectionHost);
        private void TileBgWheelSquircle_Click(object sender, RoutedEventArgs e) => ToggleColorWheelForTarget("TileBg", TileBgSectionHost);
        private void PrimWheelSquircle_Click(object sender, RoutedEventArgs e) => ToggleColorWheelForTarget("Primary", PrimSectionHost);
        private void SecWheelSquircle_Click(object sender, RoutedEventArgs e) => ToggleColorWheelForTarget("Secondary", SecSectionHost);
        private void IconBgWheelSquircle_Click(object sender, RoutedEventArgs e) => ToggleColorWheelForTarget("IconBg", IconBgSectionHost);
        private void IconFgWheelSquircle_Click(object sender, RoutedEventArgs e) => ToggleColorWheelForTarget("IconFg", IconFgSectionHost);

        private void ToggleColorWheelForTarget(string target, StackPanel targetHost)
        {
            if (ColorWheelPickerPanel.Visibility == Visibility.Visible && _activeWheelTarget == target)
            {
                ColorWheelPickerPanel.Visibility = Visibility.Collapsed;
                _activeWheelTarget = null;
                return;
            }

            _activeWheelTarget = target;
            WheelTargetTitleText.Text = target switch
            {
                "Accent" => "Custom Accent Color",
                "WinBg" => "Custom Window Background",
                "TileBg" => "Custom Card & Surface Color",
                "Primary" => "Custom Main Heading Color",
                "Secondary" => "Custom Description / FAQ Color",
                "IconBg" => "Custom Icon Background",
                "IconFg" => "Custom Icon Lines Color",
                _ => "Custom Color"
            };

            if (ColorWheelPickerPanel.Parent is Panel oldParent)
            {
                oldParent.Children.Remove(ColorWheelPickerPanel);
            }
            targetHost.Children.Add(ColorWheelPickerPanel);

            ColorWheelPickerPanel.Visibility = Visibility.Visible;
        }

        private void WheelCanvas_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            _isDraggingColorWheel = true;
            WheelCanvas.CaptureMouse();
            PickColorFromWheelPoint(e.GetPosition(WheelCanvas));
        }

        private void WheelCanvas_MouseMove(object sender, System.Windows.Input.MouseEventArgs e)
        {
            if (_isDraggingColorWheel)
            {
                PickColorFromWheelPoint(e.GetPosition(WheelCanvas));
            }
        }

        private void WheelCanvas_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            if (_isDraggingColorWheel)
            {
                _isDraggingColorWheel = false;
                WheelCanvas.ReleaseMouseCapture();
            }
        }

        private void PickColorFromWheelPoint(System.Windows.Point pt)
        {
            double size = WheelCanvas.Width;
            double radius = size / 2.0;
            double dx = pt.X - radius;
            double dy = pt.Y - radius;
            double dist = Math.Sqrt(dx * dx + dy * dy);

            if (dist > radius)
            {
                dx = (dx / dist) * radius;
                dy = (dy / dist) * radius;
                dist = radius;
            }

            Canvas.SetLeft(WheelSelectorRing, radius + dx - (WheelSelectorRing.Width / 2.0));
            Canvas.SetTop(WheelSelectorRing, radius + dy - (WheelSelectorRing.Height / 2.0));

            double angleDeg = Math.Atan2(dy, dx) * (180.0 / Math.PI) + 90.0;
            if (angleDeg < 0) angleDeg += 360.0;

            _wheelHue = angleDeg;
            _wheelSat = Math.Clamp(dist / radius, 0.0, 1.0);

            ApplyWheelHsvColor();
        }

        private void WheelBrightnessSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (!IsLoaded || _activeWheelTarget == null) return;
            ApplyWheelHsvColor();
        }

        private void ApplyWheelHsvColor()
        {
            if (_activeWheelTarget == null) return;

            double val = WheelBrightnessSlider.Value;
            MediaColor picked = HsvToRgb(_wheelHue, _wheelSat, val);
            string hex = $"#{picked.R:X2}{picked.G:X2}{picked.B:X2}";

            WheelPreviewSquircle.Background = new SolidColorBrush(picked);
            WheelHexText.Text = hex;

            switch (_activeWheelTarget)
            {
                case "Accent":
                    _settings.AccentColor = hex;
                    break;
                case "WinBg":
                    _settings.WindowBgColor = hex;
                    break;
                case "TileBg":
                    _settings.TileBgColor = hex;
                    break;
                case "Primary":
                    _settings.PrimaryTextColor = hex;
                    break;
                case "Secondary":
                    _settings.SecondaryTextColor = hex;
                    break;
                case "IconBg":
                    _settings.IconBgColor = hex;
                    break;
                case "IconFg":
                    _settings.IconFgColor = hex;
                    break;
            }

            ApplyAndRefresh();
        }

        private static WriteableBitmap CreateColorWheelBitmap(int size, bool circularMask)
        {
            var bmp = new WriteableBitmap(size, size, 96, 96, PixelFormats.Bgra32, null);
            byte[] pixels = new byte[size * size * 4];
            double center = (size - 1) / 2.0;
            double radius = size / 2.0;
            double maxRadius = circularMask ? radius - 1.0 : radius * 1.42;

            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    double dx = x - center;
                    double dy = y - center;
                    double dist = Math.Sqrt(dx * dx + dy * dy);

                    int idx = (y * size + x) * 4;

                    if (circularMask && dist > radius)
                    {
                        pixels[idx + 0] = 0;
                        pixels[idx + 1] = 0;
                        pixels[idx + 2] = 0;
                        pixels[idx + 3] = 0;
                        continue;
                    }

                    double angleDeg = Math.Atan2(dy, dx) * (180.0 / Math.PI) + 90.0;
                    if (angleDeg < 0) angleDeg += 360.0;

                    double sat = Math.Clamp(dist / maxRadius, 0.0, 1.0);
                    MediaColor c = HsvToRgb(angleDeg, sat, 1.0);

                    byte alpha = 255;
                    if (circularMask && dist > radius - 1.5)
                    {
                        alpha = (byte)Math.Clamp((radius - dist) / 1.5 * 255.0, 0.0, 255.0);
                    }

                    pixels[idx + 0] = (byte)(c.B * alpha / 255);
                    pixels[idx + 1] = (byte)(c.G * alpha / 255);
                    pixels[idx + 2] = (byte)(c.R * alpha / 255);
                    pixels[idx + 3] = alpha;
                }
            }

            bmp.WritePixels(new Int32Rect(0, 0, size, size), pixels, size * 4, 0);
            bmp.Freeze();
            return bmp;
        }

        private static MediaColor HsvToRgb(double h, double s, double v)
        {
            h = ((h % 360.0) + 360.0) % 360.0;
            double c = v * s;
            double x = c * (1.0 - Math.Abs((h / 60.0) % 2.0 - 1.0));
            double m = v - c;

            double r1, g1, b1;
            if (h < 60) { r1 = c; g1 = x; b1 = 0; }
            else if (h < 120) { r1 = x; g1 = c; b1 = 0; }
            else if (h < 180) { r1 = 0; g1 = c; b1 = x; }
            else if (h < 240) { r1 = 0; g1 = x; b1 = c; }
            else if (h < 300) { r1 = x; g1 = 0; b1 = c; }
            else { r1 = c; g1 = 0; b1 = x; }

            byte r = (byte)Math.Clamp(Math.Round((r1 + m) * 255.0), 0, 255);
            byte g = (byte)Math.Clamp(Math.Round((g1 + m) * 255.0), 0, 255);
            byte b = (byte)Math.Clamp(Math.Round((b1 + m) * 255.0), 0, 255);
            return MediaColor.FromRgb(r, g, b);
        }

        // --- Contrast & Weight Toggles ---
        private static void StylePill(System.Windows.Controls.Button btn, bool active)
        {
            var res = System.Windows.Application.Current.Resources;
            if (active)
            {
                btn.Background = (System.Windows.Media.Brush)res["AccentBrush"];
                btn.Foreground = (System.Windows.Media.Brush)res["OnAccentTextBrush"];
            }
            else
            {
                btn.Background = System.Windows.Media.Brushes.Transparent;
                btn.Foreground = (System.Windows.Media.Brush)res["SecondaryTextBrush"];
            }
        }

        private void HighContrastBtn_Click(object sender, RoutedEventArgs e)
        {
            _settings.HighContrast = !_settings.HighContrast;
            ApplyAndRefresh();
        }

        private void BoldTextBtn_Click(object sender, RoutedEventArgs e)
        {
            _settings.BoldBodyText = !_settings.BoldBodyText;
            ApplyAndRefresh();
        }

        private void ResetDefaults_Click(object sender, RoutedEventArgs e)
        {
            _settings.ThemePreset = "Default";
            _settings.TextScale = 1.00;
            _settings.HighContrast = false;
            _settings.BoldBodyText = false;
            _settings.AccentColor = "Auto";
            _settings.WindowBgColor = "Auto";
            _settings.TileBgColor = "Auto";
            _settings.PrimaryTextColor = "Auto";
            _settings.SecondaryTextColor = "Auto";
            _settings.IconBgColor = "#18181B";
            _settings.IconFgColor = "#FFFFFF";
            ColorWheelPickerPanel.Visibility = Visibility.Collapsed;
            _activeWheelTarget = null;
            ApplyAndRefresh();
        }

        private void DoneButton_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }
    }
}
