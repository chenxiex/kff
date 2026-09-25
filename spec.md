# Kindle Page Buttons

## 1. 项目目标

开发一个极轻量的 Android 应用，用于在没有实体音量键/翻页键的 Android 电纸书设备上，为 Kindle Android App 提供两个可拖动的悬浮翻页按钮。

目标设备：

- 汉王 Clear6 Turbo
- Android 14 / API 34
- 屏幕分辨率约为 1072 × 1448
- 主要目标应用：
  - Kindle Android
  - package: `com.amazon.kindle`

核心需求：

- 屏幕上显示“上一页”和“下一页”两个悬浮按钮。
- 点击按钮时，不模拟触摸屏点击。
- 必须通过 Android AccessibilityService 的系统 D-pad Global Action 实现翻页：
  - 上一页：`GLOBAL_ACTION_DPAD_LEFT`
  - 下一页：`GLOBAL_ACTION_DPAD_RIGHT`
- 用户已经确认：
  - `adb shell input keyevent 21`
  - `adb shell input keyevent 22`
  - `adb shell cmd accessibility call-system-action 18`
  - `adb shell cmd accessibility call-system-action 19`
  均可以在 Kindle 中正常、无动画翻页。
- 不使用 Shizuku。
- 不使用 root。
- 不依赖 ADB 常驻。
- 设备重启后无需重新从电脑初始化。
- 尽可能小、简单、低功耗。

这是一个专用小工具，而不是通用自动化框架。

---

# 2. 技术栈

优先使用最简单、依赖最少的 Android 原生方案。

要求：

- Java
- Android Framework API
- Gradle
- Gradle Wrapper
- 不使用 Kotlin
- 不使用 Jetpack Compose
- 不使用 AndroidX，除非确实无法避免
- 不使用 AppCompat
- 不使用 Material Components
- 不使用第三方 UI 库
- 不使用第三方持久化库
- 不使用任何网络库
- 不使用后台常驻 foreground service

建议：

- `minSdk 33`
- `targetSdk 34` 或更高
- `compileSdk 34` 或更高

之所以要求 API 33+，是因为：

```java
AccessibilityService.GLOBAL_ACTION_DPAD_LEFT
AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT
```

从 API 33 开始提供。

开发环境已经通过 Dev Container 准备好。

---

# 3. 总体架构

应用仅需要两个主要组件：

```text
MainActivity
    │
    ├── 显示当前 Accessibility Service 状态
    ├── 引导开启 Accessibility Service
    ├── 配置悬浮按钮
    └── 保存用户设置

PageButtonAccessibilityService
    │
    ├── 创建 Accessibility Overlay
    ├── 显示两个翻页按钮
    ├── 处理短按、长按和拖动
    ├── 监测当前前台应用
    └── 执行 D-pad Global Action
```

不要增加不必要的 Service、BroadcastReceiver 或后台任务。

---

# 4. Accessibility Service

实现：

```java
public class PageButtonAccessibilityService
        extends AccessibilityService
```

上一页：

```java
performGlobalAction(
    AccessibilityService.GLOBAL_ACTION_DPAD_LEFT
);
```

下一页：

```java
performGlobalAction(
    AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT
);
```

禁止使用以下方案实现翻页：

```text
dispatchGesture()
模拟屏幕点击
input keyevent shell command
Shizuku
root
Instrumentation
INJECT_EVENTS
```

因为 Global Action 已经在目标设备上验证可用。

---

# 5. Accessibility 权限最小化

应用不需要读取 Kindle 正文，也不需要分析 Accessibility 节点树。

因此：

```xml
android:canRetrieveWindowContent="false"
```

除非实现过程中 Android 系统强制要求，否则不要申请读取窗口内容。

AccessibilityService 只需要：

1. 创建 Accessibility Overlay。
2. 获取前台应用 package name。
3. 调用 `performGlobalAction()`。

尽量只监听与前台应用切换相关的事件，例如：

```text
TYPE_WINDOW_STATE_CHANGED
TYPE_WINDOWS_CHANGED
```

不要监听所有 AccessibilityEvent。

---

# 6. Overlay 实现

悬浮按钮应由 AccessibilityService 创建。

优先使用：

```java
WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
```

不要使用：

```text
TYPE_APPLICATION_OVERLAY
SYSTEM_ALERT_WINDOW
ACTION_MANAGE_OVERLAY_PERMISSION
```

除非目标 ROM 上 `TYPE_ACCESSIBILITY_OVERLAY` 无法工作。

