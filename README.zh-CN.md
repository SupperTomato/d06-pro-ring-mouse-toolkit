# D06 Pro 戒指鼠标工具包

语言：[English](README.md) | 简体中文

这个项目帮助用户在 Android 和 Linux 上理解、测试、使用 D06 Pro 戒指鼠标。

简单说：D06 Pro 在系统里像一个小型蓝牙或 USB 鼠标。本仓库记录每个按键和手势发出的信号，提供 Android SDK/示例应用，也提供 Linux 工具来查看戒指鼠标正在发送什么输入。

每个 SDK 部分、命令和事件能做什么，见英文的通俗说明：[FUNCTIONS.md](FUNCTIONS.md)。

## 适合谁

- 想知道 D06 Pro 每个按键真实作用的用户。
- 想在手机或平板上测试 D06 Pro 的 Android 用户。
- 想在 Android 应用里识别 D06 输入的开发者。
- 想在 Linux 上查看或转换 D06 输入事件的用户。
- 想查看 HID/BLE 采集证据的逆向工程用户。

## 当前状态

已经可用：

- 左键、右键、中键解码
- 上/下滚动解码
- 鼠标板移动方向映射
- 鼠标板点击/双击观察
- Android 输入解码 SDK
- Android 示例应用
- Linux `evdev` 解码器
- Linux `hidraw` 和 BLE/GATT 检查工具
- Linux 上识别 USB 接收器

仍有限制：

- 普通 Android 应用不能全局替换所有硬件鼠标事件
- 鼠标板点击和物理左键在 HID 层可能完全一样
- 隐藏按钮 4/5、键盘模式、媒体模式还需要更多真机采集
- 项目刻意不支持 vendor/Telink 写入

## Android 简单使用

普通测试流程：

1. 通过蓝牙把 D06 Pro 配对到 Android 设备，或通过 USB-OTG 连接 2.4 GHz 接收器。
2. 构建并安装示例应用：

```bash
cd android-sdk
./gradlew :d06-sample:installDebug
```

3. 打开 **D06 SDK Sample**。
4. 按按键、移动鼠标板。应用会显示解码后的 D06 事件。

目前仓库里还没有打包好的用户版 APK。你仍然需要 Gradle/Android Studio，或让别人帮你构建示例 APK。

## Linux 简单使用

列出可能的 D06 输入设备：

```bash
python3 tools/linux/d06_evdev.py --list
```

打印解码后的事件：

```bash
python3 tools/linux/d06_evdev.py --seconds 30
```

如果 Linux 阻止访问 `/dev/input/event*`，可以用 `sudo`，加入 `input` 用户组，或安装 `tools/linux/99-d06-pro.rules` 里的 udev 规则模板。

Linux 工具只打印事件。它不会接管鼠标，也不会阻止普通指针移动。

## 按键和手势映射

| D06 动作 | 主机看到的输入 | SDK 事件 |
| --- | --- | --- |
| 左键 | 鼠标左键按下/抬起 | `LeftDown`、`LeftUp` |
| 右键 | 鼠标右键按下/抬起 | `RightDown`、`RightUp` |
| 中键 | 鼠标中键按下/抬起 | `MiddleDown`、`MiddleUp` |
| 向上滚动 | 鼠标滚轮正向一步 | `Scroll(Up, units)` |
| 向下滚动 | 鼠标滚轮反向一步 | `Scroll(Down, units)` |
| 鼠标板向右 | 相对 `+X` 移动 | `MousepadMove(+dx, 0)` |
| 鼠标板向左 | 相对 `-X` 移动 | `MousepadMove(-dx, 0)` |
| 鼠标板向上 | 相对 `-Y` 移动 | `MousepadMove(0, -dy)` |
| 鼠标板向下 | 相对 `+Y` 移动 | `MousepadMove(0, +dy)` |
| 鼠标板点击 | 与左键相同的模式 | 只有启用点击检测时才是 `MousepadTap` |
| 鼠标板双击 | 连续左键点击 | 连续 tap/click 事件 |

说明书里可能写着“下一个视频”、“点赞”、“拍照”等动作。在设备层面，它们通常仍然只是鼠标、键盘或媒体控制事件。具体含义由目标应用决定。

## Android SDK 开发者用法

SDK 位于 `android-sdk/`。

| 模块 | 用途 |
| --- | --- |
| `d06-core` | 纯 Kotlin D06 事件模型和映射器 |
| `d06-input` | Android `MotionEvent` / `KeyEvent` 解码器 |
| `d06-ble` | BLE/GATT 元数据和电量辅助工具 |
| `d06-remapper` | 受限的 AccessibilityService 重映射辅助工具 |
| `d06-sample` | 用于真机测试的示例应用 |

构建全部模块：

```bash
cd android-sdk
./gradlew test assembleDebug
```

发布到 Maven local：

```bash
cd android-sdk
./gradlew publishToMavenLocal
```

本地源码开发时，可以从相邻 clone 引入模块：

```kotlin
include(":d06-core")
project(":d06-core").projectDir = file("../d06-pro-ring-mouse-toolkit/android-sdk/d06-core")

include(":d06-input")
project(":d06-input").projectDir = file("../d06-pro-ring-mouse-toolkit/android-sdk/d06-input")

include(":d06-ble")
project(":d06-ble").projectDir = file("../d06-pro-ring-mouse-toolkit/android-sdk/d06-ble")
```

基础解码用法：

```kotlin
class MainActivity : Activity() {
    private val d06 = D06Input(
        D06InputConfig(detectMousepadTap = true)
    ) { event ->
        // 处理 LeftDown、Scroll、MousepadMove、MousepadTap 等事件。
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return d06.dispatch(ev) || super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return d06.dispatch(event) || super.dispatchKeyEvent(event)
    }
}
```

完整 SDK 用法、重映射 preset、诊断日志和 BLE 权限见 [android-sdk/README.zh-CN.md](android-sdk/README.zh-CN.md)。

## Linux 和 Android 采集工具

Linux：

```bash
python3 tools/linux/d06_evdev.py --list
python3 tools/linux/d06_evdev.py --seconds 10
python3 tools/linux/d06_hid.py --list
sudo python3 tools/linux/d06_hid.py --dump --out artifacts/linux/hid/hid_caps.json
python3 tools/linux/dump_d06_gatt.py --address AA:BB:CC:DD:EE:FF --out-dir artifacts/linux/gatt
```

在已开启 USB 或无线调试的 Android 设备上，从 Linux 主机运行：

```bash
tools/android/d06_android_input.sh list
tools/android/d06_android_input.sh capture --seconds 10 --out artifacts/android/getevent/android_getevent.txt
tools/android/d06_android_input.sh dump-input --out artifacts/android/dumpsys/input.txt
```

## 仓库结构

| 路径 | 内容 |
| --- | --- |
| `android-sdk/` | Android SDK 模块和示例应用 |
| `tools/` | Linux 和 Android 采集工具 |
| `artifacts/` | 按 Android、Linux、历史主机采集分组的数据 |
| `FUNCTIONS.md` | 工具包功能和特性的通俗说明 |
| `D06_PRO_RE.md` | 详细逆向工程记录 |
| `docs/research/` | SDK 功能研究 |
| `docs/superpowers/` | 实现规格和计划 |

## 安全和隐私

- 本项目不会刷写固件。
- 本项目不会写入 vendor/Telink 服务。
- 采集文件可能包含设备名、硬件 ID 或蓝牙地址。
- Android 重映射保持保守，因为普通 Android 应用不能系统级替换所有硬件鼠标输入。
