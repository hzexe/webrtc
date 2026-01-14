# Android NDK Application.mk
# 用于配置 DeepFilterNet3 JNI 编译选项

# 目标平台 ABI
# 支持的架构：armeabi-v7a, arm64-v8a, x86, x86_64
APP_ABI := arm64-v8a

# 最低支持的 Android API 版本
APP_PLATFORM := android-27

# C 标准库（Rust 直接导出 JNI，使用 C 接口）
APP_STL := c++_shared

# 优化级别
APP_OPTIM := release
