using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Net.NetworkInformation;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Navigation;
using System.Windows.Threading;
using Microsoft.Win32;
using WaddleFree.Desktop.Models;
using WaddleFree.Desktop.Services;
using Forms = System.Windows.Forms;

namespace WaddleFree.Desktop
{
    public partial class MainWindow : Window
    {
        private readonly AppSettings _settings;
        private readonly CompanionServer _server;
        private readonly DispatcherTimer _portInfoHideTimer;
        private readonly DispatcherTimer _placementSaveDebounceTimer;
        private readonly List<FaqItem> _faqItems;
        private Forms.NotifyIcon? _trayIcon;
        private TextSettingsWindow? _textSettingsWindow;
        private bool _isExplicitExit;
        private bool _isMenuOpen;
        private bool _isGuideOpen;
        private bool _isPortInfoOpen;
        private bool _isInitializedPlacement;
        private bool _isStartupComplete;

        public MainWindow()
        {
            _settings = AppSettings.Load();

            InitializeComponent();

            RestoreWindowPlacement();
            _isInitializedPlacement = true;

            ThemeService.ApplyTheme(_settings, this);

            _server = new CompanionServer();
            _server.DeviceConnected += OnDeviceConnected;
            _server.Start(_settings);

            ActivePortText.Text = $"Port: {_server.ActivePort}";

            _portInfoHideTimer = new DispatcherTimer
            {
                Interval = TimeSpan.FromMilliseconds(140)
            };
            _portInfoHideTimer.Tick += OnPortInfoHideTimerTick;

            _placementSaveDebounceTimer = new DispatcherTimer
            {
                Interval = TimeSpan.FromMilliseconds(350)
            };
            _placementSaveDebounceTimer.Tick += (_, _) =>
            {
                _placementSaveDebounceTimer.Stop();
                SaveWindowPlacement();
            };

            _faqItems = CreateFaqItems();
            FaqItemsControl.ItemsSource = _faqItems;

            UpdateThemeButtonsVisualState();
            UpdateTrayButtonsAndHint();
            UpdateStartupModeButtonsVisualState();
            RefreshNetworkDetails();
            RefreshConnectedDevicesUi();
            UpdateDynamicPopupConstraints();
            InitializeSystemTray();

            ApplyStartupWindowMode();

            SystemEvents.UserPreferenceChanged += OnSystemPreferenceChanged;
            NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;
        }

        private void ApplyStartupWindowMode()
        {
            string startupMode = (_settings.StartupMode ?? "Full").ToLowerInvariant();
            if (startupMode == "tray")
            {
                ShowInTaskbar = false;
                WindowState = WindowState.Minimized;
                Opacity = 0;
                Loaded += (_, _) =>
                {
                    Hide();
                    Opacity = 1;
                    _isStartupComplete = true;
                };
            }
            else if (startupMode == "taskbar")
            {
                WindowState = WindowState.Minimized;
                Loaded += (_, _) =>
                {
                    _isStartupComplete = true;
                };
            }
            else
            {
                Loaded += (_, _) =>
                {
                    _isStartupComplete = true;
                };
            }
        }

