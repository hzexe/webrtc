#!/bin/bash

################################################################################
# WebRTC Android 本地编译脚本
# 
# 功能说明：
# - 编译 WebRTC for Android，包含回声消除(AEC)、降噪(NS)、语音活动检测(VAD)
# - 支持 arm64-v8a 和 x86_64 两种架构
# - 生成 AAR 包和静态库
# - 生成 Java API 文档
# - 创建安装文档和版本信息
# - 支持发布到 GitHub Release
#
# 使用方法：
#   chmod +x build-webrtc-android.sh
#   ./build-webrtc-android.sh [分支名称] [--publish]
#
# 参数说明：
#   分支名称 - WebRTC 分支，例如 7605、7604、main、lkgr（默认：7605）
#   --publish - 可选，发布到 GitHub Release（需要配置 GitHub token）
#
# 系统要求：
#   - Linux Debian/Ubuntu 系统
#   - 至少 20GB 可用磁盘空间
#   - 8GB 以上内存
#   - 稳定的网络连接（用于下载源码）
#   - gh CLI 工具（用于发布到 GitHub，可选）
#
# 发布到 GitHub 要求：
#   - 已安装 gh CLI 工具
#   - 已执行 gh auth login 进行认证
#   - 有权限创建 Release 的 GitHub token
################################################################################

set -e  # 遇到错误立即退出

################################################################################
# 配置变量
################################################################################

# WebRTC 分支（可通过命令行参数覆盖）
# 可选分支说明：
#   - 7606: Chrome 7606 里程碑分支（2025年，推荐，稳定）
#   - 7605: Chrome 7605 里程碑分支（2024年，推荐，稳定）
#   - 7604: Chrome 7604 里程碑分支（2024年，稳定）
#   - 7599: Chrome 7599 里程碑分支（2024年，稳定）
#   - main: 主分支，包含最新功能和修复（较新，但可能有未测试的更改）
#   - lkgr: 最后已知的良好版本（稳定，相对较新）
#   - master: 旧的主分支名称，与 main 基本相同
#   - m73-m79: 旧里程碑分支（2019年，较旧）
#
# 推荐使用 7605，因为它是较新的稳定版本，适合生产环境
# 注意：传入分支名称即可，如 7605、7604、main、lkgr 等
WEBRTC_BRANCH="${1:-branch-heads/7606}"

# 工作目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEBRTC_SRC_DIR="${SCRIPT_DIR}/webrtc_android"
OUTPUT_DIR="${SCRIPT_DIR}/output"

# 是否发布到 GitHub
PUBLISH_TO_GITHUB=false

# depot_tools 路径
DEPOT_TOOLS_DIR="${HOME}/depot_tools"

# 编译并行任务数（ninja -j 参数）
# 设置为 0 表示使用所有可用的CPU核心
# 可以根据系统资源调整，例如：4, 8, 16 等
NINJA_JOBS=0

# 编译架构列表
ARCHITECTURES=("arm64" "x64")

# 编译参数（GN args）
# 说明：
# - target_os/target_cpu: 目标操作系统和CPU架构
# - is_debug=false: Release 模式编译
# - is_component_build=false: 静态链接构建
# - rtc_include_tests=false: 不包含测试代码
# - rtc_build_examples=false: 不包含示例代码
# - rtc_enable_protobuf=true: 启用 Protobuf 支持
# - use_custom_libcxx=true: 使用自定义 libc++
# - treat_warnings_as_errors=false: 警告不视为错误
# - rtc_enable_android_opensl=true: 启用 Android OpenSL ES 音频
# - rtc_enable_android_aaudio=true: 启用 Android AAudio 音频
# - rtc_enable_libaom=false: 禁用 AV1 视频编解码器
# - rtc_enable_libvpx=false: 禁用 VP8/VP9 视频编解码器
# - rtc_enable_h264=false: 禁用 H.264 视频编解码器
# - rtc_enable_vp8=false: 禁用 VP8 视频编解码器
# - rtc_enable_vp9=false: 禁用 VP9 视频编解码器
# - rtc_enable_av1=false: 禁用 AV1 视频编解码器
# - rtc_enable_bwe_test_logging=false: 禁用带宽估计测试日志
# - rtc_enable_event_tracing=false: 禁用事件跟踪
# - rtc_enable_peerconnection=false: 禁用 PeerConnection（P2P）
# - rtc_enable_datachannel=false: 禁用数据通道
# - rtc_enable_sctp=false: 禁用 SCTP
# - rtc_include_builtin_audio_codecs=true: 启用内置音频编解码器（包含VAD支持）
# - use_rtti=false: 禁用 RTTI（减少二进制大小）
# - use_exceptions=false: 禁用异常（减少二进制大小）
# - symbol_level=0: 禁用调试符号（减小体积）
# - enable_pgo=false: 禁用 PGO（Android平台兼容性）
# - enable_nacl=false: 禁用 Native Client
# - enable_remoting=false: 禁用远程桌面
# - enable_widevine=false: 禁用 Widevine DRM

