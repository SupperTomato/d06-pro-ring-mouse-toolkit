# 通俗功能指南

语言：[English](FUNCTIONS.md) | 简体中文

本指南用非开发者也能理解的方式，说明 D06 Pro 戒指鼠标工具包里每个面向用户的部分能做什么。

在这个文件里，“功能”包括两类：

- 普通用户可以运行的功能，例如在 Linux 上列出 D06 设备
- 开发者可以放进 Android 应用里的 SDK 部分

私有辅助函数和测试不是给用户直接使用的。只有在它们能帮助解释工具行为时，才会提到。

## 快速功能列表

| 部分 | 能做什么 |
| --- | --- |
| Android 示例应用 | 在真实 Android 设备上显示 D06 按键、滚动、鼠标板事件。 |
| Android SDK | 让其他 Android 应用理解 D06 输入事件。 |
| Android 重映射辅助 | 在 Android 允许的范围内，把已解码的 D06 事件映射成 Android 动作。 |
| Android BLE 辅助 | 读取蓝牙服务信息和电量。 |
| Linux evdev 工具 | 列出 D06 输入设备，并打印已解码的鼠标事件。 |
| Linux hidraw 工具 | 列出 HID 设备，保存 HID 描述符，采集原始 HID report。 |
| Linux BLE/GATT 工具 | 通过 BlueZ 保存蓝牙服务信息。 |
| Android adb 工具 | 从 USB 或无线调试连接的 Android 设备采集输入数据。 |

## D06 事件名称

这些是工具包解码 D06 Pro 输入后使用的通用名称。

| 事件 | 通俗含义 |
| --- | --- |
| `LeftDown` | 鼠标左键被按下。 |
| `LeftUp` | 鼠标左键被松开。 |
| `RightDown` | 鼠标右键被按下。 |
| `RightUp` | 鼠标右键被松开。 |
| `MiddleDown` | 鼠标中键被按下。 |
| `MiddleUp` | 鼠标中键被松开。 |
| `Scroll(Up, units)` | 滚轮或滚动手势向上移动。`units` 表示步数。 |
| `Scroll(Down, units)` | 滚轮或滚动手势向下移动。 |
| `HorizontalScroll(Left, units)` | 如果设备暴露横向滚轮，表示向左横向滚动。 |
| `HorizontalScroll(Right, units)` | 如果设备暴露横向滚轮，表示向右横向滚动。 |
| `MousepadMove(dx, dy)` | D06 鼠标板移动了指针。`dx` 表示左右，`dy` 表示上下。 |
| `MousepadTap` | 检测到一次快速鼠标板点击。这是基于时间的判断，因为它可能看起来和左键一样。 |
| `Key(keyCode, scanCode, action)` | Android 从设备看到键盘键或媒体键。 |
| `UnknownButton(code, pressed)` | 看到一个按钮，但工具包还不知道它真实用途。 |

## Android SDK：核心解码器

这些部分位于 `android-sdk/d06-core`。

| SDK 部分 | 能做什么 |
| --- | --- |
| `D06RawMouse` | 保存原始鼠标数据：按钮 flag、滚轮数据、X/Y 移动和时间。可以理解为“还没翻译的输入包”。 |
| `D06Mapper` | 把原始鼠标数据变成可读的 D06 事件，例如 `LeftDown`、`Scroll`、`MousepadMove`。 |
| `D06Mapper.mapRaw(...)` | 解码一个原始输入包，返回零个、一个或多个 D06 事件。 |
| `D06EventTransformConfig` | 保存移动和滚动设置，例如反转 X、反转 Y、灵敏度、死区。 |
| `D06EventTransformer` | 把这些移动和滚动设置应用到已解码事件上。 |
| `D06EventTransformer.transform(event)` | 修改一个事件。例如让鼠标板移动更快，或反转 X 方向。 |
| `D06EventTransformer.transform(events)` | 修改一组事件。被死区过滤掉的事件会被丢弃。 |
| `ScrollDirection` | 表示纵向滚动方向：`Up` 或 `Down`。 |
| `HorizontalScrollDirection` | 表示横向滚动方向：`Left` 或 `Right`。 |
| `KeyAction` | 表示按键状态：`Down` 或 `Up`。 |

重要限制：`MousepadTap` 是可选检测，因为 D06 的鼠标板点击和物理左键可能发送同一个信号。如果需要精确保留左键行为，应关闭鼠标板点击检测。