目标是：

> 用户只需要开启 Accessibility Service，不需要再额外授予“显示在其他应用上层”权限。

---

# 7. Overlay 内容

Overlay 默认显示两个按钮：

```text
‹
›
```

默认采用垂直排列。

示意：

```text
Kindle 页面

                       ┌────┐
                       │ ‹  │
                       ├────┤
                       │ ›  │
                       └────┘
```

两个按钮默认放在屏幕右侧、中部稍偏下的位置，方便右手拇指操作。

不要使用复杂图标。

推荐直接使用 Unicode：

```text
‹
›
```

或者：

```text
<
>
```

无需 SVG、PNG 或 Material Icon。

---

# 8. 电纸书 UI 原则

这是 E-Ink 设备。

UI 必须尽量避免：

- ripple 动画
- fade 动画
- scale 动画
- 阴影动画
- 渐变
- 模糊
- 半透明动画
- Material 动效

按钮状态变化应尽可能瞬时。

默认视觉风格：

```text
纯色背景
纯色文字
简单边框
无阴影
无动画
```

透明度本身可配置，但改变透明度时无需动画。

---

# 9. 按压行为

按钮需要区分三种操作：

```text
短按
长按
拖动
```

行为定义：

```text
短按 → 翻页
长按 → 不执行任何操作
拖动 → 移动整个按钮组
```

只有“未进入拖动状态，并且按压时间短于系统长按阈值”的操作才被视为短按并触发翻页。

上一页按钮短按：

```java
performGlobalAction(GLOBAL_ACTION_DPAD_LEFT);
```

下一页按钮短按：

```java
performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT);
```

长按不执行任何翻页操作。

不要在：

```text
ACTION_DOWN
ACTION_MOVE
```

时触发翻页。

翻页判断只能在 `ACTION_UP` 时完成。

使用系统长按阈值：

```java
ViewConfiguration.getLongPressTimeout()
```

不要自己硬编码诸如 `500ms` 的长按时间。

推荐判断逻辑：

```text
ACTION_DOWN
    记录 downTime

ACTION_UP
    如果发生过 dragging
        不翻页
    否则如果 elapsedTime < longPressTimeout
        执行一次翻页
    否则
        什么也不做
```

一次短按只能产生一次翻页。

---

# 10. 拖动按钮

这是重要需求。

用户必须可以直接拖动悬浮按钮，以便按钮遮挡正文时临时移走。

要求：

- 不需要打开设置页面。
- 直接拖住按钮即可移动。
- 移动过程实时跟随手指。
- 松手后停留在当前位置。
- 新位置立即保存。
- App/Accessibility Service 重启后恢复最后位置。
- 设备重启后恢复最后位置。

需要正确区分：

```text
短按
长按
拖动
```

使用移动阈值：

```java
ViewConfiguration.getScaledTouchSlop()
```

使用长按阈值：

```java
ViewConfiguration.getLongPressTimeout()
```

推荐逻辑：

```text
ACTION_DOWN
    记录：
        初始触摸位置
        Overlay 初始位置
        downTime
    dragging = false

ACTION_MOVE
    计算手指相对于 ACTION_DOWN 的移动距离

    如果移动距离 > touchSlop
        dragging = true

    如果 dragging
        实时更新 Overlay 坐标

ACTION_UP
    计算 elapsedTime

    如果 dragging
        保存新位置
        不翻页

    否则如果 elapsedTime < longPressTimeout
        执行对应的翻页操作

    否则
        视为长按
        不执行任何操作
```

注意：

- 用户可以先长按一段时间，然后开始移动。
- 一旦移动距离超过 `touchSlop`，仍应进入拖动状态。
- 长按本身不应该阻止后续拖动。
- 不需要实现 `OnLongClickListener`，只需要根据按压持续时间决定 `ACTION_UP` 时是否执行翻页即可。
- 不要自己硬编码例如 `5px` 的移动阈值或固定毫秒数的长按阈值。

---

# 11. 两个按钮的移动方式

默认把两个按钮视作一个整体。

也就是说：

```text
┌────┐
│ ‹  │
├────┤
│ ›  │
└────┘
```

用户拖动其中任意一个按钮时，整个按钮组一起移动。

原因：

- 操作更简单。
- 两个按钮不会彼此分散。
- 设置项更少。
- 更适合单手使用。

第一版只需实现“整体拖动”。

不需要分别移动两个按钮。

---

