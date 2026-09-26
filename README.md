# K翻翻 (Kindle Page Buttons)

为 Android 13+ 的 Kindle 应用提供两个可拖动的悬浮翻页按钮。短按 `‹` / `›` 分别发送系统 D-pad 左/右操作；长按不翻页，拖动任意按钮会移动整个按钮组。

## 构建与安装

使用 JDK 17 和 Android SDK 34，在仓库根目录运行：

```bash
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。安装后打开应用，点击“打开无障碍设置”，启用 **Kindle Page Buttons** 服务。默认仅在 Kindle（`com.amazon.kindle`）前台显示；设置页可调整按钮大小、背景不透明度、间距、左右键对应关系和显示范围，也可重置位置。

若通过电脑安装，可运行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`；也可以将 APK 复制到设备后直接安装。
覆盖安装需要使用与旧版相同的签名密钥；更换构建环境后生成的调试 APK 可能无法直接覆盖旧版。

本应用完全离线，不需要 root、Shizuku、悬浮窗权限或 ADB 常驻。启用无障碍服务后，设备重启无需重新从电脑初始化。
