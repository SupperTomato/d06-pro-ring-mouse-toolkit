# D06 Android SDK

语言：[English](README.md) | 简体中文

D06 Pro 戒指鼠标的 Kotlin Android SDK。

该 SDK 用于在 Android 应用收到 D06 Pro 输入流时解码事件。它也提供 BLE/GATT 元数据辅助工具，以及受限的 AccessibilityService 重映射辅助模块。它不会替代 Android 系统 HID 驱动。

## 模块

- `d06-core`：纯 Kotlin 事件模型和已验证的 D06 映射逻辑。
- `d06-input`：Android `MotionEvent` 和 `KeyEvent` 解码器，用于收到 D06 输入的应用。
- `d06-ble`：用于服务发现和电量读取的 BLE/GATT 元数据客户端。
- `d06-remapper`：受限的 AccessibilityService 重映射辅助模块。
- `d06-sample`：实时事件控制台和验证应用。

`d06-core`、`d06-input` 和 `d06-ble` 支持 minSdk 23。`d06-remapper` 和 `d06-sample` 需要 minSdk 24，因为 Android 手势分发需要 API 24+。

## 已验证映射

| D06 动作 | SDK 事件 |
| --- | --- |
| 左键 | `LeftDown`、`LeftUp` |
| 右键 | `RightDown`、`RightUp` |
| 中键 | `MiddleDown`、`MiddleUp` |
| 向上滚动 | `Scroll(Up, units)` |
| 向下滚动 | `Scroll(Down, units)` |
| 鼠标板向右 | `MousepadMove(+dx, 0)` |
| 鼠标板向左 | `MousepadMove(-dx, 0)` |
| 鼠标板向上 | `MousepadMove(0, -dy)` |
| 鼠标板向下 | `MousepadMove(0, +dy)` |
| 鼠标板点击 | 启用点击检测时为 `MousepadTap` |
| 额外鼠标按钮 4/5 | 在硬件行为被观察到之前为 `UnknownButton(4/5, pressed)` |
| 键盘/媒体控制 | 当 Android 暴露为按键事件时为 `Key(keyCode, scanCode, action)` |

## 重要限制

鼠标板点击和物理左键可能无法区分，因为二者都可能表现为同一个 HID 左键按下/抬起组合。`D06Mapper(detectMousepadTap = true)` 使用时间窗口和无移动启发式检测。需要精确保留左键时，应保持该选项关闭。

SDK 可以在收到输入事件的应用内部解码 D06 输入。普通 Android 应用不能全局拦截并替换所有硬件 HID 事件。`d06-remapper` 只是基于 Android 允许能力的受限 AccessibilityService 辅助模块。

## BLE 权限

Android 12 及以上需要：

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

调用 BLE connect/discover/read API 前，请先请求运行时权限。

## 构建

```bash
./gradlew test assembleDebug
```

## 本地发布或使用 JitPack

库模块已配置 Gradle `maven-publish`：

```bash
./gradlew publishToMavenLocal
```

JitPack 使用方式：创建 GitHub release 或 tag，然后通过 `https://jitpack.io` 使用对应 tag 或 commit hash。

## 将 SDK 添加到应用

这些模块当前以源码形式使用。把模块添加到应用的 `settings.gradle.kts`：

```kotlin
include(":d06-core")
project(":d06-core").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-core")

include(":d06-input")
project(":d06-input").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-input")

include(":d06-ble")
project(":d06-ble").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-ble")
```

然后按需依赖模块：

```kotlin
dependencies {
    implementation(project(":d06-core"))
    implementation(project(":d06-input"))
    implementation(project(":d06-ble"))
}
```

## 解码输入

```kotlin
import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import com.d06.sdk.core.D06EventTransformConfig
import com.d06.sdk.input.D06Input
import com.d06.sdk.input.D06InputConfig
import com.d06.sdk.input.D06InputDiagnostics

class MainActivity : Activity() {
    private val diagnostics = D06InputDiagnostics()
    private val d06 = D06Input(
        D06InputConfig(
            detectMousepadTap = true,
            eventTransform = D06EventTransformConfig(
                movementSensitivity = 1.25f,
                movementDeadzone = 2
            )
        ),
        diagnostics
    ) { event ->
        // 处理 LeftDown、Scroll、MousepadMove、MousepadTap 等。
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return d06.dispatch(ev) || super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return d06.dispatch(event) || super.dispatchKeyEvent(event)
    }
}
```

使用 `d06.isD06Device(inputDevice)` 检查 Android 元数据是否匹配已知的 D06 名称或 VID/PID。

`D06Input` 只会消费已识别的 D06 设备元数据，或 Android 没有暴露设备元数据的事件。如果你的设备显示为不同名称/VID/PID，把它加到 `D06InputConfig`。

如果高级集成需要拿到解码后的事件列表而不是回调，可以直接使用 `D06InputDecoder`。

## Remap Preset

```kotlin
val preset = D06RemapPreset("my-profile") {
    on(D06Event.MousepadTap, D06RemapAction.Home)
    on(D06Event.MiddleUp, D06RemapAction.Back)
}

val action = D06Remapper(preset).actionFor(D06Event.MousepadTap)
```

内置 preset：`D06RemapPresets.Accessibility`、`Presentation`、`Media`、`MouseOnly`。

在把动作交给 `D06AccessibilityRemapperService` 前，可用 `D06RemapValidator.validateForAccessibilityService(preset)` 检查 AccessibilityService 不能直接执行的动作。

## 诊断日志

```kotlin
val jsonl = diagnostics.toJsonLines()
```

诊断模块会保留有限数量的已解码事件和输入设备元数据。

匹配器会识别两条已知 D06 路径：

- Bluetooth HID：VID/PID `248a:0101`，名称包含 `D06` 或 `D06 Pro`
- 通过 USB-OTG 使用的 2.4 GHz USB 接收器：VID/PID `248a:0401`，名称包含 `TK Wireless Receiver`

USB 接收器路径从普通 Android `MotionEvent` / `KeyEvent` 输入中解码。它不使用 BLE 模块，也不需要蓝牙权限。

## 读取 BLE 元数据

```kotlin
import com.d06.sdk.ble.D06BleClient

val client = D06BleClient(context)
client.connect(bluetoothDevice)

// 观察 client.state，以获取服务 profile 和电量更新。
```

BLE 模块用于服务发现和电量读取。它会刻意避免 vendor-service 写操作。

## 手动验证

1. 将 D06 Pro 与 Android 设备配对。
2. 启动 `d06-sample`。
3. 点击鼠标板，确认输出 `MousepadTap`。
4. 向右/左/上/下滑动，确认轴方向。
5. 上下滚动，确认滚动方向。
6. 按左键、右键、中键，确认事件。
7. 尝试长按、双击、模式控制和任何隐藏按钮；导出日志用于未映射行为分析。

## 硬件验证状态

- 设备：D06 Pro
- Android 设备：当前环境未连接
- Android 版本：未验证
- 暴露出的输入设备元数据：尚未在 Android 上验证
- 已确认映射：构建期 mapper 测试通过；真实 Android 示例验证待完成
- 未映射行为：真实硬件 Android 验证待完成
