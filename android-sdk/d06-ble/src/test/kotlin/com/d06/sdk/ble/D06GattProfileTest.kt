package com.d06.sdk.ble

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class D06GattProfileTest {
    @Test
    fun `known services include hid battery and vendor service`() {
        assertEquals(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"), D06GattUuids.HID)
        assertEquals(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"), D06GattUuids.BATTERY)
        assertEquals(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1912"), D06GattUuids.VENDOR_TELINK_LIKE)
    }

    @Test
    fun `profile marks discovered known services`() {
        val profile = D06GattProfile.fromServiceUuids(
            listOf(D06GattUuids.GAP, D06GattUuids.BATTERY, D06GattUuids.VENDOR_TELINK_LIKE)
        )

        assertTrue(profile.hasGap)
        assertTrue(profile.hasBattery)
        assertTrue(profile.hasVendorTelinkLike)
        assertFalse(profile.hasHid)
    }
}
