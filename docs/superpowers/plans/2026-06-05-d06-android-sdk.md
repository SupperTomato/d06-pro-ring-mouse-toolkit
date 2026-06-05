# D06 Android SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a multi-module Kotlin Android SDK that decodes D06 Pro ring mouse input, exposes BLE metadata, and provides a constrained remapper/sample app.

**Architecture:** Create `android-sdk/` as a Gradle Kotlin DSL project. Put deterministic mapping logic in `d06-core`, Android event adaptation in `d06-input`, GATT profile/metadata logic in `d06-ble`, accessibility/remap policy in `d06-remapper`, and a simple Android sample app in `d06-sample`.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Android Gradle Plugin, JUnit 5 for JVM tests, AndroidX where needed, Kotlin coroutines/Flow for async event streams.

---

## File Structure

- Create `android-sdk/settings.gradle.kts`: Gradle module includes and plugin management.
- Create `android-sdk/build.gradle.kts`: shared repositories and plugin versions.
- Create `android-sdk/gradle.properties`: AndroidX/Kotlin flags.
- Create `android-sdk/d06-core/build.gradle.kts`: pure Kotlin/JVM library.
- Create `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06Event.kt`: event model.
- Create `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06RawInput.kt`: platform-neutral raw input shapes.
- Create `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06Mapper.kt`: button/wheel/mousepad mapping logic.
- Create `android-sdk/d06-core/src/test/kotlin/com/d06/sdk/core/D06MapperTest.kt`: unit tests for known mappings.
- Create `android-sdk/d06-input/build.gradle.kts`: Android library module.
- Create `android-sdk/d06-input/src/main/AndroidManifest.xml`: minimal manifest.
- Create `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputConfig.kt`: Android input config.
- Create `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputDecoder.kt`: `MotionEvent`/`KeyEvent` adapter.
- Create `android-sdk/d06-input/src/test/kotlin/com/d06/sdk/input/D06InputDeviceMatcherTest.kt`: JVM tests for metadata matching.
- Create `android-sdk/d06-ble/build.gradle.kts`: Android BLE library module.
- Create `android-sdk/d06-ble/src/main/AndroidManifest.xml`: Bluetooth permission declarations.
- Create `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06GattUuids.kt`: known UUID constants.
- Create `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06GattProfile.kt`: typed service/characteristic summary model.
- Create `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06BleClient.kt`: BLE client API shell.
- Create `android-sdk/d06-ble/src/test/kotlin/com/d06/sdk/ble/D06GattProfileTest.kt`: UUID/profile tests.
- Create `android-sdk/d06-remapper/build.gradle.kts`: Android library/sample support module.
- Create `android-sdk/d06-remapper/src/main/AndroidManifest.xml`: accessibility service declaration.
- Create `android-sdk/d06-remapper/src/main/res/xml/d06_accessibility_service.xml`: service capability metadata.
- Create `android-sdk/d06-remapper/src/main/kotlin/com/d06/sdk/remapper/D06RemapPolicy.kt`: typed remap policy.
- Create `android-sdk/d06-remapper/src/main/kotlin/com/d06/sdk/remapper/D06AccessibilityRemapperService.kt`: constrained accessibility sample service.
- Create `android-sdk/d06-sample/build.gradle.kts`: Android app module.
- Create `android-sdk/d06-sample/src/main/AndroidManifest.xml`: sample app manifest.
- Create `android-sdk/d06-sample/src/main/kotlin/com/d06/sdk/sample/MainActivity.kt`: simple live event console.
- Create `android-sdk/README.md`: setup, API, permissions, limitations.

## Task 1: Gradle Scaffold

**Files:**
- Create: `android-sdk/settings.gradle.kts`
- Create: `android-sdk/build.gradle.kts`
- Create: `android-sdk/gradle.properties`

- [ ] **Step 1: Create the Gradle settings**

Create `android-sdk/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "d06-android-sdk"

include(":d06-core")
include(":d06-input")
include(":d06-ble")
include(":d06-remapper")
include(":d06-sample")
```

- [ ] **Step 2: Create the root Gradle build**

Create `android-sdk/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    id("com.android.library") version "8.7.3" apply false
    id("com.android.application") version "8.7.3" apply false
}
```

- [ ] **Step 3: Create Gradle properties**

Create `android-sdk/gradle.properties`:

