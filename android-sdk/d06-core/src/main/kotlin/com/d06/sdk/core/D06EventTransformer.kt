package com.d06.sdk.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class D06EventTransformConfig @JvmOverloads constructor(
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    val movementSensitivity: Float = 1f,
    val scrollSensitivity: Float = 1f,
    val movementDeadzone: Int = 0
) {
    init {
        require(movementSensitivity >= 0f) { "movementSensitivity must be >= 0" }
        require(scrollSensitivity >= 0f) { "scrollSensitivity must be >= 0" }
        require(movementDeadzone >= 0) { "movementDeadzone must be >= 0" }
    }
}

class D06EventTransformer(
    private val config: D06EventTransformConfig = D06EventTransformConfig()
) {
    fun transform(events: Iterable<D06Event>): List<D06Event> {
        return events.mapNotNull(::transform)
    }

    fun transform(event: D06Event): D06Event? {
        return when (event) {
            is D06Event.MousepadMove -> transformMove(event)
            is D06Event.Scroll -> transformScroll(event)
            is D06Event.HorizontalScroll -> transformHorizontalScroll(event)
            else -> event
        }
    }

    private fun transformMove(event: D06Event.MousepadMove): D06Event.MousepadMove? {
        val dx = transformAxis(event.dx, config.invertX)
        val dy = transformAxis(event.dy, config.invertY)
        if (dx == 0 && dy == 0) return null
        return D06Event.MousepadMove(dx = dx, dy = dy)
    }

    private fun transformAxis(value: Int, invert: Boolean): Int {
        if (abs(value) <= config.movementDeadzone) return 0
        val signed = if (invert) -value else value
        return (signed * config.movementSensitivity).roundToInt()
    }

    private fun transformScroll(event: D06Event.Scroll): D06Event.Scroll? {
        val units = scaledUnits(event.units) ?: return null
        return event.copy(units = units)
    }

    private fun transformHorizontalScroll(event: D06Event.HorizontalScroll): D06Event.HorizontalScroll? {
        val units = scaledUnits(event.units) ?: return null
        return event.copy(units = units)
    }

    private fun scaledUnits(units: Int): Int? {
        if (config.scrollSensitivity == 0f) return null
        return max(1, (units * config.scrollSensitivity).roundToInt())
    }
}
