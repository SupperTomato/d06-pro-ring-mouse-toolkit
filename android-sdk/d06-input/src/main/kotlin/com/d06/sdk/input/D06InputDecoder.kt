package com.d06.sdk.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.d06.sdk.core.D06Event
import com.d06.sdk.core.D06EventTransformer
import com.d06.sdk.core.D06Mapper
import com.d06.sdk.core.D06RawMouse
import com.d06.sdk.core.KeyAction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class D06InputDecoder(
    private val config: D06InputConfig = D06InputConfig(),
    private val mapper: D06Mapper = D06Mapper(
        detectMousepadTap = config.detectMousepadTap,
        tapWindowMillis = config.tapWindowMillis
    ),
    private val transformer: D06EventTransformer = D06EventTransformer(config.eventTransform)
) {
    private val matcher = D06InputDeviceMatcher(config)
    private var lastPointerX: Float? = null
    private var lastPointerY: Float? = null

    fun isD06Device(device: InputDevice): Boolean {
        return matcher.matches(D06InputDeviceInfo.from(device))
    }

    fun onMotionEvent(event: MotionEvent): List<D06Event> {
        val (dx, dy) = event.pointerMovement()
        val raw = D06RawMouse(
            buttonFlags = event.toButtonFlags(),
            buttonData = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toWheelData(),
            dx = dx,
            dy = dy,
            timestampMillis = event.eventTime
        )
        return transformer.transform(mapper.mapRaw(raw))
    }

    fun onKeyEvent(event: KeyEvent): List<D06Event> {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> KeyAction.Down
            KeyEvent.ACTION_UP -> KeyAction.Up
            else -> return emptyList()
        }

        return transformer.transform(
            listOf(
                D06Event.Key(
                    keyCode = event.keyCode,
                    scanCode = event.scanCode,
                    action = action
                )
            )
        )
    }

    fun resetPointerTracking() {
        lastPointerX = null
        lastPointerY = null
    }

    private fun MotionEvent.toButtonFlags(): Int {
        return when (actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> actionButton.toD06ButtonDownFlag()
            MotionEvent.ACTION_BUTTON_RELEASE -> actionButton.toD06ButtonUpFlag()
            else -> 0
        }
    }

    private fun MotionEvent.pointerMovement(): Pair<Int, Int> {
        if (actionMasked == MotionEvent.ACTION_CANCEL || actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            resetPointerTracking()
            return 0 to 0
        }

        val relativeX = getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val relativeY = getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        if (relativeX != 0f || relativeY != 0f) {
            lastPointerX = x
            lastPointerY = y
            return relativeX.roundToInt() to relativeY.roundToInt()
        }

        if (actionMasked != MotionEvent.ACTION_HOVER_MOVE && actionMasked != MotionEvent.ACTION_MOVE) {
            return 0 to 0
        }

        val previousX = lastPointerX
        val previousY = lastPointerY
        lastPointerX = x
        lastPointerY = y

        if (previousX == null || previousY == null) {
            return 0 to 0
        }

        return (x - previousX).roundToInt() to (y - previousY).roundToInt()
    }

    private fun Int.toD06ButtonDownFlag(): Int {
        return when (this) {
            MotionEvent.BUTTON_PRIMARY -> 0x0001
            MotionEvent.BUTTON_SECONDARY -> 0x0004
            MotionEvent.BUTTON_TERTIARY -> 0x0010
            MotionEvent.BUTTON_BACK -> 0x0040
            MotionEvent.BUTTON_FORWARD -> 0x0100
            else -> 0
        }
    }

    private fun Int.toD06ButtonUpFlag(): Int {
        return when (this) {
            MotionEvent.BUTTON_PRIMARY -> 0x0002
            MotionEvent.BUTTON_SECONDARY -> 0x0008
            MotionEvent.BUTTON_TERTIARY -> 0x0020
            MotionEvent.BUTTON_BACK -> 0x0080
            MotionEvent.BUTTON_FORWARD -> 0x0200
            else -> 0
        }
    }

    private fun Float.toWheelData(): Int {
        return when {
            this > 0f -> max(1, abs(roundToInt())) * 120
            this < 0f -> -max(1, abs(roundToInt())) * 120
            else -> 0
        }
    }
}