```properties
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

- [ ] **Step 4: Run Gradle projects**

Run:

```bash
cd android-sdk
./gradlew projects
```

Expected:

```text
Root project 'd06-android-sdk'
```

If `./gradlew` does not exist, run `gradle wrapper` first from `android-sdk/`, then rerun `./gradlew projects`.

- [ ] **Step 5: Commit scaffold if inside a Git repository**

Run:

```bash
git add android-sdk/settings.gradle.kts android-sdk/build.gradle.kts android-sdk/gradle.properties
git commit -m "chore: scaffold android sdk"
```

Expected: commit succeeds. If `git rev-parse --is-inside-work-tree` is false, skip this commit step.

## Task 2: Core Event Model and Mapping Tests

**Files:**
- Create: `android-sdk/d06-core/build.gradle.kts`
- Create: `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06Event.kt`
- Create: `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06RawInput.kt`
- Create: `android-sdk/d06-core/src/test/kotlin/com/d06/sdk/core/D06MapperTest.kt`

- [ ] **Step 1: Create the core module build file**

Create `android-sdk/d06-core/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing core mapping tests**

Create `android-sdk/d06-core/src/test/kotlin/com/d06/sdk/core/D06MapperTest.kt`:

```kotlin
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
    fun `maps mousepad relative movement signs`() {
        val mapper = D06Mapper()

        assertEquals(listOf(D06Event.MousepadMove(dx = 25, dy = 0)), mapper.mapRaw(D06RawMouse(dx = 25)))
        assertEquals(listOf(D06Event.MousepadMove(dx = -25, dy = 0)), mapper.mapRaw(D06RawMouse(dx = -25)))
        assertEquals(listOf(D06Event.MousepadMove(dx = 0, dy = -25)), mapper.mapRaw(D06RawMouse(dy = -25)))
        assertEquals(listOf(D06Event.MousepadMove(dx = 0, dy = 25)), mapper.mapRaw(D06RawMouse(dy = 25)))
    }

    @Test
    fun `marks unmapped mouse buttons as unknown`() {
        val mapper = D06Mapper()

        assertEquals(listOf(D06Event.UnknownButton(code = 4, pressed = true)), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0040)))
        assertEquals(listOf(D06Event.UnknownButton(code = 4, pressed = false)), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0080)))
        assertEquals(listOf(D06Event.UnknownButton(code = 5, pressed = true)), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0100)))
        assertEquals(listOf(D06Event.UnknownButton(code = 5, pressed = false)), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0200)))
    }
}
```

- [ ] **Step 3: Create event and raw input types referenced by tests**

Create `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06Event.kt`:

```kotlin
package com.d06.sdk.core

sealed interface D06Event {
    data object LeftDown : D06Event
    data object LeftUp : D06Event
    data object RightDown : D06Event
    data object RightUp : D06Event
    data object MiddleDown : D06Event
    data object MiddleUp : D06Event
    data class Scroll(val direction: ScrollDirection, val units: Int) : D06Event
    data class MousepadMove(val dx: Int, val dy: Int) : D06Event
    data object MousepadTap : D06Event
    data class UnknownButton(val code: Int, val pressed: Boolean) : D06Event
}

enum class ScrollDirection {
    Up,
    Down
}
```

Create `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06RawInput.kt`:

```kotlin
package com.d06.sdk.core

data class D06RawMouse(
    val buttonFlags: Int = 0,
    val buttonData: Int = 0,
    val dx: Int = 0,
    val dy: Int = 0
)
```

- [ ] **Step 4: Run tests to verify they fail because mapper is missing**

Run:

```bash
cd android-sdk
./gradlew :d06-core:test --tests com.d06.sdk.core.D06MapperTest
```

Expected: FAIL with unresolved reference or class not found for `D06Mapper`.

- [ ] **Step 5: Commit failing tests if inside a Git repository**

Run:

```bash
git add android-sdk/d06-core
git commit -m "test: define d06 core mappings"
```

Expected: commit succeeds. If not in a Git repository, skip.

## Task 3: Core Mapper Implementation

**Files:**
- Create: `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06Mapper.kt`
- Modify: `android-sdk/d06-core/src/test/kotlin/com/d06/sdk/core/D06MapperTest.kt`

- [ ] **Step 1: Implement the minimal mapper**

Create `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06Mapper.kt`:

