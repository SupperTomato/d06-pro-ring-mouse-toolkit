# D06 Pro 戒指鼠标

语言：[English](README.md) | 简体中文

D06 Pro 蓝牙戒指鼠标的逆向工程记录、采集工具和 Kotlin Android SDK。

D06 Pro 在主机上表现为一个 Bluetooth HID 设备，包含鼠标、键盘和消费者控制这几类 HID 集合。本仓库记录已观察到的 HID/BLE 行为，并把已经确认的映射封装成 Android SDK 模块，方便 Android 应用在收到 D06 输入事件时进行解码。

## 这个项目做什么

- 记录 D06 Pro 的蓝牙身份信息、GATT 服务、HID 集合和实际运行行为。
- 映射已经确认的控制：左键、右键、中键、上/下滚动、鼠标板移动、鼠标板点击、双击，以及目前观察到的长按行为。
- 提供 Windows PowerShell 工具，用于采集 BLE/GATT、HID caps 和 Raw Input 事件。
- 提供 Android SDK，包括纯 Kotlin 映射器、Android `MotionEvent` / `KeyEvent` 适配器、BLE 元数据客户端、受限的重映射辅助模块和示例应用。
- 保留采集数据，方便后续对照原始数据继续确认新的映射。

本项目不会刷写固件、修改鼠标，也不会向 vendor/Telink 服务写入数据。在协议明确之前，项目会刻意避免 vendor 写操作。

## 仓库结构

| 路径 | 用途 |
| --- | --- |
| `D06_PRO_RE.md` | 详细逆向工程报告和功能审计 |
| `artifacts/` | 已标注的 Raw Input、HID 和 GATT 采集数据 |
| `tools/` | Windows PowerShell 采集和枚举脚本 |
| `android-sdk/` | 多模块 Kotlin/Android SDK 和示例应用 |
| `docs/superpowers/specs/` | SDK 设计说明 |
| `docs/superpowers/plans/` | 构建 SDK 时使用的实现计划 |

## 已验证的 D06 映射

| D06 动作 | 主机上观察到的事件 | Android SDK 事件 |
| --- | --- | --- |
| 左键 | 鼠标左键按下/抬起：`0x0001`、`0x0002` | `LeftDown`、`LeftUp` |
| 右键 | 鼠标右键按下/抬起：`0x0004`、`0x0008` | `RightDown`、`RightUp` |
| 中键 | 鼠标中键按下/抬起：`0x0010`、`0x0020` | `MiddleDown`、`MiddleUp` |
| 向上滚动 | 滚轮 flag `0x0400`，data `+120` | `Scroll(Up, units)` |
| 向下滚动 | 滚轮 flag `0x0400`，data `-120` | `Scroll(Down, units)` |
| 鼠标板向右 | 相对位移 `+X` | `MousepadMove(+dx, 0)` |
| 鼠标板向左 | 相对位移 `-X` | `MousepadMove(-dx, 0)` |
| 鼠标板向上 | 相对位移 `-Y` | `MousepadMove(0, -dy)` |
| 鼠标板向下 | 相对位移 `+Y` | `MousepadMove(0, +dy)` |
| 鼠标板点击 | 与左键相同的 HID 模式 | 启用点击检测时为 `MousepadTap` |
| 鼠标板双击 | 连续的左键点击对 | 连续 tap/click 事件 |
| 左/右长按 | 在已采集的 Windows 模式中表现为普通按住鼠标按钮 | 普通 down/up 事件 |

鼠标板点击和物理左键在 HID 层可能无法区分，因为它们都可能表现为同一个左键按下/抬起组合。SDK 的点击模式是基于时间窗口和无移动的启发式检测，并不是独立硬件信号。

## Android SDK

SDK 位于 `android-sdk/`，包含五个模块：

| 模块 | 作用 |
| --- | --- |
| `d06-core` | 纯 Kotlin 事件模型和 D06 raw mouse 映射器 |
| `d06-input` | Android `MotionEvent` / `KeyEvent` 解码器和 D06 设备匹配器 |
| `d06-ble` | BLE/GATT UUID profile 和电量/服务发现客户端 |
| `d06-remapper` | 基于 AccessibilityService 的重映射辅助模块，用于 Android 允许的动作 |
| `d06-sample` | 用于硬件验证的实时事件控制台应用 |

最低 SDK：

- `d06-core`、`d06-input`、`d06-ble`：minSdk 23
- `d06-remapper`、`d06-sample`：minSdk 24，因为 Android 手势分发需要 API 24+

### 构建 SDK

```bash
cd android-sdk
./gradlew test assembleDebug
```

示例应用 debug APK 输出位置：

```text
android-sdk/d06-sample/build/outputs/apk/debug/d06-sample-debug.apk
```

