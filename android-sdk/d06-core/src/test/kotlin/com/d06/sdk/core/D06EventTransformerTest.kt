package com.d06.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class D06EventTransformerTest {
    @Test
    fun `transforms mousepad movement with inversion and sensitivity`() {
        val transformer = D06EventTransformer(
            D06EventTransformConfig(
                invertX = true,
                invertY = true,
                movementSensitivity = 1.5f
            )
        )

        assertEquals(
            D06Event.MousepadMove(dx = -6, dy = 3),
            transformer.transform(D06Event.MousepadMove(dx = 4, dy = -2))
        )
    }

    @Test
    fun `drops mousepad movement inside deadzone`() {
        val transformer = D06EventTransformer(D06EventTransformConfig(movementDeadzone = 3))

        assertNull(transformer.transform(D06Event.MousepadMove(dx = 2, dy = -3)))
    }

    @Test
    fun `scales vertical and horizontal scroll units`() {
        val transformer = D06EventTransformer(D06EventTransformConfig(scrollSensitivity = 2f))

        assertEquals(
            D06Event.Scroll(ScrollDirection.Down, units = 4),
            transformer.transform(D06Event.Scroll(ScrollDirection.Down, units = 2))
        )
        assertEquals(
            D06Event.HorizontalScroll(HorizontalScrollDirection.Left, units = 6),
            transformer.transform(D06Event.HorizontalScroll(HorizontalScrollDirection.Left, units = 3))
        )
    }

    @Test
    fun `leaves non motion events unchanged`() {
        val transformer = D06EventTransformer(
            D06EventTransformConfig(
                invertX = true,
                movementSensitivity = 3f,
                scrollSensitivity = 2f
            )
        )

        assertEquals(D06Event.LeftDown, transformer.transform(D06Event.LeftDown))
    }
}
