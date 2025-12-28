# WebRTC Android 库安装指南

## 概述

本指南介绍如何在 Android 项目中集成和使用 WebRTC Android 库。该库专注于音频处理功能，包括：
- 回声消除 (AEC)
- 降噪 (NS)
- 语音活动检测 (VAD)

## 版本信息

- WebRTC 分支: {{WEBRTC_BRANCH}}
- 编译时间: {{BUILD_TIME}}
- GitHub Actions Run: {{RUN_NUMBER}}
- Commit: {{COMMIT_SHA}}

## 支持架构

- `arm64-v8a` - 64 位 ARM 架构（推荐用于现代 Android 设备）
- `x86_64` - 64 位 x86 架构（用于模拟器或特定设备）

## 文件说明

| 文件名 | 说明 | 大小 |
|--------|------|------|
| `libwebrtc_arm64.aar` | ARM64 架构的 Android 库包 | ~8-12 MB |
| `libwebrtc_x64.aar` | x86_64 架构的 Android 库包 | ~8-12 MB |
| `libwebrtc_universal.aar` | 包含所有架构的通用 Android 库包 | ~15-20 MB |
| `libwebrtc_arm64.a` | ARM64 架构的静态库 | ~8-12 MB |
| `libwebrtc_x64.a` | x86_64 架构的静态库 | ~8-12 MB |

## 安装步骤

### 方法一：使用 AAR 文件（推荐）

#### 1. 下载 AAR 文件

从 Release 页面下载对应架构的 AAR 文件：
- 如果只需要支持 ARM64 设备，下载 `libwebrtc_arm64.aar`
- 如果需要支持多种架构，下载 `libwebrtc_universal.aar`

#### 2. 将 AAR 文件添加到项目

将下载的 AAR 文件复制到 Android 项目的 `app/libs/` 目录下。如果 `libs` 目录不存在，请先创建它：

```bash
mkdir -p app/libs
cp libwebrtc_arm64.aar app/libs/
```

#### 3. 在 build.gradle 中添加依赖

打开 `app/build.gradle` 文件，添加以下内容：

```gradle
android {
    // ... 其他配置

    // 确保 AAR 文件被包含在构建中
    repositories {
        flatDir {
            dirs 'libs'
        }
    }
}

dependencies {
    // 添加 WebRTC AAR 依赖
    implementation(name: 'libwebrtc_arm64', ext: 'aar')
    
    // 或者使用通用版本
    // implementation(name: 'libwebrtc_universal', ext: 'aar')
}
```

#### 4. 同步项目

在 Android Studio 中点击 "Sync Now" 或运行以下命令：

```bash
./gradlew clean build
```

### 方法二：使用静态库（高级用户）

如果您需要更细粒度的控制，可以使用静态库文件。

#### 1. 下载静态库文件

下载对应架构的静态库文件：
- `libwebrtc_arm64.a` - 用于 ARM64 架构
- `libwebrtc_x64.a` - 用于 x86_64 架构

#### 2. 创建 JNI 模块

在 `app/src/main/cpp/` 目录下创建 CMakeLists.txt：

```cmake
cmake_minimum_required(VERSION 3.18.1)

project("webrtc-audio")

# 设置 WebRTC 静态库路径
set(WEBRTC_LIB_DIR "${CMAKE_SOURCE_DIR}/libs")

# 添加 WebRTC 静态库
add_library(webrtc STATIC IMPORTED)
set_target_properties(webrtc PROPERTIES IMPORTED_LOCATION
    ${WEBRTC_LIB_DIR}/libwebrtc_arm64.a)

# 添加您的原生库
add_library(native-lib SHARED
    native-lib.cpp
)

# 链接 WebRTC 库
target_link_libraries(native-lib
    webrtc
    log
    android
    OpenSLES
)
```

#### 3. 在 build.gradle 中配置

```gradle
android {
    // ... 其他配置

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
        }
    }

    sourceSets {
        main {
            jniLibs.srcDirs = ['libs']
        }
    }
}
```

## 使用示例

### Java/Kotlin 代码示例

```java
import org.webrtc.*;

public class WebRTCAudioProcessor {
    private AudioProcessing audioProcessing;
    private AudioDeviceModule audioDeviceModule;
    
    public void initializeAudioProcessing() {
        // 创建音频处理模块
        AudioProcessingBuilder builder = new AudioProcessingBuilder();
        
        // 启用回声消除
        EchoCancellation echoCancellation = builder.createEchoCancellation();
        echoCancellation.enable(true);
        echoCancellation.setMobileMode(true);
        
        // 启用降噪
        NoiseSuppression noiseSuppression = builder.createNoiseSuppression();
        noiseSuppression.enable(true);
        noiseSuppression.setLevel(NoiseSuppression.Level.HIGH);
        
        // 启用语音活动检测
        VoiceDetection voiceDetection = builder.createVoiceDetection();
        voiceDetection.enable(true);
        voiceDetection.setLikelihood(VoiceDetection.Likelihood.HIGH);
        
        audioProcessing = builder.createAudioProcessing();
    }
    
    public void processAudioFrame(AudioFrame frame) {
        // 处理音频帧
        audioProcessing.processFrame(frame);
    }
    
    public void release() {
        if (audioProcessing != null) {
            audioProcessing.release();
            audioProcessing = null;
        }
    }
}
```

