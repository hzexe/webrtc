# WebRTC Android 编译脚本

本仓库提供了完整的 WebRTC Android GitHub Actions 编译配置，用于编译包含回声消除(AEC)、降噪(NS)和语音活动检测(VAD)功能的 WebRTC 库。

## 功能特性

- ✅ **回声消除 (AEC)** - Acoustic Echo Cancellation
- ✅ **降噪 (NS)** - Noise Suppression
- ✅ **语音活动检测 (VAD)** - Voice Activity Detection
- ✅ **多架构支持** - arm64-v8a, x86_64
- ✅ **GitHub Actions 自动编译** - 自动化构建和发布
- ✅ **Android 原生音频支持** - OpenSL ES, AAudio
- ✅ **Java API 文档** - 完整的 JavaDoc 支持
- ✅ **详细的安装指南** - 包含 Android 集成示例

## 文件说明

| 文件 | 说明 |
|------|------|
| [.github/workflows/build-webrtc-android.yml](.github/workflows/build-webrtc-android.yml) | GitHub Actions 工作流配置 |
| [docs/ANDROID_INSTALL_GUIDE.md](docs/ANDROID_INSTALL_GUIDE.md) | Android 安装和使用指南 |

## 编译参数说明

### 核心功能保障

编译参数经过精心优化，确保以下核心功能不会被剪裁：

#### 音频处理功能
- **回声消除 (AEC)** - 包含完整的 AEC 算法
- **降噪 (NS)** - 包含多级降噪支持
- **语音活动检测 (VAD)** - 包含 VAD 算法

#### Android 平台支持
- **OpenSL ES** - Android 音频 API
- **AAudio** - Android 高性能音频 API

### 优化的剪裁策略

仅剪裁以下非核心组件：
- 测试代码 (`rtc_include_tests = false`)
- 示例代码 (`rtc_build_examples = false`)
- 视频编解码器 (VP8, VP9, H.264, AV1)
- P2P 网络功能 (PeerConnection, DataChannel, SCTP)
- 测试日志 (`rtc_enable_bwe_test_logging = false`)
- 事件追踪 (`rtc_enable_event_tracing = false`)

保留所有核心音频处理功能，确保编译产物功能完整且体积更小。

## 快速开始

### 使用 GitHub Actions 自动编译

1. 将此仓库 fork 到你的 GitHub 账户
2. 进入仓库的 Actions 页面
3. 选择 "Build WebRTC for Android" workflow
4. 点击 "Run workflow"
5. 选择 WebRTC 分支（默认：m120）
6. 等待编译完成（约 1-2 小时）
7. 在 Actions 页面下载编译产物

### 手动触发编译

你也可以通过推送代码到 `main` 或 `master` 分支来触发自动编译。

## 编译产物

编译完成后，会在 GitHub Actions 中生成以下文件：

```
output/
├── arm64/
│   ├── libwebrtc_arm64.aar    # ARM64 架构的 AAR 包
│   └── libwebrtc_arm64.a      # ARM64 架构的静态库
├── x64/
│   ├── libwebrtc_x64.aar      # x86_64 架构的 AAR 包
│   └── libwebrtc_x64.a        # x86_64 架构的静态库
├── universal/
│   └── libwebrtc_universal.aar # 包含所有架构的通用 AAR 包
├── javadoc/
│   └── (HTML 文档)            # Java API 文档
├── javadoc.zip                # Java API 文档压缩包
├── ANDROID_INSTALL_GUIDE.md   # Android 安装指南
└── version_info.txt           # 编译版本信息
```

### 下载编译产物

编译产物可以通过以下方式下载：

1. **Actions 页面** - 在 Actions 运行完成后，点击 "Artifacts" 部分的下载链接
2. **Releases 页面** - 如果是通过 workflow_dispatch 触发，会自动创建 Release

## 在 Android 项目中使用

### 使用 AAR 包

1. 将 `libwebrtc_arm64.aar` 或 `libwebrtc_universal.aar` 复制到项目的 `app/libs/` 目录
2. 在 `app/build.gradle` 中添加依赖：

```gradle
android {
    ...
    repositories {
        flatDir {
            dirs 'libs'
        }
    }
}

dependencies {
    implementation(name: 'libwebrtc_arm64', ext: 'aar')
}
```

### 查看 Java API 文档

编译产物中包含完整的 Java API 文档：

1. 下载 `javadoc.zip` 文件
2. 解压到任意目录
3. 在浏览器中打开 `index.html` 文件
4. 浏览完整的 API 文档，包括：
   - `AudioProcessing` - 音频处理主类
   - `EchoCancellation` - 回声消除
   - `NoiseSuppression` - 降噪
   - `VoiceDetection` - 语音活动检测
   - `AudioDeviceModule` - 音频设备模块

### 使用静态库