```kotlin
package com.d06.sdk.core

class D06Mapper {
    fun mapRaw(raw: D06RawMouse): List<D06Event> {
        val events = mutableListOf<D06Event>()

        when (raw.buttonFlags) {
            0x0001 -> events += D06Event.LeftDown
            0x0002 -> events += D06Event.LeftUp
            0x0004 -> events += D06Event.RightDown
            0x0008 -> events += D06Event.RightUp
            0x0010 -> events += D06Event.MiddleDown
            0x0020 -> events += D06Event.MiddleUp
            0x0040 -> events += D06Event.UnknownButton(code = 4, pressed = true)
            0x0080 -> events += D06Event.UnknownButton(code = 4, pressed = false)
            0x0100 -> events += D06Event.UnknownButton(code = 5, pressed = true)
            0x0200 -> events += D06Event.UnknownButton(code = 5, pressed = false)
            0x0400 -> events += mapWheel(raw.buttonData)
        }

        if (raw.dx != 0 || raw.dy != 0) {
            events += D06Event.MousepadMove(dx = raw.dx, dy = raw.dy)
        }

        return events
    }

    private fun mapWheel(buttonData: Int): D06Event.Scroll {
        val units = kotlin.math.max(1, kotlin.math.abs(buttonData) / 120)
        return if (buttonData >= 0) {
            D06Event.Scroll(ScrollDirection.Up, units)
        } else {
            D06Event.Scroll(ScrollDirection.Down, units)
        }
    }
}
```

- [ ] **Step 2: Run core tests to verify pass**

Run:

```bash
cd android-sdk
./gradlew :d06-core:test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Add tap detection test**

Modify `android-sdk/d06-core/src/test/kotlin/com/d06/sdk/core/D06MapperTest.kt` and add:

```kotlin
    @Test
    fun `detects mousepad tap from left down up pair with no movement`() {
        val mapper = D06Mapper()

        assertEquals(emptyList(), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0001)))
        assertEquals(listOf(D06Event.MousepadTap), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0002)))
    }
```

- [ ] **Step 4: Run tap test to verify it fails**

Run:

```bash
cd android-sdk
./gradlew :d06-core:test --tests com.d06.sdk.core.D06MapperTest.detects\ mousepad\ tap\ from\ left\ down\ up\ pair\ with\ no\ movement
```

Expected: FAIL because `D06Mapper` currently emits `LeftDown` and `LeftUp`.

- [ ] **Step 5: Add configurable tap detection without removing click events by default**

Modify `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06RawInput.kt`:

```kotlin
package com.d06.sdk.core

data class D06RawMouse(
    val buttonFlags: Int = 0,
    val buttonData: Int = 0,
    val dx: Int = 0,
    val dy: Int = 0,
    val timestampMillis: Long = 0
)
```

Modify `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06Mapper.kt`:

```kotlin
package com.d06.sdk.core

class D06Mapper(
    private val detectMousepadTap: Boolean = false,
    private val tapWindowMillis: Long = 250
) {
    private var pendingLeftDownAt: Long? = null
    private var movementSinceLeftDown: Boolean = false

    fun mapRaw(raw: D06RawMouse): List<D06Event> {
        val events = mutableListOf<D06Event>()

        if (raw.dx != 0 || raw.dy != 0) {
            movementSinceLeftDown = true
            events += D06Event.MousepadMove(dx = raw.dx, dy = raw.dy)
        }

        when (raw.buttonFlags) {
            0x0001 -> {
                if (detectMousepadTap) {
                    pendingLeftDownAt = raw.timestampMillis
                    movementSinceLeftDown = false
                } else {
                    events += D06Event.LeftDown
                }
            }
            0x0002 -> {
                if (detectMousepadTap && isTap(raw.timestampMillis)) {
                    events += D06Event.MousepadTap
                } else {
                    events += D06Event.LeftUp
                }
                pendingLeftDownAt = null
            }
            0x0004 -> events += D06Event.RightDown
            0x0008 -> events += D06Event.RightUp
            0x0010 -> events += D06Event.MiddleDown
            0x0020 -> events += D06Event.MiddleUp
            0x0040 -> events += D06Event.UnknownButton(code = 4, pressed = true)
            0x0080 -> events += D06Event.UnknownButton(code = 4, pressed = false)
            0x0100 -> events += D06Event.UnknownButton(code = 5, pressed = true)
            0x0200 -> events += D06Event.UnknownButton(code = 5, pressed = false)
            0x0400 -> events += mapWheel(raw.buttonData)
        }

        return events
    }

    private fun isTap(upAt: Long): Boolean {
        val downAt = pendingLeftDownAt ?: return false
        return !movementSinceLeftDown && upAt - downAt <= tapWindowMillis
    }

    private fun mapWheel(buttonData: Int): D06Event.Scroll {
        val units = kotlin.math.max(1, kotlin.math.abs(buttonData) / 120)
        return if (buttonData >= 0) {
            D06Event.Scroll(ScrollDirection.Up, units)
        } else {
            D06Event.Scroll(ScrollDirection.Down, units)
        }
    }
}
```

Modify the tap test to construct `D06Mapper(detectMousepadTap = true)` and set timestamps:

```kotlin
    @Test
    fun `detects mousepad tap from left down up pair with no movement`() {
        val mapper = D06Mapper(detectMousepadTap = true)

        assertEquals(emptyList(), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0001, timestampMillis = 1000)))
        assertEquals(listOf(D06Event.MousepadTap), mapper.mapRaw(D06RawMouse(buttonFlags = 0x0002, timestampMillis = 1100)))
    }
