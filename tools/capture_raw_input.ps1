param(
    [string]$Filter = "d10bcb55ca78",
    [string]$OutFile = "artifacts/raw_input_events.json",
    [int]$Seconds = 15
)

$ErrorActionPreference = "Stop"

Add-Type -ReferencedAssemblies System.Windows.Forms,System.Drawing -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

public static class RawInputReverse {
    private const int WM_INPUT = 0x00FF;
    private const uint RID_INPUT = 0x10000003;
    private const uint RIDI_DEVICENAME = 0x20000007;
    private const uint RIDEV_INPUTSINK = 0x00000100;
    private const uint RIM_TYPEMOUSE = 0;
    private const uint RIM_TYPEKEYBOARD = 1;
    private const uint RIM_TYPEHID = 2;

    [StructLayout(LayoutKind.Sequential)]
    private struct RAWINPUTDEVICE {
        public ushort usUsagePage;
        public ushort usUsage;
        public uint dwFlags;
        public IntPtr hwndTarget;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct RAWINPUTHEADER {
        public uint dwType;
        public uint dwSize;
        public IntPtr hDevice;
        public IntPtr wParam;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct RAWMOUSE {
        public ushort usFlags;
        public uint ulButtons;
        public uint ulRawButtons;
        public int lLastX;
        public int lLastY;
        public uint ulExtraInformation;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct RAWKEYBOARD {
        public ushort MakeCode;
        public ushort Flags;
        public ushort Reserved;
        public ushort VKey;
        public uint Message;
        public uint ExtraInformation;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool RegisterRawInputDevices(RAWINPUTDEVICE[] pRawInputDevices, uint uiNumDevices, uint cbSize);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint GetRawInputData(IntPtr hRawInput, uint uiCommand, IntPtr pData, ref uint pcbSize, uint cbSizeHeader);

    [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern uint GetRawInputDeviceInfo(IntPtr hDevice, uint uiCommand, StringBuilder pData, ref uint pcbSize);

    public class RawEvent {
        public double TimeSeconds;
        public string Kind;
        public string DeviceName;
        public string DeviceLabel;
        public ushort UsagePage;
        public ushort Usage;
        public ushort MouseFlags;
        public ushort MouseButtonFlags;
        public ushort MouseButtonData;
        public int MouseX;
        public int MouseY;
        public ushort KeyMakeCode;
        public ushort KeyFlags;
        public ushort KeyVKey;
        public uint KeyMessage;
        public uint HidReportSize;
        public uint HidReportCount;
        public string HidHex;
    }

    private class CaptureForm : Form {
        private readonly string filter;
        private readonly DateTime start;
        private readonly List<RawEvent> events;
        private readonly Dictionary<IntPtr, string> deviceNames = new Dictionary<IntPtr, string>();

        public CaptureForm(string filter, int seconds, List<RawEvent> events) {
            this.filter = (filter ?? "").ToLowerInvariant();
            this.events = events;
            this.start = DateTime.UtcNow;
            this.ShowInTaskbar = false;
            this.FormBorderStyle = FormBorderStyle.FixedToolWindow;
            this.Width = 1;
            this.Height = 1;
            this.StartPosition = FormStartPosition.Manual;
            this.Left = -32000;
            this.Top = -32000;
            this.Opacity = 0;
        }

        protected override void OnHandleCreated(EventArgs e) {
            base.OnHandleCreated(e);
            RAWINPUTDEVICE[] devices = new RAWINPUTDEVICE[] {
                new RAWINPUTDEVICE { usUsagePage = 0x01, usUsage = 0x02, dwFlags = RIDEV_INPUTSINK, hwndTarget = this.Handle },
                new RAWINPUTDEVICE { usUsagePage = 0x01, usUsage = 0x06, dwFlags = RIDEV_INPUTSINK, hwndTarget = this.Handle },
                new RAWINPUTDEVICE { usUsagePage = 0x0C, usUsage = 0x01, dwFlags = RIDEV_INPUTSINK, hwndTarget = this.Handle }
            };
            if (!RegisterRawInputDevices(devices, (uint)devices.Length, (uint)Marshal.SizeOf(typeof(RAWINPUTDEVICE)))) {
                throw new InvalidOperationException("RegisterRawInputDevices failed: " + Marshal.GetLastWin32Error());
            }
        }

        protected override void WndProc(ref Message m) {
            if (m.Msg == WM_INPUT) {
                HandleRawInput(m.LParam);
            }
            base.WndProc(ref m);
        }

        private void HandleRawInput(IntPtr hRawInput) {
            uint size = 0;
            uint headerSize = (uint)Marshal.SizeOf(typeof(RAWINPUTHEADER));
            GetRawInputData(hRawInput, RID_INPUT, IntPtr.Zero, ref size, headerSize);
            if (size == 0) return;

            IntPtr buffer = Marshal.AllocHGlobal((int)size);
            try {
                uint read = GetRawInputData(hRawInput, RID_INPUT, buffer, ref size, headerSize);
                if (read == 0 || read == 0xFFFFFFFF) return;

                RAWINPUTHEADER header = (RAWINPUTHEADER)Marshal.PtrToStructure(buffer, typeof(RAWINPUTHEADER));
                string deviceName = GetDeviceName(header.hDevice);
                if (filter.Length > 0 && (deviceName == null || deviceName.ToLowerInvariant().IndexOf(filter) < 0)) return;

                IntPtr dataPtr = IntPtr.Add(buffer, Marshal.SizeOf(typeof(RAWINPUTHEADER)));
                RawEvent ev = new RawEvent();
                ev.TimeSeconds = Math.Round((DateTime.UtcNow - start).TotalSeconds, 6);
                ev.DeviceName = deviceName;
                ev.DeviceLabel = LabelDevice(deviceName);

                if (header.dwType == RIM_TYPEMOUSE) {
                    RAWMOUSE mouse = (RAWMOUSE)Marshal.PtrToStructure(dataPtr, typeof(RAWMOUSE));
                    ev.Kind = "mouse";
                    ev.UsagePage = 0x01;
                    ev.Usage = 0x02;
                    ev.MouseFlags = mouse.usFlags;
                    ev.MouseButtonFlags = (ushort)(mouse.ulButtons & 0xFFFF);
                    ev.MouseButtonData = (ushort)((mouse.ulButtons >> 16) & 0xFFFF);
                    ev.MouseX = mouse.lLastX;
                    ev.MouseY = mouse.lLastY;
                } else if (header.dwType == RIM_TYPEKEYBOARD) {
                    RAWKEYBOARD key = (RAWKEYBOARD)Marshal.PtrToStructure(dataPtr, typeof(RAWKEYBOARD));
                    ev.Kind = "keyboard";
                    ev.UsagePage = 0x01;
                    ev.Usage = 0x06;
                    ev.KeyMakeCode = key.MakeCode;
                    ev.KeyFlags = key.Flags;
                    ev.KeyVKey = key.VKey;
                    ev.KeyMessage = key.Message;
                } else if (header.dwType == RIM_TYPEHID) {
                    uint reportSize = (uint)Marshal.ReadInt32(dataPtr);
                    uint reportCount = (uint)Marshal.ReadInt32(IntPtr.Add(dataPtr, 4));
                    int byteCount = checked((int)(reportSize * reportCount));
                    byte[] bytes = new byte[byteCount];
                    Marshal.Copy(IntPtr.Add(dataPtr, 8), bytes, 0, byteCount);
                    ev.Kind = "hid";
                    ev.UsagePage = 0x0C;
                    ev.Usage = 0x01;
                    ev.HidReportSize = reportSize;
                    ev.HidReportCount = reportCount;
                    ev.HidHex = ToHex(bytes);
                } else {
                    return;
                }

                events.Add(ev);
            } finally {
                Marshal.FreeHGlobal(buffer);
            }
        }

        private string GetDeviceName(IntPtr device) {
            if (device == IntPtr.Zero) return null;
            string cached;
            if (deviceNames.TryGetValue(device, out cached)) return cached;

            uint chars = 0;
            GetRawInputDeviceInfo(device, RIDI_DEVICENAME, null, ref chars);
            if (chars == 0) return null;
            StringBuilder builder = new StringBuilder((int)chars + 1);
            uint result = GetRawInputDeviceInfo(device, RIDI_DEVICENAME, builder, ref chars);
            if (result == 0xFFFFFFFF) return null;
            string name = builder.ToString();
            deviceNames[device] = name;
            return name;
        }

        private static string LabelDevice(string name) {
            if (name == null) return "unknown";
            string lower = name.ToLowerInvariant();
            if (lower.IndexOf("col01") >= 0) return "keyboard";
            if (lower.IndexOf("col02") >= 0) return "consumer";
            if (lower.IndexOf("col03") >= 0) return "mouse";
            return "unknown";
        }
    }

    public static RawEvent[] Capture(string filter, int seconds) {
        List<RawEvent> result = new List<RawEvent>();
        Exception failure = null;
        CaptureForm form = null;
        ManualResetEvent ready = new ManualResetEvent(false);
        Thread thread = new Thread(() => {
            try {
                Application.EnableVisualStyles();
                using (form = new CaptureForm(filter, seconds, result)) {
                    form.HandleCreated += (sender, args) => ready.Set();
                    Application.Run(form);
                }
            } catch (Exception ex) {
                failure = ex;
                ready.Set();
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();

        if (!ready.WaitOne(5000)) {
            throw new InvalidOperationException("Raw Input window did not initialize.");
        }
        if (failure != null) throw new InvalidOperationException(failure.Message, failure);

        Thread.Sleep(Math.Max(1, seconds) * 1000);
        if (form != null && form.IsHandleCreated && !form.IsDisposed) {
            form.BeginInvoke((Action)(() => form.Close()));
        }
        if (!thread.Join(5000)) {
            throw new InvalidOperationException("Raw Input capture did not shut down.");
        }
        if (failure != null) throw new InvalidOperationException(failure.Message, failure);
        return result.ToArray();
    }

    private static string ToHex(byte[] bytes) {
        if (bytes == null || bytes.Length == 0) return "";
        char[] c = new char[bytes.Length * 3 - 1];
        int p = 0;
        for (int i = 0; i < bytes.Length; i++) {
            if (i > 0) c[p++] = ' ';
            byte b = bytes[i];
            c[p++] = GetHexNibble(b >> 4);
            c[p++] = GetHexNibble(b & 0x0F);
        }
        return new string(c);
    }

    private static char GetHexNibble(int value) {
        return (char)(value < 10 ? '0' + value : 'A' + (value - 10));
    }
}
"@

"Capturing Raw Input for $Seconds seconds. Move/click/press the D06 now."
$events = [RawInputReverse]::Capture($Filter, $Seconds)

$outDir = Split-Path -Parent $OutFile
if ($outDir) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}
ConvertTo-Json -InputObject @($events) -Depth 10 | Set-Content -Encoding UTF8 $OutFile

"Wrote $OutFile"
$events | Group-Object Kind,DeviceLabel | ForEach-Object {
    "{0}: {1}" -f $_.Name, $_.Count
}