        private static List<FaqItem> CreateFaqItems()
        {
            return new List<FaqItem>
            {
                new FaqItem
                {
                    Question = "How to configure Wake-on-Ethernet (WoL) without random wake-ups?",
                    Answer =
                        "1. Right-click the Windows Start button → open Device Manager → expand Network adapters → double-click your Ethernet controller.\n\n" +
                        "2. Power Management tab: Check 'Allow this device to wake the computer' AND check 'Only allow a magic packet to wake the computer'.\n\n" +
                        "3. Advanced tab: Set 'Wake on Magic Packet' to Enabled, and set 'Wake on Pattern Match' to Disabled (this stops random network traffic from waking your PC!).\n\n" +
                        "4. In BIOS/UEFI: Enable 'Wake on PCIe / LAN' (and disable 'ErP Ready' if it cuts Ethernet standby power)."
                },
                new FaqItem
                {
                    Question = "How does WaddleFree work?",
                    Answer =
                        "When your PC is asleep, the mobile app sends a hardware Magic Packet using your PC's MAC address so your motherboard wakes up. Once Windows is awake, this desktop companion listens locally on your home network so your phone can check the PC's status and put it back to sleep with one tap."
                },
                new FaqItem
                {
                    Question = "Why does my PC need an Ethernet cable instead of Wi-Fi to wake up?",
                    Answer =
                        "Almost all desktop motherboards cut power to Wi-Fi adapters when the PC enters sleep mode. An Ethernet port stays in a tiny low-power listening state while the PC sleeps so it can detect the Magic Packet from your router and trigger the motherboard to power up."
                },
                new FaqItem
                {
                    Question = "Why did Windows Firewall ask for permission on first launch?",
                    Answer =
                        "Windows prompts whenever a new app listens for incoming signals on your local network. Make sure 'Private networks' is allowed so your phone can communicate with your PC over your home Wi-Fi."
                },
                new FaqItem
                {
                    Question = "Why does my PC wake up and then go back to sleep after 2 minutes?",
                    Answer =
                        "Windows 11 has a hidden 'unattended wake timeout' that puts a PC back to sleep after 2 minutes if woken over the network without mouse movement. As long as WaddleFree is running in the background or system tray, it automatically tells Windows to stay awake after a remote wake-up."
                },
                new FaqItem
                {
                    Question = "What if my PC's local IP address changes?",
                    Answer =
                        "Routers occasionally reassign local IP addresses after a router reboot. To keep your PC's IP permanent, open your router settings (or Windows Network Settings) and assign a static local IP address for your PC."
                },
                new FaqItem
                {
                    Question = "How do I start WaddleFree automatically with Windows?",
                    Answer =
                        "WaddleFree never modifies your Windows Registry or system startup settings on its own.\n\n" +
                        "1. Right-click WaddleFree.exe and select 'Create shortcut'.\n\n" +
                        "2. Press Win + R, type shell:startup, and press Enter to open your Windows Startup folder.\n\n" +
                        "3. Move the shortcut into that folder.\n\n" +
                        "Tip: Use the 'On Start' option in the top-right menu to choose whether WaddleFree opens as a full window, minimized in the taskbar, or hidden in the system tray."
                },
                new FaqItem
                {
                    Question = "How do I update WaddleFree?",
                    Answer =
                        "WaddleFree is 100% portable and stores settings.json right next to WaddleFree.exe. To update, exit WaddleFree and paste the new WaddleFree.exe over the old one—your port, theme, text preferences, window position, and connected devices carry over automatically."
                }
            };
        }

        // --- Window Placement (Monitor & Size Persistence) ---
        private void RestoreWindowPlacement()
        {
            if (_settings.WindowWidth is >= 480 and <= 5000)
                Width = _settings.WindowWidth.Value;

            if (_settings.WindowHeight is >= 290 and <= 3500)
                Height = _settings.WindowHeight.Value;

            if (_settings.WindowLeft.HasValue && _settings.WindowTop.HasValue)
            {
                double left = _settings.WindowLeft.Value;
                double top = _settings.WindowTop.Value;
                double width = Width;
                double height = Height;

                if (IsRectVisibleOnAnyScreen(left, top, width, height))
                {
                    WindowStartupLocation = WindowStartupLocation.Manual;
                    Left = left;
                    Top = top;
                }
            }

            if (_settings.WindowMaximized)
            {
                WindowState = WindowState.Maximized;
            }
        }

        private static bool IsRectVisibleOnAnyScreen(double left, double top, double width, double height)
        {
            try
            {
                var windowRect = new Rectangle((int)left + 30, (int)top + 30, Math.Max(50, (int)width - 60), Math.Max(50, (int)height - 60));
                foreach (var screen in Forms.Screen.AllScreens)
                {
                    if (screen.WorkingArea.IntersectsWith(windowRect))
                        return true;
                }
            }
            catch
            {
                return true;
            }
            return false;
        }

