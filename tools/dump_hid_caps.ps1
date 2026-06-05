param(
    [string]$Filter = "d10bcb55ca78",
    [string]$OutFile = "artifacts/hid_caps.json"
)

$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @"
using Microsoft.Win32.SafeHandles;
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class HidReverse {
    private const uint OPEN_EXISTING = 3;
    private const uint FILE_SHARE_READ = 0x00000001;
    private const uint FILE_SHARE_WRITE = 0x00000002;
    private const ushort HidP_Input = 0;
    private const ushort HidP_Output = 1;
    private const ushort HidP_Feature = 2;

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern SafeFileHandle CreateFile(
        string lpFileName,
        uint dwDesiredAccess,
        uint dwShareMode,
        IntPtr lpSecurityAttributes,
        uint dwCreationDisposition,
        uint dwFlagsAndAttributes,
        IntPtr hTemplateFile);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern bool HidD_GetAttributes(SafeFileHandle hidDeviceObject, ref HIDD_ATTRIBUTES attributes);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern bool HidD_GetPreparsedData(SafeFileHandle hidDeviceObject, out IntPtr preparsedData);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern bool HidD_FreePreparsedData(IntPtr preparsedData);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern bool HidD_GetProductString(SafeFileHandle hidDeviceObject, byte[] buffer, int bufferLength);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern bool HidD_GetManufacturerString(SafeFileHandle hidDeviceObject, byte[] buffer, int bufferLength);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern bool HidD_GetSerialNumberString(SafeFileHandle hidDeviceObject, byte[] buffer, int bufferLength);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern int HidP_GetCaps(IntPtr preparsedData, out HIDP_CAPS capabilities);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern int HidP_GetButtonCaps(ushort reportType, [In, Out] HIDP_BUTTON_CAPS[] buttonCaps, ref ushort buttonCapsLength, IntPtr preparsedData);

    [DllImport("hid.dll", SetLastError = true)]
    private static extern int HidP_GetValueCaps(ushort reportType, [In, Out] HIDP_VALUE_CAPS[] valueCaps, ref ushort valueCapsLength, IntPtr preparsedData);

    [StructLayout(LayoutKind.Sequential)]
    private struct HIDD_ATTRIBUTES {
        public int Size;
        public ushort VendorID;
        public ushort ProductID;
        public ushort VersionNumber;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct HIDP_CAPS {
        public ushort Usage;
        public ushort UsagePage;
        public ushort InputReportByteLength;
        public ushort OutputReportByteLength;
        public ushort FeatureReportByteLength;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 17)]
        public ushort[] Reserved;
        public ushort NumberLinkCollectionNodes;
        public ushort NumberInputButtonCaps;
        public ushort NumberInputValueCaps;
        public ushort NumberInputDataIndices;
        public ushort NumberOutputButtonCaps;
        public ushort NumberOutputValueCaps;
        public ushort NumberOutputDataIndices;
        public ushort NumberFeatureButtonCaps;
        public ushort NumberFeatureValueCaps;
        public ushort NumberFeatureDataIndices;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct HIDP_BUTTON_CAPS {
        public ushort UsagePage;
        public byte ReportID;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsAlias;
        public ushort BitField;
        public ushort LinkCollection;
        public ushort LinkUsage;
        public ushort LinkUsagePage;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsRange;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsStringRange;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsDesignatorRange;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsAbsolute;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 10)]
        public uint[] Reserved;
        public ushort UsageMin;
        public ushort UsageMax;
        public ushort StringMin;
        public ushort StringMax;
        public ushort DesignatorMin;
        public ushort DesignatorMax;
        public ushort DataIndexMin;
        public ushort DataIndexMax;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct HIDP_VALUE_CAPS {
        public ushort UsagePage;
        public byte ReportID;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsAlias;
        public ushort BitField;
        public ushort LinkCollection;
        public ushort LinkUsage;
        public ushort LinkUsagePage;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsRange;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsStringRange;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsDesignatorRange;
        [MarshalAs(UnmanagedType.U1)]
        public bool IsAbsolute;
        [MarshalAs(UnmanagedType.U1)]
        public bool HasNull;
        public byte ReservedByte;
        public ushort BitSize;
        public ushort ReportCount;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 5)]
        public ushort[] Reserved2;
        public uint UnitsExp;
        public uint Units;
        public int LogicalMin;
        public int LogicalMax;
        public int PhysicalMin;
        public int PhysicalMax;
        public ushort UsageMin;
        public ushort UsageMax;
        public ushort StringMin;
        public ushort StringMax;
        public ushort DesignatorMin;
        public ushort DesignatorMax;
        public ushort DataIndexMin;
        public ushort DataIndexMax;
    }

    public class HidDump {
        public string Path;
        public bool Opened;
        public string Error;
        public int LastError;
        public ushort VendorId;
        public ushort ProductId;
        public ushort VersionNumber;
        public string Manufacturer;
        public string Product;
        public string Serial;
        public HIDP_CAPS Caps;
        public List<HIDP_BUTTON_CAPS> InputButtonCaps;
        public List<HIDP_VALUE_CAPS> InputValueCaps;
        public List<HIDP_BUTTON_CAPS> OutputButtonCaps;
        public List<HIDP_VALUE_CAPS> OutputValueCaps;
        public List<HIDP_BUTTON_CAPS> FeatureButtonCaps;
        public List<HIDP_VALUE_CAPS> FeatureValueCaps;
    }

    public static HidDump Dump(string path) {
        HidDump dump = new HidDump();
        dump.Path = path;
        dump.InputButtonCaps = new List<HIDP_BUTTON_CAPS>();
        dump.InputValueCaps = new List<HIDP_VALUE_CAPS>();
        dump.OutputButtonCaps = new List<HIDP_BUTTON_CAPS>();
        dump.OutputValueCaps = new List<HIDP_VALUE_CAPS>();
        dump.FeatureButtonCaps = new List<HIDP_BUTTON_CAPS>();
        dump.FeatureValueCaps = new List<HIDP_VALUE_CAPS>();

        using (SafeFileHandle handle = CreateFile(path, 0, FILE_SHARE_READ | FILE_SHARE_WRITE, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero)) {
            if (handle.IsInvalid) {
                dump.Opened = false;
                dump.LastError = Marshal.GetLastWin32Error();
                dump.Error = "CreateFile failed";
                return dump;
            }
            dump.Opened = true;

            HIDD_ATTRIBUTES attrs = new HIDD_ATTRIBUTES();
            attrs.Size = Marshal.SizeOf(typeof(HIDD_ATTRIBUTES));
            if (HidD_GetAttributes(handle, ref attrs)) {
                dump.VendorId = attrs.VendorID;
                dump.ProductId = attrs.ProductID;
                dump.VersionNumber = attrs.VersionNumber;
            }

            dump.Manufacturer = ReadString(handle, 0);
            dump.Product = ReadString(handle, 1);
            dump.Serial = ReadString(handle, 2);

            IntPtr ppd;
            if (!HidD_GetPreparsedData(handle, out ppd)) {
                dump.LastError = Marshal.GetLastWin32Error();
                dump.Error = "HidD_GetPreparsedData failed";
                return dump;
            }

            try {
                HIDP_CAPS caps;
                int status = HidP_GetCaps(ppd, out caps);
                if (status != 0x110000) {
                    dump.Error = "HidP_GetCaps failed: 0x" + status.ToString("X");
                    return dump;
                }
                dump.Caps = caps;
                dump.InputButtonCaps = GetButtonCaps(HidP_Input, caps.NumberInputButtonCaps, ppd);
                dump.InputValueCaps = GetValueCaps(HidP_Input, caps.NumberInputValueCaps, ppd);
                dump.OutputButtonCaps = GetButtonCaps(HidP_Output, caps.NumberOutputButtonCaps, ppd);
                dump.OutputValueCaps = GetValueCaps(HidP_Output, caps.NumberOutputValueCaps, ppd);
                dump.FeatureButtonCaps = GetButtonCaps(HidP_Feature, caps.NumberFeatureButtonCaps, ppd);
                dump.FeatureValueCaps = GetValueCaps(HidP_Feature, caps.NumberFeatureValueCaps, ppd);
            } finally {
                HidD_FreePreparsedData(ppd);
            }
        }

        return dump;
    }

    private static string ReadString(SafeFileHandle handle, int kind) {
        byte[] buffer = new byte[256];
        bool ok = false;
        if (kind == 0) ok = HidD_GetManufacturerString(handle, buffer, buffer.Length);
        if (kind == 1) ok = HidD_GetProductString(handle, buffer, buffer.Length);
        if (kind == 2) ok = HidD_GetSerialNumberString(handle, buffer, buffer.Length);
        if (!ok) return null;
        string value = Encoding.Unicode.GetString(buffer).TrimEnd('\0');
        return value.Length == 0 ? null : value;
    }

    private static List<HIDP_BUTTON_CAPS> GetButtonCaps(ushort reportType, ushort count, IntPtr ppd) {
        List<HIDP_BUTTON_CAPS> result = new List<HIDP_BUTTON_CAPS>();
        if (count == 0) return result;
        HIDP_BUTTON_CAPS[] caps = new HIDP_BUTTON_CAPS[count];
        for (int i = 0; i < caps.Length; i++) caps[i].Reserved = new uint[10];
        ushort length = count;
        int status = HidP_GetButtonCaps(reportType, caps, ref length, ppd);
        if (status == 0x110000 || status == 0x110001) {
            for (int i = 0; i < length; i++) result.Add(caps[i]);
        }
        return result;
    }

    private static List<HIDP_VALUE_CAPS> GetValueCaps(ushort reportType, ushort count, IntPtr ppd) {
        List<HIDP_VALUE_CAPS> result = new List<HIDP_VALUE_CAPS>();
        if (count == 0) return result;
        HIDP_VALUE_CAPS[] caps = new HIDP_VALUE_CAPS[count];
        for (int i = 0; i < caps.Length; i++) caps[i].Reserved2 = new ushort[5];
        ushort length = count;
        int status = HidP_GetValueCaps(reportType, caps, ref length, ppd);
        if (status == 0x110000 || status == 0x110001) {
            for (int i = 0; i < length; i++) result.Add(caps[i]);
        }
        return result;
    }
}
"@

function Convert-DeviceClassKeyToPath([string]$childName) {
    if (-not $childName.StartsWith("##?#")) {
        return $null
    }
    return "\\?\" + $childName.Substring(4)
}

$hidGuid = "{4d1e55b2-f16f-11cf-88cb-001111000030}"
$classKey = "HKLM:\SYSTEM\CurrentControlSet\Control\DeviceClasses\$hidGuid"
$paths = Get-ChildItem $classKey -ErrorAction Stop |
    Where-Object { $_.PSChildName -match $Filter } |
    ForEach-Object { Convert-DeviceClassKeyToPath $_.PSChildName } |
    Where-Object { $_ } |
    Sort-Object -Unique

if ($paths.Count -eq 0) {
    throw "No HID device interface paths matched filter '$Filter'."
}

$dumps = foreach ($path in $paths) {
    [HidReverse]::Dump($path)
}

$outDir = Split-Path -Parent $OutFile
if ($outDir) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}
$dumps | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 $OutFile

"Wrote $OutFile"
