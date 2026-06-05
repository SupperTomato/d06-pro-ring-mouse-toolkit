package com.d06.sdk.core

sealed interface D06Event {
    data object LeftDown : D06Event
    data object LeftUp : D06Event
    data object RightDown : D06Event
    data object RightUp : D06Event
    data object MiddleDown : D06Event
    data object MiddleUp : D06Event
    data class Scroll(val direction: ScrollDirection, val units: Int) : D06Event
    data class HorizontalScroll(val direction: HorizontalScrollDirection, val units: Int) : D06Event
    data class MousepadMove(val dx: Int, val dy: Int) : D06Event
    data object MousepadTap : D06Event
    data class Key(val keyCode: Int, val scanCode: Int, val action: KeyAction) : D06Event
    data class UnknownButton(val code: Int, val pressed: Boolean) : D06Event
}

enum class ScrollDirection {
    Up,
    Down
}

enum class HorizontalScrollDirection {
    Left,
    Right
}

enum class KeyAction {
    Down,
    Up
}