GN_ARGS_ARM64='target_os="android" target_cpu="arm64" is_debug=false is_component_build=false rtc_include_tests=false rtc_build_examples=false rtc_enable_protobuf=true use_custom_libcxx=true treat_warnings_as_errors=false rtc_enable_android_opensl=true rtc_enable_android_aaudio=true rtc_enable_libaom=false rtc_enable_libvpx=false rtc_enable_h264=false rtc_enable_vp8=false rtc_enable_vp9=false rtc_enable_av1=false rtc_enable_bwe_test_logging=false rtc_enable_event_tracing=false rtc_enable_peerconnection=false rtc_enable_datachannel=false rtc_enable_sctp=false rtc_include_builtin_audio_codecs=true use_rtti=false use_exceptions=false symbol_level=0 enable_pgo=false enable_nacl=false enable_remoting=false enable_widevine=false'

GN_ARGS_X64='target_os="android" target_cpu="x64" is_debug=false is_component_build=false rtc_include_tests=false rtc_build_examples=false rtc_enable_protobuf=true use_custom_libcxx=true treat_warnings_as_errors=false rtc_enable_android_opensl=true rtc_enable_android_aaudio=true rtc_enable_libaom=false rtc_enable_libvpx=false rtc_enable_h264=false rtc_enable_vp8=false rtc_enable_vp9=false rtc_enable_av1=false rtc_enable_bwe_test_logging=false rtc_enable_event_tracing=false rtc_enable_peerconnection=false rtc_enable_datachannel=false rtc_enable_sctp=false rtc_include_builtin_audio_codecs=true use_rtti=false use_exceptions=false symbol_level=0 enable_pgo=false enable_nacl=false enable_remoting=false enable_widevine=false'

################################################################################
# 颜色输出函数
################################################################################

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

################################################################################
# 检查系统环境
################################################################################

check_environment() {
    print_header "检查系统环境"
    
    # 检查操作系统
    if [ ! -f /etc/debian_version ]; then
        print_error "此脚本仅支持 Debian/Ubuntu 系统"
        exit 1
    fi
    
    # 检查磁盘空间
    available_space=$(df -BG . | tail -1 | awk '{print $4}' | sed 's/G//')
    if [ "$available_space" -lt 20 ]; then
        print_error "磁盘空间不足，至少需要 20GB，当前可用: ${available_space}GB"
        exit 1
    fi
    print_success "磁盘空间检查通过: ${available_space}GB 可用"
    
    # 检查内存
    total_memory=$(free -g | awk '/^Mem:/{print $2}')
    if [ "$total_memory" -lt 8 ]; then
        print_warning "内存不足 8GB，编译可能会很慢或失败"
    else
        print_success "内存检查通过: ${total_memory}GB"
    fi
    
    # 检查网络连接
    if ! ping -c 1 -W 2 google.com > /dev/null 2>&1; then
        print_warning "网络连接检查失败，请确保可以访问 Google 服务"
    else
        print_success "网络连接检查通过"
    fi
}

################################################################################
# 安装依赖
################################################################################

install_dependencies() {
    print_header "安装系统依赖"
    
    # 更新软件包列表
    print_info "更新软件包列表..."
    sudo apt-get update
    
    # 安装必要的软件包
    print_info "安装编译依赖..."
    sudo apt-get install -y \
        git \
        curl \
        unzip \
        python3 \
        python3-pip \
        build-essential \
        openjdk-11-jdk \
        ninja-build \
        pkg-config
    
    print_success "依赖安装完成"
}

