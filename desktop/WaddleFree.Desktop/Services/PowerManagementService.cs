using System;
using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace WaddleFree.Desktop.Services
{
    public static class PowerManagementService
    {
        [Flags]
        private enum EXECUTION_STATE : uint
        {
            ES_AWAYMODE_REQUIRED = 0x00000040,
            ES_CONTINUOUS = 0x80000000,
            ES_DISPLAY_REQUIRED = 0x00000002,
            ES_SYSTEM_REQUIRED = 0x00000001
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        private static extern EXECUTION_STATE SetThreadExecutionState(EXECUTION_STATE esFlags);

        [DllImport("powrprof.dll", SetLastError = true)]
        private static extern bool SetSuspendState(bool hibernate, bool forceCritical, bool disableWakeEvent);

        private static bool _initialized;

        public static void Initialize()
        {
            if (_initialized) return;
            _initialized = true;

            // Prevent Windows 11 2-minute unattended sleep timeout right after waking via WoL
            KeepSystemAwake();
            SystemEvents.PowerModeChanged += OnPowerModeChanged;
        }

        public static void Shutdown()
        {
            if (!_initialized) return;
            SystemEvents.PowerModeChanged -= OnPowerModeChanged;
            SetThreadExecutionState(EXECUTION_STATE.ES_CONTINUOUS);
            _initialized = false;
        }

        private static void OnPowerModeChanged(object sender, PowerModeChangedEventArgs e)
        {
            if (e.Mode == PowerModes.Resume)
            {
                KeepSystemAwake();
            }
        }

        public static void KeepSystemAwake()
        {
            try
            {
                SetThreadExecutionState(EXECUTION_STATE.ES_CONTINUOUS | EXECUTION_STATE.ES_SYSTEM_REQUIRED);
            }
            catch
            {
                // Ignore if restricted
            }
        }

        public static void PutPcToSleep()
        {
            try
            {
                // Clear execution state lock before entering sleep
                SetThreadExecutionState(EXECUTION_STATE.ES_CONTINUOUS);
                SetSuspendState(hibernate: false, forceCritical: false, disableWakeEvent: false);
            }
            catch
            {
                // Ignore errors
            }
        }
    }
}