        private void SaveWindowPlacement()
        {
            if (!_isInitializedPlacement) return;

            try
            {
                if (WindowState == WindowState.Normal)
                {
                    _settings.WindowLeft = Left;
                    _settings.WindowTop = Top;
                    _settings.WindowWidth = Width;
                    _settings.WindowHeight = Height;
                    _settings.WindowMaximized = false;
                }
                else
                {
                    var bounds = RestoreBounds;
                    if (!bounds.IsEmpty && bounds.Width >= MinWidth && bounds.Height >= MinHeight)
                    {
                        _settings.WindowLeft = bounds.Left;
                        _settings.WindowTop = bounds.Top;
                        _settings.WindowWidth = bounds.Width;
                        _settings.WindowHeight = bounds.Height;
                    }
                    _settings.WindowMaximized = WindowState == WindowState.Maximized;
                }

                _settings.Save();
            }
            catch
            {
                // Ignore save issues
            }
        }

        private void Window_LocationChanged(object sender, EventArgs e)
        {
            if (!_isInitializedPlacement) return;
            _placementSaveDebounceTimer.Stop();
            _placementSaveDebounceTimer.Start();
        }

        private void Window_SizeChanged(object sender, SizeChangedEventArgs e)
        {
            UpdateDynamicPopupConstraints();

            if (!_isInitializedPlacement) return;
            _placementSaveDebounceTimer.Stop();
            _placementSaveDebounceTimer.Start();
        }

        private void UpdateDynamicPopupConstraints()
        {
            if (ActualWidth <= 0 || ActualHeight <= 0) return;

            double scale = Math.Clamp(_settings.TextScale <= 0 ? 1.0 : _settings.TextScale, 0.50, 2.00);
            double uiScale = 0.55 + (0.45 * scale);
            double bottomOffset = Math.Round(46.0 * uiScale);

            GuidePanel.Margin = new Thickness(0, 0, 0, bottomOffset);
            GuidePanel.Width = Math.Min(Math.Round(440.0 * uiScale), Math.Max(260, ActualWidth - 48));
            GuidePanel.MaxHeight = Math.Max(150, ActualHeight - (bottomOffset + 65));

            double topOffset = Math.Round(46.0 * uiScale);
            PortInfoFloatingCard.Margin = new Thickness(0, topOffset, 50, 0);
            PortInfoFloatingCard.MaxWidth = Math.Min(Math.Round(340.0 * uiScale), Math.Max(220, ActualWidth - 65));
        }

        private void SidewaysMenuScrollViewer_PreviewMouseWheel(object sender, MouseWheelEventArgs e)
        {
            if (sender is ScrollViewer sv && sv.ComputedHorizontalScrollBarVisibility == Visibility.Visible)
            {
                sv.ScrollToHorizontalOffset(sv.HorizontalOffset - (e.Delta * 0.5));
                e.Handled = true;
            }
        }

        private void Window_SourceInitialized(object sender, EventArgs e)
        {
            ThemeService.ApplyWindowDwmAttributes(this);
        }

        private void OnSystemPreferenceChanged(object sender, UserPreferenceChangedEventArgs e)
        {
            Dispatcher.Invoke(() =>
            {
                ThemeService.ApplyTheme(_settings, this);
                UpdateThemeButtonsVisualState();
                UpdateTrayButtonsAndHint();
                _textSettingsWindow?.RefreshButtonsVisualState();
            });
        }

        private void OnNetworkAddressChanged(object? sender, EventArgs e)
        {
            Dispatcher.Invoke(RefreshNetworkDetails);
        }

        private void RefreshNetworkDetails()
        {
            var info = NetworkInfoService.GetPrimaryNetworkInfo();
            IpAddressText.Text = info.IpAddress;
            MacAddressText.Text = info.MacAddress;
            AdapterBadgeText.Text = info.AdapterName != "None"
                ? $"Listening • {info.AdapterName}"
                : "Offline";
        }