################################################################################
# 安装 depot_tools
################################################################################

install_depot_tools() {
    print_header "安装 depot_tools"
    
    if [ -d "$DEPOT_TOOLS_DIR" ]; then
        print_info "depot_tools 已存在，跳过安装"
    else
        print_info "正在下载 depot_tools..."
        cd "$HOME"
        git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
        
        # 添加到 PATH
        if ! grep -q "$DEPOT_TOOLS_DIR" ~/.bashrc 2>/dev/null; then
            echo "export PATH=\"\$PATH:$DEPOT_TOOLS_DIR\"" >> ~/.bashrc
            print_info "已将 depot_tools 添加到 ~/.bashrc"
        fi
        
        export PATH="$PATH:$DEPOT_TOOLS_DIR"
        print_success "depot_tools 安装完成"
    fi
    
    # 验证安装
    if command -v gclient &> /dev/null; then
        print_success "gclient 命令可用"
    else
        print_error "gclient 命令不可用，请检查 depot_tools 安装"
        exit 1
    fi
}

################################################################################
# 配置 Git
################################################################################

configure_git() {
    print_header "配置 Git"
    
    git config --global user.name "WebRTC Builder"
    git config --global user.email "builder@webrtc.local"
    
    print_success "Git 配置完成"
}

################################################################################
# 同步 WebRTC 源码
################################################################################

sync_webrtc_source() {
    print_header "同步 WebRTC 源码"
    
    if [ -d "$WEBRTC_SRC_DIR/src" ]; then
        print_info "WebRTC 源码已存在，跳过下载"
        print_info "如需重新下载，请删除目录: $WEBRTC_SRC_DIR"
        return
    fi
    
    print_info "开始同步 WebRTC 源码 (分支: $WEBRTC_BRANCH)..."
    print_warning "这可能需要较长时间（取决于网络速度）"
    
    # 创建工作目录
    mkdir -p "$WEBRTC_SRC_DIR"
    cd "$WEBRTC_SRC_DIR"
    
    # 初始化 depot_tools
    print_info "执行 fetch webrtc_android..."
    fetch --nohooks webrtc_android
    
    # 切换到指定分支
    print_info "切换到分支 $WEBRTC_BRANCH..."
    cd src
    git fetch origin
    
    # 尝试检出分支（按优先级尝试不同的分支格式）
    BRANCH_FOUND=false
    
    # 1. 尝试里程碑分支格式：branch-heads/7605
    if ! $BRANCH_FOUND && git checkout -b "$WEBRTC_BRANCH" "origin/branch-heads/$WEBRTC_BRANCH" 2>/dev/null; then
        print_success "成功切换到分支 branch-heads/$WEBRTC_BRANCH"
        BRANCH_FOUND=true
    fi
    
    # 2. 尝试 origin 前缀格式：origin/main
    if ! $BRANCH_FOUND && git checkout -b "$WEBRTC_BRANCH" "origin/$WEBRTC_BRANCH" 2>/dev/null; then
        print_success "成功切换到分支 origin/$WEBRTC_BRANCH"
        BRANCH_FOUND=true
    fi
    
    # 3. 尝试本地分支
    if ! $BRANCH_FOUND && git checkout "$WEBRTC_BRANCH" 2>/dev/null; then
        print_success "成功切换到本地分支 $WEBRTC_BRANCH"
        BRANCH_FOUND=true
    fi
    
    # 如果所有尝试都失败
    if ! $BRANCH_FOUND; then
        print_error "无法切换到分支 $WEBRTC_BRANCH"
        print_info "可用的分支:"
        git branch -r | grep -v HEAD
        exit 1
    fi
    
    # 运行 hooks
    print_info "执行 gclient sync..."
    gclient sync

    print_success "WebRTC 源码同步完成"
}

################################################################################
# 编译指定架构
################################################################################

