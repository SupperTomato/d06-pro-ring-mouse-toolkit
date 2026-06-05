package com.d06.sdk.remapper

import com.d06.sdk.core.D06Event
import com.d06.sdk.core.ScrollDirection

sealed interface D06RemapAction {
    data object NoOp : D06RemapAction
    data object Back : D06RemapAction
    data object Home : D06RemapAction
    data object RecentApps : D06RemapAction
    data class ScrollBy(val dy: Int) : D06RemapAction
}

data class D06RemapConfig(
    val middleClick: D06RemapAction = D06RemapAction.Back,
    val mousepadTap: D06RemapAction = D06RemapAction.NoOp,
    val unknownButton4: D06RemapAction = D06RemapAction.Back,
    val unknownButton5: D06RemapAction = D06RemapAction.Home
)

class D06RemapPolicy(
    private val config: D06RemapConfig = D06RemapConfig()
) {
    fun actionFor(event: D06Event): D06RemapAction {
        return when (event) {
            D06Event.MiddleUp -> config.middleClick
            D06Event.MousepadTap -> config.mousepadTap
            is D06Event.UnknownButton -> actionForUnknownButton(event)
            is D06Event.Scroll -> D06RemapAction.ScrollBy(
                dy = if (event.direction == ScrollDirection.Up) -event.units * 120 else event.units * 120
            )
            else -> D06RemapAction.NoOp
        }
    }

    private fun actionForUnknownButton(event: D06Event.UnknownButton): D06RemapAction {
        if (event.pressed) return D06RemapAction.NoOp
        return when (event.code) {
            4 -> config.unknownButton4
            5 -> config.unknownButton5
            else -> D06RemapAction.NoOp
        }
    }
}