        private void OnDeviceConnected(object? sender, DeviceActivityEventArgs e)
        {
            Dispatcher.Invoke(() =>
            {
                _settings.UpsertDevice(e.DeviceId, e.DeviceName, e.IpAddress);
                RefreshConnectedDevicesUi();
            });
        }

        private void RefreshConnectedDevicesUi()
        {
            int count = _settings.ConnectedDevices.Count;
            ConnectedCountText.Text = count.ToString();
            ConnectedLabelText.Text = count == 1 ? "Device" : "Devices";

            DevicesItemsControl.ItemsSource = null;
            DevicesItemsControl.ItemsSource = _settings.ConnectedDevices;

            if (count == 0)
            {
                NoDevicesText.Visibility = Visibility.Visible;
                DevicesListScroll.Visibility = Visibility.Collapsed;
                ClearDevicesButton.Visibility = Visibility.Collapsed;
                DevicesTileHintText.Text = "No devices yet • Click";
            }
            else
            {
                NoDevicesText.Visibility = Visibility.Collapsed;
                DevicesListScroll.Visibility = Visibility.Visible;
                ClearDevicesButton.Visibility = Visibility.Visible;
                DevicesTileHintText.Text = $"Latest: {_settings.ConnectedDevices[0].Name}";
            }
        }

        // --- Top-Right Hamburger Menu ---
        private void HamburgerButton_Click(object sender, RoutedEventArgs e)
        {
            _isMenuOpen = !_isMenuOpen;

            if (_isMenuOpen)
            {
                SidewaysMenuPanel.Visibility = Visibility.Visible;
                SidewaysMenuPanel.Opacity = 0;
                MenuSlideTransform.X = 24;

                var fadeIn = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(160))
                {
                    EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
                };
                var slideLeft = new DoubleAnimation(24, 0, TimeSpan.FromMilliseconds(180))
                {
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };

                SidewaysMenuPanel.BeginAnimation(OpacityProperty, fadeIn);
                MenuSlideTransform.BeginAnimation(TranslateTransform.XProperty, slideLeft);
            }
            else
            {
                FadeOutPortInfoCard();

                var fadeOut = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(130));
                var slideRight = new DoubleAnimation(0, 18, TimeSpan.FromMilliseconds(140));
                fadeOut.Completed += (_, _) =>
                {
                    if (!_isMenuOpen)
                        SidewaysMenuPanel.Visibility = Visibility.Collapsed;
                };

