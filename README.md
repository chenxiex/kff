# K翻翻 (Kindle Page Buttons)

为 Android 13+ 的 Kindle 应用提供两个可拖动的悬浮翻页按钮。短按 `‹` / `›` 分别发送系统 D-pad 左/右操作；长按不翻页，拖动任意按钮会移动整个按钮组。

## 构建与安装

使用 JDK 17 和 Android SDK 34，在仓库根目录运行：

```bash
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，包名为 `io.github.chenxiex.kff.debug`，应用和无障碍服务显示为 **Kindle Page Buttons (Debug)**。安装后打开应用，点击“打开无障碍设置”，启用该服务。默认仅在 Kindle（`com.amazon.kindle`）前台显示；设置页可调整按钮大小、背景不透明度、间距、左右键对应关系和显示范围，也可重置位置。

若通过电脑安装，可运行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`；也可以将 APK 复制到设备后直接安装。
Release 的包名保持 `io.github.chenxiex.kff`，可与新 Debug 版本同时安装。两版的设置和无障碍服务互不共享；切换时请在系统无障碍设置中先关闭旧版服务，再启用要使用的版本，避免两个悬浮按钮同时显示或重复翻页。更新同一版本仍需使用与其已安装 APK 相同的签名密钥；更换构建环境后生成的 Debug APK 可能无法覆盖已有的 Debug 安装。

## 发布 GitHub Release

首次发布前，在仓库外的安全位置生成并备份一份长期使用的签名密钥。例如，将下面的密钥文件路径替换为你自己的安全存放位置：

```bash
keytool -genkeypair -keystore /path/to/kff-release.jks -alias kff -keyalg RSA -keysize 3072 -validity 10000
```

在仓库的 GitHub **Settings → Secrets and variables → Actions** 中配置以下四个 Repository secrets：

| Secret | 内容 |
| --- | --- |
| `KFF_KEYSTORE_BASE64` | 密钥文件的单行 Base64 内容；可用 `base64 -w 0 /path/to/kff-release.jks` 生成 |
| `KFF_STORE_PASSWORD` | 密钥库密码 |
| `KFF_KEY_ALIAS` | 密钥别名；上述命令使用 `kff` |
| `KFF_KEY_PASSWORD` | 密钥密码；若与密钥库密码相同，仍需填写 |

Base64 只是编码，不是加密；不要将密钥文件、密码或编码后的内容提交到仓库。密钥丢失或更换后，新 APK 将无法覆盖安装由旧密钥签名的版本。

先确保目标提交已推送到远端 `main`，再从该提交创建并推送 `vX.Y.Z` 标签，例如：

```bash
git push origin main
git tag v1.0.0
git push origin v1.0.0
```

CI 只接受不带前导零或后缀的正式版本标签。它会生成签名 APK，校验包名、版本号和签名，然后创建附有 `kff-vX.Y.Z.apk` 的 GitHub Release。`versionCode` 按 `主版本 × 1,000,000 + 次版本 × 1,000 + 修订版本` 计算；次版本和修订版本必须小于 1000，发布时版本码应高于已安装版本。

旧版 Debug APK 曾使用 Release 的包名 `io.github.chenxiex.kff`。如果设备上仍安装该旧版且签名与正式 APK 不同，首次安装 Release 前需要一次性处理旧安装；卸载会删除它的本地设置并关闭其无障碍服务。请先确认设备上的包名、签名和设置，再决定是否卸载；构建和 CI 不会自动卸载设备应用。此后两版使用不同包名，无需为切换反复卸载。

本应用完全离线，不需要 root、Shizuku、悬浮窗权限或 ADB 常驻。启用无障碍服务后，设备重启无需重新从电脑初始化。
