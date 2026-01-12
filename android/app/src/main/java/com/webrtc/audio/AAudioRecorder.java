package com.webrtc.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于WebRTC JavaAudioDeviceModule的AAudio音频采集类
 * 核心特性：
 * 1. 使用AAudio硬件加速（Android 8.0+）
 * 2. 完整的WebRTC音频采集链路：PeerConnectionFactory -> AudioSource -> AudioTrack -> PeerConnection
 * 3. 通过SDP连接激活媒体流，触发native层音频采集
 * 4. 支持3A算法（AEC、NS、AGC）和VAD
 */
public class AAudioRecorder {
    private static final String TAG = "AAudioRecorder";
    private static final int AUDIO_SAMPLE_RATE = 16000;
    private static final boolean USE_STEREO = false;

    // WebRTC核心组件
    private PeerConnectionFactory peerConnectionFactory;
    private JavaAudioDeviceModule javaAudioDeviceModule;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private PeerConnection peerConnection;

    // 文件与状态
    private FileOutputStream pcmFos;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Context appContext;
    private int totalBytes = 0;

    // 主线程Handler
    private final Handler mainHandler;

    public AAudioRecorder(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 启动音频采集
     * @param pcmFileName 保存的PCM文件名
     * @return true-启动成功，false-启动失败
     */
    public boolean startRecording(String pcmFileName) {
        return startRecordingInternal(pcmFileName);
    }

    /**
     * 内部启动逻辑
     */
    private boolean startRecordingInternal(String pcmFileName) {
        if (isRecording.get()) {
            Log.w(TAG, "采集已运行，无需重复启动");
            return false;
        }

        // 1. 检查录音权限
        if (!checkRecordPermission()) {
            Log.e(TAG, "缺少RECORD_AUDIO权限");
            return false;
        }

        // 2. 创建PCM文件
        File pcmFile = new File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                pcmFileName
        );
        try {
            pcmFos = new FileOutputStream(pcmFile, false);
            Log.d(TAG, "PCM文件路径: " + pcmFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "创建PCM文件失败: " + e.getMessage());
            return false;
        }

        // 3. 初始化WebRTC
        try {
            PeerConnectionFactory.InitializationOptions initOptions =
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                            .setEnableInternalTracer(false)
                            .createInitializationOptions();
            PeerConnectionFactory.initialize(initOptions);
            Log.d(TAG, "WebRTC初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "WebRTC初始化失败: " + e.getMessage());
            closeFileStream();
            return false;
        }

        // 4. 创建JavaAudioDeviceModule
        try {
            javaAudioDeviceModule = JavaAudioDeviceModule.builder(appContext)
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setUseStereoInput(USE_STEREO)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .setSamplesReadyCallback(audioSamples -> {
                        if (isRunning.get() && pcmFos != null) {
                            handleAudioDataSafe(audioSamples);
                        }
                    })
                    .setAudioRecordErrorCallback(new JavaAudioDeviceModule.AudioRecordErrorCallback() {
                        @Override
                        public void onWebRtcAudioRecordInitError(String errorMessage) {
                            Log.e(TAG, "采集初始化错误: " + errorMessage);
                            stopRecording();
                        }

                        @Override
                        public void onWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
                            Log.e(TAG, "采集启动错误[" + errorCode + "]: " + errorMessage);
                            stopRecording();
                        }

                        @Override
                        public void onWebRtcAudioRecordError(String errorMessage) {
                            Log.e(TAG, "采集运行错误: " + errorMessage);
                        }
                    })
                    .setAudioRecordStateCallback(new JavaAudioDeviceModule.AudioRecordStateCallback() {
                        @Override
                        public void onWebRtcAudioRecordStart() {
                            Log.i(TAG, "底层AAudio/OpenSL ES采集已启动");
                        }

                        @Override
                        public void onWebRtcAudioRecordStop() {
                            Log.i(TAG, "底层采集已停止");
                        }
                    })
                    .createAudioDeviceModule();
            Log.d(TAG, "JavaAudioDeviceModule创建成功");
        } catch (Exception e) {
            Log.e(TAG, "创建JavaAudioDeviceModule失败: " + e.getMessage());
            closeFileStream();
            return false;
        }

        // 5. 构建PeerConnectionFactory并绑定ADM
        try {
            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(javaAudioDeviceModule)
                    .createPeerConnectionFactory();
            Log.d(TAG, "PeerConnectionFactory绑定ADM成功");
        } catch (Exception e) {
            Log.e(TAG, "构建PeerConnectionFactory失败: " + e.getMessage());
            closeFileStream();
            releaseAudioResources();
            return false;
        }

        // 6. 创建AudioSource和AudioTrack
        try {
            MediaConstraints audioConstraints = new MediaConstraints();
            audioSource = peerConnectionFactory.createAudioSource(audioConstraints);

            audioTrack = peerConnectionFactory.createAudioTrack(
                    "aaudio_track_" + System.currentTimeMillis(),
                    audioSource
            );
            Log.d(TAG, "AudioSource和AudioTrack创建成功");
        } catch (Exception e) {
            Log.e(TAG, "创建AudioTrack失败: " + e.getMessage());
            closeFileStream();
            releaseAudioResources();
            return false;
        }

        // 7. 创建PeerConnection并添加AudioTrack
        try {
            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
            rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
            // 禁用CPU过载检测，避免影响音频采集
            rtcConfig.enableCpuOveruseDetection = false;

            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
                @Override
                public void onSignalingChange(PeerConnection.SignalingState newState) {
                    Log.d(TAG, "onSignalingChange: " + newState);
                }

                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                    Log.d(TAG, "onIceConnectionChange: " + newState);
                }

                @Override
                public void onIceConnectionReceivingChange(boolean receiving) {
                    Log.d(TAG, "onIceConnectionReceivingChange: " + receiving);
                }

                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
                    Log.d(TAG, "onIceGatheringChange: " + newState);
                }

