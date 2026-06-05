package com.d06.sdk.core

import kotlin.test.Test
import kotlin.test.assertEquals

class D06MapperTest {
    @Test
    fun `maps left right and middle mouse button flags`() {
        val mapper = D06Mapper()

        assertEquals(listOf(D06Event.LeftDown), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0001)))
        assertEquals(listOf(D06Event.LeftUp), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0002)))
        assertEquals(listOf(D06Event.RightDown), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0004)))
        assertEquals(listOf(D06Event.RightUp), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0008)))
        assertEquals(listOf(D06Event.MiddleDown), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0010)))
        assertEquals(listOf(D06Event.MiddleUp), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0020)))
    }

    @Test
    fun `maps vertical scroll wheel data`() {
        val mapper = D06Mapper()

        assertEquals(
            listOf(D06Event.Scroll(ScrollDirection.Up, units = 1)),
            mapper.mapRaw(D06RawMouse(buttonFlags = 0x0400, buttonData = 120))
        )
        assertEquals(
            listOf(D06Event.Scroll(ScrollDirection.Down, units = 1)),
            mapper.mapRaw(D06RawMouse(buttonFlags = 0x0400, buttonData = -120))
        )
    }

    @Test
    fun `maps optional horizontal wheel data`() {
        val mapper = D06Mapper()

        assertEquals(
            listOf(D06Event.HorizontalScroll(HorizontalScrollDirection.Right, units = 1)),
            mapper.mapRaw(D06RawMouse(buttonFlags = 0x0800, buttonData = 120))
        )
        assertEquals(
            listOf(D06Event.HorizontalScroll(HorizontalScrollDirection.Left, units = 1)),
            mapper.mapRaw(D06RawMouse(buttonFlags = 0x0800, buttonData = -120))
        )
    }

    @Test
    fun `maps mousepad relative movement signs`() {
        val mapper = D06Mapper()

        assertEquals(listOf(D06Event.MousepadMove(dx = 25, dy = 0)), mapper.mapRaw(D06RawMouse(dx = 25)))
        assertEquals(listOf(D06Event.MousepadMove(dx = -25, dy = 0)), mapper.mapRaw(D06RawMouse(dx = -25)))
        assertEquals(listOf(D06Event.MousepadMove(dx = 0, dy = -25)), mapper.mapRaw(D06RawMouse(dy = -25)))
        assertEquals(listOf(D06Event.MousepadMove(dx = 0, dy = 25)), mapper.mapRaw(D06RawMouse(dy = 25)))
    }

    @Test
    fun `marks descriptor-supported extra buttons as unknown`() {
        val mapper = D06Mapper()

        assertEquals(listOf(D06Event.UnknownButton(code = 4, pressed = true)), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0040)))
        assertEquals(listOf(D06Event.UnknownButton(code = 4, pressed = false)), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0080)))
        assertEquals(listOf(D06Event.UnknownButton(code = 5, pressed = true)), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0100)))
        assertEquals(listOf(D06Event.UnknownButton(code = 5, pressed = false)), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0200)))
    }

    @Test
    fun `decodes combined raw flags`() {
        val mapper = D06Mapper()

        assertEquals(
            listOf(
                D06Event.MousepadMove(dx = 8, dy = -4),
                D06Event.LeftDown,
                D06Event.Scroll(ScrollDirection.Up, units = 1)
            ),
            mapper.mapRaw(D06RawMouse(buttonFlags = 0x0401, buttonData = 120, dx = 8, dy = -4))
        )
    }

    @Test
    fun `leaves left clicks unchanged when tap detection is off`() {
        val mapper = D06Mapper()

        assertEquals(listOf(D06Event.LeftDown), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0001, timestampMillis = 1000)))
        assertEquals(listOf(D06Event.LeftUp), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0002, timestampMillis = 1100)))
    }

    @Test
    fun `detects mousepad tap from left down up pair with no movement`() {
        val mapper = D06Mapper(detectMousepadTap = true)

        assertEquals(emptyList(), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0001, timestampMillis = 1000)))
        assertEquals(listOf(D06Event.MousepadTap), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0002, timestampMillis = 1100)))
    }

    @Test
    fun `keeps drag as left button plus movement when tap detection is on`() {
        val mapper = D06Mapper(detectMousepadTap = true)

        assertEquals(emptyList(), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0001, timestampMillis = 1000)))
        assertEquals(
            listOf(D06Event.LeftDown, D06Event.MousepadMove(dx = 12, dy = 3)),
            mapper.mapRaw(D06RawMouse(dx = 12, dy = 3, timestampMillis = 1020))
        )
        assertEquals(listOf(D06Event.LeftUp), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0002, timestampMillis = 1150)))
    }
}