```

- [ ] **Step 6: Run core tests to verify pass**

Run:

```bash
cd android-sdk
./gradlew :d06-core:test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit core mapper if inside a Git repository**

Run:

```bash
git add android-sdk/d06-core
git commit -m "feat: add d06 core mapper"
```

Expected: commit succeeds. If not in a Git repository, skip.

## Task 4: Android Input Module

**Files:**
- Create: `android-sdk/d06-input/build.gradle.kts`
- Create: `android-sdk/d06-input/src/main/AndroidManifest.xml`
- Create: `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputConfig.kt`
- Create: `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputDecoder.kt`
- Create: `android-sdk/d06-input/src/test/kotlin/com/d06/sdk/input/D06InputDeviceMatcherTest.kt`

- [ ] **Step 1: Create Android input module build file**

Create `android-sdk/d06-input/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.d06.sdk.input"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(project(":d06-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 2: Create input manifest**

Create `android-sdk/d06-input/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 3: Write failing matcher tests**

Create `android-sdk/d06-input/src/test/kotlin/com/d06/sdk/input/D06InputDeviceMatcherTest.kt`:

```kotlin
package com.d06.sdk.input

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class D06InputDeviceMatcherTest {
    @Test
    fun `matches d06 by known name`() {
        val matcher = D06InputDeviceMatcher()

        assertTrue(matcher.matches(D06InputDeviceInfo(name = "D06 Pro", vendorId = null, productId = null)))
    }

    @Test
    fun `matches d06 by known vid pid`() {
        val matcher = D06InputDeviceMatcher()

        assertTrue(matcher.matches(D06InputDeviceInfo(name = "Bluetooth Mouse", vendorId = 0x248A, productId = 0x0101)))
    }

    @Test
    fun `rejects unrelated input device`() {
        val matcher = D06InputDeviceMatcher()

        assertFalse(matcher.matches(D06InputDeviceInfo(name = "Laptop Touchpad", vendorId = 0x1234, productId = 0x5678)))
    }
}
```

- [ ] **Step 4: Run matcher tests to verify fail**

Run:

```bash
cd android-sdk
./gradlew :d06-input:testDebugUnitTest --tests com.d06.sdk.input.D06InputDeviceMatcherTest
```

Expected: FAIL because `D06InputDeviceMatcher` and `D06InputDeviceInfo` do not exist.

- [ ] **Step 5: Implement input config and matcher**

Create `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputConfig.kt`:

```kotlin
package com.d06.sdk.input

data class D06InputConfig(
    val detectMousepadTap: Boolean = false,
    val acceptedNames: Set<String> = setOf("D06 Pro", "D06"),
    val vendorId: Int = 0x248A,
    val productId: Int = 0x0101
)

data class D06InputDeviceInfo(
    val name: String?,
    val vendorId: Int?,
    val productId: Int?
)

class D06InputDeviceMatcher(
    private val config: D06InputConfig = D06InputConfig()
) {
    fun matches(info: D06InputDeviceInfo): Boolean {
        val nameMatches = info.name?.let { name ->
            config.acceptedNames.any { accepted -> name.contains(accepted, ignoreCase = true) }
        } ?: false

        val idMatches = info.vendorId == config.vendorId && info.productId == config.productId
        return nameMatches || idMatches
    }
}
```

- [ ] **Step 6: Run matcher tests to verify pass**

Run:

```bash
cd android-sdk
./gradlew :d06-input:testDebugUnitTest --tests com.d06.sdk.input.D06InputDeviceMatcherTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Implement Android event decoder shell**

Create `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputDecoder.kt`:

```kotlin
package com.d06.sdk.input

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.d06.sdk.core.D06Event
import com.d06.sdk.core.D06Mapper
import com.d06.sdk.core.D06RawMouse
import kotlin.math.roundToInt