## Android SDK：应用输入解码器

这些部分位于 `android-sdk/d06-input`。

| SDK 部分 | 能做什么 |
| --- | --- |
| `D06InputConfig` | 控制 Android 输入如何解码。可以开启鼠标板点击检测、设置点击时间窗口、设置移动变换、定义哪些设备名或 USB/蓝牙 ID 算作 D06。 |
| `D06InputDeviceId` | 保存 vendor ID 和 product ID。用于识别蓝牙 D06 或 2.4 GHz 接收器。 |
| `D06InputDeviceInfo` | 保存 Android 能看到的设备名、vendor ID、product ID。 |
| `D06InputDeviceInfo.from(device)` | 把 Android 设备元数据复制成工具包格式。 |
| `D06InputDeviceMatcher` | 检查 Android 输入设备看起来是不是 D06 Pro 或 USB 接收器。 |
| `D06InputDeviceMatcher.matches(info)` | 当名称或硬件 ID 匹配 D06 列表时返回 true。 |
| `D06EventListener` | 给 Java 用户使用的简单回调接口。它会收到每个已解码的 D06 事件。 |
| `D06EventListener.onEvent(event)` | SDK 解码出 D06 事件时运行。 |
| `D06Input` | Android 应用最常用的辅助类。把 Android 输入事件交给它，它会回调你的代码并给出 D06 事件。 |
| `D06Input.create(listener)` | 用默认设置创建 `D06Input`，方便 Java 使用。 |
| `D06Input.create(config, listener)` | 用自定义设置创建 `D06Input`，方便 Java 使用。 |
| `D06Input.create(config, diagnostics, listener)` | 用自定义设置和诊断日志创建 `D06Input`，方便 Java 使用。 |
| `D06Input.dispatch(MotionEvent)` | 尝试解码一个 Android 鼠标/移动事件。如果产生了 D06 事件，返回 true。 |
| `D06Input.dispatch(KeyEvent)` | 尝试解码一个 Android 按键事件。如果产生了 D06 事件，返回 true。 |
| `D06Input.isD06Device(device)` | 检查一个 Android `InputDevice` 是否看起来像 D06 或其接收器。 |
| `D06Input.resetPointerTracking()` | 在焦点变化、取消事件或应用状态变化后，清除已保存的指针位置。 |
| `D06InputDecoder` | 更底层的解码器。适合需要直接拿到事件列表，而不是自动回调的应用。 |
| `D06InputDecoder.onMotionEvent(event)` | 把一个 Android `MotionEvent` 转换成 D06 事件。 |
| `D06InputDecoder.onKeyEvent(event)` | 把一个 Android `KeyEvent` 转换成 D06 按键事件。 |
| `D06InputDecoder.isD06Device(device)` | 和 `D06Input` 一样的设备检查，但在底层解码器中使用。 |
| `D06InputDecoder.resetPointerTracking()` | 清除底层解码器保存的指针位置。 |
| `D06DiagnosticEvent` | 一条诊断记录：已解码事件、设备信息、时间。 |
| `D06InputDiagnostics` | 保存最近一小段已解码事件，方便日志或 bug 报告。 |
| `D06InputDiagnostics.record(...)` | 向诊断历史添加一个事件。 |
| `D06InputDiagnostics.snapshot()` | 返回当前诊断历史。 |
| `D06InputDiagnostics.clear()` | 清空诊断历史。 |
| `D06InputDiagnostics.toJsonLines()` | 把诊断历史导出成文本，每一行是一个 JSON 事件。 |
| `D06_VENDOR_ID` | D06 系列已知 vendor ID：`248a`。 |
| `D06_BLUETOOTH_PRODUCT_ID` | 蓝牙 D06 已知 product ID：`0101`。 |
| `D06_USB_RECEIVER_PRODUCT_ID` | 2.4 GHz 接收器已知 product ID：`0401`。 |
| `D06_PRODUCT_ID` | 为兼容旧代码保留的旧名称。建议改用蓝牙或接收器对应的 product ID。 |
| `D06_DEVICE_IDS` | 内置的 D06 硬件 ID 列表。 |

## Android SDK：BLE 元数据

这些部分位于 `android-sdk/d06-ble`。

