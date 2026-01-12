# WebRTC Android 音频采集器

## 概述

这是一个基于 WebRTC 的 Android 音频采集器，使用 AAudio 硬件加速，支持 16kHz 采样率、单声道，并启用 3A 算法（AEC、NS、AGC）。

## 功能特性

- **AAudio 硬件加速**：在 Android 8.1+ 设备上使用 AAudio API 进行低延迟音频采集
- **16kHz 采样率**：适合语音识别和实时通信场景
- **单声道**：减少数据量，提高处理效率
- **3A 算法**：
  - AEC（Acoustic Echo Cancellation）：回声消除
  - NS（Noise Suppression）：噪声抑制
  - AGC（Automatic Gain Control）：自动增益控制
- **VAD（Voice Activity Detection）**：语音活动检测
- **实时音频处理**：使用 WebRTC AudioProcessing 进行实时音频增强

## 系统要求

- Android 8.1 (API 27) 或更高版本
- 支持 AAudio 的设备
- 录音权限
- 存储权限（用于保存音频文件）

## 项目结构

```
android/
├── build.gradle                           # Gradle 构建配置
├── src/main/
│   ├── AndroidManifest.xml                # Android 清单文件
│   ├── java/com/webrtc/audio/
│   │   ├── WebRTCAudioCapturer.java       # 音频采集器主类
│   │   └── example/
│   │       └── WebRTCAudioCapturerExample.java  # 使用示例
│   └── res/                               # 资源文件
└── libs/                                  # WebRTC AAR 库文件
    └── libwebrtc_arm64.aar                # ARM64 架构库
```

## 快速开始

### 1. 添加依赖

将 WebRTC AAR 文件复制到 `android/libs/` 目录：

```bash
cp libwebrtc_arm64.aar android/libs/
```

### 2. 配置权限

在 `AndroidManifest.xml` 中添加必要权限：

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

### 3. 初始化音频采集器

```java
import com.webrtc.audio.WebRTCAudioCapturer;

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
    WebRTCAudioCapturer capturer = new WebRTCAudioCapturer();
    
    // 配置 3A 算法
    capturer.setEnableAEC(true);      // 启用回声消除
    capturer.setEnableNS(true);       // 启用噪声抑制
    capturer.setEnableAGC(true);      // 启用自动增益控制
    capturer.setEnableVAD(true);      // 启用语音活动检测
    
    // 设置音频数据回调
    capturer.setAudioDataCallback(new WebRTCAudioCapturer.AudioDataCallback() {
        @Override
        public void onAudioData(byte[] data, int length) {
            // 处理音频数据
            // data: PCM 音频数据（16-bit，单声道）
            // length: 数据长度（字节）
        }
        
        @Override
        public void onCaptureError(String error) {
            // 处理错误
        }
    });
    
    // 初始化并开始采集
    if (capturer.initialize()) {
        capturer.startCapture();
    }
}
```

### 4. 停止采集

```java
if (capturer.isCapturing()) {
    capturer.stopCapture();
}

// 释放资源
capturer.release();
```

## API 文档

### WebRTCAudioCapturer

#### 构造方法

```java
public WebRTCAudioCapturer()
```

创建音频采集器实例。

#### 主要方法

##### 初始化

```java
public boolean initialize()
```

初始化音频采集器和音频处理模块。

**返回值**：`true` 表示初始化成功，`false` 表示失败

##### 开始采集

```java
public boolean startCapture()
```

开始音频采集。

**返回值**：`true` 表示开始成功，`false` 表示失败

##### 停止采集

```java
public boolean stopCapture()
```

停止音频采集。

**返回值**：`true` 表示停止成功，`false` 表示失败

##### 释放资源

```java
public void release()
```

释放所有资源，包括 AudioRecord 和 AudioProcessing。

##### 设置音频数据回调

```java
public void setAudioDataCallback(AudioDataCallback callback)
```

设置音频数据回调接口。

**参数**：
- `callback`：AudioDataCallback 实例

#### 3A 算法配置

##### 回声消除（AEC）

```java
// 启用/禁用回声消除
public void setEnableAEC(boolean enable)

// 设置回声消除级别
public void setEchoCancellationLevel(int level)
// 可选值：
// - EchoCancellation.SuppressionLevel.kLowSuppression
// - EchoCancellation.SuppressionLevel.kModerateSuppression
// - EchoCancellation.SuppressionLevel.kHighSuppression
```

##### 噪声抑制（NS）