                SidewaysMenuPanel.BeginAnimation(OpacityProperty, fadeOut);
                MenuSlideTransform.BeginAnimation(TranslateTransform.XProperty, slideRight);
            }
        }

        // --- 'T + Cog' Text & Readability Settings Window ---
        private void TextSettingsButton_Click(object sender, RoutedEventArgs e)
        {
            if (_textSettingsWindow != null && _textSettingsWindow.IsLoaded)
            {
                _textSettingsWindow.Activate();
                return;
            }

            _textSettingsWindow = new TextSettingsWindow(_settings, () =>
            {
                ThemeService.ApplyTheme(_settings, this);
                UpdateThemeButtonsVisualState();
                UpdateTrayButtonsAndHint();
                UpdateStartupModeButtonsVisualState();
                UpdateDynamicPopupConstraints();
                UpdateTrayIcon();
            })
            {
                Owner = this
            };

            var customIcon = ThemeService.CreateWindowIconSource(_settings.IconBgColor, _settings.IconFgColor, 128);
            if (customIcon != null)
            {
                _textSettingsWindow.Icon = customIcon;
            }

            _textSettingsWindow.Closed += (_, _) => _textSettingsWindow = null;
            _textSettingsWindow.Show();
        }

        // --- Bottom-Left Guide / FAQ Menu ---
        private void GuideButton_Click(object sender, RoutedEventArgs e)
        {
            _isGuideOpen = !_isGuideOpen;
            UpdateDynamicPopupConstraints();

            if (_isGuideOpen)
            {
                GuidePanel.Visibility = Visibility.Visible;
                GuidePanel.Opacity = 0;
                GuideSlideTransform.Y = 22;

                var fadeIn = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(170))
                {
                    EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
                };
                var slideUp = new DoubleAnimation(22, 0, TimeSpan.FromMilliseconds(190))
                {
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };

                GuidePanel.BeginAnimation(OpacityProperty, fadeIn);
                GuideSlideTransform.BeginAnimation(TranslateTransform.YProperty, slideUp);
            }
            else
            {
                var fadeOut = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(140));
                var slideDown = new DoubleAnimation(0, 18, TimeSpan.FromMilliseconds(150));
                fadeOut.Completed += (_, _) =>
                {
                    if (!_isGuideOpen)
                        GuidePanel.Visibility = Visibility.Collapsed;
                };

                GuidePanel.BeginAnimation(OpacityProperty, fadeOut);
                GuideSlideTransform.BeginAnimation(TranslateTransform.YProperty, slideDown);
            }
        }

        private void FaqItemButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is FrameworkElement fe && fe.Tag is FaqItem item)
            {
                item.IsExpanded = !item.IsExpanded;
            }
        }

        // --- Theme Options ---
        private void ThemeDarkButton_Click(object sender, RoutedEventArgs e) => SetThemePreference("Dark");
        private void ThemeLightButton_Click(object sender, RoutedEventArgs e) => SetThemePreference("Light");
        private void ThemeSystemButton_Click(object sender, RoutedEventArgs e) => SetThemePreference("System");

        private void SetThemePreference(string pref)
        {
            _settings.ThemePreference = pref;
            _settings.Save();
            ThemeService.ApplyTheme(_settings, this);
            UpdateThemeButtonsVisualState();
            UpdateTrayButtonsAndHint();
            UpdateStartupModeButtonsVisualState();
            _textSettingsWindow?.RefreshButtonsVisualState();
        }

        private void UpdateThemeButtonsVisualState()
        {
            string pref = (_settings.ThemePreference ?? "System").ToLowerInvariant();
            StylePillButton(ThemeDarkButton, pref == "dark");
            StylePillButton(ThemeLightButton, pref == "light");
            StylePillButton(ThemeSystemButton, pref != "dark" && pref != "light");
        }

        // --- Tray Minimize Options ---
        private void TrayMinimizeButton_Click(object sender, RoutedEventArgs e) => SetTrayBehavior("Minimize");
        private void TrayCloseButton_Click(object sender, RoutedEventArgs e) => SetTrayBehavior("Close");
        private void TrayBothButton_Click(object sender, RoutedEventArgs e) => SetTrayBehavior("Both");
        private void TrayNoneButton_Click(object sender, RoutedEventArgs e) => SetTrayBehavior("None");

        private void SetTrayBehavior(string behavior)
        {
            _settings.TrayBehavior = behavior;
            _settings.Save();
            UpdateTrayButtonsAndHint();
        }

        private void UpdateTrayButtonsAndHint()
        {
            string behavior = (_settings.TrayBehavior ?? "Close").ToLowerInvariant();

            StylePillButton(TrayMinimizeButton, behavior == "minimize");
            StylePillButton(TrayCloseButton, behavior == "close");
            StylePillButton(TrayBothButton, behavior == "both");
            StylePillButton(TrayNoneButton, behavior == "none");
        }

        // --- Startup Window Mode Options ---
        private void StartFullButton_Click(object sender, RoutedEventArgs e) => SetStartupMode("Full");
        private void StartTaskbarButton_Click(object sender, RoutedEventArgs e) => SetStartupMode("Taskbar");
        private void StartTrayButton_Click(object sender, RoutedEventArgs e) => SetStartupMode("Tray");

        private void SetStartupMode(string mode)
        {
            _settings.StartupMode = mode;
            _settings.Save();
            UpdateStartupModeButtonsVisualState();
        }

        private void UpdateStartupModeButtonsVisualState()
        {
            string mode = (_settings.StartupMode ?? "Full").ToLowerInvariant();
            StylePillButton(StartFullButton, mode != "taskbar" && mode != "tray");
            StylePillButton(StartTaskbarButton, mode == "taskbar");
            StylePillButton(StartTrayButton, mode == "tray");
        }

        private void GitHubRepoButton_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                Process.Start(new ProcessStartInfo("https://github.com/bit-hero-333/WaddleFree")
                {
                    UseShellExecute = true
                });
            }
            catch
            {
                // Ignore if browser launch fails
            }
        }

        private void StylePillButton(System.Windows.Controls.Button btn, bool isActive)
        {
            var res = System.Windows.Application.Current.Resources;
            if (isActive)
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

        // --- Port Info Hover Card & Smooth Fade ---
        private void PortInfoArea_MouseEnter(object sender, System.Windows.Input.MouseEventArgs e)
        {
            _portInfoHideTimer.Stop();

            if (!_isPortInfoOpen)
            {
                _isPortInfoOpen = true;
                PortInfoFloatingCard.Visibility = Visibility.Visible;

                var fadeIn = new DoubleAnimation(1.0, TimeSpan.FromMilliseconds(160))
                {
                    EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
                };
                PortInfoFloatingCard.BeginAnimation(OpacityProperty, fadeIn);
            }
        }

        private void PortInfoArea_MouseLeave(object sender, System.Windows.Input.MouseEventArgs e)
        {
            _portInfoHideTimer.Stop();
            _portInfoHideTimer.Start();
        }

        private void OnPortInfoHideTimerTick(object? sender, EventArgs e)
        {
            _portInfoHideTimer.Stop();

            if (!PortInfoIconBorder.IsMouseOver && !PortInfoFloatingCard.IsMouseOver)
            {
                FadeOutPortInfoCard();
            }
        }

        private void FadeOutPortInfoCard()
        {
            if (!_isPortInfoOpen) return;
            _isPortInfoOpen = false;

            var fadeOut = new DoubleAnimation(0.0, TimeSpan.FromMilliseconds(240))
            {
                EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseInOut }
            };
            fadeOut.Completed += (_, _) =>
            {
                if (!_isPortInfoOpen)
                {
                    PortInfoFloatingCard.Visibility = Visibility.Collapsed;
                }
            };
            PortInfoFloatingCard.BeginAnimation(OpacityProperty, fadeOut);
        }

        private void PortWikiHyperlink_RequestNavigate(object sender, RequestNavigateEventArgs e)
        {
            try
            {
                Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri)
                {
                    UseShellExecute = true
                });
                e.Handled = true;
            }
            catch
            {
                // Ignore if browser launch fails
            }
        }

        // --- Tiles Interaction ---
        private void DevicesDropdownButton_Click(object sender, RoutedEventArgs e)
        {
            RefreshConnectedDevicesUi();
            DevicesPopup.IsOpen = !DevicesPopup.IsOpen;
        }

        private void ClearDevicesButton_Click(object sender, RoutedEventArgs e)
        {
            _settings.ConnectedDevices.Clear();
            _settings.Save();
            RefreshConnectedDevicesUi();
        }

        private async void IpTileButton_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                System.Windows.Clipboard.SetText(IpAddressText.Text);
                IpCopyHintText.Text = "✓ Copied to clipboard";
                IpCopyHintText.Foreground = (System.Windows.Media.Brush)System.Windows.Application.Current.Resources["AccentBrush"];
                await Task.Delay(1800);
                IpCopyHintText.Text = "Click to copy";
                IpCopyHintText.Foreground = (System.Windows.Media.Brush)System.Windows.Application.Current.Resources["SecondaryTextBrush"];
            }
            catch
            {
                // Ignore clipboard lock
            }
        }

        private async void MacTileButton_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                System.Windows.Clipboard.SetText(MacAddressText.Text);
                MacCopyHintText.Text = "✓ Copied to clipboard";
                MacCopyHintText.Foreground = (System.Windows.Media.Brush)System.Windows.Application.Current.Resources["AccentBrush"];
                await Task.Delay(1800);
                MacCopyHintText.Text = "Click to copy";
                MacCopyHintText.Foreground = (System.Windows.Media.Brush)System.Windows.Application.Current.Resources["SecondaryTextBrush"];
            }
            catch
            {
                // Ignore clipboard lock
            }
        }

        // --- System Tray & Window Lifecycle ---
        private void InitializeSystemTray()
        {
            System.Drawing.Icon generatedIcon = ThemeService.CreateCustomTrayIcon(_settings);

            var contextMenu = new Forms.ContextMenuStrip();
            var openItem = new Forms.ToolStripMenuItem("Open WaddleFree");
            openItem.Click += (_, _) => Dispatcher.Invoke(ShowMainWindow);

            var sleepItem = new Forms.ToolStripMenuItem("Put PC to Sleep Now");
            sleepItem.Click += (_, _) => PowerManagementService.PutPcToSleep();

            var exitItem = new Forms.ToolStripMenuItem("Exit");
            exitItem.Click += (_, _) => Dispatcher.Invoke(ExitApplication);

            contextMenu.Items.Add(openItem);
            contextMenu.Items.Add(sleepItem);
            contextMenu.Items.Add(new Forms.ToolStripSeparator());
            contextMenu.Items.Add(exitItem);

            _trayIcon = new Forms.NotifyIcon
            {
                Icon = generatedIcon,
                Text = "WaddleFree Companion",
                Visible = true,
                ContextMenuStrip = contextMenu
            };

            _trayIcon.DoubleClick += (_, _) => Dispatcher.Invoke(ShowMainWindow);
        }

        private void UpdateTrayIcon()
        {
            if (_trayIcon == null) return;
            try
            {
                var oldIcon = _trayIcon.Icon;
                _trayIcon.Icon = ThemeService.CreateCustomTrayIcon(_settings);
                oldIcon?.Dispose();
            }
            catch
            {
                // Ignore tray icon update issues
            }
        }

        private void ShowMainWindow()
        {
            ShowInTaskbar = true;
            Opacity = 1;
            Show();
            if (WindowState == WindowState.Minimized)
                WindowState = WindowState.Normal;
            Activate();
        }

        private void ExitApplication()
        {
            _isExplicitExit = true;
            Close();
        }

        private void Window_StateChanged(object sender, EventArgs e)
        {
            if (WindowState == WindowState.Minimized)
            {
                if (_isStartupComplete)
                {
                    string behavior = (_settings.TrayBehavior ?? "Close").ToLowerInvariant();
                    if (behavior == "minimize" || behavior == "both")
                    {
                        Hide();
                    }
                }
            }
            else if (_isInitializedPlacement)
            {
                SaveWindowPlacement();
            }
        }

        private void Window_Closing(object sender, CancelEventArgs e)
        {
            SaveWindowPlacement();

            if (!_isExplicitExit)
            {
                string behavior = (_settings.TrayBehavior ?? "Close").ToLowerInvariant();
                if (behavior == "close" || behavior == "both")
                {
                    e.Cancel = true;
                    Hide();
                    return;
                }
            }

            _textSettingsWindow?.Close();
            _portInfoHideTimer.Stop();
            _placementSaveDebounceTimer.Stop();
            SystemEvents.UserPreferenceChanged -= OnSystemPreferenceChanged;
            NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;

            if (_trayIcon != null)
            {
                _trayIcon.Visible = false;
                _trayIcon.Dispose();
            }

            _server.Dispose();
        }
    }
}