| SDK 部分 | 能做什么 |
| --- | --- |
| `D06BleState.Idle` | BLE 辅助未连接。 |
| `D06BleState.Connecting` | BLE 辅助正在尝试连接。 |
| `D06BleState.Connected` | BLE 连接已打开，正在开始服务发现。 |
| `D06BleState.ServicesDiscovered(profile)` | 已找到并汇总 BLE 服务。 |
| `D06BleState.BatteryLevel(percent)` | 已读取电量。 |
| `D06BleState.Error(message)` | BLE 操作失败。 |
| `D06BleClient` | Android 蓝牙辅助类，用于读取 D06 服务信息和电量。 |
| `D06BleClient.state` | 应用可以观察的实时状态流。 |
| `D06BleClient.connect(device)` | 连接到已配对或已选择的蓝牙 D06 设备。 |
| `D06BleClient.disconnect()` | 关闭 BLE 连接并回到空闲状态。 |
| `D06BleClient.readBatteryLevel()` | 请求读取电量。如果没有电池服务，返回 false。 |
| `D06GattServiceSummary` | 一个 BLE 服务及其 characteristic 的简短摘要。 |
| `D06GattProfile` | 设备上发现的已知 BLE 服务汇总。 |
| `D06GattProfile.fromServiceUuids(...)` | 从简单的服务 ID 列表构建 profile。 |
| `D06GattProfile.fromBluetoothGattServices(...)` | 从 Android 蓝牙服务对象构建 profile。 |
| `D06GattUuids` | 保存已知蓝牙 UUID：GAP、GATT、设备信息、电池、HID、HID Report Map、类似 Telink 的 vendor 服务。 |

BLE 辅助只读取元数据。它刻意不写入 vendor/Telink 命令。

## Android SDK：重映射

这些部分位于 `android-sdk/d06-remapper`。

| SDK 部分 | 能做什么 |
| --- | --- |
| `D06RemapAction.NoOp` | 什么都不做。 |
| `D06RemapAction.Back` | 请求 Android 返回。 |
| `D06RemapAction.Home` | 请求 Android 回到主页。 |
| `D06RemapAction.RecentApps` | 请求 Android 打开最近任务。 |
| `D06RemapAction.ScrollBy(dy)` | 请求 Android 执行一次纵向滚动手势。 |
| `D06RemapAction.SendKey(keyCode)` | 表示发送一个按键。辅助类可以选择这个动作，但 AccessibilityService 不能直接执行它。 |
| `D06RemapAction.Custom(name)` | 应用自定义动作的占位符。具体含义由应用决定。 |
| `D06RemapConfig` | 简单重映射设置：中键、鼠标板点击、未知按钮 4、未知按钮 5。 |
| `D06RemapConfig.toPreset(name)` | 把简单重映射设置变成一个命名 preset。 |
| `D06RemapPreset` | 一组命名规则，说明哪个 D06 事件应该变成哪个动作。 |
| `D06RemapPreset.actionFor(event)` | 查询某个 preset 会为一个 D06 事件选择什么动作。 |
| `D06RemapPresetBuilder.on(event, action)` | 添加规则：当这个精确事件发生时，使用这个动作。 |
| `D06RemapPresetBuilder.on(event) { ... }` | 添加规则：针对一个精确事件动态选择动作。 |
| `D06RemapPresetBuilder.onScroll { ... }` | 添加滚动事件规则。可用于音量、翻页或自定义滚动行为。 |
| `D06RemapPresetBuilder.onUnknownButton(code, action)` | 为未知按钮 4 或 5 的松开动作添加规则。 |
| `D06RemapPresetBuilder.fallback(action)` | 设置没有规则匹配时执行什么动作。 |
| `D06RemapPresets.Accessibility` | 内置 preset，用于常见无障碍风格动作。 |
| `D06RemapPresets.Presentation` | 内置 preset，用于幻灯片/翻页控制。 |
| `D06RemapPresets.Media` | 内置 preset，用于播放/暂停、下一项、音量控制。 |
| `D06RemapPresets.MouseOnly` | 内置 preset，尽量保持鼠标行为不变。 |
| `D06Remapper` | 把 preset 应用到传入的 D06 事件。 |
| `D06Remapper.actionFor(event)` | 返回一个事件对应的动作。 |
| `D06Remapper.handle(event, perform)` | 找到动作后，让你的应用执行它。如果确实执行了，返回 true。 |
| `D06RemapPolicy` | `D06Remapper` 的小包装，适合想用 policy 对象的应用。 |
| `D06RemapPolicy.actionFor(event)` | 返回一个事件对应的动作。 |
| `D06RemapValidationIssue` | 说明为什么某个 preset 动作不能由内置 AccessibilityService 辅助执行。 |
| `D06RemapValidator.validateForAccessibilityService(preset)` | 检查 preset，并报告 Android AccessibilityService 不能直接执行的动作。 |
| `D06AccessibilityRemapperService` | Android 服务辅助类。应用把已解码的 D06 事件交给它后，它可以执行 Android 允许的全局动作。 |
| `D06AccessibilityRemapperService.perform(action)` | 执行支持的动作：返回、主页、最近任务、滚动手势。对不支持的按键/自定义动作返回 false。 |

