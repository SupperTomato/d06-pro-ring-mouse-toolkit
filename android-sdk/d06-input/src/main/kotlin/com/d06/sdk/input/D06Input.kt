package com.d06.sdk.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.d06.sdk.core.D06Event

fun interface D06EventListener {
    fun onEvent(event: D06Event)
}

class D06Input(
    config: D06InputConfig = D06InputConfig(),
    private val diagnostics: D06InputDiagnostics? = null,
    private val onEvent: (D06Event) -> Unit
) {
    private val matcher = D06InputDeviceMatcher(config)
    private val decoder = D06InputDecoder(config)

    companion object {
        @JvmStatic
        fun create(listener: D06EventListener): D06Input {
            return create(D06InputConfig(), null, listener)
        }

        @JvmStatic
        fun create(config: D06InputConfig, listener: D06EventListener): D06Input {
            return create(config, null, listener)
        }

        @JvmStatic
        fun create(
            config: D06InputConfig,
            diagnostics: D06InputDiagnostics?,
            listener: D06EventListener
        ): D06Input {
            return D06Input(config, diagnostics) { event -> listener.onEvent(event) }
        }
    }

    fun dispatch(event: MotionEvent): Boolean {
        val deviceInfo = event.device?.let(D06InputDeviceInfo::from)
        if (!shouldDispatchDevice(deviceInfo)) {
            return false
        }
        return dispatchDecoded(decoder.onMotionEvent(event), deviceInfo, event.eventTime)
    }

    fun dispatch(event: KeyEvent): Boolean {
        val deviceInfo = event.device?.let(D06InputDeviceInfo::from)
        if (!shouldDispatchDevice(deviceInfo)) {
            return false
        }
        return dispatchDecoded(decoder.onKeyEvent(event), deviceInfo, event.eventTime)
    }

    fun isD06Device(device: InputDevice): Boolean {
        return matcher.matches(D06InputDeviceInfo.from(device))
    }

    fun resetPointerTracking() {
        decoder.resetPointerTracking()
    }

    internal fun dispatchDecoded(
        events: Iterable<D06Event>,
        deviceInfo: D06InputDeviceInfo? = null,
        timestampMillis: Long? = null
    ): Boolean {
        var handled = false
        events.forEach { event ->
            handled = true
            diagnostics?.record(event, deviceInfo, timestampMillis)
            onEvent(event)
        }
        return handled
    }

    internal fun shouldDispatchDevice(deviceInfo: D06InputDeviceInfo?): Boolean {
        return deviceInfo == null || matcher.matches(deviceInfo)
    }
}