如果你需要使用静态库，可以参考以下配置：

```cmake
# CMakeLists.txt
add_library(webrtc STATIC IMPORTED)
set_target_properties(webrtc PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libwebrtc_${ANDROID_ABI}.a)

target_link_libraries(your_native_lib webrtc)
```

### 详细的集成指南

请参考 [docs/ANDROID_INSTALL_GUIDE.md](docs/ANDROID_INSTALL_GUIDE.md) 获取详细的 Android 集成指南，包括：
- 完整的安装步骤
- Java/Kotlin 代码示例
- C++ 代码示例
- 权限配置
- ProGuard 配置
- 常见问题解答

## WebRTC API 使用示例

### 回声消除 (AEC)

```java
import org.webrtc.AudioProcessing;
import org.webrtc.AudioProcessingBuilder;

// 创建 AudioProcessor
AudioProcessing audioProcessing = new AudioProcessingBuilder()
    .setEchoCancellation(true)
    .build();

// 应用音频处理
audioProcessing.processStream(...);
```

### 降噪 (NS)

```java
AudioProcessing audioProcessing = new AudioProcessingBuilder()
    .setNoiseSuppression(true)
    .setNoiseSuppressionLevel(AudioProcessing.NoiseSuppressionLevel.High)
    .build();
```

### 语音活动检测 (VAD)

```java
AudioProcessing audioProcessing = new AudioProcessingBuilder()
    .setVoiceDetection(true)
    .setVoiceDetectionFrameSize(10)
    .build();

// 检测语音活动
boolean hasVoice = audioProcessing.hasVoice();
```

## 支持的 WebRTC 分支

以下是一些常用的 WebRTC 分支：

- `m120` - WebRTC M120（稳定版本）
- `m121` - WebRTC M121
- `m122` - WebRTC M122
- `main` - 主分支（最新开发版本）

> 注意：不同分支可能有 API 变化，建议使用稳定版本。

## 常见问题

### Q: 编译需要多长时间？

A: 首次编译（包括下载源码）可能需要 2-4 小时，后续编译约 1-2 小时。

### Q: 编译失败怎么办？

A: 检查以下几点：
1. 确保网络连接正常（需要访问 Google 服务器）
2. 查看编译日志中的错误信息
3. 检查选择的 WebRTC 分支是否存在

### Q: 如何确认编译产物包含 AEC/NS/VAD 功能？

A: 编译参数已优化，确保包含所有音频处理功能。你可以通过以下方式验证：
1. 检查 AAR 包中的 JNI 库大小（包含完整功能的库通常较大）
2. 在代码中尝试调用相关 API
3. 查看 `version_info.txt` 文件中的编译信息

### Q: GitHub Actions 编译失败怎么办？

A: 检查 Actions 日志，常见原因：
1. 网络问题（无法访问 Google 服务器）
2. 磁盘空间不足
3. 分支不存在
4. 超时（GitHub Actions 有时间限制）

### Q: 编译产物的大小是多少？

A: 典型大小（已移除视频编解码器和 P2P 网络功能）：
- ARM64 AAR: 约 8-12 MB
- x86_64 AAR: 约 8-12 MB
- 通用 AAR: 约 15-20 MB
- JavaDoc ZIP: 约 5-10 MB

### Q: 如何查看 Java API 文档？

A: 编译产物中包含 `javadoc.zip` 文件，解压后在浏览器中打开 `index.html` 即可查看完整的 Java API 文档。

### Q: 这个版本支持视频功能吗？

A: 不支持。本版本专注于音频处理功能，已移除所有视频编解码器和 P2P 网络功能，以减小库体积。

## 注意事项

1. **网络要求**：需要能够访问 Google 服务器（包括 GitHub、chromium.googlesource.com 等）
2. **磁盘空间**：GitHub Actions 需要至少 30GB 可用空间
3. **编译时间**：首次编译需要较长时间
4. **版本兼容性**：不同 WebRTC 分支可能有 API 变化
5. **功能完整性**：编译参数已优化，确保所有核心功能完整保留

## 许可证

WebRTC 使用 BSD 3-Clause 许可证。编译产物也遵循相同的许可证。

## 参考资源

- [WebRTC 官方文档](https://webrtc.org/)
- [WebRTC Native API](https://webrtc.googlesource.com/src/+/refs/heads/main/docs/native-api/)
- [Android WebRTC 编译指南](https://webrtc.googlesource.com/src/+/refs/heads/main/docs/native-code/android/)
- [depot_tools 文档](https://commondatastorage.googleapis.com/chrome-infra-docs/flat/depot_tools/docs/html/depot_tools_tutorial.html)
- [WebRTC GN 构建参数](https://webrtc.googlesource.com/src/+/refs/heads/main/docs/gn_build_args.md)

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题，请提交 Issue。
