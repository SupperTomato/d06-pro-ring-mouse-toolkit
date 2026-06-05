package com.d06.sdk.input

import com.d06.sdk.core.D06Event

data class D06DiagnosticEvent(
    val event: D06Event,
    val deviceInfo: D06InputDeviceInfo?,
    val timestampMillis: Long?
)

class D06InputDiagnostics @JvmOverloads constructor(
    private val capacity: Int = 200
) {
    private val events = ArrayDeque<D06DiagnosticEvent>()

    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    fun record(event: D06Event, deviceInfo: D06InputDeviceInfo?, timestampMillis: Long?) {
        while (events.size >= capacity) {
            events.removeFirst()
        }
        events.addLast(
            D06DiagnosticEvent(
                event = event,
                deviceInfo = deviceInfo,
                timestampMillis = timestampMillis
            )
        )
    }

    fun snapshot(): List<D06DiagnosticEvent> {
        return events.toList()
    }

    fun clear() {
        events.clear()
    }

    fun toJsonLines(): String {
        if (events.isEmpty()) return ""
        return buildString {
            events.forEach { event ->
                append(event.toJson())
                append('\n')
            }
        }
    }
}

private fun D06DiagnosticEvent.toJson(): String {
    return buildString {
        append('{')
        appendEventFields(event)
        append(",\"timestampMillis\":")
        append(timestampMillis?.toString() ?: "null")
        append(",\"device\":")
        appendDevice(deviceInfo)
        append('}')
    }
}

private fun StringBuilder.appendEventFields(event: D06Event) {
    when (event) {
        D06Event.LeftDown -> append("\"event\":\"LeftDown\"")
        D06Event.LeftUp -> append("\"event\":\"LeftUp\"")
        D06Event.RightDown -> append("\"event\":\"RightDown\"")
        D06Event.RightUp -> append("\"event\":\"RightUp\"")
        D06Event.MiddleDown -> append("\"event\":\"MiddleDown\"")
        D06Event.MiddleUp -> append("\"event\":\"MiddleUp\"")
        D06Event.MousepadTap -> append("\"event\":\"MousepadTap\"")
        is D06Event.MousepadMove -> {
            append("\"event\":\"MousepadMove\",\"dx\":")
            append(event.dx)
            append(",\"dy\":")
            append(event.dy)
        }
        is D06Event.Scroll -> {
            append("\"event\":\"Scroll\",\"direction\":\"")
            append(event.direction.name)
            append("\",\"units\":")
            append(event.units)
        }
        is D06Event.HorizontalScroll -> {
            append("\"event\":\"HorizontalScroll\",\"direction\":\"")
            append(event.direction.name)
            append("\",\"units\":")
            append(event.units)
        }
        is D06Event.Key -> {
            append("\"event\":\"Key\",\"keyCode\":")
            append(event.keyCode)
            append(",\"scanCode\":")
            append(event.scanCode)
            append(",\"action\":\"")
            append(event.action.name)
            append('"')
        }
        is D06Event.UnknownButton -> {
            append("\"event\":\"UnknownButton\",\"code\":")
            append(event.code)
            append(",\"pressed\":")
            append(event.pressed)
        }
    }
}

private fun StringBuilder.appendDevice(deviceInfo: D06InputDeviceInfo?) {
    if (deviceInfo == null) {
        append("null")
        return
    }

    append('{')
    append("\"name\":")
    appendJsonString(deviceInfo.name)
    append(",\"vendorId\":")
    append(deviceInfo.vendorId?.toString() ?: "null")
    append(",\"productId\":")
    append(deviceInfo.productId?.toString() ?: "null")
    append('}')
}

private fun StringBuilder.appendJsonString(value: String?) {
    if (value == null) {
        append("null")
        return
    }

    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
