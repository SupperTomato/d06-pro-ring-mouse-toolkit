package com.d06.sdk.remapper

import com.d06.sdk.core.D06Event
import com.d06.sdk.core.ScrollDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class D06RemapPresetTest {
    @Test
    fun `custom preset maps exact events and falls back to no op`() {
        val preset = D06RemapPreset("gaming") {
            on(D06Event.MiddleUp, D06RemapAction.Home)
            on(D06Event.MousepadTap) { D06RemapAction.Back }
        }

        val remapper = D06Remapper(preset)

        assertEquals(D06RemapAction.Home, remapper.actionFor(D06Event.MiddleUp))
        assertEquals(D06RemapAction.Back, remapper.actionFor(D06Event.MousepadTap))
        assertEquals(D06RemapAction.NoOp, remapper.actionFor(D06Event.LeftDown))
    }

    @Test
    fun `custom preset maps scroll events with event data`() {
        val preset = D06RemapPreset("fast-scroll") {
            onScroll { event ->
                D06RemapAction.ScrollBy(
                    dy = if (event.direction == ScrollDirection.Up) -event.units * 240 else event.units * 240
                )
            }
        }

        assertEquals(
            D06RemapAction.ScrollBy(dy = -480),
            D06Remapper(preset).actionFor(D06Event.Scroll(ScrollDirection.Up, units = 2))
        )
    }

    @Test
    fun `built in media preset exposes app handled key actions`() {
        assertEquals(
            D06RemapAction.SendKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
            D06Remapper(D06RemapPresets.Media).actionFor(D06Event.MousepadTap)
        )
    }

    @Test
    fun `accessibility validator flags actions accessibility service cannot perform`() {
        val issues = D06RemapValidator.validateForAccessibilityService(D06RemapPresets.Media)

        assertTrue(issues.any { it.action is D06RemapAction.SendKey })
    }

    @Test
    fun `legacy config still maps through policy`() {
        val policy = D06RemapPolicy(
            D06RemapConfig(
                middleClick = D06RemapAction.RecentApps,
                mousepadTap = D06RemapAction.Home
            )
        )

        assertEquals(D06RemapAction.RecentApps, policy.actionFor(D06Event.MiddleUp))
        assertEquals(D06RemapAction.Home, policy.actionFor(D06Event.MousepadTap))
    }
}
