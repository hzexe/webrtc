# 保留 WebRTC 相关类
-keep class org.webrtc.** { *; }
-keep class com.google.webrtc.** { *; }

# 保留 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留音频采集器类
-keep class com.webrtc.audio.** { *; }
-keep class com.webrtc.audio.example.** { *; }
