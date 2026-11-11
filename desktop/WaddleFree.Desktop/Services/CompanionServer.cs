using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using WaddleFree.Desktop.Models;

namespace WaddleFree.Desktop.Services
{
    public class DeviceActivityEventArgs : EventArgs
    {
        public string DeviceId { get; init; } = string.Empty;
        public string DeviceName { get; init; } = "Android Device";
        public string IpAddress { get; init; } = string.Empty;
        public string Action { get; init; } = "ping";
    }

    public sealed class CompanionServer : IDisposable
    {
        private const int Zone2MinPort = 20000;
        private const int Zone2MaxPort = 49151;

        public int ActivePort { get; private set; }

        private TcpListener? _tcpListener;
        private UdpClient? _udpServer;
        private CancellationTokenSource? _cts;

        public bool IsRunning { get; private set; }

        public event EventHandler<DeviceActivityEventArgs>? DeviceConnected;

        /// <summary>
        /// Derives the starting Zone 2 port from the PC's MAC address so the mobile app
        /// (which also has the MAC address) knows where to look without hardcoding a single port.
        /// </summary>
        public static int DeriveInitialZone2Port(string macAddress)
        {
            string normalized = (macAddress ?? string.Empty)
                .Replace(":", "")
                .Replace("-", "")
                .Trim()
                .ToUpperInvariant();

            uint hash = 2166136261u;
            foreach (char c in normalized)
            {
                hash ^= c;
                hash *= 16777619u;
            }

            int range = Zone2MaxPort - Zone2MinPort - 20; // Leave headroom for fallback steps
            return Zone2MinPort + (int)(hash % (uint)range);
        }

        public static int FindUnreservedZone2Port(string macAddress)
        {
            var usedPorts = new HashSet<int>();
            try
            {
                var ipProps = IPGlobalProperties.GetIPGlobalProperties();
                foreach (var ep in ipProps.GetActiveTcpListeners())
                    usedPorts.Add(ep.Port);
                foreach (var ep in ipProps.GetActiveUdpListeners())
                    usedPorts.Add(ep.Port);
            }
            catch
            {
                // Ignore if OS restricts table query
            }

            int startPort = DeriveInitialZone2Port(macAddress);
            for (int offset = 0; offset < 500; offset++)
            {
                int candidate = startPort + offset;
                if (candidate > Zone2MaxPort)
                    candidate = Zone2MinPort + (candidate - Zone2MaxPort - 1);

                if (!usedPorts.Contains(candidate))
                    return candidate;
            }

            return startPort;
        }

        public void Start(AppSettings settings)
        {
            if (IsRunning) return;

            _cts = new CancellationTokenSource();
            var token = _cts.Token;

            var netInfo = NetworkInfoService.GetPrimaryNetworkInfo();

            // If a port was already chosen previously, reuse it from portable settings.json.
            // Otherwise, find an unreserved port in Zone 2 and save it permanently.
            int targetPort = settings.SelectedPort is >= 1024 and <= Zone2MaxPort
                ? settings.SelectedPort
                : FindUnreservedZone2Port(netInfo.MacAddress);

            for (int attempt = 0; attempt < 20; attempt++)
            {
                int candidatePort = targetPort + attempt;
                if (candidatePort > Zone2MaxPort)
                    candidatePort = Zone2MinPort + (candidatePort - Zone2MaxPort - 1);

                try
                {
                    var tcp = new TcpListener(IPAddress.Any, candidatePort);
                    tcp.Start();
                    _tcpListener = tcp;
                    ActivePort = candidatePort;

                    // Persist the chosen port forever if it wasn't saved yet
                    if (settings.SelectedPort != candidatePort)
                    {
                        settings.SelectedPort = candidatePort;
                        settings.Save();
                    }

                    try
                    {
                        _udpServer = new UdpClient(candidatePort);
                        _ = Task.Run(() => ListenUdpAsync(token), token);
                    }
                    catch
                    {
                        // TCP bound cleanly
                    }

                    _ = Task.Run(() => AcceptTcpClientsAsync(token), token);
                    IsRunning = true;
                    break;
                }
                catch
                {
                    // Port was busy; try next unreserved port
                }
            }
        }

        public void Stop()
        {
            if (!IsRunning) return;
            IsRunning = false;

            try { _cts?.Cancel(); } catch { }
            try { _tcpListener?.Stop(); } catch { }
            try { _udpServer?.Close(); } catch { }
        }

