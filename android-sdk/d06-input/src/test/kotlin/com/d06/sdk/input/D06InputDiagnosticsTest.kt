package com.d06.sdk.input

import com.d06.sdk.core.D06Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class D06InputDiagnosticsTest {
    @Test
    fun `diagnostics keeps bounded event history`() {
        val diagnostics = D06InputDiagnostics(capacity = 2)
        val device = D06InputDeviceInfo(name = "D06 Pro", vendorId = 0x248A, productId = 0x0101)

        diagnostics.record(D06Event.LeftDown, device, timestampMillis = 100)
        diagnostics.record(D06Event.MousepadMove(dx = 4, dy = -2), device, timestampMillis = 110)
        diagnostics.record(D06Event.LeftUp, device, timestampMillis = 120)

        assertEquals(
            listOf(D06Event.MousepadMove(dx = 4, dy = -2), D06Event.LeftUp),
            diagnostics.snapshot().map { it.event }
        )
    }

    @Test
    fun `diagnostics exports json lines without extra dependency`() {
        val diagnostics = D06InputDiagnostics(capacity = 4)

        diagnostics.record(
            D06Event.Scroll(com.d06.sdk.core.ScrollDirection.Down, units = 2),
            D06InputDeviceInfo(name = "D06 Pro", vendorId = 0x248A, productId = 0x0101),
            timestampMillis = 100
        )

        val json = diagnostics.toJsonLines()

        assertTrue(json.contains("\"event\":\"Scroll\""))
        assertTrue(json.contains("\"direction\":\"Down\""))
        assertTrue(json.contains("\"vendorId\":9354"))
        assertTrue(json.endsWith("\n"))
    }

    @Test
    fun `d06 input records diagnostics while dispatching decoded events`() {
        val diagnostics = D06InputDiagnostics()
        val input = D06Input(diagnostics = diagnostics) {}

        input.dispatchDecoded(
            listOf(D06Event.RightUp),
            deviceInfo = D06InputDeviceInfo(name = "TK Wireless Receiver Mouse", vendorId = 0x248A, productId = 0x0401),
            timestampMillis = 200
        )

        assertEquals(D06Event.RightUp, diagnostics.snapshot().single().event)
        assertEquals(200, diagnostics.snapshot().single().timestampMillis)
    }
}
