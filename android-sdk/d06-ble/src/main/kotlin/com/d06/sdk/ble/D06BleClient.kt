package com.d06.sdk.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

sealed interface D06BleState {
    data object Idle : D06BleState
    data object Connecting : D06BleState
    data object Connected : D06BleState
    data class ServicesDiscovered(val profile: D06GattProfile) : D06BleState
    data class BatteryLevel(val percent: Int) : D06BleState
    data class Error(val message: String) : D06BleState
}

class D06BleClient(
    private val context: Context
) {
    private val mutableState = MutableStateFlow<D06BleState>(D06BleState.Idle)
    val state: StateFlow<D06BleState> = mutableState

    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        mutableState.value = D06BleState.Connecting
        gatt = device.connectGatt(context, false, callback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        mutableState.value = D06BleState.Idle
    }

    @SuppressLint("MissingPermission")
    fun readBatteryLevel(): Boolean {
        val currentGatt = gatt ?: return false
        val characteristic = currentGatt
            .getService(D06GattUuids.BATTERY)
            ?.getCharacteristic(D06GattUuids.BATTERY_LEVEL)
            ?: return false
        return currentGatt.readCharacteristic(characteristic)
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mutableState.value = D06BleState.Error("GATT status $status")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    mutableState.value = D06BleState.Connected
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> mutableState.value = D06BleState.Idle
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mutableState.value = D06BleState.Error("Service discovery status $status")
                return
            }
            mutableState.value = D06BleState.ServicesDiscovered(
                D06GattProfile.fromBluetoothGattServices(gatt.services)
            )
        }

        @Deprecated("Kept for Android versions before API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            handleCharacteristicRead(characteristic.uuid, characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharacteristicRead(characteristic.uuid, value, status)
        }
    }

    private fun handleCharacteristicRead(uuid: UUID, value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            mutableState.value = D06BleState.Error("Characteristic read status $status")
            return
        }
        if (uuid == D06GattUuids.BATTERY_LEVEL && value.isNotEmpty()) {
            mutableState.value = D06BleState.BatteryLevel(value.first().toInt() and 0xFF)
        }
    }
}
