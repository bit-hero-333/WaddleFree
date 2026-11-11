using System;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace WaddleFree.Desktop.Services
{
    public record PcNetworkInfo(string IpAddress, string MacAddress, string AdapterName);

    public static class NetworkInfoService
    {
        public static PcNetworkInfo GetPrimaryNetworkInfo()
        {
            try
            {
                var interfaces = NetworkInterface.GetAllNetworkInterfaces()
                    .Where(ni =>
                        ni.OperationalStatus == OperationalStatus.Up &&
                        ni.NetworkInterfaceType != NetworkInterfaceType.Loopback &&
                        ni.NetworkInterfaceType != NetworkInterfaceType.Tunnel &&
                        !IsVirtualAdapter(ni.Description, ni.Name))
                    .OrderByDescending(ni => HasIpv4Gateway(ni))
                    .ThenByDescending(ni => ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet)
                    .ThenByDescending(ni => ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211)
                    .ToList();

                // Fallback: if filtering excluded everything, include any Up non-loopback interface
                if (interfaces.Count == 0)
                {
                    interfaces = NetworkInterface.GetAllNetworkInterfaces()
                        .Where(ni => ni.OperationalStatus == OperationalStatus.Up &&
                                     ni.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                        .ToList();
                }

                foreach (var ni in interfaces)
                {
                    var ipProps = ni.GetIPProperties();
                    var ipv4 = ipProps.UnicastAddresses
                        .FirstOrDefault(ua =>
                            ua.Address.AddressFamily == AddressFamily.InterNetwork &&
                            !IPAddress.IsLoopback(ua.Address) &&
                            !ua.Address.ToString().StartsWith("169.254."));

                    if (ipv4 != null)
                    {
                        var macBytes = ni.GetPhysicalAddress().GetAddressBytes();
                        var macFormatted = macBytes.Length > 0
                            ? string.Join(":", macBytes.Select(b => b.ToString("X2")))
                            : "Unknown";

                        return new PcNetworkInfo(ipv4.Address.ToString(), macFormatted, ni.Name);
                    }
                }
            }
            catch
            {
                // Fallback below
            }

            return new PcNetworkInfo("Unavailable", "Unavailable", "None");
        }

        private static bool HasIpv4Gateway(NetworkInterface ni)
        {
            try
            {
                return ni.GetIPProperties().GatewayAddresses
                    .Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork &&
                              !g.Address.Equals(IPAddress.Any));
            }
            catch
            {
                return false;
            }
        }

        private static bool IsVirtualAdapter(string description, string name)
        {
            string combined = $"{description} {name}".ToLowerInvariant();
            string[] virtualKeywords =
            {
                "virtual", "hyper-v", "vethernet", "vmware", "vbox",
                "wsl", "docker", "tailscale", "zerotier", "hamachi",
                "wireguard", "tap-", "tun", "loopback", "bluetooth"
            };
            return virtualKeywords.Any(k => combined.Contains(k));
        }
    }
}
