# SDK 性能指南

语言：[English](PERFORMANCE.md) | 简体中文

本指南列出使用 D06 Pro 戒指鼠标工具包时，能获得最好性能的实际优化方式。

## 最佳默认设置

| 优化 | 为什么有用 |
| --- | --- |
| 只使用需要的模块。 | 应用更小，依赖更少，启动工作更少。 |
| 复用一个 `D06Input` 或 `D06InputDecoder` 实例。 | 避免每个事件都重新创建 mapper、matcher 和指针跟踪状态。 |
| 除非需要点击检测，否则保持 `detectMousepadTap = false`。 | 避免延迟左键判断，也能精确保留物理左键。 |
| 让事件回调保持很快。 | Android 输入事件走应用输入路径；慢操作会让界面感觉延迟。 |
| 发布版本不要开启 diagnostics，除非正在调试。 | 避免保存/记录额外事件历史。 |
| 不要在每个输入事件上导出 JSON 日志。 | JSON 导出适合 bug 报告，不适合热路径。 |
| 复用 remap preset 和 remapper。 | 规则只构建一次，输入时只做动作查询。 |
| 只有读取元数据或电量时才连接 BLE。 | HID 鼠标输入不需要 BLE helper 一直连接。 |
| 用 release build 和 R8/minify 做真实测试。 | debug build 更慢，并带有更多检查/日志。 |

## Android 应用优化

| 怎么做 | 通俗解释 |
| --- | --- |
| 每个 Activity/View/输入界面创建一次 `D06Input`。 | 把它当成这个界面的一个控制器，不要每次点击都重新创建。 |
| 只把真实输入事件交给它。 | 调用 `dispatchGenericMotionEvent` 和 `dispatchKeyEvent`；不要把无关应用事件传进去。 |
| 让内置设备匹配器过滤设备。 | `D06Input` 会跳过看起来不像 D06 或接收器的设备。 |
| 让 `acceptedNames` 和 `acceptedDeviceIds` 保持小而明确。 | 匹配更简单，也避免误解码其他鼠标。 |
| 做昂贵应用逻辑前先用 `isD06Device(device)`。 | 先做便宜检查，只对 D06 设备执行昂贵逻辑。 |
| 焦点丢失、取消事件或切换界面后调用 `resetPointerTracking()`。 | 防止旧指针位置导致一次错误的大跳动。 |
| 不要在事件回调里阻塞。 | 不要在回调里直接写文件、网络请求、sleep、大量数据库操作或重 UI rebuild。 |
| 把重工作放到另一个 coroutine/thread。 | 输入保持流畅，日志、统计或应用动作分开处理。 |
| 更新自定义 UI 时合并高频 `MousepadMove` 事件。 | 很多小移动事件可以在重绘前合并。 |
| 限制可见 debug 日志频率。 | 屏幕显示所有事件适合测试，但列表无限增长会拖慢 UI。 |

## 点击检测优化

| 设置 | 性能影响 |
| --- | --- |
| `detectMousepadTap = false` | 对普通左键最快、最准确。这是最安全默认值。 |
| `detectMousepadTap = true` | 可以得到 `MousepadTap`，但左键可能要等点击窗口结束后才能决定。 |
| 更小的 `tapWindowMillis` | 更快决定一次快速左键模式是不是点击。 |
| 更大的 `tapWindowMillis` | 更容易检测点击，但左键行为可能感觉没那么立即。 |

设备可能让鼠标板点击和物理左键看起来完全一样。只有应用真的需要时才开启点击检测，性能和正确性都最好。

## 移动和滚动优化

| 选项 | 怎么用 |
| --- | --- |
| `movementDeadzone` | 在应用看到之前丢掉很小的误触移动。适合过滤抖动。 |
| `movementSensitivity` | 在 SDK 里统一调整鼠标板移动速度，不要每个界面都自己缩放。 |
| `scrollSensitivity` | 在 SDK 里统一调整滚动步数。 |
| `invertX` / `invertY` | 在 SDK 里统一翻转方向，不要每个界面都修正。 |
| 应用内事件合并 | 构建自定义 UI 时，在视觉重绘前合并多个 `MousepadMove` 事件。 |

除非确实需要，不要设置特别高的灵敏度。它会把很小的噪声放大。

## 诊断和日志

| 怎么做 | 为什么 |
| --- | --- |
| 只在测试或收集 bug 报告时使用 `D06InputDiagnostics`。 | 它会保存额外事件记录。 |
| 保持较小的 diagnostic `capacity`。 | 历史记录越大，占用内存越多。 |
| 只在需要时调用 `snapshot()`。 | 它会复制保存的历史。 |
| 只在导出时调用 `toJsonLines()`。 | 它会构建文本，不应该每个事件都运行。 |
| 用 `clear()` 清理旧日志。 | 长时间运行时内存更可控。 |