```java
// 启用/禁用噪声抑制
public void setEnableNS(boolean enable)

// 设置噪声抑制级别
public void setNoiseSuppressionLevel(int level)
// 可选值：
// - NoiseSuppression.Level.LOW
// - NoiseSuppression.Level.MODERATE
// - NoiseSuppression.Level.HIGH
// - NoiseSuppression.Level.VERY_HIGH
```

##### 自动增益控制（AGC）

```java
// 启用/禁用自动增益控制
public void setEnableAGC(boolean enable)

// 设置目标增益（dB）
public void setTargetGainDb(int gainDb)
// 推荐值：0-31

// 设置压缩增益（dB）
public void setCompressionGainDb(int gainDb)
// 推荐值：0-90

// 启用/禁用限幅器
public void setLimiterEnabled(boolean enabled)
```

##### 语音活动检测（VAD）

```java
// 启用/禁用语音活动检测
public void setEnableVAD(boolean enable)

// 设置 VAD 置信度
public void setVADLikelihood(int likelihood)
// 可选值：
// - VoiceDetection.Likelihood.kVeryLowLikelihood
// - VoiceDetection.Likelihood.kLowLikelihood
// - VoiceDetection.Likelihood.kModerateLikelihood
// - VoiceDetection.Likelihood.kHighLikelihood
```

#### 状态查询

```java
// 检查是否正在采集
public boolean isCapturing()

// 获取采样率（固定为 16000 Hz）
public int getSampleRate()

// 获取声道数（固定为 1）
public int getChannels()

// 获取已处理的帧数
public int getFrameCount()

// 获取总样本数
public long getTotalSamples()

// 获取采集时长（毫秒）
public long getCaptureDurationMs()

// 检查是否支持 AAudio
public boolean isAAudioSupported()
```

#### 回调接口

```java
public interface AudioDataCallback {
    // 音频数据回调
    void onAudioData(byte[] data, int length);
    
    // 错误回调
    void onCaptureError(String error);
}
```

## 音频数据格式

- **采样率**：16000 Hz
- **声道数**：1（单声道）
- **位深度**：16-bit PCM
- **字节序**：小端序（Little-Endian）
- **数据格式**：有符号整数

### 数据示例

```java
// 读取 16-bit PCM 数据
for (int i = 0; i < length / 2; i++) {
    short sample = (short) ((data[i * 2 + 1] << 8) | (data[i * 2] & 0xFF));
    // 处理样本...
}
```

## 性能优化建议

1. **使用专用线程**：音频采集在专用线程中进行，避免阻塞主线程
2. **避免频繁创建/销毁对象**：重用 AudioProcessing 实例
3. **合理设置缓冲区大小**：根据设备性能调整缓冲区大小
4. **及时释放资源**：在不再使用时调用 `release()` 方法
5. **监控性能指标**：使用 `getFrameCount()` 和 `getTotalSamples()` 监控采集状态

## 常见问题

### Q1: 为什么需要 Android 8.1+？

A: AAudio API 在 Android 8.1 (API 27) 中引入，用于提供低延迟音频处理。虽然可以使用传统 AudioRecord API，但 AAudio 性能更好。

### Q2: 如何选择 3A 算法的参数？

A: 
- **AEC**：通常使用 `kHighSuppression` 获得最佳效果
- **NS**：根据环境噪声水平选择，`HIGH` 或 `VERY_HIGH` 适合嘈杂环境
- **AGC**：`targetGainDb` 通常设置为 10-15，`compressionGainDb` 设置为 9

### Q3: 音频数据如何保存为文件？

A: 音频数据是原始 PCM 格式，可以直接保存为 `.pcm` 文件。如果需要转换为 WAV 或 MP3，需要使用音频编码库。

```java
FileOutputStream fos = new FileOutputStream("output.pcm");
fos.write(data, 0, length);
fos.close();
```

### Q4: 如何处理权限请求？

A: 在运行时请求录音权限：

```java
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
        != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.RECORD_AUDIO},
            REQUEST_RECORD_AUDIO_PERMISSION);
}
```

### Q5: 如何调试音频采集问题？

A: 使用 Logcat 查看日志：

```bash
adb logcat | grep WebRTCAudioCapturer
```

## 示例应用

完整的使用示例请参考 `WebRTCAudioCapturerExample.java`，该示例展示了：
- 初始化音频采集器
- 配置 3A 算法
- 开始/停止采集
- 保存音频数据到文件
- 显示采集状态信息

## 技术支持

如有问题或建议，请通过以下方式联系：
- GitHub Issues: [项目地址]
- 文档: [文档地址]

## 许可证

本项目遵循 WebRTC 原始许可证（BSD 3-Clause）。
