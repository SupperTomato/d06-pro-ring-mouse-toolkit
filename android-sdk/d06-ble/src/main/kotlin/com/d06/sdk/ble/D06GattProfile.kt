package com.d06.sdk.ble

import android.bluetooth.BluetoothGattService
import java.util.UUID

data class D06GattServiceSummary(
    val uuid: UUID,
    val characteristicUuids: Set<UUID>
)

data class D06GattProfile(
    val services: Set<D06GattServiceSummary>,
    val serviceUuids: Set<UUID>,
    val hasGap: Boolean,
    val hasGatt: Boolean,
    val hasDis: Boolean,
    val hasBattery: Boolean,
    val hasHid: Boolean,
    val hasVendorTelinkLike: Boolean
) {
    companion object {
        fun fromServiceUuids(uuids: Iterable<UUID>): D06GattProfile {
            val serviceUuids = uuids.toSet()
            return fromSummaries(serviceUuids.map { D06GattServiceSummary(it, emptySet()) })
        }

        fun fromBluetoothGattServices(services: Iterable<BluetoothGattService>): D06GattProfile {
            return fromSummaries(
                services.map { service ->
                    D06GattServiceSummary(
                        uuid = service.uuid,
                        characteristicUuids = service.characteristics.map { it.uuid }.toSet()
                    )
                }
            )
        }

        private fun fromSummaries(summaries: Iterable<D06GattServiceSummary>): D06GattProfile {
            val serviceSet = summaries.toSet()
            val uuidSet = serviceSet.map { it.uuid }.toSet()
            return D06GattProfile(
                services = serviceSet,
                serviceUuids = uuidSet,
                hasGap = D06GattUuids.GAP in uuidSet,
                hasGatt = D06GattUuids.GATT in uuidSet,
                hasDis = D06GattUuids.DIS in uuidSet,
                hasBattery = D06GattUuids.BATTERY in uuidSet,
                hasHid = D06GattUuids.HID in uuidSet,
                hasVendorTelinkLike = D06GattUuids.VENDOR_TELINK_LIKE in uuidSet
            )
        }
    }
}