# 12. Overlay 边界

按钮不能被拖到完全离开屏幕。

松手后至少保证整个按钮组仍位于可操作区域。

需要考虑：

- 状态栏
- 导航栏
- Display Cutout
- 系统 Insets

如果实现 Insets 会显著增加复杂度，则最低要求是：

```text
x >= 0
y >= 0
x + overlayWidth <= screenWidth
y + overlayHeight <= screenHeight
```

禁止让按钮因为一次误操作永久消失在屏幕之外。

---

# 13. 坐标保存

不要只保存针对 1072 × 1448 的绝对像素。

推荐保存归一化位置，例如：

```text
xFraction
yFraction
```

范围：

```text
0.0 ~ 1.0
```

例如：

```text
xFraction = 0.92
yFraction = 0.60
```

创建 Overlay 时：

```text
x = availableWidth * xFraction
y = availableHeight * yFraction
```

这样可以更好地适配：

- 分辨率变化
- DPI 变化
- 横竖屏变化

---

# 14. 设置界面

MainActivity 提供一个非常简单的设置页面。

不使用复杂 UI 框架。

建议使用：

```text
ScrollView
    LinearLayout
```

里面直接创建原生 View。

---

# 15. 必须提供的设置

## 15.1 Accessibility Service 状态

显示：

```text
Accessibility Service

状态：已启用
```

或者：

```text
状态：未启用

[开启无障碍服务]
```

点击后跳转到系统 Accessibility Settings：

```java
Settings.ACTION_ACCESSIBILITY_SETTINGS
```

因为汉王隐藏了完整 Android Settings，所以应用必须自己提供这个跳转入口。

---

## 15.2 按钮大小

提供一个滑块。

例如：

```text
按钮大小

小 ─────●───── 大
```

建议范围：

```text
36dp ~ 96dp
```

默认：

```text
56dp
```

该值控制按钮宽度和高度。

修改后 Overlay 应立即更新。

保存到：

```java
SharedPreferences
```

---

## 15.3 透明度

提供一个滑块：

```text
透明度

20% ─────●───── 100%
```

建议范围：

```text
20% ~ 100%
```

默认：

```text
70%
```

这里的透明度主要影响按钮背景。

文字应保持足够可读。

如果实现更简单，可以设置整个按钮组的 alpha。

---

## 15.4 按钮间距

提供：

```text
按钮间距

0dp ─────●───── 32dp
```

默认：

```text
4dp
```

这样用户可以让两个按钮紧贴，也可以稍微分开。

---

## 15.5 左右键交换

提供：

```text
[ ] 交换上一页 / 下一页
```

默认：

```text
上一页 = DPAD_LEFT
下一页 = DPAD_RIGHT
```

开启后：

```text
上一页 = DPAD_RIGHT
下一页 = DPAD_LEFT
```

---

# 16. 显示范围

提供：

```text
按钮显示：

(*) 仅 Kindle
( ) 所有应用
```

默认：

```text
仅 Kindle
```

Kindle package：

```text
com.amazon.kindle
```

“所有应用”主要用于：

- 调试
- 未来兼容其他阅读器

---

# 17. 重置位置

提供按钮：

```text
[重置按钮位置]
```

恢复默认：

```text
屏幕右侧
垂直位置约为屏幕高度 60%
```

这是必要的容错措施。

---

# 18. 不需要的设置

不要为了“功能完整”增加下面这些：

- 自定义脚本
- 自定义 Shell command
- 自定义 package rule
- Tasker 集成
- MacroDroid 集成
- Shizuku
- root
- 自定义 gesture
- 自动点击
- 网络同步
- 云备份
- 账号
- 日志上传
- analytics
- crash telemetry
- 广告
- Firebase

这是一个专用、离线工具。

---

# 19. Kindle 前台检测

默认只在 Kindle 前台时显示 Overlay。

目标包：

```text
com.amazon.kindle
```

AccessibilityService 收到窗口切换事件后：

```java
CharSequence packageName = event.getPackageName();
```

如果：

```text
packageName == com.amazon.kindle
```

则：

```text
showOverlay()
```

否则：

```text
hideOverlay()
```

不要每次 AccessibilityEvent 都销毁并重建 Overlay。

Overlay 应尽量创建一次，然后控制显示状态。

---

# 20. 悬浮按钮与 Kindle 交互

Overlay 自己只能占用按钮实际大小的区域。

禁止创建一个覆盖整个屏幕的透明 Window。

