param(
    [string]$Address = "D10BCB55CA78",
    [switch]$Uncached,
    [string]$OutDir = "."
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Runtime.WindowsRuntime

[Windows.Devices.Bluetooth.BluetoothLEDevice, Windows.Devices.Bluetooth, ContentType = WindowsRuntime] | Out-Null
[Windows.Devices.Bluetooth.BluetoothCacheMode, Windows.Devices.Bluetooth, ContentType = WindowsRuntime] | Out-Null
[Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristic, Windows.Devices.Bluetooth, ContentType = WindowsRuntime] | Out-Null
[Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristicProperties, Windows.Devices.Bluetooth, ContentType = WindowsRuntime] | Out-Null
[Windows.Devices.Bluetooth.GenericAttributeProfile.GattCommunicationStatus, Windows.Devices.Bluetooth, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.Streams.IBuffer, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
[Windows.Security.Cryptography.CryptographicBuffer, Windows.Security.Cryptography, ContentType = WindowsRuntime] | Out-Null

$asTaskMethods = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq "AsTask" -and
    $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
}
$asTaskGeneric = $asTaskMethods[0]

function Await-WinRt($operation, [type]$resultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($resultType)
    $task = $asTask.Invoke($null, @($operation))
    $task.Wait()
    return $task.Result
}

function Convert-BufferToHex($buffer) {
    if ($null -eq $buffer -or $buffer.Length -eq 0) {
        return ""
    }

    [byte[]]$bytes = @()
    [Windows.Security.Cryptography.CryptographicBuffer]::CopyToByteArray($buffer, [ref]$bytes)
    return ($bytes | ForEach-Object { $_.ToString("X2") }) -join " "
}

function Convert-BufferToText($buffer) {
    if ($null -eq $buffer -or $buffer.Length -eq 0) {
        return ""
    }

    [byte[]]$bytes = @()
    [Windows.Security.Cryptography.CryptographicBuffer]::CopyToByteArray($buffer, [ref]$bytes)
    $printable = $true
    foreach ($b in $bytes) {
        if (($b -lt 0x20 -or $b -gt 0x7e) -and $b -ne 0x09 -and $b -ne 0x0a -and $b -ne 0x0d) {
            $printable = $false
            break
        }
    }
    if (-not $printable) {
        return ""
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Format-GuidName([guid]$uuid) {
    $known = @{
        "00001800-0000-1000-8000-00805f9b34fb" = "Generic Access"
        "00001801-0000-1000-8000-00805f9b34fb" = "Generic Attribute"
        "0000180a-0000-1000-8000-00805f9b34fb" = "Device Information"
        "0000180f-0000-1000-8000-00805f9b34fb" = "Battery Service"
        "00001812-0000-1000-8000-00805f9b34fb" = "Human Interface Device"
        "00002a00-0000-1000-8000-00805f9b34fb" = "Device Name"
        "00002a01-0000-1000-8000-00805f9b34fb" = "Appearance"
        "00002a04-0000-1000-8000-00805f9b34fb" = "Peripheral Preferred Connection Parameters"
        "00002a05-0000-1000-8000-00805f9b34fb" = "Service Changed"
        "00002a19-0000-1000-8000-00805f9b34fb" = "Battery Level"
        "00002a24-0000-1000-8000-00805f9b34fb" = "Model Number"
        "00002a25-0000-1000-8000-00805f9b34fb" = "Serial Number"
        "00002a26-0000-1000-8000-00805f9b34fb" = "Firmware Revision"
        "00002a27-0000-1000-8000-00805f9b34fb" = "Hardware Revision"
        "00002a28-0000-1000-8000-00805f9b34fb" = "Software Revision"
        "00002a29-0000-1000-8000-00805f9b34fb" = "Manufacturer Name"
        "00002a4a-0000-1000-8000-00805f9b34fb" = "HID Information"
        "00002a4b-0000-1000-8000-00805f9b34fb" = "HID Report Map"
        "00002a4c-0000-1000-8000-00805f9b34fb" = "HID Control Point"
        "00002a4d-0000-1000-8000-00805f9b34fb" = "HID Report"
        "00002a4e-0000-1000-8000-00805f9b34fb" = "HID Protocol Mode"
        "00002a4f-0000-1000-8000-00805f9b34fb" = "Scan Interval Window"
        "00002a50-0000-1000-8000-00805f9b34fb" = "PnP ID"
        "00002902-0000-1000-8000-00805f9b34fb" = "Client Characteristic Configuration"
        "00002908-0000-1000-8000-00805f9b34fb" = "Report Reference"
    }
    $key = $uuid.ToString().ToLowerInvariant()
    if ($known.ContainsKey($key)) {
        return $known[$key]
    }
    return ""
}

function Get-CharacteristicProperties($props) {
    $names = [enum]::GetValues([Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristicProperties]) |
        Where-Object { $_ -ne 0 -and (($props -band $_) -eq $_) } |
        ForEach-Object { $_.ToString() }
    return $names -join ", "
}

$normalizedAddress = $Address -replace "[: -]", ""
$bluetoothAddress = [Convert]::ToUInt64($normalizedAddress, 16)
$cacheMode = [Windows.Devices.Bluetooth.BluetoothCacheMode]::Cached
if ($Uncached) {
    $cacheMode = [Windows.Devices.Bluetooth.BluetoothCacheMode]::Uncached
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$device = Await-WinRt ([Windows.Devices.Bluetooth.BluetoothLEDevice]::FromBluetoothAddressAsync($bluetoothAddress)) ([Windows.Devices.Bluetooth.BluetoothLEDevice])
if ($null -eq $device) {
    throw "Bluetooth LE device $Address was not found."
}

$resultType = [Windows.Devices.Bluetooth.GenericAttributeProfile.GattDeviceServicesResult]
$servicesResult = Await-WinRt ($device.GetGattServicesAsync($cacheMode)) $resultType
if ($servicesResult.Status.ToString() -ne "Success") {
    throw "GetGattServicesAsync failed: $($servicesResult.Status)"
}

$dump = [ordered]@{
    address          = ("{0:X12}" -f $bluetoothAddress)
    name             = $device.Name
    bluetoothAddress = $device.BluetoothAddress
    connectionStatus = $device.ConnectionStatus.ToString()
    services         = @()
}

foreach ($service in $servicesResult.Services) {
    $serviceEntry = [ordered]@{
        uuid            = $service.Uuid.ToString()
        name            = Format-GuidName $service.Uuid
        attributeHandle = $service.AttributeHandle
        characteristics = @()
    }

    $characteristicsResult = Await-WinRt ($service.GetCharacteristicsAsync($cacheMode)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristicsResult])
    if ($characteristicsResult.Status.ToString() -ne "Success") {
        $serviceEntry.characteristicsError = $characteristicsResult.Status.ToString()
        $dump.services += $serviceEntry
        continue
    }

    foreach ($characteristic in $characteristicsResult.Characteristics) {
        $charEntry = [ordered]@{
            uuid            = $characteristic.Uuid.ToString()
            name            = Format-GuidName $characteristic.Uuid
            attributeHandle = $characteristic.AttributeHandle
            properties      = Get-CharacteristicProperties $characteristic.CharacteristicProperties
            descriptors     = @()
        }

        if (($characteristic.CharacteristicProperties -band [Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristicProperties]::Read) -ne 0) {
            try {
                $readResult = Await-WinRt ($characteristic.ReadValueAsync($cacheMode)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattReadResult])
                $charEntry.readStatus = $readResult.Status.ToString()
                if ($readResult.Status.ToString() -eq "Success") {
                    $charEntry.valueHex = Convert-BufferToHex $readResult.Value
                    $text = Convert-BufferToText $readResult.Value
                    if ($text.Length -gt 0) {
                        $charEntry.valueText = $text
                    }
                    if ($characteristic.Uuid.ToString().ToLowerInvariant() -eq "00002a4b-0000-1000-8000-00805f9b34fb") {
                        $path = Join-Path $OutDir "hid_report_map.bin"
                        $bytes = ($charEntry.valueHex -split " " | Where-Object { $_.Length -gt 0 } | ForEach-Object { [Convert]::ToByte($_, 16) })
                        [System.IO.File]::WriteAllBytes($path, [byte[]]$bytes)
                    }
                }
            } catch {
                $charEntry.readError = $_.Exception.Message
            }
        }

        try {
            $descriptorsResult = Await-WinRt ($characteristic.GetDescriptorsAsync($cacheMode)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattDescriptorsResult])
            $charEntry.descriptorStatus = $descriptorsResult.Status.ToString()
            if ($descriptorsResult.Status.ToString() -eq "Success") {
                foreach ($descriptor in $descriptorsResult.Descriptors) {
                    $descEntry = [ordered]@{
                        uuid            = $descriptor.Uuid.ToString()
                        name            = Format-GuidName $descriptor.Uuid
                        attributeHandle = $descriptor.AttributeHandle
                    }
                    try {
                        $descReadResult = Await-WinRt ($descriptor.ReadValueAsync($cacheMode)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattReadResult])
                        $descEntry.readStatus = $descReadResult.Status.ToString()
                        if ($descReadResult.Status.ToString() -eq "Success") {
                            $descEntry.valueHex = Convert-BufferToHex $descReadResult.Value
                            $text = Convert-BufferToText $descReadResult.Value
                            if ($text.Length -gt 0) {
                                $descEntry.valueText = $text
                            }
                        }
                    } catch {
                        $descEntry.readError = $_.Exception.Message
                    }
                    $charEntry.descriptors += $descEntry
                }
            }
        } catch {
            $charEntry.descriptorError = $_.Exception.Message
        }

        $serviceEntry.characteristics += $charEntry
    }

    $dump.services += $serviceEntry
}

$jsonPath = Join-Path $OutDir "gatt_dump.json"
$dump | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 $jsonPath

"Wrote $jsonPath"
if (Test-Path (Join-Path $OutDir "hid_report_map.bin")) {
    "Wrote $(Join-Path $OutDir "hid_report_map.bin")"
}