重要限制：Android 不允许普通应用全局替换所有硬件鼠标事件。重映射能力受 Android 普通应用和 AccessibilityService 能力限制。

## Android 示例应用

| 部分 | 能做什么 |
| --- | --- |
| `d06-sample` | 安装一个小型 Android 应用，实时显示已解码的 D06 事件。 |
| `MainActivity` | 示例应用主界面。它接收 Android 输入，交给 `D06Input`，并打印事件日志。 |

对非开发者测试来说，只要有人构建好 APK，这是最简单的 Android 路径。

## Linux 工具：已解码输入事件

这个工具是 `tools/linux/d06_evdev.py`。

| 命令或函数 | 能做什么 |
| --- | --- |
| `python3 tools/linux/d06_evdev.py --list` | 列出 Linux 输入设备，并标记可能是 D06 的设备。 |
| `python3 tools/linux/d06_evdev.py --seconds 30` | 打印 30 秒已解码 D06 事件。 |
| `--node /dev/input/eventX` | 读取一个指定的 Linux 输入设备。自动检测不准时很有用。 |
| `--match TERM` | 根据你提供的词或 ID，从 Linux 元数据中选择设备。 |
| `--profile file.json` | 从 JSON profile 应用移动、滚动或重映射设置。 |
| `D06EvdevProfile.from_dict(...)` | 从类似 JSON 的字典加载变换/重映射设置。 |
| `D06EvdevProfile.apply(...)` | 把 profile 设置应用到一个已解码事件。 |
| `D06EvdevTranslator.process(...)` | 把一个 Linux 输入事件转换成零个或多个 D06 JSON 事件。 |
| `event_to_json(...)` | 把一个已解码事件转成一行 JSON 文本。 |
| `load_profile(...)` | 读取 profile JSON 文件。 |
| `parse_input_devices(...)` | 读取 Linux `/proc/bus/input/devices` 文本并找出输入设备。 |
| `load_input_devices(...)` | 从真实的 `/proc/bus/input/devices` 加载 Linux 输入设备列表。 |
| `select_nodes(...)` | 选择要读取的 `/dev/input/eventX` 节点。 |
| `decode_event_bytes(...)` | 把 Linux 原始输入字节转换成可读的输入事件对象。 |
| `stream_nodes(...)` | 监听输入节点并打印已解码 JSON 事件。 |
| `print_device_list(...)` | 打印人类可读的输入设备列表。 |
| `main(...)` | 运行命令行工具。 |

这个工具只打印事件。它不会接管鼠标，也不会阻止正常指针移动。

## Linux 工具：HID 检查

这个工具是 `tools/linux/d06_hid.py`。