错误：

```text
全屏透明 Overlay
   ├── ‹
   └── ›
```

因为这可能拦截 Kindle 的正常触摸。

正确：

```text
Window 大小 == 按钮组大小
```

这样除了按钮所在的小区域之外，Kindle 应继续正常接收：

- 单击
- 长按
- 滑动
- 选择文本
- 打开菜单

---

# 21. WindowManager 参数

推荐：

```java
new WindowManager.LayoutParams(
    WRAP_CONTENT,
    WRAP_CONTENT,
    TYPE_ACCESSIBILITY_OVERLAY,
    FLAG_NOT_FOCUSABLE,
    PixelFormat.TRANSLUCENT
);
```

Overlay：

- 不获得键盘焦点
- 不影响 Kindle 输入焦点
- 仅按钮区域可触摸

不要设置会导致整个屏幕拦截触摸的 flags。

---

# 22. 持久化

使用：

```java
SharedPreferences
```

即可。

不需要：

- SQLite
- Room
- DataStore

至少保存：

```text
buttonSizeDp
opacity
spacingDp
xFraction
yFraction
swapButtons
showOnlyInKindle
```

建议定义一个简单的 `AppSettings` 类集中管理 key。

不要在不同文件里到处写字符串常量。

---

# 23. Accessibility Service 生命周期

需要正确处理：

```java
onServiceConnected()
onAccessibilityEvent()
onInterrupt()
onDestroy()
```

在 Service 结束时必须：

```java
windowManager.removeView(...)
```

防止 Window 泄漏。

需要防止 Overlay 重复创建。

例如维护：

```java
private View overlayView;
```

只有：

```java
overlayView == null
```

时才创建。

---

# 24. MainActivity 与 Overlay 设置同步

如果用户在 MainActivity 中修改：

```text
大小
透明度
间距
```

已经运行的 AccessibilityService 应尽可能立即反映变化。

可以使用：

```text
SharedPreferences.OnSharedPreferenceChangeListener
```

Service 监听偏好变化后调用：

```text
updateOverlayAppearance()
updateOverlayLayout()
```

不需要引入 LiveData、Flow 或 Binder。

---

# 25. 默认值

建议默认设置：

```text
按钮大小：56dp
透明度：70%
按钮间距：4dp

位置：
右侧
大约屏幕高度 60%

显示：
仅 Kindle

上一页：
GLOBAL_ACTION_DPAD_LEFT

下一页：
GLOBAL_ACTION_DPAD_RIGHT
```

按钮整体建议：

```text
背景：白色或浅灰
文字：黑色
边框：黑色
```

优先适配黑白 E-Ink。

---

# 26. 可拖动反馈

拖动过程中不要添加动画。

第一版可以完全没有额外视觉反馈。

最重要的是：

```text
拖动流畅
不误触翻页
```

长按本身无需提供额外视觉反馈。

---

# 27. 短按、长按与拖动冲突

这是重点测试项。

### 拖动

以下操作：

```text
按住
移动 100px
松手
```

必须只移动按钮：

```text
不得翻页
```

无论开始移动前已经按住多久，只要移动距离超过：

```java
ViewConfiguration.getScaledTouchSlop()
```

就应视为拖动。

---

### 短按

以下操作：

```text
快速按下
基本没有移动
松手
```

必须：

```text
翻一页
```

要求：

```text
移动距离 <= touchSlop
并且
按压时间 < longPressTimeout
```

---

### 长按

以下操作：

```text
按住超过系统长按阈值
不明显移动
松手
```

必须：

```text
什么也不做
不得翻页
```

长按阈值使用：

```java
ViewConfiguration.getLongPressTimeout()
```

不要自己硬编码持续时间。

---

### 长按后拖动

以下操作：

```text
长按超过 longPressTimeout
随后拖动按钮
松手
```

必须：

```text
正常移动按钮并保存新位置
不得翻页
```

也就是说，长按不是一个会锁死操作状态的独立模式。

真正的优先级应为：

```text
发生有效拖动
→ 拖动

否则按压时间达到长按阈值
→ 长按，不执行操作

否则
→ 短按，翻页
```

---

# 28. 连续点击

用户可能快速连续翻页：

```text
›
›
›
›
```

每次有效短按都应该产生一次：

```java
performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT)
```

不要人为加入较大的 debounce。

优先完全不加 debounce。

长按不应产生任何重复翻页。

---

# 29. 屏幕旋转

应用至少不能因为旋转崩溃。

