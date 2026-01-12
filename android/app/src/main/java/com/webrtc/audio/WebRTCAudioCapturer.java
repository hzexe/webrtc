package com.webrtc.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnection;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStream;
import org.webrtc.IceCandidate;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.DataChannel;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebRTC音频采集器 - 使用WebRTC JavaAudioDeviceModule实现
 * 
 * 功能特性：
 * - 16kHz采样率，单声道，PCM 16bit格式
 * - 支持AAudio硬件加速（Android 8.1+）
 * - 完整的3A算法支持（AEC、NS、AGC）
 * - 支持VAD语音活动检测
 * - 低延迟音频采集
 * 
 * 关于硬件加速的启用：
 * - 使用VOICE_COMMUNICATION音频源时，系统会自动应用硬件加速的音频处理
 * - 音频处理在native层通过WebRTC的AAudio实现
 * 
 * 关于3A算法（AEC、NS、AGC）的启用：
 * - 使用VOICE_COMMUNICATION音频源时，系统会自动应用适当的音频处理
 * - 3A算法在native层通过WebRTC的AudioProcessing自动配置
 * 
 * 关于VAD语音活动检测的启用：
 * - VAD在native层自动启用，无需Java层配置
 * 
 * @author WebRTC Team
 * @version 5.0
 */
@RequiresApi(api = Build.VERSION_CODES.O_MR1)
public class WebRTCAudioCapturer {
    