class D06InputDecoder(
    private val config: D06InputConfig = D06InputConfig(),
    private val mapper: D06Mapper = D06Mapper(detectMousepadTap = config.detectMousepadTap)
) {
    private val matcher = D06InputDeviceMatcher(config)

    fun isD06Device(device: InputDevice): Boolean {
        return matcher.matches(
            D06InputDeviceInfo(
                name = device.name,
                vendorId = if (Build.VERSION.SDK_INT >= 19) device.vendorId else null,
                productId = if (Build.VERSION.SDK_INT >= 19) device.productId else null
            )
        )
    }

    fun onMotionEvent(event: MotionEvent): List<D06Event> {
        val buttonFlags = when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> event.actionButton.toD06ButtonDownFlag()
            MotionEvent.ACTION_BUTTON_RELEASE -> event.actionButton.toD06ButtonUpFlag()
            else -> 0
        }

        val raw = D06RawMouse(
            buttonFlags = buttonFlags,
            buttonData = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toWheelData(),
            dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X).toInt(),
            dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y).toInt(),
            timestampMillis = event.eventTime
        )
        return mapper.mapRaw(raw)
    }

    fun onKeyEvent(event: KeyEvent): List<D06Event> {
        return emptyList()
    }

    private fun Int.toD06ButtonDownFlag(): Int {
        return when (this) {
            MotionEvent.BUTTON_PRIMARY -> 0x0001
            MotionEvent.BUTTON_SECONDARY -> 0x0004
            MotionEvent.BUTTON_TERTIARY -> 0x0010
            MotionEvent.BUTTON_BACK -> 0x0040
            MotionEvent.BUTTON_FORWARD -> 0x0100
            else -> 0
        }
    }

    private fun Int.toD06ButtonUpFlag(): Int {
        return when (this) {
            MotionEvent.BUTTON_PRIMARY -> 0x0002
            MotionEvent.BUTTON_SECONDARY -> 0x0008
            MotionEvent.BUTTON_TERTIARY -> 0x0020
            MotionEvent.BUTTON_BACK -> 0x0080
            MotionEvent.BUTTON_FORWARD -> 0x0200
            else -> 0
        }
    }

    private fun Float.toWheelData(): Int {
        val ticks = roundToInt()
        return when {
            ticks > 0 -> ticks * 120
            ticks < 0 -> ticks * 120
            else -> 0
        }
    }
}
```

This decoder intentionally uses `actionButton` only for button press/release transitions. `MotionEvent.buttonState` is a held-state bitmask, so using it as a transition flag would repeatedly emit incorrect click events during move or scroll events.

- [ ] **Step 8: Run module build**

Run:

```bash
cd android-sdk
./gradlew :d06-input:assembleDebug :d06-input:testDebugUnitTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 9: Commit input module if inside a Git repository**

Run:

```bash
git add android-sdk/d06-input
git commit -m "feat: add android input decoder"
```

Expected: commit succeeds. If not in a Git repository, skip.

## Task 5: BLE Metadata Module

**Files:**
- Create: `android-sdk/d06-ble/build.gradle.kts`
- Create: `android-sdk/d06-ble/src/main/AndroidManifest.xml`
- Create: `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06GattUuids.kt`
- Create: `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06GattProfile.kt`
- Create: `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06BleClient.kt`
- Create: `android-sdk/d06-ble/src/test/kotlin/com/d06/sdk/ble/D06GattProfileTest.kt`

- [ ] **Step 1: Create BLE module build file**