如果检测到 display size changed，应重新根据归一化坐标计算 Overlay 位置。

代码中不得硬编码：

```text
1072
1448
```

目标设备当前使用这个分辨率，但实现应依赖实际显示尺寸。

---

# 30. 电源与后台行为

这个应用不应：

- 持有 WakeLock
- 阻止设备休眠
- 周期性轮询
- 创建 Timer
- 创建 Handler 循环
- 使用 WorkManager
- 启动 foreground service

AccessibilityService 处于启用状态时由 Android 系统管理。

目标是：

```text
无操作时几乎没有 CPU 活动
```

这对电纸书尤其重要。

注意：

实现长按判断不应通过持续运行的 Timer 或轮询完成。

推荐仅在：

```text
ACTION_DOWN
ACTION_UP
```

之间记录时间戳并计算持续时间。

---

# 31. 网络与隐私

Manifest 中不得申请：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

应用完全离线。

也不应申请：

```text
存储
相机
麦克风
位置
蓝牙
联系人
通知读取
```

除 Accessibility Service 所必需的能力之外，不申请其他敏感权限。

---

# 32. APK 体积

目标：

```text
尽可能小
```

由于项目：

```text
Java
Android Framework only
无 AndroidX
无第三方库
```

Release APK 应保持很小。

可以启用：

```text
minifyEnabled true
shrinkResources true
```

前提是不会影响 AccessibilityService 正常工作。

需要添加必要的 R8 keep rule，确保 Service 不被错误删除。

不要为了减少几十 KB 做危险的优化。

---

# 33. 应用图标

不要求复杂设计。

可以使用一个简单的矢量 drawable，例如：

```text
‹ ›
```

或者：

```text
⇄
```

不需要 PNG asset。

---

# 34. MainActivity UI 示例

MainActivity 大致可以是：

```text
Kindle Page Buttons

Accessibility Service
状态：已启用

[打开无障碍设置]

────────────────────

按钮大小
[──────●────]

透明度
[────●──────]

按钮间距
[──●────────]

[ ] 交换上一页 / 下一页

按钮显示
(*) 仅 Kindle
( ) 所有应用

[重置按钮位置]

────────────────────

操作：
短按 ‹ / › 翻页。
拖动悬浮按钮可移动位置。
长按按钮不会翻页。
```

不要制作复杂导航结构。

整个 App 只需要一个 Activity。

---

# 35. 文件结构建议

保持项目尽可能简单：

```text
app/
├── build.gradle
├── proguard-rules.pro
│
└── src/main/
    ├── AndroidManifest.xml
    │
    ├── java/.../
    │   ├── MainActivity.java
    │   ├── PageButtonAccessibilityService.java
    │   └── AppSettings.java
    │
    └── res/
        ├── drawable/
        │   └── overlay_button_background.xml
        │
        ├── values/
        │   ├── strings.xml
        │   ├── colors.xml
        │   └── styles.xml
        │
        └── xml/
            └── accessibility_service_config.xml

gradle/
└── wrapper/

build.gradle
settings.gradle
gradlew
gradlew.bat
```

如果某个文件没有实际必要，可以进一步删除。

---

# 36. Manifest

必须注册：

```xml
<service
    android:name=".PageButtonAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
```

并添加：

```xml
<intent-filter>
    <action android:name="android.accessibilityservice.AccessibilityService" />
</intent-filter>

<meta-data
    android:name="android.accessibilityservice"
    android:resource="@xml/accessibility_service_config" />
```

不要申请 Overlay permission。

---

# 37. Accessibility 配置

`accessibility_service_config.xml` 应尽量最小。

大致：

```xml
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"

    android:accessibilityEventTypes="
        typeWindowStateChanged|typeWindowsChanged"

    android:accessibilityFeedbackType="feedbackGeneric"

    android:notificationTimeout="100"

    android:canRetrieveWindowContent="false"

    android:description="@string/accessibility_service_description" />
```

如果某个属性会妨碍正常获取前台 package，则以实际 Android 14 行为为准进行最小调整。

---

# 38. 无障碍描述

Accessibility Service 描述必须清楚说明用途。

例如：

```text
显示可移动的翻页悬浮按钮，并在短按时发送系统方向键左/右操作。

长按按钮不会翻页；拖动按钮可以调整悬浮位置。

本服务不会读取或保存屏幕内容。
```

---

# 39. 错误处理

如果：

```java
performGlobalAction(...)
```

返回：

