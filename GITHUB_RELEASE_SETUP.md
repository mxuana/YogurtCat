# V5 一次性更新发布配置

> 这些步骤只需要设置一次。不要把 Token、证书密码或 keystore 内容发到聊天或截图中。

## 1. 创建公开发布仓库

在 GitHub 新建一个 **Public** 仓库，名称必须为：

`Android-Releases`

源码仓库仍保持 Private。公开仓库只用于存放 APK Releases。

## 2. 创建固定签名证书

Termux：

```bash
pkg install openjdk-17 -y
keytool -genkeypair -v -keystore ~/ai-image-release.jks -alias aiimage -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 ~/ai-image-release.jks > ~/ai-image-release.b64
```

`keytool` 会让你设置 keystore 密码。请自己保存好密码。证书丢失后，将无法再覆盖安装已经发布的 App。

## 3. 创建发布仓库 Token

创建一个仅授权 `Android-Releases` 仓库的 Fine-grained Personal Access Token。Repository permissions 至少给 `Contents: Read and write`。

## 4. 在私有源码仓库添加 Actions secrets

源码仓库 -> Settings -> Secrets and variables -> Actions，添加：

- `ANDROID_KEYSTORE_BASE64`：`~/ai-image-release.b64` 文件中的整串内容
- `ANDROID_KEYSTORE_PASSWORD`：keystore 密码
- `ANDROID_KEY_ALIAS`：`aiimage`
- `ANDROID_KEY_PASSWORD`：key 密码（如果创建时和 keystore 密码相同，就填相同值）
- `RELEASE_REPO_TOKEN`：上一步创建的 Token

## 5. 推送 V5

Secrets 配好后，推送代码。Actions 会：

1. 自动生成版本号，例如 `5.23`；
2. 使用固定证书签名；
3. 构建 Release APK；
4. 自动发布到公开 `mxuana/Android-Releases`；
5. App 内“检查更新”即可看到最新版。

## 注意

第一次从之前的 debug APK 切换到固定签名 Release APK 时，Android 可能要求先卸载旧 debug 版，因为签名不同。**从第一次 V5 Release 安装以后，后续版本就可以直接覆盖更新。**
