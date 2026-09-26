# Kindle Page Buttons — 当前实现规格

更新日期：2026-09-26。本文描述第一版已经交付的行为与约束；构建、安装和日常使用步骤见 [README.md](README.md)。

## 目标与范围

本应用为没有实体翻页键的 Android 电纸书设备，在 Kindle Android 阅读页面提供两个可拖动的悬浮按钮。主要设备为汉王 Clear6 Turbo（Android 14、约 1072 × 1448），Kindle 包名为 com.amazon.kindle。

短按按钮通过 AccessibilityService 的 GLOBAL_ACTION_DPAD_LEFT / GLOBAL_ACTION_DPAD_RIGHT 翻页。目标设备此前已验证系统 D-pad 操作可在 Kindle 中无滑动动画翻页。应用不模拟触摸点击，不使用 shell 注入、root、Shizuku 或常驻 ADB；设备重启后由系统管理已启用的无障碍服务。

第一版是离线的专用工具，不包含其他阅读器配置、脚本、手势自动化、账号、网络同步或遥测。
空闲时只等待系统事件，不持有 WakeLock，不使用周期性轮询、Timer、Handler 循环、WorkManager 或前台服务。

## 平台与工程

| 项目 | 当前实现 |
| --- | --- |
| 应用 ID | Release：io.github.chenxiex.kff；Debug：io.github.chenxiex.kff.debug |
| 语言与 UI | Java、Android Framework 原生 View；单个 MainActivity |
| 后台组件 | 单个 PageButtonAccessibilityService；无额外 Service、Receiver、轮询或前台服务 |
| SDK | minSdk 33、compileSdk 34、targetSdk 34 |
| 构建 | JDK 17、Gradle Wrapper 8.7、Android Gradle Plugin 8.5.2、SDK Build Tools 36.0.0 |
| 依赖 | 无 Kotlin、AndroidX、第三方 UI 或持久化库 |
| Release 配置 | 目前未启用代码压缩或资源裁剪；无需 R8 keep 规则 |
| 数据 | SharedPreferences 保存本地设置；Manifest 禁止应用数据备份 |

Manifest 不申请 INTERNET、SYSTEM_ALERT_WINDOW 或其他 uses-permission。无障碍服务以 BIND_ACCESSIBILITY_SERVICE 保护，使用 TYPE_ACCESSIBILITY_OVERLAY 创建窗口，配置 canRetrieveWindowContent=false，不读取 Kindle 正文或节点树，也不使用 dispatchGesture。

Debug 与 Release 可同时安装。中文环境下，应用和无障碍服务名称分别为 K翻翻（调试版）与 K翻翻；其他语言环境下分别为 Kindle Page Buttons (Debug) 与 Kindle Page Buttons。两版各自保存设置并有独立的无障碍服务；使用时只启用其中一个服务。旧版 Debug 曾使用 Release 包名，首次改用不同签名的正式 APK 时可能仍需一次性迁移旧安装。

## 无障碍服务与显示范围

服务只订阅 TYPE_WINDOW_STATE_CHANGED。未授予读取窗口内容能力时，TYPE_WINDOWS_CHANGED 及 getWindows() 不能作为前台识别依据。服务以窗口状态事件的 packageName 判断最近活动窗口；忽略自身悬浮窗事件，但本应用 MainActivity 的事件会使“仅 Kindle”模式隐藏按钮。

默认仅在 com.amazon.kindle 的窗口状态事件到来后显示按钮，切到设置页、Home 或其他应用时隐藏。“所有应用”模式下，服务连接后即可显示。服务刚连接而尚未收到可判断的窗口事件时，“仅 Kindle”模式保持隐藏。这里的 packageName 是事件来源，不是独立的前台应用查询；系统弹窗等场景需要在目标设备回归。

Android 14 的 Service Context 本身不关联 Display。服务从 DisplayManager 获取默认显示器，并调用 createWindowContext(Display, TYPE_ACCESSIBILITY_OVERLAY, null)，再从该窗口 Context 获取 WindowManager 和创建 View。直接调用不带 Display 的 createWindowContext(type, null) 曾在目标设备上导致 UnsupportedOperationException，使服务和同进程 Activity 反复崩溃。初始化异常由服务捕获并在 Debug 构建中记录，不再让整个进程因该步骤崩溃。

窗口参数为 TYPE_ACCESSIBILITY_OVERLAY、FLAG_NOT_FOCUSABLE、PixelFormat.TRANSLUCENT，gravity 为 TOP | LEFT。窗口只占按钮组的矩形范围，宽度为一个按钮，高度为两个按钮加间距；不创建全屏透明窗口，也不申请普通悬浮窗权限。服务保留同一个 View，在需要显示时附加窗口、隐藏时移除，并在销毁时清理。