        private async Task AcceptTcpClientsAsync(CancellationToken token)
        {
            while (!token.IsCancellationRequested && _tcpListener != null)
            {
                try
                {
                    var client = await _tcpListener.AcceptTcpClientAsync(token);
                    _ = Task.Run(() => HandleTcpClientAsync(client, token), token);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch
                {
                    await Task.Delay(200, token);
                }
            }
        }

        private async Task HandleTcpClientAsync(TcpClient client, CancellationToken token)
        {
            using (client)
            {
                try
                {
                    client.ReceiveTimeout = 3000;
                    client.SendTimeout = 3000;

                    string remoteIp = (client.Client.RemoteEndPoint as IPEndPoint)?.Address.ToString() ?? string.Empty;
                    if (remoteIp.StartsWith("::ffff:"))
                        remoteIp = remoteIp.Substring(7);

                    using var stream = client.GetStream();
                    using var reader = new StreamReader(stream, Encoding.UTF8, detectEncodingFromByteOrderMarks: false, bufferSize: 1024, leaveOpen: true);

                    string? requestLine = await reader.ReadLineAsync(token);
                    if (string.IsNullOrWhiteSpace(requestLine))
                        return;

                    string[] parts = requestLine.Split(' ');
                    string rawPath = parts.Length > 1 ? parts[1] : "/";

                    string deviceId = remoteIp;
                    string deviceName = "Android Device";

                    string? headerLine;
                    while (!string.IsNullOrEmpty(headerLine = await reader.ReadLineAsync(token)))
                    {
                        int colonIndex = headerLine.IndexOf(':');
                        if (colonIndex > 0)
                        {
                            string headerKey = headerLine.Substring(0, colonIndex).Trim();
                            string headerVal = headerLine.Substring(colonIndex + 1).Trim();

                            if (headerKey.Equals("X-Device-Id", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrWhiteSpace(headerVal))
                                deviceId = Uri.UnescapeDataString(headerVal);
                            else if (headerKey.Equals("X-Device-Name", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrWhiteSpace(headerVal))
                                deviceName = Uri.UnescapeDataString(headerVal);
                        }
                    }

                    string path = rawPath;
                    int queryIdx = rawPath.IndexOf('?');
                    if (queryIdx >= 0)
                    {
                        path = rawPath.Substring(0, queryIdx);
                        string query = rawPath.Substring(queryIdx + 1);
                        foreach (var pair in query.Split('&', StringSplitOptions.RemoveEmptyEntries))
                        {
                            var kv = pair.Split('=', 2);
                            if (kv.Length == 2)
                            {
                                string k = Uri.UnescapeDataString(kv[0]);
                                string v = Uri.UnescapeDataString(kv[1]);
                                if (k.Equals("deviceId", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrWhiteSpace(v))
                                    deviceId = v;
                                else if (k.Equals("deviceName", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrWhiteSpace(v))
                                    deviceName = v;
                            }
                        }
                    }

                    bool isSleepCommand = path.Equals("/sleep", StringComparison.OrdinalIgnoreCase);

                    DeviceConnected?.Invoke(this, new DeviceActivityEventArgs
                    {
                        DeviceId = deviceId,
                        DeviceName = deviceName,
                        IpAddress = remoteIp,
                        Action = isSleepCommand ? "sleep" : "status"
                    });

                    var netInfo = NetworkInfoService.GetPrimaryNetworkInfo();
                    string responseJson = JsonSerializer.Serialize(new
                    {
                        status = isSleepCommand ? "sleeping" : "awake",
                        pcName = Environment.MachineName,
                        ip = netInfo.IpAddress,
                        mac = netInfo.MacAddress,
                        port = ActivePort
                    });

                    byte[] bodyBytes = Encoding.UTF8.GetBytes(responseJson);
                    string httpHeader =
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        $"Content-Length: {bodyBytes.Length}\r\n" +
                        "Connection: close\r\n\r\n";

                    byte[] headerBytes = Encoding.UTF8.GetBytes(httpHeader);
                    await stream.WriteAsync(headerBytes, token);
                    await stream.WriteAsync(bodyBytes, token);
                    await stream.FlushAsync(token);

                    if (isSleepCommand)
                    {
                        _ = Task.Run(async () =>
                        {
                            await Task.Delay(350);
                            PowerManagementService.PutPcToSleep();
                        });
                    }
                }
                catch
                {
                    // Ignore malformed client connections
                }
            }
        }

        private async Task ListenUdpAsync(CancellationToken token)
        {
            while (!token.IsCancellationRequested && _udpServer != null)
            {
                try
                {
                    var result = await _udpServer.ReceiveAsync(token);
                    string message = Encoding.UTF8.GetString(result.Buffer).Trim();

                    if (message.StartsWith("WADDLEFREE_", StringComparison.OrdinalIgnoreCase))
                    {
                        string[] parts = message.Split('|');
                        string cmd = parts[0].ToUpperInvariant();
                        string remoteIp = result.RemoteEndPoint.Address.ToString();
                        string deviceId = parts.Length > 1 && !string.IsNullOrWhiteSpace(parts[1]) ? parts[1] : remoteIp;
                        string deviceName = parts.Length > 2 && !string.IsNullOrWhiteSpace(parts[2]) ? parts[2] : "Android Device";

                        bool isSleep = cmd == "WADDLEFREE_SLEEP";

                        DeviceConnected?.Invoke(this, new DeviceActivityEventArgs
                        {
                            DeviceId = deviceId,
                            DeviceName = deviceName,
                            IpAddress = remoteIp,
                            Action = isSleep ? "sleep" : "ping"
                        });

                        var netInfo = NetworkInfoService.GetPrimaryNetworkInfo();
                        string reply = isSleep
                            ? $"WADDLEFREE_SLEEPING|{Environment.MachineName}|{netInfo.IpAddress}|{netInfo.MacAddress}|{ActivePort}"
                            : $"WADDLEFREE_AWAKE|{Environment.MachineName}|{netInfo.IpAddress}|{netInfo.MacAddress}|{ActivePort}";

                        byte[] replyBytes = Encoding.UTF8.GetBytes(reply);
                        await _udpServer.SendAsync(replyBytes, replyBytes.Length, result.RemoteEndPoint);

                        if (isSleep)
                        {
                            _ = Task.Run(async () =>
                            {
                                await Task.Delay(350);
                                PowerManagementService.PutPcToSleep();
                            });
                        }
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch
                {
                    await Task.Delay(200, token);
                }
            }
        }

        public void Dispose()
        {
            Stop();
            _cts?.Dispose();
        }
    }
}
