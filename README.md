# WebRTC Android 编译脚本

本仓库提供了完整的 WebRTC Android 编译脚本和 GitHub Actions 配置，用于编译包含回声消除(AEC)、降噪(NS)和语音活动检测(VAD)功能的 WebRTC 库。

## 功能特性

- ✅ **回声消除 (AEC)** - Acoustic Echo Cancellation
- ✅ **降噪 (NS)** - Noise Suppression
- ✅ **语音活动检测 (VAD)** - Voice Activity Detection
- ✅ **多架构支持** - arm64-v8a, x86_64
- ✅ **GitHub Actions 自动编译** - 自动化构建和发布
- ✅ **跨平台支持** - Linux 和 Windows

## 文件说明

| 文件 | 说明 |
|------|------|
| [build_webrtc_android.sh](build_webrtc_android.sh) | Linux/Mac 编译脚本 |
| [build_webrtc_android.bat](build_webrtc_android.bat) | Windows 编译脚本 |
| [sync_webrtc.sh](sync_webrtc.sh) | Linux/Mac 源码同步脚本 |
| [sync_webrtc.bat](sync_webrtc.bat) | Windows 源码同步脚本 |
| [.github/workflows/build-webrtc-android.yml](.github/workflows/build-webrtc-android.yml) | GitHub Actions 工作流配置 |

## 快速开始

### 方法一：使用 GitHub Actions 自动编译（推荐）

1. 将此仓库 fork 到你的 GitHub 账户
2. 进入仓库的 Actions 页面
3. 选择 "Build WebRTC for Android" workflow
4. 点击 "Run workflow"
5. 选择 WebRTC 分支（默认：m120）
6. 等待编译完成（约 1-2 小时）
7. 在 Actions 页面下载编译产物

### 方法二：本地编译

#### 前置要求

**Linux/Mac:**
```bash
# 安装 depot_tools
git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
export PATH="$PATH:$HOME/depot_tools"

# 安装依赖
sudo apt-get update
sudo apt-get install -y git curl unzip python3 python3-pip build-essential openjdk-11-jdk
```

**Windows:**
```batch
# 安装 depot_tools
git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
# 将 depot_tools 目录添加到系统 PATH 环境变量

# 安装依赖
# 下载并安装 Visual Studio Build Tools
# 下载并安装 Python 3
# 下载并安装 Git
```

#### 编译步骤

**Linux/Mac:**
```bash
# 1. 同步 WebRTC 源码
chmod +x sync_webrtc.sh build_webrtc_android.sh
./sync_webrtc.sh

# 2. 编译 WebRTC
./build_webrtc_android.sh

# 3. 查看编译产物
ls -lh output/
```

**Windows:**
```batch
REM 1. 同步 WebRTC 源码
sync_webrtc.bat

REM 2. 编译 WebRTC
build_webrtc_android.bat

REM 3. 查看编译产物
dir output\
```

## 编译产物

编译完成后，会在 `output` 目录下生成以下文件：

```
output/
├── arm64/
│   ├── libwebrtc_arm64.aar    # ARM64 架构的 AAR 包
│   └── libwebrtc_arm64.a      # ARM64 架构的静态库
├── x64/
│   ├── libwebrtc_x64.aar      # x86_64 架构的 AAR 包
│   └── libwebrtc_x64.a        # x86_64 架构的静态库
└── universal/
    └── libwebrtc_universal.aar # 包含所有架构的通用 AAR 包
```

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

### 使用静态库

如果你需要使用静态库，可以参考以下配置：

```cmake
# CMakeLists.txt
add_library(webrtc STATIC IMPORTED)
set_target_properties(webrtc PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libwebrtc_${ANDROID_ABI}.a)

target_link_libraries(your_native_lib webrtc)
```

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

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `WEBRTC_SRC_DIR` | WebRTC 源码目录 | `./src` |
| `OUTPUT_DIR` | 输出目录 | `./output` |
| `WEBRTC_BRANCH` | WebRTC 分支 | `m120` |
| `BUILD_TYPE` | 编译类型 | `Release` |

### 使用示例

```bash
# 指定 WebRTC 分支
WEBRTC_BRANCH=m121 ./build_webrtc_android.sh

# 指定输出目录
OUTPUT_DIR=./custom_output ./build_webrtc_android.sh
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
1. 确保已安装所有依赖
2. 确保网络连接正常（需要访问 Google 服务器）
3. 确保磁盘空间充足（至少 30GB）
4. 查看编译日志中的错误信息

### Q: 如何修改编译配置？

A: 编辑编译脚本中的 `gn_args` 部分，根据需要调整参数。

### Q: GitHub Actions 编译失败怎么办？

A: 检查 Actions 日志，常见原因：
1. 网络问题（无法访问 Google 服务器）
2. 磁盘空间不足
3. 分支不存在

### Q: 如何添加其他架构？

A: 在编译脚本中添加对应的架构配置，例如：

```bash
case $arch in
    armv7)
        gn_args='target_os = "android" target_cpu = "arm" ...'
        ;;
esac
```

## 注意事项

1. **网络要求**：需要能够访问 Google 服务器（包括 GitHub、chromium.googlesource.com 等）
2. **磁盘空间**：至少需要 30GB 可用空间
3. **内存要求**：建议至少 16GB RAM
4. **编译时间**：首次编译需要较长时间
5. **版本兼容性**：不同 WebRTC 分支可能有 API 变化

## 许可证

WebRTC 使用 BSD 3-Clause 许可证。编译产物也遵循相同的许可证。

## 参考资源

- [WebRTC 官方文档](https://webrtc.org/)
- [WebRTC Native API](https://webrtc.googlesource.com/src/+/refs/heads/main/docs/native-api/)
- [Android WebRTC 编译指南](https://webrtc.googlesource.com/src/+/refs/heads/main/docs/native-code/android/)
- [depot_tools 文档](https://commondatastorage.googleapis.com/chrome-infra-docs/flat/depot_tools/docs/html/depot_tools_tutorial.html)

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题，请提交 Issue。