build_architecture() {
    local arch=$1
    local gn_args=$2
    
    print_header "编译 $arch 架构"
    
    cd "$WEBRTC_SRC_DIR/src"
    
    # 生成构建配置
    print_info "生成构建配置..."
    gn gen "out/android_$arch" --args="$gn_args"
    
    # 编译（使用多核优化）
    print_info "开始编译（这可能需要较长时间，使用 $NINJA_JOBS 个并行任务）..."
    if [ "$NINJA_JOBS" -eq 0 ]; then
        print_info "使用所有可用的CPU核心进行编译"
        ninja -C "out/android_$arch" libwebrtc
    else
        print_info "使用 $NINJA_JOBS 个CPU核心进行编译"
        ninja -C "out/android_$arch" -j "$NINJA_JOBS" libwebrtc
    fi
    
    print_success "$arch 架构编译完成"
}

################################################################################
# 收集编译产物
################################################################################

collect_artifacts() {
    print_header "收集编译产物"
    
    # 创建输出目录
    mkdir -p "$OUTPUT_DIR"/{arm64,x64,universal}
    
    # 复制 arm64 AAR
    if [ -f "$WEBRTC_SRC_DIR/src/out/android_arm64/lib.java/sdk/android/libwebrtc/libwebrtc.aar" ]; then
        cp "$WEBRTC_SRC_DIR/src/out/android_arm64/lib.java/sdk/android/libwebrtc/libwebrtc.aar" \
           "$OUTPUT_DIR/arm64/libwebrtc_arm64.aar"
        print_success "复制 arm64 AAR"
    else
        print_warning "未找到 arm64 AAR"
    fi
    
    # 复制 x64 AAR
    if [ -f "$WEBRTC_SRC_DIR/src/out/android_x64/lib.java/sdk/android/libwebrtc/libwebrtc.aar" ]; then
        cp "$WEBRTC_SRC_DIR/src/out/android_x64/lib.java/sdk/android/libwebrtc/libwebrtc.aar" \
           "$OUTPUT_DIR/x64/libwebrtc_x64.aar"
        print_success "复制 x64 AAR"
    else
        print_warning "未找到 x64 AAR"
    fi
    
    # 复制 arm64 静态库
    if [ -f "$WEBRTC_SRC_DIR/src/out/android_arm64/obj/libwebrtc.a" ]; then
        cp "$WEBRTC_SRC_DIR/src/out/android_arm64/obj/libwebrtc.a" \
           "$OUTPUT_DIR/arm64/libwebrtc_arm64.a"
        print_success "复制 arm64 静态库"
    else
        print_warning "未找到 arm64 静态库"
    fi
    
    # 复制 x64 静态库
    if [ -f "$WEBRTC_SRC_DIR/src/out/android_x64/obj/libwebrtc.a" ]; then
        cp "$WEBRTC_SRC_DIR/src/out/android_x64/obj/libwebrtc.a" \
           "$OUTPUT_DIR/x64/libwebrtc_x64.a"
        print_success "复制 x64 静态库"
    else
        print_warning "未找到 x64 静态库"
    fi
    
    # 创建通用 AAR（合并多架构）
    create_universal_aar
    
    # 列出输出文件
    print_info "编译产物列表:"
    find "$OUTPUT_DIR" -type f \( -name "*.aar" -o -name "*.a" \) | while read -r file; do
        echo "  $(basename "$file") - $(du -h "$file" | cut -f1)"
    done
}

################################################################################
# 创建通用 AAR
################################################################################