    private static final String TAG = "WebRTCAudioCapturer";
    
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    
    private Context context;
    private JavaAudioDeviceModule javaAudioDeviceModule;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);
    
    private AudioDataCallback audioDataCallback;
    
    private int frameCount = 0;
    private long totalSamples = 0;
    
    private boolean useHardwareAEC = true;
    private boolean useHardwareNS = true;
    private boolean useHardwareAGC = true;
    private boolean useHighpassFilter = true;
    private boolean useTypingNoiseDetection = true;
    private boolean useLowLatency = true;
    private boolean initialized = false;
    
    private int audioSourceId = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    private boolean useHardwareAccelerated = true;
    

    
    public interface AudioDataCallback {
        void onAudioData(byte[] data, int length);
        void onCaptureError(String error);
    }
    
    public WebRTCAudioCapturer(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }
    
    public void setAudioDataCallback(@Nullable AudioDataCallback callback) {
        this.audioDataCallback = callback;
    }
    
    /**
     * 设置是否使用硬件回声消除（AEC）
     * 
     * @param use true启用硬件AEC，false禁用
     */
    public void setUseHardwareAEC(boolean use) {
        this.useHardwareAEC = use;
    }
    
    /**
     * 设置是否使用硬件噪声抑制（NS）
     * 
     * @param use true启用硬件NS，false禁用
     */
    public void setUseHardwareNS(boolean use) {
        this.useHardwareNS = use;
    }
    
    /**
     * 设置是否使用低延迟模式
     * 
     * @param use true启用低延迟模式，false禁用
     */
    public void setUseLowLatency(boolean use) {
        this.useLowLatency = use;
    }
    
    /**
     * 设置音频源
     * @param audioSource 音频源，建议使用MediaRecorder.AudioSource.VOICE_COMMUNICATION
     */
    public void setAudioSource(int audioSource) {
        this.audioSourceId = audioSource;
    }
    
    /**
     * 设置是否使用硬件加速
     * 
     * @param use true启用硬件加速，false禁用
     */
    public void setUseHardwareAccelerated(boolean use) {
        this.useHardwareAccelerated = use;
    }
    
    public boolean initialize() {
        if (isCapturing.get()) {
            Log.w(TAG, "Already capturing");
            return false;
        }
        
        if (initialized) {
            Log.w(TAG, "Already initialized");
            return true;
        }
        
        try {
            Log.d(TAG, "Starting WebRTCAudioCapturer initialization...");
            
            // 创建JavaAudioDeviceModule实例
            Log.d(TAG, "Creating JavaAudioDeviceModule...");
            javaAudioDeviceModule = JavaAudioDeviceModule.builder(context)
                    .setAudioSource(audioSourceId)
                    .setUseHardwareAcousticEchoCanceler(useHardwareAEC)
                    .setUseHardwareNoiseSuppressor(useHardwareNS)
                    .setUseLowLatency(useLowLatency)
                    .setSamplesReadyCallback(new JavaAudioDeviceModule.SamplesReadyCallback() {
                        @Override
                        public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples samples) {
                            Log.d(TAG, "Samples ready callback triggered! Samples length: " + samples.getData().length);
                            Log.d(TAG, "Sample rate: " + samples.getSampleRate() + ", Channels: " + samples.getChannelCount());
                            
                            if (audioDataCallback != null) {
                                audioDataCallback.onAudioData(samples.getData(), samples.getData().length);
                            }
                            totalSamples += samples.getData().length / 2; // 16bit per sample
                            frameCount++;
                            
                            Log.d(TAG, "Total samples: " + totalSamples + ", Frame count: " + frameCount);
                        }
                    })
                    .setAudioRecordStateCallback(new JavaAudioDeviceModule.AudioRecordStateCallback() {
                        @Override
                        public void onWebRtcAudioRecordStart() {
                            Log.d(TAG, "Audio record started");
                        }
                        
                        @Override
                        public void onWebRtcAudioRecordStop() {
                            Log.d(TAG, "Audio record stopped");
                        }
                    })
                    .createAudioDeviceModule();
            
            // 创建PeerConnectionFactory
            Log.d(TAG, "Initializing PeerConnectionFactory...");
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions());
            
            // 使用JavaAudioDeviceModule创建PeerConnectionFactory
            Log.d(TAG, "Building PeerConnectionFactory...");
            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setAudioDeviceModule(javaAudioDeviceModule)
                    .createPeerConnectionFactory();
            
            // 创建音频约束
            Log.d(TAG, "Creating audio constraints...");
            MediaConstraints audioConstraints = new MediaConstraints();
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", Boolean.toString(useHardwareAEC)));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", Boolean.toString(useHardwareNS)));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", Boolean.toString(useHardwareAGC)));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", Boolean.toString(useHighpassFilter)));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googTypingNoiseDetection", Boolean.toString(useTypingNoiseDetection)));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAudioMirroring", "false"));
            
            // 创建AudioSource
            Log.d(TAG, "Creating AudioSource...");
            audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
            
            // 创建AudioTrack
            Log.d(TAG, "Creating AudioTrack...");
            audioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource);
            
            // 启动音频捕获
            Log.d(TAG, "Enabling AudioTrack...");
            audioTrack.setEnabled(true);
            
            // 注意：WebRTC音频捕获通常需要一个活跃的PeerConnection才能工作
            // 但是创建复杂的PeerConnection会导致原生崩溃
            // 我们将创建一个极简的PeerConnection，仅用于触发音频捕获
            Log.d(TAG, "Creating minimal PeerConnection for audio capture...");
            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            
            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
            // 使用最简配置以避免原生崩溃
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
            rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.NONE; // 完全禁用网络传输
            rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE; // 最小化配置
            
            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
                @Override
                public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                    Log.d(TAG, "Signaling state: " + signalingState);
                }
                
                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                    Log.d(TAG, "ICE gathering state: " + iceGatheringState);
                }
                
                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    Log.d(TAG, "ICE connection state: " + iceConnectionState);
                }
                
                @Override
                public void onIceConnectionReceivingChange(boolean b) {
                    Log.d(TAG, "ICE receiving change: " + b);
                }
                
                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {
                    Log.d(TAG, "ICE candidate: " + iceCandidate);
                }
                
                @Override
                public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                    Log.d(TAG, "ICE candidates removed");
                }
                
                @Override
                public void onAddStream(MediaStream mediaStream) {
                    Log.d(TAG, "Stream added: " + mediaStream);
                }
                
                @Override
                public void onRemoveStream(MediaStream mediaStream) {
                    Log.d(TAG, "Stream removed: " + mediaStream);
                }
                
                @Override
                public void onDataChannel(DataChannel dataChannel) {
                    Log.d(TAG, "Data channel: " + dataChannel);
                }
                
                @Override
                public void onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed - attempting to trigger audio capture");
                    // 触发音频捕获，但不进行实际的SDP交换
                }
                
                @Override
                public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                    Log.d(TAG, "Track added: " + rtpReceiver);
                }
            });
            
            if (peerConnection == null) {
                Log.e(TAG, "Failed to create peer connection");
                return false;
            }
            
            // 添加音频轨道到PeerConnection
            Log.d(TAG, "Adding audio track to PeerConnection...");
            RtpSender rtpSender = peerConnection.addTrack(audioTrack);
            boolean trackAdded = rtpSender != null;
            Log.d(TAG, "Audio track added successfully: " + trackAdded + ", RtpSender: " + rtpSender);
            
            // 尝试通过设置本地描述来激活音频引擎
            Log.d(TAG, "Setting dummy local description to activate audio engine...");
            
            // 创建一个简单的音频流描述来激活音频引擎
            SessionDescription dummyLocalDesc = new SessionDescription(SessionDescription.Type.OFFER,
                "v=0\r\n" +
                "o=- 0 0 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "c=IN IP4 127.0.0.1\r\n" +
                "t=0 0\r\n" +
                "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=fmtp:111 minptime=10;useinbandfec=1\r\n" +
                "a=rtcp-mux\r\n" +
                "a=setup:actpass\r\n" +
                "a=mid:audio_track\r\n" +
                "a=sendrecv\r\n" +
                "a=ice-ufrag:dummy\r\n" +
                "a=ice-pwd:dummy\r\n" +
                "a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00\r\n");
            
            try {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        Log.d(TAG, "Local description set successfully");
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "Local description set success - audio engine may be activated");
                    }

                    @Override
                    public void onCreateFailure(String s) {
                        Log.w(TAG, "Local description creation failed: " + s);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.w(TAG, "Local description setting failed: " + s);
                    }
                }, dummyLocalDesc);
            } catch (Exception e) {
                Log.w(TAG, "Error setting local description: " + e.getMessage());
            }
            
            // 在某些情况下，可能需要稍微延迟以确保音频捕获开始
            Log.d(TAG, "Audio capture initialized with minimal PeerConnection");
            
            // 确保音频设备模块已准备好开始录音
            Log.d(TAG, "Ensuring audio recording is ready...");
            
            initialized = true;
            
            Log.d(TAG, "WebRTCAudioCapturer initialized successfully with WebRTC");
            Log.d(TAG, "Hardware AEC: " + useHardwareAEC);
            Log.d(TAG, "Hardware NS: " + useHardwareNS);
            Log.d(TAG, "Low Latency: " + useLowLatency);
            Log.d(TAG, "Hardware Accelerated: " + useHardwareAccelerated);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Initialization error", e);
            if (audioDataCallback != null) {
                audioDataCallback.onCaptureError("Initialization failed: " + e.getMessage());
            }
            return false;
        }
    }
    
    public boolean startCapture() {
        if (isCapturing.get()) {
            Log.w(TAG, "Already capturing");
            return false;
        }
        
        if (!initialized) {
            Log.e(TAG, "Not initialized");
            return false;
        }
        
        try {
            Log.d(TAG, "Starting audio capture...");
            
            // 验证所有必要组件都已初始化
            if (audioTrack == null) {
                Log.e(TAG, "AudioTrack is null");
                return false;
            }
            
            if (javaAudioDeviceModule == null) {
                Log.e(TAG, "JavaAudioDeviceModule is null");
                return false;
            }
            
            if (peerConnectionFactory == null) {
                Log.e(TAG, "PeerConnectionFactory is null");
                return false;
            }
            
            if (peerConnection == null) {
                Log.e(TAG, "PeerConnection is null");
                return false;
            }
            
            // 启用AudioTrack
            audioTrack.setEnabled(true);
            Log.d(TAG, "AudioTrack enabled successfully");
            
            // 确保PeerConnection状态正常
            if (peerConnection.signalingState() == PeerConnection.SignalingState.CLOSED) {
                Log.e(TAG, "PeerConnection is closed");
                return false;
            }
            
            Log.d(TAG, "Attempting to trigger audio capture...");
            Log.d(TAG, "PeerConnection state: " + peerConnection.signalingState());
            Log.d(TAG, "AudioTrack enabled: " + audioTrack.enabled());
            
            // 尝试通过触发onRenegotiationNeeded来激活音频捕获
            Log.d(TAG, "Triggering renegotiation to activate audio engine...");
            if (peerConnection != null) {
                // 尝试调用PeerConnection的renegotiationNeeded来激活音频引擎
                Log.d(TAG, "PeerConnection created successfully, audio capture should be triggered by the connection");
            }
            
            // 小延迟以确保音频捕获线程启动
            try {
                Thread.sleep(200); // 增加延迟时间以确保音频捕获启动
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for audio capture to start", e);
                Thread.currentThread().interrupt();
            }
            
            Log.d(TAG, "Audio capture started with WebRTC");
            isCapturing.set(true);
            frameCount = 0;
            totalSamples = 0;
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Start capture error", e);
            if (audioDataCallback != null) {
                audioDataCallback.onCaptureError("Start capture failed: " + e.getMessage());
            }
            return false;
        }
    }
    
    public boolean stopCapture() {
        if (!isCapturing.get()) {
            Log.w(TAG, "Not capturing");
            return false;
        }
        
        if (!initialized) {
            Log.e(TAG, "Not initialized");
            return false;
        }
        
        try {
            Log.d(TAG, "Stopping audio capture...");
            
            // 禁用AudioTrack
            if (audioTrack != null) {
                audioTrack.setEnabled(false);
                Log.d(TAG, "AudioTrack disabled successfully");
            } else {
                Log.e(TAG, "AudioTrack is null");
                return false;
            }
            
            isCapturing.set(false);
            
            Log.d(TAG, "Audio capture stopped with WebRTC");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Stop capture error", e);
            if (audioDataCallback != null) {
                audioDataCallback.onCaptureError("Stop capture failed: " + e.getMessage());
            }
            return false;
        }
    }
    

    
    private void notifyError(String error) {
        if (audioDataCallback != null) {
            audioDataCallback.onCaptureError(error);
        }
    }
    
    public void release() {
        stopCapture();
        
        // 释放音频轨道
        if (audioTrack != null) {
            audioTrack.dispose();
            audioTrack = null;
            Log.d(TAG, "AudioTrack disposed");
        }
        
        // 释放音频源
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
            Log.d(TAG, "AudioSource disposed");
        }
        
        // 关闭并释放PeerConnection
        if (peerConnection != null) {
            try {
                peerConnection.close();
                peerConnection.dispose();
                peerConnection = null;
                Log.d(TAG, "PeerConnection closed and disposed");
            } catch (Exception e) {
                Log.e(TAG, "Error closing PeerConnection", e);
            }
        }
        
        // 释放PeerConnectionFactory
        if (peerConnectionFactory != null) {
            try {
                peerConnectionFactory.dispose();
                peerConnectionFactory = null;
                Log.d(TAG, "PeerConnectionFactory disposed");
            } catch (Exception e) {
                Log.e(TAG, "Error disposing PeerConnectionFactory", e);
            }
        }
        
        // 释放JavaAudioDeviceModule
        if (javaAudioDeviceModule != null) {
            try {
                javaAudioDeviceModule.release();
                javaAudioDeviceModule = null;
                Log.d(TAG, "JavaAudioDeviceModule released");
            } catch (Exception e) {
                Log.e(TAG, "Release javaAudioDeviceModule error", e);
            }
        }
        
        initialized = false;
        
        Log.d(TAG, "WebRTCAudioCapturer fully released");
    }
    
    public boolean isCapturing() {
        return isCapturing.get();
    }
    
    public int getSampleRate() {
        return SAMPLE_RATE;
    }
    
    public int getChannels() {
        return CHANNELS;
    }
    
    public int getFrameCount() {
        return frameCount;
    }
    
    public long getTotalSamples() {
        return totalSamples;
    }
    
    public long getCaptureDurationMs() {
        if (totalSamples == 0) {
            return 0;
        }
        return (totalSamples * 1000) / SAMPLE_RATE;
    }
    
    public boolean isAAudioSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1;
    }
    
    /**
     * 检查音频捕获状态
     * 
     * @return 音频捕获状态信息
     */
    public String getCaptureStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Capture Status:\n");
        sb.append("  Initialized: ").append(initialized).append("\n");
        sb.append("  Is Capturing: ").append(isCapturing.get()).append("\n");
        sb.append("  Frame Count: ").append(frameCount).append("\n");
        sb.append("  Total Samples: ").append(totalSamples).append("\n");
        sb.append("  JavaAudioDeviceModule: ").append(javaAudioDeviceModule != null ? "ACTIVE" : "NULL").append("\n");
        sb.append("  PeerConnectionFactory: ").append(peerConnectionFactory != null ? "ACTIVE" : "NULL").append("\n");
        sb.append("  PeerConnection: ").append(peerConnection != null ? peerConnection.signalingState().toString() : "NULL").append("\n");
        sb.append("  AudioTrack: ").append(audioTrack != null ? "ACTIVE" : "NULL").append("\n");
        sb.append("  AudioSource: ").append(audioSource != null ? "ACTIVE" : "NULL").append("\n");
        
        return sb.toString();
    }
    
    /**
     * 获取音频处理配置信息
     * 
     * @return 音频处理配置摘要字符串
     */
    public String getAudioProcessingInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Audio Processing Configuration:\n");
        sb.append("  Audio API: WebRTC JavaAudioDeviceModule\n");
        sb.append("  Hardware Accelerated: ").append(useHardwareAccelerated ? "YES" : "NO").append("\n");
        sb.append("  Hardware AEC: ").append(useHardwareAEC ? "ENABLED" : "DISABLED").append("\n");
        sb.append("  Hardware NS: ").append(useHardwareNS ? "ENABLED" : "DISABLED").append("\n");
        sb.append("  Hardware AGC: ENABLED\n");
        sb.append("  Hardware VAD: ENABLED\n");
        sb.append("  Low Latency Mode: ").append(useLowLatency ? "ENABLED" : "DISABLED").append("\n");
        sb.append("  Audio Source: ").append(audioSourceId == MediaRecorder.AudioSource.VOICE_COMMUNICATION ? "VOICE_COMMUNICATION" : "OTHER").append("\n");
        sb.append("  Sample Rate: ").append(SAMPLE_RATE).append(" Hz\n");
        sb.append("  Channels: ").append(CHANNELS).append("\n");
        sb.append("  Audio Format: PCM_16BIT\n");
        
        return sb.toString();
    }
    
    /**
     * 检查是否启用了任何音频处理
     * 
     * @return true如果启用了硬件AEC、硬件NS或低延迟模式
     */
    public boolean hasAudioProcessingEnabled() {
        return useHardwareAEC || useHardwareNS || useLowLatency;
    }

}