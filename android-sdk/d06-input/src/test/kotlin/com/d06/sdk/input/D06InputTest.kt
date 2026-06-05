package com.d06.sdk.input

import com.d06.sdk.core.D06Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class D06InputTest {
    @Test
    fun `dispatchDecoded forwards decoded events in order and reports handled`() {
        val handledEvents = mutableListOf<D06Event>()
        val input = D06Input { event -> handledEvents += event }

        val handled = input.dispatchDecoded(
            listOf(
                D06Event.LeftDown,
                D06Event.MousepadMove(dx = 3, dy = -2),
                D06Event.LeftUp
            )
        )

        assertTrue(handled)
        assertEquals(
            listOf(
                D06Event.LeftDown,
                D06Event.MousepadMove(dx = 3, dy = -2),
                D06Event.LeftUp
            ),
            handledEvents
        )
    }

    @Test
    fun `dispatchDecoded reports unhandled when decoder returns no events`() {
        val handledEvents = mutableListOf<D06Event>()
        val input = D06Input { event -> handledEvents += event }

        val handled = input.dispatchDecoded(emptyList())

        assertFalse(handled)
        assertEquals(emptyList(), handledEvents)
    }

    @Test
    fun `shouldDispatchDevice accepts known d06 devices`() {
        val input = D06Input {}

        assertTrue(
            input.shouldDispatchDevice(
                D06InputDeviceInfo(name = "D06 Pro", vendorId = 0x248A, productId = 0x0101)
            )
        )
        assertTrue(
            input.shouldDispatchDevice(
                D06InputDeviceInfo(name = "TK Wireless Receiver Mouse", vendorId = 0x248A, productId = 0x0401)
            )
        )
    }

    @Test
    fun `shouldDispatchDevice accepts unknown device metadata`() {
        val input = D06Input {}

        assertTrue(input.shouldDispatchDevice(deviceInfo = null))
    }

    @Test
    fun `shouldDispatchDevice rejects unrelated devices`() {
        val input = D06Input {}

        assertFalse(
            input.shouldDispatchDevice(
                D06InputDeviceInfo(name = "Laptop Keyboard", vendorId = 0x1234, productId = 0x5678)
            )
        )
    }
}
