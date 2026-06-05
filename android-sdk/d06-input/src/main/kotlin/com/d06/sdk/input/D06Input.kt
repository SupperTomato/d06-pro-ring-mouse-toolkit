package com.d06.sdk.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.d06.sdk.core.D06Event

class D06Input(
    config: D06InputConfig = D06InputConfig(),
    private val onEvent: (D06Event) -> Unit
) {
    private val matcher = D06InputDeviceMatcher(config)
    private val decoder = D06InputDecoder(config)

    fun dispatch(event: MotionEvent): Boolean {
        if (!shouldDispatchDevice(event.device?.let(D06InputDeviceInfo::from))) {
            return false
        }
        return dispatchDecoded(decoder.onMotionEvent(event))
    }

    fun dispatch(event: KeyEvent): Boolean {
        if (!shouldDispatchDevice(event.device?.let(D06InputDeviceInfo::from))) {
            return false
        }
        return dispatchDecoded(decoder.onKeyEvent(event))
    }

    fun isD06Device(device: InputDevice): Boolean {
        return matcher.matches(D06InputDeviceInfo.from(device))
    }

    fun resetPointerTracking() {
        decoder.resetPointerTracking()
    }

    internal fun dispatchDecoded(events: Iterable<D06Event>): Boolean {
        var handled = false
        events.forEach { event ->
            handled = true
            onEvent(event)
        }
        return handled
    }

    internal fun shouldDispatchDevice(deviceInfo: D06InputDeviceInfo?): Boolean {
        return deviceInfo == null || matcher.matches(deviceInfo)
    }
}