                @Override
                public void onIceCandidate(IceCandidate candidate) {
                    Log.d(TAG, "onIceCandidate: " + candidate);
                }

                @Override
                public void onIceCandidatesRemoved(IceCandidate[] candidates) {
                    Log.d(TAG, "onIceCandidatesRemoved");
                }

                @Override
                public void onAddStream(MediaStream stream) {
                    Log.d(TAG, "onAddStream: " + stream);
                }

                @Override
                public void onRemoveStream(MediaStream stream) {
                    Log.d(TAG, "onRemoveStream: " + stream);
                }

                @Override
                public void onDataChannel(org.webrtc.DataChannel dataChannel) {
                    Log.d(TAG, "onDataChannel: " + dataChannel);
                }

                @Override
                public void onRenegotiationNeeded() {
                    Log.d(TAG, "onRenegotiationNeeded");
                }

                @Override
                public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                    Log.d(TAG, "onAddTrack: " + receiver);
                }
            });

            if (peerConnection == null) {
                Log.e(TAG, "创建PeerConnection失败");
                closeFileStream();
                releaseAudioResources();
                return false;
            }

            // 添加AudioTrack到PeerConnection（关键步骤：激活音频采集）
            peerConnection.addTrack(audioTrack, new ArrayList<>());
            Log.d(TAG, "AudioTrack已添加到PeerConnection");
            
            // 创建数据通道以触发媒体流
            DataChannel.Init dataChannelInit = new DataChannel.Init();
            dataChannelInit.ordered = true;
            DataChannel dataChannel = peerConnection.createDataChannel("audio_trigger", dataChannelInit);
            Log.d(TAG, "数据通道已创建: " + (dataChannel != null ? "成功" : "失败"));
        } catch (Exception e) {
            Log.e(TAG, "创建PeerConnection失败: " + e.getMessage());
            closeFileStream();
            releaseAudioResources();
            return false;
        }

        // 8. 创建SDP offer并设置本地描述（关键步骤：激活媒体流）
        createAndSetLocalDescription();

        // 启动状态标记
        isRecording.set(true);
        isRunning.set(true);

        return true;
    }

    /**
     * 创建SDP offer并设置本地描述
     */
    private void createAndSetLocalDescription() {
        MediaConstraints sdpConstraints = new MediaConstraints();
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "创建SDP offer成功");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "设置本地描述成功");
                        // 设置本地描述后，设置远程描述以完成SDP协商
                        setRemoteDescription(sessionDescription);
                    }

                    @Override
                    public void onCreateFailure(String error) {
                        Log.e(TAG, "创建SDP失败: " + error);
                    }

                    @Override
                    public void onSetFailure(String error) {
                        Log.e(TAG, "设置本地描述失败: " + error);
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "创建SDP offer失败: " + error);
            }

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "设置SDP失败: " + error);
            }
        }, sdpConstraints);
    }

    /**
     * 设置远程描述以完成SDP协商
     */
    private void setRemoteDescription(SessionDescription localSdp) {
        // 修改SDP的setup属性，使其成为answer
        String sdpContent = localSdp.description;
        sdpContent = sdpContent.replace("a=setup:actpass", "a=setup:active");

        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, sdpContent);
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "设置远程描述成功，SDP协商完成，音频采集已激活");
                // 启用音频录制（虽然默认已启用，但显式调用确保音频采集开始）
                peerConnection.setAudioRecording(true);
                Log.d(TAG, "已显式启用音频录制");
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "创建SDP失败: " + error);
            }

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "设置远程描述失败: " + error);
            }
        }, remoteSdp);
    }

    /**
     * 处理音频数据
     */
    private void handleAudioDataSafe(JavaAudioDeviceModule.AudioSamples audioSamples) {
        if (audioSamples.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT) {
            Log.e(TAG, "无效格式: " + audioSamples.getAudioFormat());
            return;
        }

        try {
            byte[] data = audioSamples.getData();
            pcmFos.write(data);
            pcmFos.flush();

            totalBytes += data.length;
            Log.d(TAG, "✅ 写入数据: " + data.length + "字节 | 累计: " + totalBytes + "字节");
        } catch (IOException e) {
            Log.e(TAG, "写入数据失败: " + e.getMessage());
            stopRecording();
        }
    }

    /**
     * 停止采集
     */
    public void stopRecording() {
        mainHandler.post(this::stopRecordingInternal);
    }

    /**
     * 内部停止逻辑
     */
    private void stopRecordingInternal() {
        Log.d(TAG, "停止音频采集");
        isRunning.set(false);
        isRecording.set(false);

        // 关闭文件流
        closeFileStream();

        // 释放WebRTC资源
        releaseAudioResources();

        // 重置统计
        totalBytes = 0;
    }

    /**
     * 释放音频资源
     */
    private void releaseAudioResources() {
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        if (audioTrack != null) {
            audioTrack.dispose();
            audioTrack = null;
        }
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        if (javaAudioDeviceModule != null) {
            javaAudioDeviceModule.release();
            javaAudioDeviceModule = null;
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
    }

    /**
     * 检查录音权限
     */
    private boolean checkRecordPermission() {
        String permission = android.Manifest.permission.RECORD_AUDIO;
        int result = appContext.checkSelfPermission(permission);
        boolean hasPermission = result == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!hasPermission) {
            Log.e(TAG, "⚠️ 请先动态申请RECORD_AUDIO权限");
        }
        return hasPermission;
    }

    /**
     * 关闭文件流
     */
    private void closeFileStream() {
        if (pcmFos != null) {
            try {
                pcmFos.close();
                pcmFos = null;
                Log.d(TAG, "PCM文件流已关闭");
            } catch (IOException e) {
                Log.e(TAG, "关闭文件流失败: " + e.getMessage());
            }
        }
    }

    // ===== 兼容方法 =====
    public AudioDeviceModule getAudioDeviceModule() {
        return javaAudioDeviceModule;
    }

    public void startAudioRecording() {
        Log.d(TAG, "startAudioRecording调用（已通过SDP激活，无需此方法）");
    }

    // ===== 公共方法 =====
    public int getTotalBytes() {
        return totalBytes;
    }

    public boolean isRecording() {
        return isRecording.get();
    }

    /**
     * 销毁所有资源
     */
    public void destroy() {
        mainHandler.post(() -> {
            stopRecordingInternal();
            PeerConnectionFactory.shutdownInternalTracer();
            Log.d(TAG, "AAudioRecorder资源已完全销毁");
        });
    }
}