package com.d06.sdk.input

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class D06InputDeviceMatcherTest {
    @Test
    fun `matches d06 by known name`() {
        val matcher = D06InputDeviceMatcher()

        assertTrue(matcher.matches(D06InputDeviceInfo(name = "D06 Pro", vendorId = null, productId = null)))
        assertTrue(matcher.matches(D06InputDeviceInfo(name = "Bluetooth D06 Ring Mouse", vendorId = null, productId = null)))
        assertTrue(matcher.matches(D06InputDeviceInfo(name = "TK Wireless Receiver Mouse", vendorId = null, productId = null)))
    }

    @Test
    fun `matches d06 by known vid pid`() {
        val matcher = D06InputDeviceMatcher()

        assertTrue(matcher.matches(D06InputDeviceInfo(name = "Bluetooth Mouse", vendorId = 0x248A, productId = 0x0101)))
        assertTrue(matcher.matches(D06InputDeviceInfo(name = "USB Mouse", vendorId = 0x248A, productId = 0x0401)))
    }

    @Test
    fun `rejects unrelated input device`() {
        val matcher = D06InputDeviceMatcher()

        assertFalse(matcher.matches(D06InputDeviceInfo(name = "Laptop Touchpad", vendorId = 0x1234, productId = 0x5678)))
        assertFalse(matcher.matches(D06InputDeviceInfo(name = null, vendorId = null, productId = null)))
    }

    @Test
    fun `keeps legacy custom vid pid config working`() {
        val matcher = D06InputDeviceMatcher(
            D06InputConfig(vendorId = 0x1209, productId = 0xD060)
        )

        assertTrue(matcher.matches(D06InputDeviceInfo(name = "Custom Receiver", vendorId = 0x1209, productId = 0xD060)))
    }
}
