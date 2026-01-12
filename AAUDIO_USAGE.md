# WebRTC AAudio 使用方法

## 1. AAudio 简介
AAudio 是 Android 8.1 (API 27) 引入的低延迟音频 API，专为实时音频应用设计，支持硬件加速，可实现亚毫秒级延迟。

## 2. WebRTC 中的 AAudio 实现
WebRTC 在 native 层实现了 AAudio 支持，Java 层通过 `JavaAudioDeviceModule` 间接调用。

### 2.1 核心文件分析
- `webrtc/src/sdk/android/src/jni/audio_device/aaudio_recorder.cc`: AAudio 录音器实现
- `webrtc/src/sdk/android/src/jni/audio_device/aaudio_wrapper.cc`: AAudio 流管理
- `webrtc/src/sdk/android/api/org/webrtc/audio/JavaAudioDeviceModule.java`: Java 层 API

### 2.2 AAudio 启用条件
- Android 8.1 (API 27) 或更高版本
- 设备支持 AAudio 硬件加速
- WebRTC 编译时启用 AAudio 支持

## 3. Java 层使用 AAudio 的方法

### 3.1 配置 JavaAudioDeviceModule
```java
JavaAudioDeviceModule.builder(context)
        .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        .setSampleRate(16000)
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseHardwareNoiseSuppressor(true)
        .setUseLowLatency(true)
        .createAudioDeviceModule();
```

### 3.2 关键配置说明
- **setAudioSource**: 使用 `VOICE_COMMUNICATION` 启用系统音频优化
- **setSampleRate**: 16kHz 采样率，单声道
- **setUseHardwareAcousticEchoCanceler**: 启用硬件回声消除 (AEC)
- **setUseHardwareNoiseSuppressor**: 启用硬件噪声抑制 (NS)
- **setUseLowLatency**: 启用低延迟模式（自动使用 AAudio）

### 3.3 3A 算法和 VAD
- **AEC (回声消除)**: 硬件加速实现，由系统自动处理
- **NS (噪声抑制)**: 硬件加速实现，由系统自动处理
- **AGC (自动增益控制)**: 由 WebRTC native 层自动启用
- **VAD (语音活动检测)**: 由 WebRTC native 层自动启用

## 4. 硬件加速验证
```java
// 检查 AAudio 支持
public boolean isAAudioSupported() {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1;
}
```

## 5. 注意事项
1. Java 层无法直接访问 AAudio API，需通过 WebRTC 间接使用
2. 低延迟模式会自动优先使用 AAudio
3. 3A 算法和 VAD 由 native 层自动处理，无需 Java 层配置
4. 确保设备支持 AAudio 硬件加速

## 6. 性能优化建议
- 使用 16kHz 采样率和单声道以减少带宽
- 启用低延迟模式以获得最佳性能
- 使用 VOICE_COMMUNICATION 音频源以获得最佳音频质量
- 避免在主线程处理音频数据