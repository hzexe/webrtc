# Rust 自动化集成说明

## 概述

本项目已配置自动化构建系统，在构建 Android 应用时自动编译 Rust 代码，无需手动操作。

## 工作原理

1. **自动编译**：Gradle 在构建 Android 项目前自动执行 `cargo build`
2. **自动复制**：编译好的 Rust 静态库自动复制到 jni 目录
3. **自动链接**：ndk-build 自动链接 Rust 静态库

## 使用方法

### 正常构建

```bash
# 在 android 目录下执行
./gradlew assembleDebug
```

Gradle 会自动：
1. 检查 Rust 是否已安装
2. 编译 Rust 代码（`cargo build --release --target aarch64-linux-android`）
3. 复制 `libdeepfilter_ort.a` 到 `src/main/jni/` 目录
4. 执行 ndk-build 链接 Rust 库
5. 构建 Android APK

### 单独编译 Rust 库

```bash
# 在 android/app 目录下执行
./gradlew buildRustLib
```

### 清理所有构建产物

```bash
# 在 android 目录下执行
./gradlew clean
```

这会清理：
- Android 构建产物
- Rust 编译产物（`deepfilter-ort/target/`）
- 复制的 Rust 静态库（`src/main/jni/libdeepfilter_ort.a`）

## 配置说明

### Rust 项目路径配置

在 `app/build.gradle` 中可以修改 Rust 项目路径：

```gradle
def rustProjectPath = '../../deepfilter-ort'  // Rust 项目相对路径
def rustTarget = 'aarch64-linux-android'      // 目标架构
```

### 支持的架构

当前配置仅支持 `arm64-v8a`。如需支持其他架构：

1. 修改 `app/build.gradle` 中的 `rustTarget`
2. 修改 `app/build.gradle` 中的 `defaultConfig.ndk.abiFilters`
3. 修改 `src/main/jni/Application.mk` 中的 `APP_ABI`

### Rust 版本要求

- Rust 版本：1.60 或更高
- NDK 版本：r21 或更高（推荐 r29）

## 构建流程图

```
gradlew assembleDebug
    ↓
buildRustLib 任务
    ↓
cargo build --release --target aarch64-linux-android
    ↓
copyRustLib 任务
    ↓
复制 libdeepfilter_ort.a → src/main/jni/
    ↓
externalNativeBuildDebug 任务
    ↓
ndk-build 链接 Rust 静态库
    ↓
生成 APK
```

## 故障排查

### Windows 环境缺少 MSVC 链接器

**错误信息**：
```
error: linker `link.exe` not found
note: the msvc targets depend on the msvc linker but `link.exe` was not found
```

**原因**：在 Windows 上编译 Rust 时，cargo 默认使用 MSVC 工具链来编译 proc-macro 和 build scripts，即使你指定了 Android 目标架构。

**解决方法**（选择一种）：

#### 方法 1：安装 Visual Studio Build Tools（推荐）

1. 下载 [Visual Studio Build Tools](https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2022)
2. 安装时选择 "使用 C++ 的桌面开发" 工作负载
3. 确保包含 "MSVC v143 - VS 2022 C++ x64/x86 生成工具"

#### 方法 2：使用 GNU 工具链

```bash
# 安装 GNU 工具链
rustup toolchain install stable-x86_64-pc-windows-gnu
rustup default stable-x86_64-pc-windows-gnu
```

#### 方法 3：使用 WSL 或 Linux 环境

在 WSL（Windows Subsystem for Linux）中编译：

```bash
# 在 WSL 中
cd /mnt/f/webrtc/android
./gradlew buildRustLib
```

### Rust 未安装

**错误信息**：`Rust 未安装或不在 PATH 中`

**解决方法**：安装 Rust：https://rustup.rs/

### cargo build 失败

**错误信息**：`Rust 库编译失败: libdeepfilter_ort.a 不存在`

**解决方法**：
1. 检查 Rust 项目路径是否正确
2. 手动进入 Rust 项目目录执行 `cargo build --release --target aarch64-linux-android` 查看详细错误
3. 确保 Android NDK 路径配置正确（在 `deepfilter-ort/.cargo/config.toml` 中）

### ndk-build 失败

**错误信息**：`Android NDK not found`

**解决方法**：
1. 在 `local.properties` 中配置 NDK 路径：
   ```properties
   ndk.dir=C\:\\androidsdk\\ndk\\29.0.13113456
   ```
2. 或在 `build.gradle` 中配置：
   ```gradle
   android {
       ndkPath "C:\\androidsdk\\ndk\\29.0.13113456"
   }
   ```

## 优势

相比手动编译和复制，自动化方案具有以下优势：

1. **零手动操作**：无需手动编译 Rust 或复制文件
2. **版本一致性**：确保 Rust 代码和 Android 应用始终同步
3. **CI/CD 友好**：易于集成到持续集成流程
4. **增量编译**：Gradle 会自动检测 Rust 代码变化，仅重新编译必要的部分
5. **跨平台**：支持 Windows、macOS、Linux

## 高级配置

### 添加更多 Rust 目标

如需支持多个架构，可以在 `build.gradle` 中添加多个任务：

```gradle
task buildRustLibArm64(type: Exec) {
    commandLine 'cargo', 'build', '--release', '--target', 'aarch64-linux-android'
}

task buildRustLibX86_64(type: Exec) {
    commandLine 'cargo', 'build', '--release', '--target', 'x86_64-linux-android'
}
```

### 自定义编译选项

在 Rust 项目的 `.cargo/config.toml` 中可以配置编译选项：

```toml
[target.aarch64-linux-android]
rustflags = ["-C", "link-arg=-landroid", "-C", "link-arg=-llog"]
```

## 相关文件

- `app/build.gradle` - Gradle 构建配置（包含 Rust 自动化任务）
- `app/src/main/jni/Android.mk` - ndk-build 配置
- `app/src/main/jni/Application.mk` - NDK 应用配置
- `deepfilter-ort/Cargo.toml` - Rust 项目配置
- `deepfilter-ort/.cargo/config.toml` - Rust 工具链配置