## 重映射优化

| 怎么做 | 为什么 |
| --- | --- |
| 创建一次 `D06RemapPreset`。 | 规则构建是初始化工作，不是每个事件都做。 |
| 复用 `D06Remapper`。 | 每个事件的动作查询保持很小。 |
| 初始化时运行 `D06RemapValidator.validateForAccessibilityService(...)`。 | 不要每个事件都验证。 |
| 重映射主要用于按钮、点击、滚动动作。 | Accessibility 手势是系统操作，不适合高频指针移动。 |
| 不要把 `SendKey` 交给 `D06AccessibilityRemapperService` 重复尝试。 | 该 service 不能直接执行 `SendKey`。 |
| 让 `Custom` 动作在应用内部快速执行。 | SDK 只命名自定义动作，真正执行由应用负责。 |

## BLE 优化

| 怎么做 | 为什么 |
| --- | --- |
| 不要用 BLE 做普通鼠标输入。 | Android 通过 HID 接收鼠标输入；BLE helper 用于元数据和电量。 |
| 只在需要时连接。 | 节省电量，减少蓝牙工作。 |
| 读取电量/服务信息后断开。 | 不干扰 HID 路径，也避免陈旧连接。 |
| 避免重连循环。 | 失败的 BLE 连接可能很耗时，也会制造很多噪声。 |
| 从应用生命周期里只观察一次 `D06BleClient.state`。 | 避免多个 collector 重复做同样工作。 |

## Linux 工具优化

| 工具 | 优化 |
| --- | --- |
| `d06_evdev.py` | 知道设备后使用 `--node /dev/input/eventX`，跳过自动选择。 |
| `d06_evdev.py` | 用 profile JSON 做变换/重映射，不要再用另一个脚本后处理输出。 |
| `d06_evdev.py` | 捕获时用 `--seconds N`，避免长期测试让日志无限增长。 |
| `d06_hid.py` | 只需要设备元数据时，用 `--dump --no-descriptor`。 |
| `d06_hid.py` | 只有需要原始 HID report 时才用 `--capture`；它比 evdev 更底层，也更吵。 |
| `dump_d06_gatt.py` | 只需要服务结构时用 `--no-read`。读取所有值会更慢。 |
| Android adb 脚本 | 识别正确 Android 输入节点后，用 `--device /dev/input/eventX`。 |
| Android adb 脚本 | 用 `--seconds N` 和 `--out file.txt` 做有边界、可复现的采集。 |

## 应用构建优化

| 怎么做 | 为什么 |
| --- | --- |
| 如果只需要解码输入，只依赖 `d06-core` 和 `d06-input`。 | BLE/remapper 模块是可选的。 |
| 只有读取电量或服务元数据时才添加 `d06-ble`。 | 不需要蓝牙功能的应用不用包含蓝牙代码。 |
| 只有使用 Android 重映射辅助时才添加 `d06-remapper`。 | 只解码输入的应用不用包含无障碍/重映射代码。 |
| 用 release build 测性能。 | debug build 不能代表真实速度。 |
| 最终应用开启 R8/minify。 | 移除未使用的 SDK/应用代码。 |
| 不要把示例/debug 事件控制台放进生产界面。 | 实时事件列表适合测试，不适合最快运行。 |

## 未来值得加入 SDK 的性能优化

正常使用不一定需要这些，但如果 SDK 继续扩大，这些改进有价值。

| 可加入的 SDK 改进 | 好处 |
| --- | --- |
| 无分配 callback 解码器 | 为很高频输入路径减少 list 分配。 |
| 按 Android device ID 缓存设备匹配结果 | 避免同一设备重复做名称/VID/PID 匹配。 |
| 内置移动事件合并器 | 在应用 UI 重绘前合并快速 `MousepadMove` 事件。 |
| 内置限速 diagnostics exporter | 更安全地做长时间 debug 日志。 |
| benchmark 模块 | 测量解码成本，并发现性能回退。 |
| 可选 Kotlin Flow/Channel adapter | 给应用提供更容易处理背压的事件流。 |
| preset validation 缓存 | 避免重复验证同一个 preset。 |
| 示例应用 release APK | 非开发者无需从源码构建也能测试。 |
| 发布 Maven/JitPack release | 应用开发者可依赖已编译产物，不必引入本地源码模块。 |

## 最快推荐 Android 配置

如果应用只需要理解 D06 输入：

1. 只依赖 `d06-core` 和 `d06-input`。
2. 创建一个 `D06Input` 实例并复用。
3. 除非需要，保持 `detectMousepadTap = false`。
4. 让回调里的工作极小。
5. release 版本不启用 diagnostics。
6. 只有收集 bug 报告时使用 `D06InputDiagnostics`。
7. 在真实设备和 release build 上测试真实性能。
