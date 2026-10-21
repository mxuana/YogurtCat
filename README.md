# AI 生图 V5

V5 在 V4 的界面、会话、OC、历史、中转站能力基础上新增应用内更新。

## 应用内更新

- 设置页可手动“检查更新”。
- App 启动时最多每 12 小时静默检查一次。
- 最新版本从公开 GitHub Release 仓库 `mxuana/Android-Releases` 获取。
- 下载完成后会打开 Android 系统安装界面。首次使用需要允许本 App“安装未知应用”。
- 为了能够覆盖升级，Release APK 必须使用同一份固定签名证书。

## 发布链

私有源码仓库 -> GitHub Actions -> 固定签名 Release APK -> 公开 `Android-Releases` Release -> App 内检查更新。

首次使用前请阅读 `GITHUB_RELEASE_SETUP.md`。