Create `android-sdk/d06-ble/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.d06.sdk.ble"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 2: Create BLE manifest permissions**

Create `android-sdk/d06-ble/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
</manifest>
```

- [ ] **Step 3: Write UUID/profile tests**

Create `android-sdk/d06-ble/src/test/kotlin/com/d06/sdk/ble/D06GattProfileTest.kt`:

```kotlin
package com.d06.sdk.ble

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class D06GattProfileTest {
    @Test
    fun `known services include hid battery and vendor service`() {
        assertEquals(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"), D06GattUuids.HID)
        assertEquals(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"), D06GattUuids.BATTERY)
        assertEquals(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1912"), D06GattUuids.VENDOR_TELINK_LIKE)
    }

    @Test
    fun `profile marks discovered known services`() {
        val profile = D06GattProfile.fromServiceUuids(
            listOf(D06GattUuids.GAP, D06GattUuids.BATTERY, D06GattUuids.VENDOR_TELINK_LIKE)
        )

        assertTrue(profile.hasGap)
        assertTrue(profile.hasBattery)
        assertTrue(profile.hasVendorTelinkLike)
    }
}
```

- [ ] **Step 4: Run BLE tests to verify fail**

Run:

```bash
cd android-sdk
./gradlew :d06-ble:testDebugUnitTest --tests com.d06.sdk.ble.D06GattProfileTest
```

Expected: FAIL because `D06GattUuids` and `D06GattProfile` do not exist.

- [ ] **Step 5: Implement UUID and profile models**

Create `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06GattUuids.kt`:

```kotlin
package com.d06.sdk.ble

import java.util.UUID

object D06GattUuids {
    val GAP: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    val GATT: UUID = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
    val DIS: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val BATTERY: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val HID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
    val VENDOR_TELINK_LIKE: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1912")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
}
```

Create `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06GattProfile.kt`:

```kotlin
package com.d06.sdk.ble

import java.util.UUID

data class D06GattProfile(
    val serviceUuids: Set<UUID>,
    val hasGap: Boolean,
    val hasGatt: Boolean,
    val hasDis: Boolean,
    val hasBattery: Boolean,
    val hasHid: Boolean,
    val hasVendorTelinkLike: Boolean
) {
    companion object {
        fun fromServiceUuids(uuids: Iterable<UUID>): D06GattProfile {
            val set = uuids.toSet()
            return D06GattProfile(
                serviceUuids = set,
                hasGap = D06GattUuids.GAP in set,
                hasGatt = D06GattUuids.GATT in set,
                hasDis = D06GattUuids.DIS in set,
                hasBattery = D06GattUuids.BATTERY in set,
                hasHid = D06GattUuids.HID in set,
                hasVendorTelinkLike = D06GattUuids.VENDOR_TELINK_LIKE in set
            )
        }
    }
}
```

- [ ] **Step 6: Add BLE client API shell**

Create `android-sdk/d06-ble/src/main/kotlin/com/d06/sdk/ble/D06BleClient.kt`:

```kotlin
package com.d06.sdk.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface D06BleState {
    data object Idle : D06BleState
    data object Connecting : D06BleState
    data object Connected : D06BleState
    data class ServicesDiscovered(val profile: D06GattProfile) : D06BleState
    data class Error(val message: String) : D06BleState
}

class D06BleClient(
    private val context: Context
) {
    private val mutableState = MutableStateFlow<D06BleState>(D06BleState.Idle)
    val state: StateFlow<D06BleState> = mutableState

    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        mutableState.value = D06BleState.Connecting
        gatt = device.connectGatt(context, false, callback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        mutableState.value = D06BleState.Idle
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mutableState.value = D06BleState.Error("GATT status $status")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mutableState.value = D06BleState.Connected
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mutableState.value = D06BleState.Error("Service discovery status $status")
                return
            }
            mutableState.value = D06BleState.ServicesDiscovered(
                D06GattProfile.fromServiceUuids(gatt.services.map { it.uuid })
            )
        }
    }
}
```

- [ ] **Step 7: Run BLE tests and build**

Run:

```bash
cd android-sdk
./gradlew :d06-ble:testDebugUnitTest :d06-ble:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit BLE module if inside a Git repository**

Run:

```bash
git add android-sdk/d06-ble
git commit -m "feat: add d06 ble metadata client"
```

Expected: commit succeeds. If not in a Git repository, skip.

## Task 6: Remapper Module

**Files:**
- Create: `android-sdk/d06-remapper/build.gradle.kts`
- Create: `android-sdk/d06-remapper/src/main/AndroidManifest.xml`
- Create: `android-sdk/d06-remapper/src/main/res/xml/d06_accessibility_service.xml`
- Create: `android-sdk/d06-remapper/src/main/kotlin/com/d06/sdk/remapper/D06RemapPolicy.kt`
- Create: `android-sdk/d06-remapper/src/main/kotlin/com/d06/sdk/remapper/D06AccessibilityRemapperService.kt`

- [ ] **Step 1: Create remapper module build file**

Create `android-sdk/d06-remapper/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.d06.sdk.remapper"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    api(project(":d06-core"))
}
```

- [ ] **Step 2: Create accessibility manifest**

Create `android-sdk/d06-remapper/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <service
            android:name=".D06AccessibilityRemapperService"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/d06_accessibility_service" />
        </service>
    </application>
</manifest>
```

Create `android-sdk/d06-remapper/src/main/res/xml/d06_accessibility_service.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="false"
    android:description="@string/d06_accessibility_service_description" />
```

- [ ] **Step 3: Add remap policy**

Create `android-sdk/d06-remapper/src/main/kotlin/com/d06/sdk/remapper/D06RemapPolicy.kt`:

```kotlin
package com.d06.sdk.remapper

import com.d06.sdk.core.D06Event
import com.d06.sdk.core.ScrollDirection

sealed interface D06RemapAction {
    data object NoOp : D06RemapAction
    data object Back : D06RemapAction
    data object Home : D06RemapAction
    data class ScrollBy(val dy: Int) : D06RemapAction
}

class D06RemapPolicy {
    fun actionFor(event: D06Event): D06RemapAction {
        return when (event) {
            is D06Event.Scroll -> D06RemapAction.ScrollBy(
                dy = if (event.direction == ScrollDirection.Up) -event.units * 120 else event.units * 120
            )
            else -> D06RemapAction.NoOp
        }
    }
}
```

- [ ] **Step 4: Add constrained accessibility service**

Create `android-sdk/d06-remapper/src/main/kotlin/com/d06/sdk/remapper/D06AccessibilityRemapperService.kt`:

```kotlin
package com.d06.sdk.remapper

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class D06AccessibilityRemapperService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service is intentionally conservative. Stock Android does not
        // expose full global HID interception to ordinary apps.
    }

    override fun onInterrupt() {
        // No persistent operation to interrupt.
    }
}
```

- [ ] **Step 5: Add string resource**

Create `android-sdk/d06-remapper/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="d06_accessibility_service_description">D06 remapper sample service</string>
</resources>
```

- [ ] **Step 6: Run remapper build**

Run:

```bash
cd android-sdk
./gradlew :d06-remapper:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit remapper if inside a Git repository**

Run:

```bash
git add android-sdk/d06-remapper
git commit -m "feat: add constrained remapper module"
```

Expected: commit succeeds. If not in a Git repository, skip.

## Task 7: Sample App

**Files:**
- Create: `android-sdk/d06-sample/build.gradle.kts`
- Create: `android-sdk/d06-sample/src/main/AndroidManifest.xml`
- Create: `android-sdk/d06-sample/src/main/kotlin/com/d06/sdk/sample/MainActivity.kt`

- [ ] **Step 1: Create sample app build file**

Create `android-sdk/d06-sample/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.d06.sdk.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.d06.sdk.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":d06-core"))
    implementation(project(":d06-input"))
    implementation(project(":d06-ble"))
    implementation(project(":d06-remapper"))
}
```

- [ ] **Step 2: Create sample manifest**

Create `android-sdk/d06-sample/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <application
        android:label="D06 SDK Sample"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Create `android-sdk/d06-sample/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 3: Create sample activity**

Create `android-sdk/d06-sample/src/main/kotlin/com/d06/sdk/sample/MainActivity.kt`:

```kotlin
package com.d06.sdk.sample

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.ScrollView
import android.widget.TextView
import com.d06.sdk.input.D06InputConfig
import com.d06.sdk.input.D06InputDecoder