### 安装示例应用

连接一台已开启 USB 调试的 Android 设备：

```bash
cd android-sdk
./gradlew :d06-sample:installDebug
```

然后通过蓝牙配对 D06 Pro，打开 **D06 SDK Sample**，并在屏幕上操作 D06。示例应用会打印解码后的事件，以及手机暴露出来的 Android 输入设备元数据。

### 在 Android 应用中使用 SDK

当前 SDK 以源码模块形式使用，尚未发布到 Maven。把模块添加到你的应用 `settings.gradle.kts`：

```kotlin
include(":d06-core")
project(":d06-core").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-core")

include(":d06-input")
project(":d06-input").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-input")

include(":d06-ble")
project(":d06-ble").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-ble")
```

然后在应用模块中添加依赖：

```kotlin
dependencies {
    implementation(project(":d06-core"))
    implementation(project(":d06-input"))
    implementation(project(":d06-ble"))
}
```

在 `Activity` 中解码 D06 输入：

```kotlin
import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import com.d06.sdk.input.D06InputConfig
import com.d06.sdk.input.D06InputDecoder

class MainActivity : Activity() {
    private val d06 = D06InputDecoder(
        D06InputConfig(detectMousepadTap = true)
    )

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val events = d06.onMotionEvent(ev)
        if (events.isNotEmpty()) {
            events.forEach { event ->
                // 处理 LeftDown、Scroll、MousepadMove、MousepadTap 等事件。
            }
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val events = d06.onKeyEvent(event)
        if (events.isNotEmpty()) {
            events.forEach { d06Event ->
                // 处理 Android 暴露出来的键盘或消费者控制事件。
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
```

如果需要判断 Android 输入设备是否匹配 D06：

```kotlin
val isD06 = motionEvent.device?.let { d06.isD06Device(it) } == true
```

### BLE 用法

Android 12 及以上需要 `BLUETOOTH_CONNECT`：

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

当应用已经获得运行时权限并拿到 `BluetoothDevice` 后：

```kotlin
import com.d06.sdk.ble.D06BleClient

val client = D06BleClient(context)
client.connect(bluetoothDevice)

// 观察 client.state：
// Idle、Connecting、Connected、ServicesDiscovered、BatteryLevel、Error
```

BLE 客户端用于元数据、电量和服务发现。它不会替代 Android 的 HID 栈，也不会写入 vendor 配置命令。

### 重映射限制

当你的应用有合法路径获得已解码的 `D06Event` 时，`d06-remapper` 可以帮助触发 Android 支持的无障碍动作，例如返回、主页或可分发的手势。

普通 Android 应用没有系统 API 可以全局拦截并替换所有硬件 HID 鼠标事件。若需要完整的全局 HID 拦截，需要平台权限、自定义输入服务、root 级集成，或其它设备策略接口。

## 逆向工程工具

在 Windows PowerShell 中运行，前提是 D06 Pro 已经配对：

```powershell
.\tools\dump_d06_gatt.ps1
.\tools\dump_hid_caps.ps1
.\tools\list_raw_input_devices.ps1
.\tools\capture_raw_input.ps1 -Seconds 10
```

这些采集可以用于：

- 判断某个控制发出的是鼠标、键盘还是消费者控制 report；
- 检查按钮 flag 和滚轮 data；
- 确认鼠标板轴方向；
- 发现特定设备或模式下是否存在隐藏的按钮 4/5。

## 仍值得测试的功能

- 如果物理设备有侧键/返回/前进键，测试鼠标按钮 4/5 行为。
- HID 集合 `Col01` 的键盘模式输出。
- HID 集合 `Col02` 的消费者/媒体控制输出。
- 模式切换前后重复已知控制，观察行为变化。
- 在允许访问 HID-over-GATT 的主机上测试电量通知和原始 HID report map。
- vendor/Telink 服务通知。在协议明确前避免写入。

## 当前验证状态

已完成：

- Windows BLE/GATT 枚举
- Windows HID collection/caps 解析
- 已知控制的 Windows Raw Input 采集
- Android SDK 构建和单元测试
- Android 示例应用 debug 构建

待完成：

- 使用示例应用在真实 Android 设备上进行验证
- 采集 D06 在具体手机/平板上的 Android 元数据
- 测试采集过程中未看到或未激活的控制

## 安全说明

- D06 vendor 服务类似 Telink OTA/config 服务。除非有明确协议和恢复方案，否则不要写入。
- 采集数据可能包含你的具体蓝牙地址和设备 ID。如果发布 fork 或 issue，请注意这一点。
- Android 重映射模块刻意保持保守，避免暗示 stock Android 可以做到系统级全局输入拦截。