### C++ 代码示例

```cpp
#include <webrtc/modules/audio_processing/include/audio_processing.h>
#include <webrtc/modules/audio_processing/include/audio_processing_builder.h>

class WebRTCAudioProcessor {
private:
    webrtc::AudioProcessing* audio_processing_;
    
public:
    bool Initialize() {
        // 创建音频处理构建器
        webrtc::AudioProcessingBuilder builder;
        
        // 创建音频处理实例
        audio_processing_ = builder.Create();
        
        if (!audio_processing_) {
            return false;
        }
        
        // 配置回声消除
        webrtc::EchoCancellation* echo_cancellation =
            audio_processing_->echo_cancellation();
        if (echo_cancellation) {
            echo_cancellation->Enable(true);
            echo_cancellation->set_suppression_level(
                webrtc::EchoCancellation::SuppressionLevel::kHighSuppression);
        }
        
        // 配置降噪
        webrtc::NoiseSuppression* noise_suppression =
            audio_processing_->noise_suppression();
        if (noise_suppression) {
            noise_suppression->Enable(true);
            noise_suppression->set_level(
                webrtc::NoiseSuppression::Level::kHigh);
        }
        
        // 配置语音活动检测
        webrtc::VoiceDetection* voice_detection =
            audio_processing_->voice_detection();
        if (voice_detection) {
            voice_detection->Enable(true);
            voice_detection->set_likelihood(
                webrtc::VoiceDetection::Likelihood::kHighLikelihood);
        }
        
        return true;
    }
    
    void ProcessAudioFrame(webrtc::AudioFrame* frame) {
        if (audio_processing_) {
            audio_processing_->ProcessFrame(frame);
        }
    }
    
    void Release() {
        if (audio_processing_) {
            delete audio_processing_;
            audio_processing_ = nullptr;
        }
    }
};
```

## 权限配置

在 `AndroidManifest.xml` 中添加必要的权限：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.yourapp">
    
    <!-- 录音权限 -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    
    <!-- 修改音频设置权限 -->
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    
    <!-- 如果使用蓝牙 -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    
    <application>
        <!-- ... -->
    </application>
</manifest>
```

## 运行时权限请求（Android 6.0+）

```java
private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;

private void requestAudioPermission() {
    if (ContextCompat.checkSelfPermission(this, 
            Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
        
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.RECORD_AUDIO},
            REQUEST_RECORD_AUDIO_PERMISSION);
    }
}

@Override
public void onRequestPermissionsResult(int requestCode,
        String[] permissions, int[] grantResults) {
    if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
        if (grantResults.length > 0 
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予，可以开始录音
        } else {
            // 权限被拒绝
        }
    }
}
```

## ProGuard 配置

如果启用了 ProGuard，在 `proguard-rules.pro` 中添加：

```proguard
# 保留 WebRTC 相关类
-keep class org.webrtc.** { *; }
-keep class com.google.webrtc.** { *; }

# 保留 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}
```

## 常见问题

### Q1: 编译时出现 "Duplicate class" 错误

**A:** 这可能是因为项目中已经包含了其他 WebRTC 库。请检查依赖并确保没有冲突。

### Q2: 运行时出现 "UnsatisfiedLinkError"

**A:** 确保下载的 AAR 文件架构与目标设备架构匹配。如果使用通用 AAR，确保包含所有需要的架构。

### Q3: 音频处理效果不明显

**A:** 检查以下几点：
- 确保正确初始化了 AudioProcessing
- 确认音频采样率和通道数配置正确
- 检查是否正确调用了 ProcessFrame 方法

### Q4: 如何选择使用哪个 AAR 文件？

**A:** 
- 如果只支持 ARM64 设备（大多数现代 Android 设备），使用 `libwebrtc_arm64.aar`
- 如果需要在模拟器上测试，使用 `libwebrtc_x64.aar`
- 如果需要支持多种架构，使用 `libwebrtc_universal.aar`（文件较大）

### Q5: 这个库支持视频功能吗？

**A:** 不支持。本版本专注于音频处理功能，已移除所有视频编解码器和 P2P 网络功能。

## 性能优化建议

1. **使用适当的采样率**：根据应用需求选择合适的音频采样率（如 16kHz 或 48kHz）
2. **避免频繁创建/销毁对象**：重用 AudioProcessing 实例
3. **使用正确的线程**：音频处理应在专门的音频线程中进行
4. **及时释放资源**：在不再使用时调用 release() 方法

## 技术支持

如有问题或建议，请通过以下方式联系：
- GitHub Issues: [项目地址]
- 文档: [文档地址]

## 更新日志

- 移除了所有视频编解码器（VP8、VP9、H.264、AV1）
- 移除了 P2P 网络功能（PeerConnection、DataChannel、SCTP）
- 专注于音频处理功能（AEC、NS、VAD）
- 优化了库体积，减小了约 30-40%
