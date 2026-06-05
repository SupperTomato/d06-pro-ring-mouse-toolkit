param(
    [string]$CapsFile = "artifacts/hid_caps.json",
    [string]$OutFile = "artifacts/hid_reports.json",
    [int]$Seconds = 15
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $CapsFile)) {
    throw "Caps file not found: $CapsFile. Run tools/dump_hid_caps.ps1 first."
}

Add-Type -TypeDefinition @"
using Microsoft.Win32.SafeHandles;
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading.Tasks;

public static class HidCapture {
    private const uint GENERIC_READ = 0x80000000;
    private const uint FILE_SHARE_READ = 0x00000001;
    private const uint FILE_SHARE_WRITE = 0x00000002;
    private const uint OPEN_EXISTING = 3;
    private const uint FILE_FLAG_OVERLAPPED = 0x40000000;
    private const int ERROR_IO_PENDING = 997;
    private const uint WAIT_OBJECT_0 = 0x00000000;
    private const uint WAIT_TIMEOUT = 0x00000102;

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern SafeFileHandle CreateFile(
        string lpFileName,
        uint dwDesiredAccess,
        uint dwShareMode,
        IntPtr lpSecurityAttributes,
        uint dwCreationDisposition,
        uint dwFlagsAndAttributes,
        IntPtr hTemplateFile);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool ReadFile(SafeFileHandle hFile, IntPtr lpBuffer, uint nNumberOfBytesToRead, IntPtr lpNumberOfBytesRead, IntPtr lpOverlapped);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CancelIoEx(SafeFileHandle hFile, IntPtr lpOverlapped);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GetOverlappedResult(SafeFileHandle hFile, IntPtr lpOverlapped, out uint lpNumberOfBytesTransferred, bool bWait);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr CreateEvent(IntPtr lpEventAttributes, bool bManualReset, bool bInitialState, string lpName);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool ResetEvent(IntPtr hEvent);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern uint WaitForSingleObject(IntPtr hHandle, uint dwMilliseconds);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr hObject);

    [StructLayout(LayoutKind.Sequential)]
    private struct OVERLAPPED {
        public IntPtr Internal;
        public IntPtr InternalHigh;
        public uint Offset;
        public uint OffsetHigh;
        public IntPtr hEvent;
    }

    public class CaptureSpec {
        public string Label;
        public string Path;
        public int ReportLength;
    }

    public class CaptureEvent {
        public string Label;
        public string Path;
        public double TimeSeconds;
        public int Length;
        public string Hex;
    }

    public class CaptureResult {
        public string Label;
        public string Path;
        public int ReportLength;
        public bool Opened;
        public int LastError;
        public string Error;
        public List<CaptureEvent> Events = new List<CaptureEvent>();
    }

    public static CaptureResult[] CaptureMany(CaptureSpec[] specs, int seconds) {
        Task<CaptureResult>[] tasks = new Task<CaptureResult>[specs.Length];
        for (int i = 0; i < specs.Length; i++) {
            CaptureSpec spec = specs[i];
            tasks[i] = Task.Run(() => CaptureOne(spec, seconds));
        }
        Task.WaitAll(tasks);
        CaptureResult[] results = new CaptureResult[tasks.Length];
        for (int i = 0; i < tasks.Length; i++) results[i] = tasks[i].Result;
        return results;
    }

    private static CaptureResult CaptureOne(CaptureSpec spec, int seconds) {
        CaptureResult result = new CaptureResult();
        result.Label = spec.Label;
        result.Path = spec.Path;
        result.ReportLength = spec.ReportLength;

        using (SafeFileHandle handle = CreateFile(spec.Path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, IntPtr.Zero, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, IntPtr.Zero)) {
            if (handle.IsInvalid) {
                result.Opened = false;
                result.LastError = Marshal.GetLastWin32Error();
                result.Error = new Win32Exception(result.LastError).Message;
                return result;
            }
            result.Opened = true;

            IntPtr eventHandle = CreateEvent(IntPtr.Zero, true, false, null);
            if (eventHandle == IntPtr.Zero) {
                result.LastError = Marshal.GetLastWin32Error();
                result.Error = "CreateEvent failed: " + new Win32Exception(result.LastError).Message;
                return result;
            }

            IntPtr buffer = Marshal.AllocHGlobal(spec.ReportLength);
            IntPtr overlappedPtr = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(OVERLAPPED)));
            try {
                Stopwatch sw = Stopwatch.StartNew();
                while (sw.Elapsed.TotalSeconds < seconds) {
                    ResetEvent(eventHandle);
                    OVERLAPPED overlapped = new OVERLAPPED();
                    overlapped.hEvent = eventHandle;
                    Marshal.StructureToPtr(overlapped, overlappedPtr, false);
                    for (int i = 0; i < spec.ReportLength; i++) Marshal.WriteByte(buffer, i, 0);

                    bool issued = ReadFile(handle, buffer, (uint)spec.ReportLength, IntPtr.Zero, overlappedPtr);
                    int err = issued ? 0 : Marshal.GetLastWin32Error();
                    if (!issued && err != ERROR_IO_PENDING) {
                        result.LastError = err;
                        result.Error = "ReadFile failed: " + new Win32Exception(err).Message;
                        break;
                    }

                    bool completed = issued;
                    uint bytesRead = 0;
                    while (!completed && sw.Elapsed.TotalSeconds < seconds) {
                        uint waitMs = (uint)Math.Min(250, Math.Max(1, (seconds - sw.Elapsed.TotalSeconds) * 1000));
                        uint wait = WaitForSingleObject(eventHandle, waitMs);
                        if (wait == WAIT_OBJECT_0) {
                            completed = GetOverlappedResult(handle, overlappedPtr, out bytesRead, false);
                            if (!completed) {
                                result.LastError = Marshal.GetLastWin32Error();
                                result.Error = "GetOverlappedResult failed: " + new Win32Exception(result.LastError).Message;
                            }
                            break;
                        }
                        if (wait != WAIT_TIMEOUT) {
                            result.LastError = Marshal.GetLastWin32Error();
                            result.Error = "WaitForSingleObject failed";
                            break;
                        }
                    }

                    if (!completed) {
                        CancelIoEx(handle, overlappedPtr);
                        WaitForSingleObject(eventHandle, 1000);
                        break;
                    }

                    if (issued) {
                        GetOverlappedResult(handle, overlappedPtr, out bytesRead, false);
                    }

                    if (bytesRead > 0) {
                        int count = (int)Math.Min(bytesRead, (uint)spec.ReportLength);
                        byte[] managed = new byte[count];
                        Marshal.Copy(buffer, managed, 0, count);
                        CaptureEvent ev = new CaptureEvent();
                        ev.Label = spec.Label;
                        ev.Path = spec.Path;
                        ev.TimeSeconds = Math.Round(sw.Elapsed.TotalSeconds, 6);
                        ev.Length = count;
                        ev.Hex = ToHex(managed, count);
                        result.Events.Add(ev);
                    }
                }
            } finally {
                CancelIoEx(handle, overlappedPtr);
                Marshal.FreeHGlobal(buffer);
                Marshal.FreeHGlobal(overlappedPtr);
                CloseHandle(eventHandle);
            }
        }

        return result;
    }

    private static string ToHex(byte[] bytes, int count) {
        char[] c = new char[count * 3 - 1];
        int p = 0;
        for (int i = 0; i < count; i++) {
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

$caps = Get-Content $CapsFile -Raw | ConvertFrom-Json
$specs = foreach ($dev in $caps) {
    $label = "unknown"
    if ($dev.Caps.UsagePage -eq 0x01 -and $dev.Caps.Usage -eq 0x06) { $label = "keyboard" }
    elseif ($dev.Caps.UsagePage -eq 0x0c -and $dev.Caps.Usage -eq 0x01) { $label = "consumer" }
    elseif ($dev.Caps.UsagePage -eq 0x01 -and $dev.Caps.Usage -eq 0x02) { $label = "mouse" }

    $spec = New-Object HidCapture+CaptureSpec
    $spec.Label = $label
    $spec.Path = $dev.Path
    $spec.ReportLength = [int]$dev.Caps.InputReportByteLength
    $spec
}

"Capturing D06 HID reports for $Seconds seconds. Move/click/press the ring now."
$results = [HidCapture]::CaptureMany([HidCapture+CaptureSpec[]]$specs, $Seconds)

$outDir = Split-Path -Parent $OutFile
if ($outDir) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}
$results | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 $OutFile

"Wrote $OutFile"
$results | ForEach-Object {
    "{0}: opened={1} events={2} error={3}" -f $_.Label, $_.Opened, $_.Events.Count, $_.Error
}
