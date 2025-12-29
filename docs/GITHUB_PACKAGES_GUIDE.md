# GitHub Packages 使用指南

## 概述

本指南介绍如何在 Android 项目中使用 GitHub Packages 中发布的 WebRTC Android 库。

## 关于认证

**重要说明：** 即使是公开的 GitHub 仓库，访问 GitHub Packages **仍然需要认证**。这是 GitHub 的安全策略，所有对 GitHub Packages 的访问（包括读取公开包）都需要使用 GitHub Token 进行认证。

对于公开仓库，只需要有读取权限的 token 即可（不需要写权限）。

## 前置要求

1. **GitHub Personal Access Token (PAT)**
   - 访问 https://github.com/settings/tokens
   - 点击 "Generate new token (classic)"
   - 选择 `read:packages` 权限
   - 生成并保存 token

2. **Gradle 版本**
   - 确保使用 Gradle 6.8 或更高版本

## 配置步骤

### 1. 配置 Maven 仓库

#### 使用 build.gradle (Groovy DSL)

在项目的根目录下的 `build.gradle` 文件中添加 GitHub Packages 仓库：

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        
        // 添加 GitHub Packages 仓库
        maven {
            url = uri("https://maven.pkg.github.com/hzexe/webrtc")
            credentials {
                username = "YOUR_GITHUB_USERNAME"
                password = "YOUR_GITHUB_TOKEN"
            }
        }
    }
}
```

#### 使用 build.gradle.kts (Kotlin DSL)

在项目的根目录下的 `build.gradle.kts` 文件中添加 GitHub Packages 仓库：

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        
        // 添加 GitHub Packages 仓库
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPOSITORY")
            credentials {
                username = "YOUR_GITHUB_USERNAME"
                password = "YOUR_GITHUB_TOKEN"
            }
        }
    }
}
```

**注意：** 
- 将 `YOUR_USERNAME` 替换为你的 GitHub 用户名
- 将 `YOUR_REPOSITORY` 替换为 WebRTC 仓库名称
- 将 `YOUR_GITHUB_TOKEN` 替换为你的 GitHub Personal Access Token

**安全提示：** 不要将 token 直接提交到代码仓库中。建议使用以下方式之一：

#### 方式一：使用 local.properties 文件（推荐）

在 `local.properties` 文件中添加：
```properties
github.username=YOUR_GITHUB_USERNAME
github.token=YOUR_GITHUB_TOKEN
```

**使用 build.gradle (Groovy DSL) 读取：**
```gradle
def githubProps = new Properties()
file("local.properties").withInputStream { githubProps.load(it) }

allprojects {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPOSITORY")
            credentials {
                username = githubProps.getProperty("github.username")
                password = githubProps.getProperty("github.token")
            }
        }
    }
}
```

**使用 build.gradle.kts (Kotlin DSL) 读取：**
```kotlin
val githubProps = Properties()
file("local.properties").inputStream().use { githubProps.load(it) }

allprojects {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPOSITORY")
            credentials {
                username = githubProps.getProperty("github.username")
                password = githubProps.getProperty("github.token")
            }
        }
    }
}
```

#### 方式二：使用环境变量

在 `~/.bashrc` 或 `~/.zshrc` 中添加：
```bash
export GITHUB_USERNAME="YOUR_GITHUB_USERNAME"
export GITHUB_TOKEN="YOUR_GITHUB_TOKEN"
```

