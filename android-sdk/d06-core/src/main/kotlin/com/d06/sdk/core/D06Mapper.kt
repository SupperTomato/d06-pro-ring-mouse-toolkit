package com.d06.sdk.core

import kotlin.math.abs
import kotlin.math.max

class D06Mapper(
    private val detectMousepadTap: Boolean = false,
    private val tapWindowMillis: Long = 250
) {
    private var pendingLeftDownAt: Long? = null

    fun mapRaw(raw: D06RawMouse): List<D06Event> {
        val events = mutableListOf<D06Event>()

        if (raw.hasMovement) {
            emitPendingLeftDown(events)
            events += D06Event.MousepadMove(dx = raw.dx, dy = raw.dy)
        }

        if (raw.buttonFlags and LEFT_DOWN != 0) {
            if (detectMousepadTap) {
                pendingLeftDownAt = raw.timestampMillis
            } else {
                events += D06Event.LeftDown
            }
        }

        if (raw.buttonFlags and LEFT_UP != 0) {
            val downAt = pendingLeftDownAt
            if (detectMousepadTap && downAt != null && raw.timestampMillis - downAt <= tapWindowMillis) {
                events += D06Event.MousepadTap
            } else {
                emitPendingLeftDown(events)
                events += D06Event.LeftUp
            }
            pendingLeftDownAt = null
        }

        if (raw.buttonFlags and RIGHT_DOWN != 0) events += D06Event.RightDown
        if (raw.buttonFlags and RIGHT_UP != 0) events += D06Event.RightUp
        if (raw.buttonFlags and MIDDLE_DOWN != 0) events += D06Event.MiddleDown
        if (raw.buttonFlags and MIDDLE_UP != 0) events += D06Event.MiddleUp
        if (raw.buttonFlags and BUTTON4_DOWN != 0) events += D06Event.UnknownButton(code = 4, pressed = true)
        if (raw.buttonFlags and BUTTON4_UP != 0) events += D06Event.UnknownButton(code = 4, pressed = false)
        if (raw.buttonFlags and BUTTON5_DOWN != 0) events += D06Event.UnknownButton(code = 5, pressed = true)
        if (raw.buttonFlags and BUTTON5_UP != 0) events += D06Event.UnknownButton(code = 5, pressed = false)
        if (raw.buttonFlags and VERTICAL_WHEEL != 0) events += mapVerticalWheel(raw.buttonData)
        if (raw.buttonFlags and HORIZONTAL_WHEEL != 0) events += mapHorizontalWheel(raw.buttonData)

        return events
    }

    private fun emitPendingLeftDown(events: MutableList<D06Event>) {
        if (pendingLeftDownAt != null) {
            events += D06Event.LeftDown
            pendingLeftDownAt = null
        }
    }

    private fun mapVerticalWheel(buttonData: Int): D06Event.Scroll {
        val units = max(1, abs(buttonData) / WHEEL_DELTA)
        return if (buttonData >= 0) {
            D06Event.Scroll(ScrollDirection.Up, units)
        } else {
            D06Event.Scroll(ScrollDirection.Down, units)
        }
    }

    private fun mapHorizontalWheel(buttonData: Int): D06Event.HorizontalScroll {
        val units = max(1, abs(buttonData) / WHEEL_DELTA)
        return if (buttonData >= 0) {
            D06Event.HorizontalScroll(HorizontalScrollDirection.Right, units)
        } else {
            D06Event.HorizontalScroll(HorizontalScrollDirection.Left, units)
        }
    }

    private val D06RawMouse.hasMovement: Boolean
        get() = dx != 0 || dy != 0

    private companion object {
        const val LEFT_DOWN = 0x0001
        const val LEFT_UP = 0x0002
        const val RIGHT_DOWN = 0x0004
        const val RIGHT_UP = 0x0008
        const val MIDDLE_DOWN = 0x0010
        const val MIDDLE_UP = 0x0020
        const val BUTTON4_DOWN = 0x0040
        const val BUTTON4_UP = 0x0080
        const val BUTTON5_DOWN = 0x0100
        const val BUTTON5_UP = 0x0200
        const val VERTICAL_WHEEL = 0x0400
        const val HORIZONTAL_WHEEL = 0x0800
        const val WHEEL_DELTA = 120
    }
}