class MainActivity : Activity() {
    private val decoder = D06InputDecoder(D06InputConfig(detectMousepadTap = true))
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this).apply {
            textSize = 14f
            text = "D06 SDK Sample\nPair the D06 and interact with this screen.\n\n"
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(logView) })
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val events = decoder.onMotionEvent(ev)
        if (events.isNotEmpty()) {
            append("motion ${events.joinToString()}")
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val events = decoder.onKeyEvent(event)
        if (events.isNotEmpty()) {
            append("key ${events.joinToString()}")
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun append(line: String) {
        logView.append("$line\n")
    }
}
```

- [ ] **Step 4: Run sample build**

Run:

```bash
cd android-sdk
./gradlew :d06-sample:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit sample if inside a Git repository**

Run:

```bash
git add android-sdk/d06-sample
git commit -m "feat: add d06 sample app"
```

Expected: commit succeeds. If not in a Git repository, skip.

## Task 8: README and Validation Checklist

**Files:**
- Create: `android-sdk/README.md`

- [ ] **Step 1: Create README**

Create `android-sdk/README.md`:

```markdown
# D06 Android SDK

Kotlin Android SDK for the D06 Pro ring mouse.

## Modules

- `d06-core`: pure Kotlin event model and verified D06 mapping logic.
- `d06-input`: Android `MotionEvent` / `KeyEvent` decoder for apps that receive D06 input.
- `d06-ble`: BLE/GATT metadata client for battery, services, and device profile discovery.
- `d06-remapper`: constrained accessibility/remapper sample. Stock Android does not expose full global HID interception to ordinary apps.
- `d06-sample`: live event console and validation app.

## Verified Mapping

| D06 action | SDK event |
| --- | --- |
| Left click | `LeftDown`, `LeftUp` |
| Right click | `RightDown`, `RightUp` |
| Middle click | `MiddleDown`, `MiddleUp` |
| Scroll up | `Scroll(Up, units)` |
| Scroll down | `Scroll(Down, units)` |
| Mousepad right | `MousepadMove(+dx, 0)` |
| Mousepad left | `MousepadMove(-dx, 0)` |
| Mousepad up | `MousepadMove(0, -dy)` |
| Mousepad down | `MousepadMove(0, +dy)` |
| Mousepad tap | `MousepadTap` when tap detection is enabled |

## Android Limits

The SDK can decode D06 input inside apps that receive the input events. A stock Android app cannot universally intercept and replace every hardware HID event system-wide. The remapper module is a constrained sample for AccessibilityService-based behavior.

## BLE Permissions

Android 12 and newer require:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

Request the permission at runtime before calling BLE connect/discover/read APIs.

## Build

```bash
./gradlew test assembleDebug
```

## Manual Validation

1. Pair the D06 Pro to the Android device.
2. Launch `d06-sample`.
3. Tap the mousepad and confirm `MousepadTap`.
4. Swipe right/left/up/down and confirm axis signs.
5. Scroll up/down and confirm scroll direction.
6. Press left/right/middle buttons and confirm events.
7. Try long press, double tap, and mode controls; export logs for unmapped behavior.
```

- [ ] **Step 2: Run full build**

Run:

```bash
cd android-sdk
./gradlew test assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Commit README if inside a Git repository**

Run:

```bash
git add android-sdk/README.md
git commit -m "docs: add d06 sdk readme"
```

Expected: commit succeeds. If not in a Git repository, skip.

## Task 9: Final Verification

**Files:**
- Verify: all files under `android-sdk/`

- [ ] **Step 1: Run all tests**

Run:

```bash
cd android-sdk
./gradlew test
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Build all debug artifacts**

Run:

```bash
cd android-sdk
./gradlew assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Install sample on connected Android device**

Run:

```bash
cd android-sdk
./gradlew :d06-sample:installDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

If no Android device is connected, skip install and record that hardware validation remains pending.

- [ ] **Step 4: Manual hardware smoke test**

Use the sample app and verify:

```text
left click -> LeftDown/LeftUp
right click -> RightDown/RightUp
middle click -> MiddleDown/MiddleUp
mousepad tap -> MousepadTap
scroll up -> Scroll(Up, ...)
scroll down -> Scroll(Down, ...)
mousepad right -> positive dx
mousepad left -> negative dx
mousepad up -> negative dy
mousepad down -> positive dy
```

- [ ] **Step 5: Record validation results**

Append a short section to `android-sdk/README.md`:

```markdown
## Hardware Validation

- Device: D06 Pro
- Android device:
- Android version:
- Input device metadata exposed:
- Confirmed mappings:
- Unmapped behavior:
```

Fill the fields with actual observed values from the sample app.

- [ ] **Step 6: Commit validation if inside a Git repository**

Run:

```bash
git add android-sdk/README.md
git commit -m "docs: record android hardware validation"
```

Expected: commit succeeds. If not in a Git repository, skip.

## Self-Review

Spec coverage:

- App-level decoding is covered by Tasks 2-4 and Task 7.
- BLE/GATT metadata is covered by Task 5.
- Remapper constraints and sample service are covered by Task 6.
- Sample app and calibration path are covered by Tasks 7-9.
- Android limitations are covered by Tasks 6 and 8.

Placeholder scan:

- No task uses `TBD`, `TODO`, or "implement later".
- The README validation fields are intentionally blank only at the final hardware-validation step, where the worker must fill them from actual Android-device output.

Type consistency:

- `D06Event`, `ScrollDirection`, `D06RawMouse`, and `D06Mapper` are defined in `d06-core` before dependent modules use them.
- `D06InputConfig`, `D06InputDeviceInfo`, and `D06InputDeviceMatcher` are defined before `D06InputDecoder` uses them.
- `D06GattUuids`, `D06GattProfile`, and `D06BleState` are defined before BLE client usage.
