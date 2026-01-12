package com.webrtc.audio;

import android.content.Context;
import android.util.Log;

/**
 * WebRTC音频处理工厂类
 * 用于配置和创建WebRTC音频处理实例
 * 
 * @author WebRTC Team
 * @version 1.0
 */
public class WebRTCAudioProcessingFactory {
    
    private static final String TAG = "WebRTCAudioProcessingFactory";
    
    private Context context;
    private AudioProcessingConfig config;
    
    /**
     * 音频处理配置类
     */
    public static class AudioProcessingConfig {
        public boolean enableAEC = true;
        public boolean enableNS = true;
        public boolean enableAGC = true;
        public boolean enableVAD = true;
        
        private AudioProcessingConfig() {
        }
        
        /**
         * 创建高质量音频处理配置
         * 
         * @return 高质量音频处理配置
         */
        public static AudioProcessingConfig createHighQuality() {
            AudioProcessingConfig config = new AudioProcessingConfig();
            config.enableAEC = true;
            config.enableNS = true;
            config.enableAGC = true;
            config.enableVAD = true;
            return config;
        }
        
        /**
         * 创建低延迟音频处理配置
         * 
         * @return 低延迟音频处理配置
         */
        public static AudioProcessingConfig createLowLatency() {
            AudioProcessingConfig config = new AudioProcessingConfig();
            config.enableAEC = true;
            config.enableNS = true;
            config.enableAGC = false;
            config.enableVAD = true;
            return config;
        }
        
        /**
         * 创建仅VAD配置
         * 
         * @return 仅VAD配置
         */
        public static AudioProcessingConfig createVADOnly() {
            AudioProcessingConfig config = new AudioProcessingConfig();
            config.enableAEC = false;
            config.enableNS = false;
            config.enableAGC = false;
            config.enableVAD = true;
            return config;
        }
    }
    
    /**
     * 构造函数
     * 
     * @param context 上下文
     * @param config 音频处理配置
     */
    public WebRTCAudioProcessingFactory(Context context, AudioProcessingConfig config) {
        this.context = context;
        this.config = config;
    }
    
    /**
     * 生成原生音频处理配置
     * 
     * @return 原生音频处理配置字符串
     */
    public String generateNativeConfig() {
        return String.format("{\"aec\":%b,\"ns\":%b,\"agc\":%b,\"vad\":%b}",
                config.enableAEC,
                config.enableNS,
                config.enableAGC,
                config.enableVAD);
    }
    
    /**
     * 获取配置摘要
     * 
     * @return 配置摘要字符串
     */
    public String getConfigSummary() {
        return String.format("AEC:%s, NS:%s, AGC:%s, VAD:%s",
                config.enableAEC ? "ENABLED" : "DISABLED",
                config.enableNS ? "ENABLED" : "DISABLED",
                config.enableAGC ? "ENABLED" : "DISABLED",
                config.enableVAD ? "ENABLED" : "DISABLED");
    }
    
    /**
     * 更新配置
     * 
     * @param config 新的音频处理配置
     */
    public void updateConfig(AudioProcessingConfig config) {
        this.config = config;
        Log.d(TAG, "Audio processing config updated: " + getConfigSummary());
    }
    
    /**
     * 获取当前配置
     * 
     * @return 当前音频处理配置
     */
    public AudioProcessingConfig getConfig() {
        return config;
    }
}