| 命令或函数 | 能做什么 |
| --- | --- |
| `python3 tools/linux/d06_hid.py --list` | 列出 Linux hidraw 设备，并标记可能是 D06 的设备。 |
| `python3 tools/linux/d06_hid.py --dump --out file.json` | 把 HID 设备元数据和 report descriptor 详情保存为 JSON。 |
| `python3 tools/linux/d06_hid.py --capture --seconds 15` | 在短时间内采集原始 HID report。 |
| `--node /dev/hidrawX` | 读取一个指定的 hidraw 设备。 |
| `--match TERM` | 根据元数据中的词或 ID 选择 hidraw 设备。 |
| `--report-size N` | 改变每次采集读取多少字节。 |
| `--no-descriptor` | 只 dump 元数据，不包含 descriptor 字节。 |
| `list_hidraw_devices(...)` | 从 Linux sysfs 查找 hidraw 设备。 |
| `dump_hidraw_devices(...)` | 构建可写入 JSON 的 HID 设备摘要。 |
| `select_hidraw_nodes(...)` | 选择要读取的 `/dev/hidrawX` 节点。 |
| `capture_hidraw_nodes(...)` | 监听 hidraw 节点，并把原始 HID report 打印成 JSON Lines。 |
| `summarize_report_descriptor(...)` | 把 HID descriptor 转成更简单的摘要。 |
| `HidReportDescriptorParser.parse(...)` | 读取 HID descriptor，提取 report ID、collection、field。 |
| `parse_uevent(...)` | 读取 Linux sysfs key/value 元数据。 |
| `parse_hid_id(...)` | 把 HID 硬件 ID 拆成 bus、vendor、product ID。 |
| `d06_match_reasons(...)` | 解释为什么一个设备看起来像 D06：名称匹配或硬件 ID 匹配。 |
| `bytes_to_hex(...)` | 把字节格式化成可读的十六进制文本。 |
| `print_device_list(...)` | 打印人类可读的 hidraw 设备列表。 |
| `main(...)` | 运行命令行工具。 |

当你需要比普通鼠标事件更底层的证据时，用这个工具。

## Linux 工具：BLE/GATT Dump

这个工具是 `tools/linux/dump_d06_gatt.py`。

| 命令或函数 | 能做什么 |
| --- | --- |
| `python3 tools/linux/dump_d06_gatt.py --address AA:BB:CC:DD:EE:FF` | 连接 BLE 设备，并保存 service/characteristic 元数据。 |
| `--name D06` | 没有提供地址时，按设备名扫描。 |
| `--out-dir DIR` | 选择保存 `gatt_dump.json` 和 HID report-map 文件的位置。 |
| `--timeout 10` | 设置 BLE 扫描/连接超时时间。 |
| `--no-read` | 只列出 BLE 服务，不尝试读取值。 |
| `uuid_name(...)` | 把已知 UUID 数字转换成友好名称。 |
| `bytes_to_hex(...)` | 把字节格式化成可读的十六进制文本。 |
| `bytes_to_text(...)` | 当 BLE 值是普通文本时显示可读文本。 |
| `dump_gatt(...)` | 通过 BlueZ/bleak 连接并构建完整 GATT dump。 |
| `main(...)` | 运行命令行工具。 |

这个工具读取蓝牙元数据。它不会写入 vendor 命令。

## Android 主机工具：adb 输入采集

这个工具是 `tools/android/d06_android_input.sh`。

| 命令或函数 | 能做什么 |
| --- | --- |
| `tools/android/d06_android_input.sh list` | 显示 Android 输入设备，以及来自 `getevent` 和 `dumpsys input` 的 D06/TK 接收器线索。 |
| `tools/android/d06_android_input.sh capture --seconds 10` | 在你按下或移动 D06 时，短时间采集 Android 输入事件。 |
| `--device /dev/input/eventX` | 采集一个指定的 Android 输入节点。 |
| `--serial SERIAL` | 当连接多个 Android 手机/平板时，选择其中一个。 |
| `--out file.txt` | 把采集输出保存到文件，同时也显示在屏幕上。 |
| `tools/android/d06_android_input.sh dump-input` | 保存 Android 完整输入设备报告。 |
| `usage()` | 打印帮助文本。 |
| `run_adb()` | 运行 adb，可选择指定 serial number。 |
| `parse_common()` | 读取共享命令选项，例如 `--serial`。 |

用 USB 调试或无线调试测试真实 Android 设备时，用这个工具。

## 配置和服务文件

| 文件 | 能做什么 |
| --- | --- |
| `tools/linux/d06-profile.example.json` | Linux profile 示例，可设置移动灵敏度、滚动灵敏度、轴反转、死区、事件重映射。 |
| `tools/linux/99-d06-pro.rules` | udev 规则模板，可让 D06 输入设备更容易访问，不必一直用 root。 |
| `tools/linux/d06-evdev.service` | systemd 用户服务模板，用于在后台运行 Linux evdev 解码器。 |

## 这个工具包不做什么

- 不刷写或修改 D06 固件。
- 不写入 vendor/Telink 服务。
- 不能让普通 Android 应用全局替换所有鼠标输入。
- 不保证 `MousepadTap` 在硬件层面和左键不同。
- 不会隐藏 Linux 或 Android 上的正常鼠标指针移动。