**使用 build.gradle (Groovy DSL) 读取：**
```gradle
allprojects {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPOSITORY")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

**使用 build.gradle.kts (Kotlin DSL) 读取：**
```kotlin
allprojects {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPOSITORY")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### 2. 添加依赖

#### 使用 app/build.gradle (Groovy DSL)

在 `app/build.gradle` 文件中添加 WebRTC 依赖：

```gradle
dependencies {
    // 选择适合的架构版本
    
    // ARM64 架构（推荐用于大多数现代 Android 设备）
    implementation 'com.webrtc:libwebrtc-arm64:VERSION'
    
    // x86_64 架构（用于模拟器或特定设备）
    // implementation 'com.webrtc:libwebrtc-x64:VERSION'
    
    // Universal 版本（包含所有架构，文件较大）
    // implementation 'com.webrtc:libwebrtc-universal:VERSION'
}
```

#### 使用 app/build.gradle.kts (Kotlin DSL)

在 `app/build.gradle.kts` 文件中添加 WebRTC 依赖：

```kotlin
dependencies {
    // 选择适合的架构版本
    
    // ARM64 架构（推荐用于大多数现代 Android 设备）
    implementation("com.webrtc:libwebrtc-arm64:VERSION")
    
    // x86_64 架构（用于模拟器或特定设备）
    // implementation("com.webrtc:libwebrtc-x64:VERSION")
    
    // Universal 版本（包含所有架构，文件较大）
    // implementation("com.webrtc:libwebrtc-universal:VERSION")
}
```

**注意：** 将 `VERSION` 替换为实际的版本号，例如：`branch-heads-7605-1`

### 3. 同步项目

在 Android Studio 中点击 "Sync Now" 或运行以下命令：

```bash
./gradlew clean build
```

## 版本号说明

版本号格式为：`{WEBRTC_BRANCH}-{RUN_NUMBER}`

例如：
- `branch-heads-7605-1` - WebRTC 分支 branch-heads/7605，第 1 次构建
- `branch-heads-7605-2` - WebRTC 分支 branch-heads/7605，第 2 次构建

查看可用版本：
1. 访问 GitHub 仓库的 Packages 页面
2. 选择 `com.webrtc` 组织
3. 查看可用的包版本

## 架构选择建议

| 架构 | 适用场景 | 文件大小 |
|------|---------|---------|
| `libwebrtc-arm64` | 大多数现代 Android 设备 | ~8-12 MB |
| `libwebrtc-x64` | Android 模拟器、x86 设备 | ~8-12 MB |
| `libwebrtc-universal` | 需要支持多种架构 | ~15-20 MB |

**推荐做法：**
- 生产环境使用 `libwebrtc-arm64`（覆盖 90%+ 的设备）
- 开发测试使用 `libwebrtc-x64`（在模拟器上测试）
- 如果需要同时支持多种架构，使用 `libwebrtc-universal`

## 完整示例

### 使用 Groovy DSL (build.gradle)

#### app/build.gradle

```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.example.webrtcdemo'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.webrtcdemo"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    
    // WebRTC 依赖
    implementation 'com.webrtc:libwebrtc-arm64:branch-heads-7605-1'
}
```

#### 根目录 build.gradle

```gradle
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        
        // GitHub Packages 仓库
        def githubProps = new Properties()
        file("local.properties").withInputStream { githubProps.load(it) }
        
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPOSITORY")
            credentials {
                username = githubProps.getProperty("github.username")
                password = githubProps.getProperty("github.token")
            }
        }
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

### 使用 Kotlin DSL (build.gradle.kts)

#### app/build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.webrtcdemo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.webrtcdemo"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // WebRTC 依赖
    implementation("com.webrtc:libwebrtc-arm64:branch-heads-7605-1")
}
```

#### 根目录 build.gradle.kts

```kotlin
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        
        // GitHub Packages 仓库
        val githubProps = Properties()
        file("local.properties").inputStream().use { githubProps.load(it) }
        
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPOSITORY")
            credentials {
                username = githubProps.getProperty("github.username")
                password = githubProps.getProperty("github.token")
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
```

### local.properties（不要提交到 Git）

```properties
## This file must *NOT* be checked into Version Control Systems,
# as it contains information specific to your local configuration.
#
# Location of the SDK. This is only used by Gradle.
# For customization when using a Version Control System, please read the
# header note.
#sdk.dir=/path/to/android/sdk

# GitHub Packages 认证信息
github.username=YOUR_GITHUB_USERNAME
github.token=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## 使用示例

### Java 代码示例

```java
import org.webrtc.*;

public class WebRTCAudioProcessor {
    private AudioProcessing audioProcessing;
    
    public void initializeAudioProcessing() {
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

### Kotlin 代码示例

```kotlin
import org.webrtc.*

class WebRTCAudioProcessor {
    private var audioProcessing: AudioProcessing? = null
    
    fun initializeAudioProcessing() {
        val builder = AudioProcessingBuilder()
        
        // 启用回声消除
        val echoCancellation = builder.createEchoCancellation()
        echoCancellation.enable(true)
        echoCancellation.setMobileMode(true)
        
        // 启用降噪
        val noiseSuppression = builder.createNoiseSuppression()
        noiseSuppression.enable(true)
        noiseSuppression.level = NoiseSuppression.Level.HIGH
        
        // 启用语音活动检测
        val voiceDetection = builder.createVoiceDetection()
        voiceDetection.enable(true)
        voiceDetection.likelihood = VoiceDetection.Likelihood.HIGH
        
        audioProcessing = builder.createAudioProcessing()
    }
    
    fun processAudioFrame(frame: AudioFrame) {
        audioProcessing?.processFrame(frame)
    }
    
    fun release() {
        audioProcessing?.release()
        audioProcessing = null
    }
}
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

## ProGuard 配置

在 `proguard-rules.pro` 中添加：

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

### Q1: 编译时出现 "Could not find com.webrtc:libwebrtc-arm64:VERSION"

**A:** 检查以下几点：
- 确认 GitHub Packages 仓库 URL 正确
- 确认 GitHub Token 有 `read:packages` 权限
- 确认版本号正确
- 确认网络连接正常

### Q2: 认证失败 "401 Unauthorized"

**A:** 检查以下几点：
- 确认 GitHub Token 有效且未过期
- 确认 Token 有 `read:packages` 权限
- 确认用户名和 Token 配置正确

### Q3: 如何查看可用的版本？

**A:** 访问以下 URL：
```
https://github.com/hzexe/webrtc/packages
```

### Q4: 是否支持多架构打包？

**A:** 是的，提供了三种架构版本：
- `libwebrtc-arm64` - ARM64 架构
- `libwebrtc-x64` - x86_64 架构
- `libwebrtc-universal` - 通用版本（包含所有架构）

### Q5: 如何在团队中共享依赖配置？

**A:** 建议使用以下方式：
1. 在 CI/CD 环境中使用环境变量
2. 在团队中共享 `local.properties.template` 文件（不包含敏感信息）
3. 使用 Gradle 的 `init.gradle` 全局配置

### Q6: GitHub Token 有有效期限制吗？

**A:** 是的，GitHub Token 有有效期限制：
- Classic Token 可以设置 30 天、90 天、180 天或无过期时间
- 建议定期更新 Token 以提高安全性

## CI/CD 配置示例

### GitHub Actions 示例

```yaml
name: Android CI

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
      
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      
      - name: Build with Gradle
        run: ./gradlew build
        env:
          GITHUB_USERNAME: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## 相关文档

- [Android 安装指南](ANDROID_INSTALL_GUIDE.md)
- [GitHub Packages 官方文档](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
- [WebRTC 官方文档](https://webrtc.org/)

## 更新日志

- 添加 GitHub Packages 支持
- 提供 ARM64、x86_64 和 Universal 三种架构版本
- 支持 Maven 依赖管理
- 提供完整的配置和使用示例