create_universal_aar() {
    print_info "创建通用 AAR..."
    
    TEMP_DIR=$(mktemp -d)
    
    # 解压 arm64 AAR
    if [ -f "$OUTPUT_DIR/arm64/libwebrtc_arm64.aar" ]; then
        mkdir -p "$TEMP_DIR/arm64"
        cd "$TEMP_DIR/arm64"
        unzip -q "$OUTPUT_DIR/arm64/libwebrtc_arm64.aar"
        cd -
    fi
    
    # 解压 x64 AAR
    if [ -f "$OUTPUT_DIR/x64/libwebrtc_x64.aar" ]; then
        mkdir -p "$TEMP_DIR/x64"
        cd "$TEMP_DIR/x64"
        unzip -q "$OUTPUT_DIR/x64/libwebrtc_x64.aar"
        cd -
    fi
    
    # 合并 JNI 库
    mkdir -p "$TEMP_DIR/universal/jni"
    if [ -d "$TEMP_DIR/arm64/jni" ]; then
        cp -r "$TEMP_DIR/arm64/jni"/* "$TEMP_DIR/universal/jni/"
    fi
    if [ -d "$TEMP_DIR/x64/jni" ]; then
        cp -r "$TEMP_DIR/x64/jni"/* "$TEMP_DIR/universal/jni/"
    fi
    
    # 复制其他必要文件
    if [ -d "$TEMP_DIR/arm64" ]; then
        find "$TEMP_DIR/arm64" -maxdepth 1 -type f -exec cp {} "$TEMP_DIR/universal/" \;
    fi
    
    # 打包通用 AAR
    cd "$TEMP_DIR/universal"
    zip -q -r "$OUTPUT_DIR/universal/libwebrtc_universal.aar" *
    cd -
    
    rm -rf "$TEMP_DIR"
    
    print_success "通用 AAR 创建完成"
}

################################################################################
# 创建版本信息文件
################################################################################

create_version_info() {
    print_info "创建版本信息文件..."
    
    cat > "$OUTPUT_DIR/version_info.txt" << EOF
WebRTC Android 编译信息
=======================
编译时间: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
WebRTC 分支: $WEBRTC_BRANCH
编译主机: $(hostname)
用户: $(whoami)

包含功能:
- 回声消除 (AEC)
- 降噪 (NS)
- 语音活动检测 (VAD)

已移除功能:
- 视频编解码器 (VP8, VP9, H.264, AV1)
- P2P 网络功能 (PeerConnection, DataChannel, SCTP)

支持架构:
- arm64-v8a
- x86_64

编译参数:
- is_debug=false (Release 模式)
- is_component_build=false (静态链接)
- use_rtti=false (禁用 RTTI)
- use_exceptions=false (禁用异常)
EOF
    
    print_success "版本信息文件创建完成"
}

################################################################################
# 复制安装文档
################################################################################

copy_install_guide() {
    print_info "复制安装文档..."
    
    if [ -f "$SCRIPT_DIR/docs/ANDROID_INSTALL_GUIDE.md" ]; then
        cp "$SCRIPT_DIR/docs/ANDROID_INSTALL_GUIDE.md" "$OUTPUT_DIR/ANDROID_INSTALL_GUIDE.md"
        
        # 替换模板变量
        sed -i "s/{{WEBRTC_BRANCH}}/$WEBRTC_BRANCH/g" "$OUTPUT_DIR/ANDROID_INSTALL_GUIDE.md"
        sed -i "s/{{BUILD_TIME}}/$(date -u +"%Y-%m-%d %H:%M:%S UTC")/g" "$OUTPUT_DIR/ANDROID_INSTALL_GUIDE.md"
        sed -i "s/{{RUN_NUMBER}}/local/g" "$OUTPUT_DIR/ANDROID_INSTALL_GUIDE.md"
        sed -i "s/{{COMMIT_SHA}}/$(cd "$WEBRTC_SRC_DIR/src" && git rev-parse HEAD)/g" "$OUTPUT_DIR/ANDROID_INSTALL_GUIDE.md"
        
        print_success "安装文档已复制"
    else
        print_warning "未找到安装文档: $SCRIPT_DIR/docs/ANDROID_INSTALL_GUIDE.md"
    fi
}

################################################################################
# 生成 JavaDoc
################################################################################

generate_javadoc() {
    print_header "生成 Java API 文档"
    
    # 创建 JavaDoc 输出目录
    JAVADOC_DIR="$OUTPUT_DIR/javadoc"
    mkdir -p "$JAVADOC_DIR"
    
    # 查找 Java 源文件
    JAVA_SRC_DIR="$WEBRTC_SRC_DIR/src/sdk/android/src/java"
    
    if [ ! -d "$JAVA_SRC_DIR" ]; then
        print_warning "Java 源代码目录不存在: $JAVA_SRC_DIR"
        print_warning "跳过 JavaDoc 生成"
        return
    fi
    
    print_info "找到 Java 源代码目录: $JAVA_SRC_DIR"
    
    # 收集所有 Java 源文件
    JAVA_FILES=$(find "$JAVA_SRC_DIR" -name "*.java" | grep -E "(AudioProcessing|EchoCancellation|NoiseSuppression|VoiceDetection)" | head -100)
    
    if [ -z "$JAVA_FILES" ]; then
        print_warning "未找到相关的 Java 源文件"
        print_warning "跳过 JavaDoc 生成"
        return
    fi
    
    print_info "找到 $(echo "$JAVA_FILES" | wc -l) 个 Java 文件"
    
    # 生成 JavaDoc
    print_info "正在生成 JavaDoc..."
    javadoc -d "$JAVADOC_DIR" \
        -encoding UTF-8 \
        -charset UTF-8 \
        -docencoding UTF-8 \
        -public \
        -use \
        -version \
        -author \
        -windowtitle "WebRTC Android Audio API" \
        -doctitle "WebRTC Android Audio API Documentation" \
        -header "WebRTC Android $WEBRTC_BRANCH" \
        -footer "Generated by Build Script" \
        -bottom "Copyright © $(date +%Y) WebRTC Project" \
        -link https://developer.android.com/reference \
        $JAVA_FILES 2>&1 || print_warning "JavaDoc 生成完成（可能有警告）"
    
    print_success "JavaDoc 生成完成"
    
    # 打包 JavaDoc
    print_info "打包 JavaDoc..."
    cd "$JAVADOC_DIR"
    zip -q -r "$OUTPUT_DIR/javadoc.zip" *
    cd -
    
    print_success "JavaDoc 已打包到: $OUTPUT_DIR/javadoc.zip"
}

################################################################################
# 显示编译结果
################################################################################

show_results() {
    print_header "编译完成"
    
    echo ""
    echo "========================================="
    echo "WebRTC Android 编译成功！"
    echo "========================================="
    echo ""
    echo "WebRTC 分支: $WEBRTC_BRANCH"
    echo "输出目录: $OUTPUT_DIR"
    echo ""
    echo "编译产物:"
    echo "  - arm64/libwebrtc_arm64.aar (ARM64 AAR 包)"
    echo "  - x64/libwebrtc_x64.aar (x86_64 AAR 包)"
    echo "  - universal/libwebrtc_universal.aar (通用 AAR 包)"
    echo "  - arm64/libwebrtc_arm64.a (ARM64 静态库)"
    echo "  - x64/libwebrtc_x64.a (x86_64 静态库)"
    echo "  - ANDROID_INSTALL_GUIDE.md (安装文档)"
    echo "  - javadoc.zip (Java API 文档)"
    echo "  - version_info.txt (版本信息)"
    echo ""
    echo "下一步:"
    echo "  1. 查看 ANDROID_INSTALL_GUIDE.md 了解如何集成"
    echo "  2. 解压 javadoc.zip 查看 Java API 文档"
    echo "  3. 根据设备架构下载对应的 AAR 文件"
    echo ""
}

################################################################################
# 主函数
################################################################################

main() {
    print_header "WebRTC Android 本地编译脚本"
    
    echo "配置信息:"
    echo "  WebRTC 分支: $WEBRTC_BRANCH"
    echo "  工作目录: $WEBRTC_SRC_DIR"
    echo "  输出目录: $OUTPUT_DIR"
    
    # 显示CPU核心数
    if [ -f /proc/cpuinfo ]; then
        CPU_CORES=$(nproc 2>/dev/null || echo "未知")
        echo "  CPU 核心数: $CPU_CORES"
    fi
    
    # 显示ninja并行任务数
    if [ "$NINJA_JOBS" -eq 0 ]; then
        echo "  Ninja 并行任务: 自动（使用所有可用核心）"
    else
        echo "  Ninja 并行任务: $NINJA_JOBS"
    fi
    
    echo ""
    
    # 创建输出目录（用于 Google Colab 环境）
    print_info "创建输出目录: $OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR"
    
    # 检查环境
    check_environment
    
    # 安装依赖
    install_dependencies
    
    # 安装 depot_tools
    install_depot_tools
    
    # 配置 Git
    configure_git
    
    # 同步源码
    sync_webrtc_source
    
    # 编译各架构
    build_architecture "arm64" "$GN_ARGS_ARM64"
    build_architecture "x64" "$GN_ARGS_X64"
    
    # 收集产物
    collect_artifacts
    
    # 创建版本信息
    create_version_info
    
    # 复制安装文档
    copy_install_guide
    
    # 生成 JavaDoc
    generate_javadoc
    
    # 显示结果
    show_results
}

################################################################################
# 执行主函数
################################################################################

main "$@"
