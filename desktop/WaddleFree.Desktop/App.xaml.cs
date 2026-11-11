using System;
using System.Threading;
using System.Windows;
using WaddleFree.Desktop.Services;

namespace WaddleFree.Desktop
{
    public partial class App : System.Windows.Application
    {
        private static Mutex? _singleInstanceMutex;

        protected override void OnStartup(StartupEventArgs e)
        {
            const string mutexName = "Global\\WaddleFree_Desktop_SingleInstance_Mutex";
            _singleInstanceMutex = new Mutex(true, mutexName, out bool createdNew);

            if (!createdNew)
            {
                System.Windows.MessageBox.Show(
                    "WaddleFree is already running (check your system tray).",
                    "WaddleFree",
                    MessageBoxButton.OK,
                    MessageBoxImage.Information);
                Shutdown();
                return;
            }

            PowerManagementService.Initialize();
            base.OnStartup(e);
        }

        protected override void OnExit(ExitEventArgs e)
        {
            PowerManagementService.Shutdown();
            try
            {
                _singleInstanceMutex?.ReleaseMutex();
                _singleInstanceMutex?.Dispose();
            }
            catch
            {
                // Ignore
            }
            base.OnExit(e);
        }
    }
}
