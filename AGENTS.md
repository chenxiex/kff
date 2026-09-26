# AGENTS.md

## 项目概况

- 这是一个使用 Java Android Framework 编写的 Android 应用，不使用 AndroidX。
- 功能现状与回归边界见 [spec.md](spec.md)，构建安装步骤见 [README.md](README.md)。
- 构建环境固定为 JDK 17、Gradle Wrapper 8.7、Android Gradle Plugin 8.5.2、SDK Build Tools 36.0.0；应用的 `minSdk` 为 33，`compileSdk` 和 `targetSdk` 为 34。
- 主要功能由 `PageButtonAccessibilityService` 提供：通过无障碍服务创建 Kindle 翻页悬浮按钮，并通过 `AppSettings` 和 `SharedPreferences` 保存设置。
- 翻页只使用 `performGlobalAction(GLOBAL_ACTION_DPAD_LEFT/RIGHT)`；不使用触摸模拟、shell 按键、root 或 Shizuku。应用完全离线，不申请 `INTERNET` 或普通悬浮窗权限。

## 编码与文档规范

- 使用 4 个空格缩进。
- 生成的文档不要按固定列宽硬换行。
- 仓库级文档保持聚焦于导航和共享约束；与具体代码或主题相关的细节写在相邻文档或对应代码附近。

## 无障碍服务实现要点

- `TYPE_ACCESSIBILITY_OVERLAY` 必须使用默认 `Display` 创建窗口上下文：先通过 `DisplayManager` 获取 `Display.DEFAULT_DISPLAY`，再调用 `createWindowContext(display, TYPE_ACCESSIBILITY_OVERLAY, null)`。直接使用无障碍服务自身的 Context 创建窗口，在 Android 14 设备上会触发 `UnsupportedOperationException`，导致服务反复停止。
- 当前服务配置 `canRetrieveWindowContent=false`，因此前台应用检测只依赖 `TYPE_WINDOW_STATE_CHANGED`。不要在没有相应权限配置和验证的情况下改为依赖窗口列表事件。
- 服务应过滤自身悬浮窗产生的窗口事件；设置 Activity 的窗口事件仍需能够让 Kindle 专属显示模式隐藏悬浮按钮。显示范围、服务启用状态和 Kindle 前台判断的改动应结合设备日志验证。
- 修改服务初始化、窗口添加/移除、配置变化或事件过滤时，优先保持异常保护和 `onDestroy()` 清理路径，避免无障碍服务进入“此服务出现故障”状态。

## 生产设备 ADB 调试

- 设备属于生产环境。先做只读检查，优先使用 `adb logcat`、`adb shell dumpsys`、包管理和无障碍服务状态查询定位问题；不要触发翻页、拖动或其他会改变阅读状态的操作，除非任务明确要求。
- 重点检查应用崩溃日志、无障碍服务绑定状态、窗口/显示信息和当前前台应用。将设备行为与构建产物及本地代码对应起来后再修改实现。
- Release 包名是 `io.github.chenxiex.kff`，Debug 包名是 `io.github.chenxiex.kff.debug`；两版各有独立的设置和无障碍服务，应只启用其中一个服务。旧版 Debug 曾使用 Release 包名。调试 APK 如果使用了不同签名密钥，不能覆盖安装同包名的已有版本。需要安装新签名版本时，先确认目标包名与签名，并向用户说明卸载会关闭该包的无障碍服务且可能影响其本地状态；不要擅自清除数据或卸载应用。
- 安装或重装后重新检查服务是否绑定、应用是否仍崩溃、Kindle 前台时按钮是否出现以及离开 Kindle 后是否隐藏。设备验证不能仅由 `assembleDebug` 构建成功替代。

## 常用验证

在仓库根目录使用 JDK 17 构建 Debug APK：

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。修改 Java 或无障碍配置后，至少运行构建并检查 `git diff --check`；涉及设备行为时，再按上面的只读 ADB 流程验证。