## 按钮与触摸行为

两个按钮显示为纵向排列的 ‹ 和 ›，分别代表上一页和下一页。背景完全透明，文字与边框为黑色，无 ripple、渐变、阴影、动画或复杂图标。按钮不透明度同时作用于文字与边框，背景始终透明。

| 操作 | 行为 |
| --- | --- |
| 有效短按 | 只在 ACTION_UP 执行一次系统 D-pad Global Action；默认 ‹ 向左、› 向右 |
| 长按但未拖动 | ACTION_UP 不执行翻页；长按期间也不重复触发 |
| 移动超过系统 touch slop | 立即拖动整个按钮组；松手保存位置，不翻页 |
| 长按后再移动 | 仍可进入拖动状态，不翻页 |
| 多指触摸或取消 | 不翻页；若已拖动，保存当前位置 |

移动阈值来自 ViewConfiguration.getScaledTouchSlop()，长按阈值来自 ViewConfiguration.getLongPressTimeout()。一次有效短按对应一次 Global Action，不添加人为 debounce。启用“交换上一页 / 下一页”后，两个按钮对应的左右 D-pad Action 互换。performGlobalAction() 返回 false 时保持应用可用，Debug 构建记录警告，不弹 Toast。

## 位置、边界与设置

两个按钮始终作为整体移动。拖动过程实时更新窗口坐标；松手通过 SharedPreferences 保存归一化 xFraction / yFraction。坐标以当前安全区域内的最大可移动距离为基准，随分辨率、DPI、按钮尺寸或方向变化重新计算。WindowMetrics 的边界减去系统栏和 Display Cutout 的 Insets，最终坐标限制在安全区域内。位置重置回安全区域右侧、纵向约 60% 处。

MainActivity 使用 ScrollView、LinearLayout 和原生控件，提供系统无障碍设置入口，以及以下设置：

| 设置 | 范围与默认值 |
| --- | --- |
| 按钮大小 | 36–96dp，默认 56dp |
| 按钮不透明度 | 20%–100%，默认 70% |
| 按钮间距 | 0–32dp，默认 4dp |
| 左右键交换 | 默认关闭 |
| 显示范围 | 默认仅 Kindle，可选所有应用 |
| 重置位置 | 恢复默认归一化位置 |

大小、透明度和间距修改后，已运行的服务通过 SharedPreferences 监听器立即更新 Overlay。设置页显示系统报告的无障碍服务启用状态，并通过 Settings.ACTION_ACCESSIBILITY_SETTINGS 打开系统设置；“已启用”仅表示系统配置已开启，不保证服务已成功绑定或悬浮窗已创建。

## 构建与验证

在仓库根目录执行：

~~~bash
./gradlew assembleDebug
~~~

输出为 app/build/outputs/apk/debug/app-debug.apk。2026-09-26 的构建已通过，安装包已通过签名校验；APK 未声明应用权限。

目标设备上的 ADB 验证结果：

- 修复版服务在系统中显示为 Bound services，Crashed services 为空；启用服务后 MainActivity 可正常打开。
- Kindle 前台时 TYPE_ACCESSIBILITY_OVERLAY 窗口存在且可见，尺寸约 126 × 261 像素；切到本应用设置页后窗口消失，返回 Kindle 后重新显示。
- 调试期间为避免改变用户当前阅读位置，没有通过 ADB 点击按钮或拖动；短按、长按、拖动、正常 Kindle 触控、旋转及重启后的持久化属于后续设备回归项目。

覆盖安装同一包名必须使用与设备上旧版相同的签名密钥。不同构建环境的默认 Debug 密钥可能不同；此前一次修复部署因签名不一致，在用户明确授权且确认应用没有保存设置后，才卸载并重装目标应用。上述设备验证发生在 Debug 包名增加 `.debug` 后缀之前；两版并存及服务切换尚需设备回归。

## 后续改动的验收边界

改动触摸逻辑时，验证快速短按只翻一页、静止长按不翻页、拖动或长按后拖动不翻页，并确认按钮组位置在 Kindle 重开、服务重连及设备重启后保持。改动窗口或前台识别时，验证 Kindle 内显示、其他应用内隐藏、按钮外的正文点击/选词/滑动不受影响，以及旋转后按钮不会离开可操作区域。改动构建或签名时，先确认能够安装且不会意外清除设备数据。
