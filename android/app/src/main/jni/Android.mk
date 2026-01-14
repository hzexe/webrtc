LOCAL_PATH := $(call my-dir)

# 预编译 DeepFilterNet ONNX Runtime Rust 静态库
# Rust 直接导出 JNI 函数，无需额外的 C++ 桥接
include $(CLEAR_VARS)
LOCAL_MODULE := deepfilter_ort
LOCAL_SRC_FILES := libdeepfilter_ort.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_LDLIBS := -llog -landroid
include $(PREBUILT_STATIC_LIBRARY)
