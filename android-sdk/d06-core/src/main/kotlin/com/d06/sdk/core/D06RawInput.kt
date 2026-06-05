package com.d06.sdk.core

data class D06RawMouse(
    val buttonFlags: Int = 0,
    val buttonData: Int = 0,
    val dx: Int = 0,
    val dy: Int = 0,
    val timestampMillis: Long = 0
)