```java
false
```

不要 crash。

Debug build 可以：

```java
Log.w(...)
```

Release build 不需要弹 Toast。

翻页失败时保持按钮正常可用。

---

# 40. 日志

只保留基本 Debug 日志。

不要持续记录 AccessibilityEvent。

特别禁止类似：

```java
Log.d(TAG, event.toString());
```

因为：

- 没必要
- 会产生大量日志
- 可能包含其他应用信息
- 对 E-Ink 小工具没有价值

---

# 41. 测试目标

主要测试设备：

```text
Hanvon Clear6 Turbo
Android 14
1072 × 1448
```

主要目标 App：

```text
Amazon Kindle
com.amazon.kindle
```

---

# 42. 功能验收标准

## 翻页

进入 Kindle 阅读页面。

短按：

```text
›
```

必须：

```text
下一页
```

短按：

```text
‹
```

必须：

```text
上一页
```

并且 Kindle 不出现触摸滑动翻页动画。

---

## 长按

按住任意按钮超过系统：

```java
ViewConfiguration.getLongPressTimeout()
```

且没有发生有效拖动。

松手后：

```text
不得翻页
不得执行其他操作
```

长按期间也不得重复触发翻页。

---

## 拖动

拖住按钮组移动。

必须：

```text
实时跟手移动
```

松手：

```text
停留在新位置
```

不得同时触发翻页。

即使用户先长按一段时间再开始移动，也必须能够正常拖动。

---

## 持久化

移动按钮后：

```text
关闭 Kindle
重新打开 Kindle
```

按钮位置不变。

重启设备后：

```text
启动 Kindle
```

按钮仍恢复到上次的位置。

---

## 大小

修改按钮大小后：

```text
Overlay 立即变化
```

重启后保持。

---

## 透明度

修改透明度后：

```text
Overlay 立即变化
```

重启后保持。

---

## Kindle 外隐藏

默认模式下：

```text
Kindle 前台
→ 显示按钮

Home / 其他 App 前台
→ 隐藏按钮

重新进入 Kindle
→ 恢复按钮
```

---

## 正常 Kindle 触控

Overlay 以外的屏幕区域必须保持完全正常。

例如：

```text
点击正文
长按选词
滑动
打开菜单
添加书签
```

均不能受到影响。

---

# 43. 性能验收

当用户没有触摸按钮时：

```text
不应持续执行任务
不应周期性唤醒 CPU
不应 polling
```

AccessibilityEvent 到来时才处理逻辑。

长按判断不得依赖持续轮询。

内存占用应保持很低。

---

# 44. 第一版优先级

P0，必须实现：

```text
AccessibilityService
TYPE_ACCESSIBILITY_OVERLAY
上一页按钮
下一页按钮
GLOBAL_ACTION_DPAD_LEFT
GLOBAL_ACTION_DPAD_RIGHT
短按翻页
长按不翻页
按钮组拖动
位置持久化
大小设置
透明度设置
仅 Kindle 显示
Accessibility Settings 跳转
```

P1，建议实现：

```text
按钮间距设置
左右键交换
重置位置
所有应用显示模式
归一化位置
```

P2，第一版不要实现：

```text
独立拖动两个按钮
自定义按钮图标
主题系统
横向排列
其他阅读器 profile
自动应用识别
导入导出配置
```

---

# 45. 实现原则

如果某个设计存在以下两个选择：

```text
A. 增加框架/依赖，使代码更“现代”
B. 使用简单 Android Framework API，实现同样功能
```

选择 B。

如果存在：

```text
A. 增加一个抽象层，未来可能扩展
B. 直接实现当前需求
```

选择 B。

项目目标不是建立一个通用按键映射平台，而是：

> 做一个可靠、轻量、低功耗、适合 Android E-Ink Kindle 阅读的双悬浮翻页按钮。

优先考虑：

```text
简单
可靠
低功耗
低依赖
小 APK
容易维护
```

而不是：

```text
通用性
复杂架构
高度抽象
未来可能用到的功能
```

---

# 46. 最终交付

Agent 完成后至少应保证：

```bash
./gradlew assembleDebug
```

能够在现有开发环境中成功执行。

并产生可安装 APK。

同时在 README 中简短说明：

1. 如何构建 APK。
2. APK 输出位置。
3. 安装后如何开启 Accessibility Service。
4. 如何使用短按、长按和拖动翻页按钮。
5. 不需要 root、Shizuku 或 ADB 常驻。
