package com.webrtc.audio;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;

/**
 * DeepFilterNet 降噪引擎 JNI 接口（基于 Tract 框架）
 * 
 * 功能说明：
 * 1. 加载 DeepFilterNet 模型（enc.onnx, erb_dec.onnx, df_dec.onnx）
 * 2. 提供音频降噪处理接口
 * 3. 支持实时音频流处理
 * 4. 使用 Tract 框架进行模型推理
 * 5. 支持纯降噪模式，保守降噪强度
 * 
 * @author WebRTC Team
 * @version 2.0
 */
public class DeepFilterNet {
    
    private static final String TAG = "DeepFilterNet";
    
    // JNI 库名称
    private static final String LIB_NAME = "deepfilter_ort";
    
    // 模型文件名（tar.gz 压缩包）
    private static final String MODEL_ARCHIVE = "DeepFilterNet3_ll_onnx.tgz";
    
    // 模型文件字节数组（tar.gz 压缩包）
    private byte[] modelBytes;
    
    // 原生句柄
    private long nativeHandle;
    
    // 是否已初始化
    private boolean initialized = false;
    
    // 静态初始化块：加载 JNI 库
    static {
        try {
            System.loadLibrary(LIB_NAME);
            Log.d(TAG, "JNI 库 " + LIB_NAME + " 加载成功");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "加载 JNI 库失败: " + e.getMessage());
        }
    }
    
    /**
     * 构造函数
     * 
     * @param context Android 上下文
     */
    public DeepFilterNet(Context context) {
        loadModelFromAssets(context);
    }
    
    /**
     * 从 assets 直接加载模型文件为字节数组
     * 
     * @param context Android 上下文
     */
    private void loadModelFromAssets(Context context) {
        try (InputStream is = context.getAssets().open(MODEL_ARCHIVE)) {
            int fileSize = is.available();
            modelBytes = new byte[fileSize];
            
            int bytesRead = 0;
            int offset = 0;
            while (offset < fileSize && (bytesRead = is.read(modelBytes, offset, fileSize - offset)) != -1) {
                offset += bytesRead;
            }
            
            if (offset != fileSize) {
                Log.e(TAG, "读取模型文件不完整: 已读 " + offset + " 字节，期望 " + fileSize + " 字节");
                modelBytes = null;
            } else {
                Log.d(TAG, "模型文件加载成功，大小: " + fileSize + " 字节");
            }
        } catch (IOException e) {
            Log.e(TAG, "加载模型文件失败: " + e.getMessage());
            modelBytes = null;
        }
    }
    
    /**
     * 初始化 DeepFilterNet 引擎
     * 
     * @param postFilterBeta 后滤波器 beta 参数（控制降噪强度）
     * @param attenLimDb 衰减限制（dB）
     * @return true-初始化成功，false-初始化失败
     */
    public boolean initialize(float postFilterBeta, float attenLimDb) {
        if (modelBytes == null) {
            Log.e(TAG, "模型文件未加载，无法初始化");
            return false;
        }
        
        try {
            nativeHandle = nativeCreate(modelBytes, postFilterBeta, attenLimDb);
            if (nativeHandle != 0) {
                initialized = true;
                Log.d(TAG, "DeepFilterNet 初始化成功，句柄: " + nativeHandle);
                return true;
            } else {
                Log.e(TAG, "DeepFilterNet 初始化失败，返回句柄为 0");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 初始化 DeepFilterNet 引擎（使用默认参数）
     * 
     * @return true-初始化成功，false-初始化失败
     */
    public boolean initialize() {
        // 使用默认参数：保守降噪强度
        return initialize(0.5f, 12.0f);
    }
    
    /**
     * 处理一帧音频数据（f32 格式）
     * 
     * @param input 输入音频数据（f32，小端序，单声道）
     * @param output 输出音频数据（f32，小端序，单声道）
     * @return LSNR 值（负数表示失败）
     */
    public float process(byte[] input, byte[] output) {
        if (!initialized) {
            Log.e(TAG, "引擎未初始化，无法处理音频");
            return -1.0f;
        }
        
        if (input == null || output == null) {
            Log.e(TAG, "输入或输出缓冲区为空");
            return -1.0f;
        }
        
        if (input.length != output.length) {
            Log.e(TAG, "输入和输出缓冲区长度不匹配");
            return -1.0f;
        }
        
        // 计算帧大小（字节数 / 4，因为每个 float 占 4 字节）
        int frameSize = input.length / 4;
        // 直接使用偏移量和长度参数，避免内存复制
        return nativeProcess(nativeHandle, input, 0, input.length, output, 0, frameSize);
    }
    
    /**
     * 处理一帧音频数据（float 数组）
     * 
     * @param input 输入音频数据（float，单声道）
     * @param output 输出音频数据（float，单声道）
     * @return LSNR 值（负数表示失败）
     */
    public float process(float[] input, float[] output) {
        if (!initialized) {
            Log.e(TAG, "引擎未初始化，无法处理音频");
            return -1.0f;
        }
        
        if (input == null || output == null) {
            Log.e(TAG, "输入或输出缓冲区为空");
            return -1.0f;
        }
        
        if (input.length != output.length) {
            Log.e(TAG, "输入和输出缓冲区长度不匹配");
            return -1.0f;
        }
        
        // 将 float 数组转换为 byte 数组（小端序）
        byte[] inputBytes = floatArrayToByteArray(input);
        byte[] outputBytes = new byte[outputBytesLength(input.length)];
        
        // 使用偏移量和长度参数，避免内存复制
        float lsnr = nativeProcess(nativeHandle, inputBytes, 0, inputBytes.length, outputBytes, 0, input.length);
        
        if (lsnr >= 0.0f) {
            // 将输出 byte 数组转换回 float 数组
            byteArrayToFloatArray(outputBytes, output);
        }
        
        return lsnr;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (initialized && nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
            initialized = false;
            Log.d(TAG, "DeepFilterNet 资源已释放");
        }
    }
    
    /**
     * 检查是否已初始化
     * 
     * @return true-已初始化，false-未初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 将 float 数组转换为 byte 数组（小端序）
     * 
     * @param floats float 数组
     * @return byte 数组
     */
    private byte[] floatArrayToByteArray(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = Float.floatToIntBits(floats[i]);
            bytes[i * 4] = (byte) (bits & 0xff);
            bytes[i * 4 + 1] = (byte) ((bits >> 8) & 0xff);
            bytes[i * 4 + 2] = (byte) ((bits >> 16) & 0xff);
            bytes[i * 4 + 3] = (byte) ((bits >> 24) & 0xff);
        }
        return bytes;
    }
    
    /**
     * 将 byte 数组转换为 float 数组（小端序）
     * 
     * @param bytes byte 数组
     * @param floats 输出 float 数组
     */
    private void byteArrayToFloatArray(byte[] bytes, float[] floats) {
        for (int i = 0; i < floats.length; i++) {
            int bits = ((bytes[i * 4 + 3] & 0xff) << 24) |
                       ((bytes[i * 4 + 2] & 0xff) << 16) |
                       ((bytes[i * 4 + 1] & 0xff) << 8) |
                       (bytes[i * 4] & 0xff);
            floats[i] = Float.intBitsToFloat(bits);
        }
    }
    
    /**
     * 计算 float 数组对应的 byte 数组长度
     * 
     * @param floatCount float 数量
     * @return byte 数组长度
     */
    private int outputBytesLength(int floatCount) {
        return floatCount * 4;
    }
    
    // ===== JNI 原生方法声明 =====
    
    /**
     * 创建 DeepFilterNet 实例
     * 
     * @param modelBytes 模型压缩包字节数组（tar.gz）
     * @param postFilterBeta 后滤波器 beta 参数（控制降噪强度）
     * @param attenLimDb 衰减限制（dB）
     * @return 原生句柄（0 表示失败）
     */
    private native long nativeCreate(byte[] modelBytes, float postFilterBeta, float attenLimDb);
    
    /**
     * 处理一帧音频数据（优化版本 - 支持偏移量和长度参数）
     * 
     * @param handle 原生句柄
     * @param input 输入缓冲区（f32，小端序）
     * @param inputOffset 输入缓冲区起始偏移量（字节）
     * @param inputLength 输入缓冲区有效长度（字节）
     * @param output 输出缓冲区（f32，小端序）
     * @param outputOffset 输出缓冲区起始偏移量（字节）
     * @param frameSize 帧大小（采样点数）
     * @return LSNR 值（负数表示失败）
     */
    private native float nativeProcess(long handle, byte[] input, int inputOffset, int inputLength, 
                                       byte[] output, int outputOffset, int frameSize);
    
    /**
     * 销毁 DeepFilterNet 实例
     * 
     * @param handle 原生句柄
     */
    private native void nativeDestroy(long handle);
}
