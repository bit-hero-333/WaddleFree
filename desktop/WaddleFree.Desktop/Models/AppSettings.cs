using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace WaddleFree.Desktop.Models
{
    public class AppSettings
    {
        public string ThemePreference { get; set; } = "System"; // "System", "Dark", "Light"
        public string TrayBehavior { get; set; } = "Close"; // "Close", "Minimize", "Both", "None"
        public int SelectedPort { get; set; } = 0; // Picked once from unreserved Zone 2 ports and persisted forever

        // Theme, Colors & Readability Customization
        public string ThemePreset { get; set; } = "Default"; // "Default", "Ocean", "Amethyst", "Emerald", "Ember"
        public double TextScale { get; set; } = 1.0; // 0.50 to 2.00
        public bool HighContrast { get; set; } = false;
        public bool BoldBodyText { get; set; } = false;
        public string AccentColor { get; set; } = "Auto"; // "Auto" or "#RRGGBB"
        public string WindowBgColor { get; set; } = "Auto"; // "Auto" or "#RRGGBB"
        public string TileBgColor { get; set; } = "Auto"; // "Auto" or "#RRGGBB"
        public string PrimaryTextColor { get; set; } = "Auto"; // "Auto" or "#RRGGBB"
        public string SecondaryTextColor { get; set; } = "Auto"; // "Auto" or "#RRGGBB"
        public string IconBgColor { get; set; } = "#18181B"; // Default dark squircle background
        public string IconFgColor { get; set; } = "#FFFFFF"; // Default white icon lines

        // Saved Window Placement (Monitor position & size)
        public double? WindowLeft { get; set; }
        public double? WindowTop { get; set; }
        public double? WindowWidth { get; set; }
        public double? WindowHeight { get; set; }
        public bool WindowMaximized { get; set; }

        public List<ConnectedDevice> ConnectedDevices { get; set; } = new();

        // Portable path: stored directly alongside WaddleFree.exe so pasting a new EXE over the old one keeps all settings
        private static readonly string PortableSettingsPath = Path.Combine(
            AppContext.BaseDirectory,
            "settings.json");

        public static AppSettings Load()
        {
            try
            {
                if (File.Exists(PortableSettingsPath))
                {
                    var json = File.ReadAllText(PortableSettingsPath);
                    var loaded = JsonSerializer.Deserialize<AppSettings>(json);
                    if (loaded != null)
                        return loaded;
                }
            }
            catch
            {
                // Ignore corrupted settings and fall back to defaults
            }
            return new AppSettings();
        }

        public void Save()
        {
            try
            {
                var json = JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(PortableSettingsPath, json);
            }
            catch
            {
                // Ignore disk write failures
            }
        }

        public void UpsertDevice(string id, string name, string ipAddress)
        {
            if (string.IsNullOrWhiteSpace(id))
                id = ipAddress;
            if (string.IsNullOrWhiteSpace(name))
                name = "Android Device";

            var existing = ConnectedDevices.FirstOrDefault(d =>
                string.Equals(d.Id, id, StringComparison.OrdinalIgnoreCase) ||
                (!string.IsNullOrWhiteSpace(ipAddress) && string.Equals(d.IpAddress, ipAddress, StringComparison.OrdinalIgnoreCase)));

            if (existing != null)
            {
                existing.Id = id;
                existing.Name = name;
                existing.IpAddress = ipAddress;
                existing.LastSeen = DateTime.Now;
            }
            else
            {
                ConnectedDevices.Insert(0, new ConnectedDevice
                {
                    Id = id,
                    Name = name,
                    IpAddress = ipAddress,
                    LastSeen = DateTime.Now
                });
            }

            ConnectedDevices = ConnectedDevices.OrderByDescending(d => d.LastSeen).ToList();
            Save();
        }

        public void RemoveDevice(string id)
        {
            ConnectedDevices.RemoveAll(d => string.Equals(d.Id, id, StringComparison.OrdinalIgnoreCase));
            Save();
        }
    }
}
