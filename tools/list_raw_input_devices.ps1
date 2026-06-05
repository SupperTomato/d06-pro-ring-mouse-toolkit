param(
    [string]$Filter = ""
)

$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class RawInputList {
    private const uint RIDI_DEVICENAME = 0x20000007;

    [StructLayout(LayoutKind.Sequential)]
    private struct RAWINPUTDEVICELIST {
        public IntPtr hDevice;
        public uint dwType;
    }

    public class RawDevice {
        public string Handle;
        public uint Type;
        public string TypeName;
        public string Name;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint GetRawInputDeviceList(IntPtr pRawInputDeviceList, ref uint puiNumDevices, uint cbSize);

    [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern uint GetRawInputDeviceInfo(IntPtr hDevice, uint uiCommand, StringBuilder pData, ref uint pcbSize);

    public static RawDevice[] List() {
        uint count = 0;
        uint itemSize = (uint)Marshal.SizeOf(typeof(RAWINPUTDEVICELIST));
        GetRawInputDeviceList(IntPtr.Zero, ref count, itemSize);
        IntPtr buffer = Marshal.AllocHGlobal((int)(itemSize * count));
        try {
            uint actual = GetRawInputDeviceList(buffer, ref count, itemSize);
            if (actual == 0xFFFFFFFF) throw new InvalidOperationException("GetRawInputDeviceList failed: " + Marshal.GetLastWin32Error());
            List<RawDevice> devices = new List<RawDevice>();
            for (int i = 0; i < actual; i++) {
                IntPtr itemPtr = IntPtr.Add(buffer, i * (int)itemSize);
                RAWINPUTDEVICELIST item = (RAWINPUTDEVICELIST)Marshal.PtrToStructure(itemPtr, typeof(RAWINPUTDEVICELIST));
                RawDevice dev = new RawDevice();
                dev.Handle = "0x" + item.hDevice.ToInt64().ToString("X");
                dev.Type = item.dwType;
                dev.TypeName = item.dwType == 0 ? "mouse" : item.dwType == 1 ? "keyboard" : item.dwType == 2 ? "hid" : "unknown";
                dev.Name = GetName(item.hDevice);
                devices.Add(dev);
            }
            return devices.ToArray();
        } finally {
            Marshal.FreeHGlobal(buffer);
        }
    }

    private static string GetName(IntPtr handle) {
        uint chars = 0;
        GetRawInputDeviceInfo(handle, RIDI_DEVICENAME, null, ref chars);
        if (chars == 0) return null;
        StringBuilder builder = new StringBuilder((int)chars + 1);
        uint result = GetRawInputDeviceInfo(handle, RIDI_DEVICENAME, builder, ref chars);
        if (result == 0xFFFFFFFF) return null;
        return builder.ToString();
    }
}
"@

$devices = [RawInputList]::List()
if ($Filter.Length -gt 0) {
    $devices = $devices | Where-Object { $_.Name -match $Filter -or $_.TypeName -match $Filter }
}
$devices | Sort-Object TypeName,Name | Format-Table -Wrap -AutoSize
