package com.d06.sdk.remapper

import android.view.KeyEvent
import com.d06.sdk.core.D06Event
import com.d06.sdk.core.ScrollDirection

sealed interface D06RemapAction {
    data object NoOp : D06RemapAction
    data object Back : D06RemapAction
    data object Home : D06RemapAction
    data object RecentApps : D06RemapAction
    data class ScrollBy(val dy: Int) : D06RemapAction
    data class SendKey(val keyCode: Int) : D06RemapAction
    data class Custom(val name: String) : D06RemapAction
}

data class D06RemapConfig(
    val middleClick: D06RemapAction = D06RemapAction.Back,
    val mousepadTap: D06RemapAction = D06RemapAction.NoOp,
    val unknownButton4: D06RemapAction = D06RemapAction.Back,
    val unknownButton5: D06RemapAction = D06RemapAction.Home
)

class D06RemapPreset internal constructor(
    val name: String,
    private val rules: List<D06RemapRule>,
    private val fallback: D06RemapAction
) {
    fun actionFor(event: D06Event): D06RemapAction {
        return rules.firstNotNullOfOrNull { rule -> rule.actionFor(event) } ?: fallback
    }

    internal fun sampleActions(): Set<D06RemapAction> {
        return D06_REMAP_SAMPLE_EVENTS.map(::actionFor).toSet()
    }

    companion object {
        operator fun invoke(name: String, block: D06RemapPresetBuilder.() -> Unit): D06RemapPreset {
            val builder = D06RemapPresetBuilder(name)
            builder.block()
            return builder.build()
        }
    }
}

class D06RemapPresetBuilder internal constructor(
    private val name: String
) {
    private val rules = mutableListOf<D06RemapRule>()
    private var fallback: D06RemapAction = D06RemapAction.NoOp

    fun on(event: D06Event, action: D06RemapAction) {
        on(event) { action }
    }

    fun on(event: D06Event, action: (D06Event) -> D06RemapAction) {
        rules += D06RemapRule { candidate ->
            if (candidate == event) action(candidate) else null
        }
    }

    fun onScroll(action: (D06Event.Scroll) -> D06RemapAction) {
        rules += D06RemapRule { event ->
            if (event is D06Event.Scroll) action(event) else null
        }
    }

    fun onUnknownButton(code: Int, action: D06RemapAction) {
        rules += D06RemapRule { event ->
            if (event is D06Event.UnknownButton && !event.pressed && event.code == code) action else null
        }
    }

    fun fallback(action: D06RemapAction) {
        fallback = action
    }

    internal fun build(): D06RemapPreset {
        return D06RemapPreset(name = name, rules = rules.toList(), fallback = fallback)
    }
}

object D06RemapPresets {
    val Accessibility: D06RemapPreset = D06RemapConfig().toPreset("accessibility")

    val Presentation: D06RemapPreset = D06RemapPreset("presentation") {
        on(D06Event.LeftUp, D06RemapAction.SendKey(KeyEvent.KEYCODE_DPAD_LEFT))
        on(D06Event.RightUp, D06RemapAction.SendKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        on(D06Event.MousepadTap, D06RemapAction.SendKey(KeyEvent.KEYCODE_SPACE))
        on(D06Event.MiddleUp, D06RemapAction.Back)
        onScroll { event ->
            D06RemapAction.SendKey(
                if (event.direction == ScrollDirection.Up) {
                    KeyEvent.KEYCODE_DPAD_UP
                } else {
                    KeyEvent.KEYCODE_DPAD_DOWN
                }
            )
        }
    }

    val Media: D06RemapPreset = D06RemapPreset("media") {
        on(D06Event.MousepadTap, D06RemapAction.SendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        on(D06Event.MiddleUp, D06RemapAction.SendKey(KeyEvent.KEYCODE_MEDIA_NEXT))
        onScroll { event ->
            D06RemapAction.SendKey(
                if (event.direction == ScrollDirection.Up) {
                    KeyEvent.KEYCODE_VOLUME_UP
                } else {
                    KeyEvent.KEYCODE_VOLUME_DOWN
                }
            )
        }
    }

    val MouseOnly: D06RemapPreset = D06RemapPreset("mouse-only") {
        fallback(D06RemapAction.NoOp)
    }
}

class D06Remapper(
    private val preset: D06RemapPreset = D06RemapPresets.Accessibility
) {
    constructor(config: D06RemapConfig) : this(config.toPreset())

    fun actionFor(event: D06Event): D06RemapAction {
        return preset.actionFor(event)
    }

    fun handle(event: D06Event, perform: (D06RemapAction) -> Boolean): Boolean {
        val action = actionFor(event)
        return action != D06RemapAction.NoOp && perform(action)
    }
}

class D06RemapPolicy(
    private val remapper: D06Remapper = D06Remapper()
) {
    constructor(config: D06RemapConfig) : this(D06Remapper(config))
    constructor(preset: D06RemapPreset) : this(D06Remapper(preset))

    fun actionFor(event: D06Event): D06RemapAction {
        return remapper.actionFor(event)
    }
}

data class D06RemapValidationIssue(
    val presetName: String,
    val action: D06RemapAction,
    val reason: String
)

object D06RemapValidator {
    fun validateForAccessibilityService(preset: D06RemapPreset): List<D06RemapValidationIssue> {
        return preset.sampleActions()
            .filterNot(::isAccessibilityServiceAction)
            .map { action ->
                D06RemapValidationIssue(
                    presetName = preset.name,
                    action = action,
                    reason = "AccessibilityService performer cannot execute this action directly."
                )
            }
    }

    private fun isAccessibilityServiceAction(action: D06RemapAction): Boolean {
        return when (action) {
            D06RemapAction.NoOp,
            D06RemapAction.Back,
            D06RemapAction.Home,
            D06RemapAction.RecentApps,
            is D06RemapAction.ScrollBy -> true
            is D06RemapAction.SendKey,
            is D06RemapAction.Custom -> false
        }
    }
}

fun D06RemapConfig.toPreset(name: String = "custom"): D06RemapPreset {
    return D06RemapPreset(name) {
        on(D06Event.MiddleUp, middleClick)
        on(D06Event.MousepadTap, mousepadTap)
        onUnknownButton(code = 4, action = unknownButton4)
        onUnknownButton(code = 5, action = unknownButton5)
        onScroll { event ->
            D06RemapAction.ScrollBy(
                dy = if (event.direction == ScrollDirection.Up) {
                    -event.units * 120
                } else {
                    event.units * 120
                }
            )
        }
    }
}

internal fun interface D06RemapRule {
    fun actionFor(event: D06Event): D06RemapAction?
}

private val D06_REMAP_SAMPLE_EVENTS = listOf(
    D06Event.LeftUp,
    D06Event.RightUp,
    D06Event.MiddleUp,
    D06Event.MousepadTap,
    D06Event.UnknownButton(code = 4, pressed = false),
    D06Event.UnknownButton(code = 5, pressed = false),
    D06Event.Scroll(ScrollDirection.Up, units = 1),
    D06Event.Scroll(ScrollDirection.Down, units = 1)
)
