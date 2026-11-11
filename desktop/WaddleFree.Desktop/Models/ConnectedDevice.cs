using System;

namespace WaddleFree.Desktop.Models
{
    public class ConnectedDevice
    {
        public string Id { get; set; } = string.Empty;
        public string Name { get; set; } = "Android Device";
        public string IpAddress { get; set; } = string.Empty;
        public DateTime LastSeen { get; set; } = DateTime.Now;

        public string DisplaySubtitle => string.IsNullOrWhiteSpace(IpAddress)
            ? $"Last active: {FormatRelativeTime(LastSeen)}"
            : $"{IpAddress} • {FormatRelativeTime(LastSeen)}";

        private static string FormatRelativeTime(DateTime time)
        {
            var diff = DateTime.Now - time;
            if (diff.TotalSeconds < 60) return "Just now";
            if (diff.TotalMinutes < 60) return $"{(int)diff.TotalMinutes}m ago";
            if (diff.TotalHours < 24) return $"{(int)diff.TotalHours}h ago";
            return time.ToString("MMM d, HH:mm");
        }
    }
}
