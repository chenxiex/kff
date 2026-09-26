# Kindle Page Buttons

[English](README.md) | [简体中文](README.zh-CN.md)

Kindle Page Buttons provides two draggable page-turning buttons for the Kindle app on Android 13 and later. Briefly tap `‹` or `›` to send the system D-pad left or right action. A long press does not turn the page; dragging either button moves the entire button group.

## Build and install

With JDK 17 and Android SDK 34 installed, run this from the repository root:

```bash
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`, with package name `io.github.chenxiex.kff.debug`. After installing, open the app, tap **Open accessibility settings**, and enable the service. By default, the buttons appear only while Kindle (`com.amazon.kindle`) is in the foreground. In settings, you can adjust button width and height (as percentages of the available screen), opacity, spacing, borderless mode, left/right key mapping, and display range. You can also reset the button position.

To install from a computer, run `adb install -r app/build/outputs/apk/debug/app-debug.apk`. You can also copy the APK to the device and install it there. The Release package name is `io.github.chenxiex.kff`, so it can be installed alongside the Debug version. The two versions have separate settings and accessibility services. When switching versions, disable the old service in Android accessibility settings before enabling the one you want, to avoid duplicate buttons or page turns.

## Get a CI Debug APK

Whenever a branch is pushed or a pull request is updated, the **Debug APK** GitHub Actions workflow builds and verifies a Debug package. On the corresponding workflow run page, download `kff-debug-*` under **Artifacts** and extract `app-debug.apk`.

CI uses Android's default Debug signing key. Signatures may differ between build environments, so a downloaded APK may not install over an existing Debug version. Check the installed app's package name and signature before installing. Uninstalling the existing app deletes its local settings and disables its accessibility service.

## Publish a GitHub Release

Before the first release, generate and back up a signing key in a secure location outside the repository. Replace the example key file path with your own secure location:

```bash
keytool -genkeypair -keystore /path/to/kff-release.jks -alias kff -keyalg RSA -keysize 3072 -validity 10000
```

In the repository's GitHub **Settings → Secrets and variables → Actions**, configure these four repository secrets:

| Secret | Value |
| --- | --- |
| `KFF_KEYSTORE_BASE64` | The keystore file encoded as a single-line Base64 string; generate it with `base64 -w 0 /path/to/kff-release.jks` |
| `KFF_STORE_PASSWORD` | The keystore password |
| `KFF_KEY_ALIAS` | The key alias; the command above uses `kff` |
| `KFF_KEY_PASSWORD` | The key password; provide it even if it is the same as the keystore password |

First make sure the target commit has been pushed to the remote `main` branch. Then create and push a `vX.Y.Z` tag from that commit, for example:

```bash
git push origin main
git tag v1.0.0
git push origin v1.0.0
```

CI accepts only stable version tags without leading zeroes or suffixes. It builds a signed APK, verifies its package name, version, and signature, then creates a GitHub Release with `kff-vX.Y.Z.apk` attached. The `versionCode` is calculated as `major × 1,000,000 + minor × 1,000 + patch`; the minor and patch values must be below 1000, and each release's version code must be higher than the installed version.

The app works entirely offline and requires no root, Shizuku, overlay permission, or persistent ADB connection.
