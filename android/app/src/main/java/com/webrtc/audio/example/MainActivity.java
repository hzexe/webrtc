package com.webrtc.audio.example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.webrtc.audio.AAudioRecorder;
import com.webrtc.audio.WebRTCAudioCapturer;

import org.webrtc.AudioSource;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.AudioDeviceModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 100;
    
    private WebRTCAudioCapturer webRTCAudioCapturer;
    private AAudioRecorder aAudioRecorder;
    private boolean isRecording = false;
    
    private FileOutputStream audioFileOutputStream;
    private File audioFile;
    
    private TextView statusTextView;
    private TextView infoTextView;
    private Button startButton;
    private Button stopButton;
    private Button playButton;
    private Button deleteButton;
    
    private long startTime;
    private int totalBytesReceived = 0;
    
    private android.media.AudioTrack audioTrack;
    private boolean isPlaying = false;
    
    // 添加PeerConnection相关字段
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private org.webrtc.AudioTrack audioTrackWebRTC;
    private org.webrtc.DataChannel dataChannel; // 添加data channel字段
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        checkAAudioSupport();
        checkPermissions();
        initializeAudioCapturer();
    }
    
    private void initViews() {
        statusTextView = findViewById(R.id.status_text);
        infoTextView = findViewById(R.id.info_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        playButton = findViewById(R.id.play_button);
        deleteButton = findViewById(R.id.delete_button);
        
        startButton.setOnClickListener(v -> startCapture());
        stopButton.setOnClickListener(v -> stopCapture());
        playButton.setOnClickListener(v -> playAudio());
        deleteButton.setOnClickListener(v -> deleteAudio());
        
        updateInfoText();
        updateButtonStates();
    }
    
    private void updateInfoText() {
        String info = "采样率: 16kHz\n" +
                      "声道: 单声道\n" +
                      "硬件加速: AAudio (API 27+)\n" +
                      "音频处理: 硬件AEC/NS";
        infoTextView.setText(info);
    }
    
    private void checkAAudioSupport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Log.i(TAG, "AAudio is supported on this device");
            Toast.makeText(this, "AAudio硬件加速已启用", Toast.LENGTH_SHORT).show();
        } else {
            Log.w(TAG, "AAudio is not supported on this device (API < 27)");
            Toast.makeText(this, "AAudio不支持，使用传统音频API", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        200);
            }
        }
    }
    
    private void initializeAudioCapturer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 使用AAudioRecorder
            aAudioRecorder = new AAudioRecorder(this);
        } else {
            // Android 8.0以下版本使用WebRTC的JavaAudioDeviceModule方式
            webRTCAudioCapturer = new WebRTCAudioCapturer(this);
            
            webRTCAudioCapturer.setAudioDataCallback(new WebRTCAudioCapturer.AudioDataCallback() {
                @Override
                public void onAudioData(byte[] data, int length) {
                    handleAudioData(data, length);
                }
                
                @Override
                public void onCaptureError(String error) {
                    handleError(error);
                }
            });
            
            configureWebRTCAudioSettings();
        }
    }
    
    private void configureWebRTCAudioSettings() {
        if (webRTCAudioCapturer != null) {
            webRTCAudioCapturer.setUseHardwareAEC(true);
            webRTCAudioCapturer.setUseHardwareNS(true);
            webRTCAudioCapturer.setUseLowLatency(true);
            
            Log.i(TAG, "Audio settings configured: Hardware AEC=ENABLED, Hardware NS=ENABLED, Low Latency=ENABLED");
            Log.i(TAG, "Audio processing info:\n" + webRTCAudioCapturer.getAudioProcessingInfo());
        }
    }
    
    private void startCapture() {
        Log.d(TAG, "startCapture() called");
        
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Record audio permission not granted");
            pendingStartCapture = true; // 标记需要在获得权限后开始录音
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        
        // 如果有权限，则直接开始录音
        startCaptureInternal();
    }
    
    private void startCaptureInternal() {
        Log.d(TAG, "startCaptureInternal() called");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 使用AAudioRecorder
            if (aAudioRecorder == null) {
                Log.e(TAG, "aAudioRecorder is null");
                Toast.makeText(this, "AAudio录音器未初始化", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (isRecording) {
                Log.w(TAG, "Already recording");
                return;
            }
            
            Log.d(TAG, "Starting AAudio recording...");
            String fileName = "audio_" + System.currentTimeMillis() + "_aaudio.pcm";
            
            // 创建audioFile以便在停止时使用
            File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "WebRTCAudio");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            audioFile = new File(outputDir, fileName);
            
            if (!aAudioRecorder.startRecording(fileName)) {
                Log.e(TAG, "Failed to start AAudio recording");
                Toast.makeText(this, "启动AAudio录音失败", Toast.LENGTH_SHORT).show();
                audioFile = null;
                return;
            }
            
            isRecording = true;
            startTime = System.currentTimeMillis();
            totalBytesReceived = 0;
            
            Log.d(TAG, "AAudio recording started successfully");
            updateUIState(true);
            Toast.makeText(this, "开始AAudio采集音频", Toast.LENGTH_SHORT).show();
        } else {
            // Android 8.0以下版本使用WebRTC的JavaAudioDeviceModule方式
            if (webRTCAudioCapturer == null) {
                Log.e(TAG, "webRTCAudioCapturer is null");
                Toast.makeText(this, "WebRTC录音器未初始化", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (isRecording) {
                Log.w(TAG, "Already recording");
                return;
            }
            
            Log.d(TAG, "Creating audio file...");
            if (!createAudioFile()) {
                Log.e(TAG, "Failed to create audio file");
                Toast.makeText(this, "创建音频文件失败", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d(TAG, "Starting WebRTC recording...");
            if (!webRTCAudioCapturer.startCapture()) {
                Log.e(TAG, "Failed to start WebRTC recording");
                Toast.makeText(this, "启动WebRTC录音失败", Toast.LENGTH_SHORT).show();
                closeAudioFile();
                return;
            }
            
            isRecording = true;
            startTime = System.currentTimeMillis();
            totalBytesReceived = 0;
            
            Log.d(TAG, "WebRTC recording started successfully");
            updateUIState(true);
            Toast.makeText(this, "开始WebRTC采集音频", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopCapture() {
        if (!isRecording) {
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (aAudioRecorder != null) {
                aAudioRecorder.stopRecording();
            }
        } else {
            if (webRTCAudioCapturer != null) {
                webRTCAudioCapturer.stopCapture();
            }
        }
        
        closeAudioFile();
        
        isRecording = false;
        long duration = System.currentTimeMillis() - startTime;
        
        updateUIState(false);
        updateCaptureInfo(duration);
        
        Toast.makeText(this, "停止采集，已保存: " + audioFile.getName(), 
                      Toast.LENGTH_SHORT).show();
    }
    
    private boolean createAudioFile() {
        try {
            File outputDir;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用媒体存储API
                outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "WebRTCAudio");
            } else {
                // Android 9及以下使用传统方式
                outputDir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_MUSIC),
                        "WebRTCAudio");
            }
            
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            audioFile = new File(outputDir, "audio_" + timestamp + ".pcm");
            
            audioFileOutputStream = new FileOutputStream(audioFile);
            
            Log.i(TAG, "Audio file created: " + audioFile.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to create audio file", e);
            return false;
        }
    }
    
    private void closeAudioFile() {
        if (audioFileOutputStream != null) {
            try {
                audioFileOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close audio file", e);
            }
            audioFileOutputStream = null;
        }
    }
    
    private void handleAudioData(byte[] data, int length) {
        Log.d(TAG, "Received audio data! Length: " + length + " bytes");
        
        totalBytesReceived += length;
        
        if (audioFileOutputStream != null) {
            try {
                audioFileOutputStream.write(data, 0, length);
                Log.d(TAG, "Wrote " + length + " bytes to audio file");
            } catch (IOException e) {
                Log.e(TAG, "Failed to write audio data", e);
            }
        }
        
        runOnUiThread(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int seconds = (int) (duration / 1000);
            
            String status = String.format(Locale.getDefault(),
                    "状态: 采集中\n时长: %d秒\n接收字节: %d",
                    seconds, totalBytesReceived);
            statusTextView.setText(status);
            
            Log.d(TAG, "UI updated - Duration: " + seconds + "s, Bytes: " + totalBytesReceived);
        });
    }
    
    private void handleError(String error) {
        Log.e(TAG, "Capture error: " + error);
        
        runOnUiThread(() -> {
            Toast.makeText(this, "采集错误: " + error, Toast.LENGTH_LONG).show();
            stopCapture();
        });
    }
    
    private void updateUIState(boolean recording) {
        startButton.setEnabled(!recording);
        stopButton.setEnabled(recording);
        playButton.setEnabled(!recording && audioFile != null && !isPlaying);
        deleteButton.setEnabled(!recording && audioFile != null && !isPlaying);
        
        if (recording) {
            statusTextView.setText("状态: 采集中...");
        } else {
            statusTextView.setText("状态: 已停止");
        }
    }
    
    private void updateButtonStates() {
        boolean hasAudioFile = audioFile != null && audioFile.exists();
        playButton.setEnabled(hasAudioFile && !isRecording && !isPlaying);
        deleteButton.setEnabled(hasAudioFile && !isRecording && !isPlaying);
    }
    
    private void updateCaptureInfo(long durationMs) {
        int seconds = (int) (durationMs / 1000);
        
        int frames = 0;
        long samples = 0;
        long captureDuration = 0;
        int actualBytesReceived = totalBytesReceived;
        int sampleRate = 16000;
        int channels = 1;
        String audioSourceType = "AAudioRecorder (纯WebRTC API)";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 使用AAudioRecorder
            if (aAudioRecorder != null) {
                actualBytesReceived = aAudioRecorder.getTotalBytes();
            }
        } else {
            // 使用WebRTC JavaAudioDeviceModule
            if (webRTCAudioCapturer != null) {
                frames = webRTCAudioCapturer.getFrameCount();
                samples = webRTCAudioCapturer.getTotalSamples();
                captureDuration = webRTCAudioCapturer.getCaptureDurationMs();
                sampleRate = webRTCAudioCapturer.getSampleRate();
                channels = webRTCAudioCapturer.getChannels();
                audioSourceType = "WebRTC JavaAudioDeviceModule";
            }
        }
        
        String info = String.format(Locale.getDefault(),
                "音频源: %s\n" +
                "采样率: %d Hz\n" +
                "声道: %d\n" +
                "3A算法: AEC=启用, NS=启用, AGC=启用, VAD=启用\n" +
                "采集时长: %d 秒\n" +
                "总帧数: %d\n" +
                "总样本数: %d\n" +
                "接收字节: %d\n" +
                "文件: %s",
                audioSourceType,
                sampleRate,
                channels,
                seconds,
                frames,
                samples,
                actualBytesReceived,
                audioFile != null ? audioFile.getName() : "N/A");
        
        infoTextView.setText(info);
        
        Log.i(TAG, "Capture completed - Duration: " + seconds + "s, " +
              "Frames: " + frames + ", Samples: " + samples + ", " +
              "Bytes: " + actualBytesReceived);
    }
    
    private boolean pendingStartCapture = false; // 标记是否需要在获得权限后开始录音
    
    @Override
    public void onRequestPermissionsResult(int requestCode, 
                                          @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show();
                // 如果之前请求录音但权限被拒绝，现在权限已授予，则开始录音
                if (pendingStartCapture) {
                    pendingStartCapture = false;
                    // 延迟一点时间再开始录音，确保权限系统完全生效
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startCaptureInternal();
                        }
                    }, 100);
                }
            } else {
                Toast.makeText(this, "录音权限被拒绝", Toast.LENGTH_SHORT).show();
                pendingStartCapture = false;
            }
        }
    }
    
    private void playAudio() {
        if (isPlaying || audioFile == null || !audioFile.exists()) {
            return;
        }
        
        new Thread(() -> {
            try {
                int sampleRate = 16000;
                int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                
                audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        sampleRate, channelConfig, audioFormat,
                        bufferSize, AudioTrack.MODE_STREAM);
                
                audioTrack.play();
                isPlaying = true;
                
                runOnUiThread(() -> {
                    statusTextView.setText("状态: 播放中...");
                    updateButtonStates();
                });
                
                byte[] buffer = new byte[bufferSize];
                java.io.FileInputStream fis = new java.io.FileInputStream(audioFile);
                
                int bytesRead;
                while (isPlaying && (bytesRead = fis.read(buffer)) != -1) {
                    audioTrack.write(buffer, 0, bytesRead);
                }
                
                fis.close();
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
                isPlaying = false;
                
                runOnUiThread(() -> {
                    statusTextView.setText("状态: 播放完成");
                    updateButtonStates();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "播放失败: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    isPlaying = false;
                    updateButtonStates();
                });
            }
        }).start();
    }
    
    private void deleteAudio() {
        if (audioFile == null || !audioFile.exists()) {
            Toast.makeText(this, "没有可删除的音频文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (audioFile.delete()) {
            Toast.makeText(this, "文件已删除", Toast.LENGTH_SHORT).show();
            audioFile = null;
            updateButtonStates();
            updateInfoText();
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void createPeerConnectionForAudioOnly() {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is null, cannot create PeerConnection");
            return;
        }

        // 创建简单的PeerConnection配置，仅用于激活音频
        List<PeerConnection.IceServer> iceServers = new LinkedList<>();
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        
        // 设置为允许所有传输，这样音频采集可以正常工作
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.NONE; // 不使用ICE传输，仅用于本地音频采集
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE; // 最小化SDP
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnectionAdapter());
        
        if (peerConnection == null) {
            Log.e(TAG, "Failed to create PeerConnection");
            return;
        }

        Log.d(TAG, "Created PeerConnection for audio capture");

        // 创建音频约束，确保音频采集正常工作
        MediaConstraints audioConstraints = new MediaConstraints();
        // 启用音频回声消除
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        // 启用自动增益控制
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        // 启用噪声抑制
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        // 启用高通过滤器
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        // 启用音频抖动缓冲
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAudioJitterBufferMaxPackets", "50"));
        // 启用音频处理
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAudioJitterBufferFastAccelerate", "true"));
        
        // 创建音频源和轨道
        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        audioTrackWebRTC = peerConnectionFactory.createAudioTrack("audio1", audioSource);

        if (audioTrackWebRTC == null) {
            Log.e(TAG, "Failed to create audio track");
            return;
        }

        // 添加音频轨道到PeerConnection
        RtpSender rtpSender = peerConnection.addTrack(audioTrackWebRTC);
        if (rtpSender == null) {
            Log.e(TAG, "Failed to add audio track to PeerConnection");
            return;
        }

        Log.d(TAG, "Added audio track to PeerConnection, ready to capture audio");
        
        // 在创建SDP offer之前创建data channel，这样data channel的描述会包含在SDP中
        createDataChannel();
        
        // 创建SDP offer来激活音频采集
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")); // 接收音频以触发采集
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                Log.d(TAG, "SDP offer created successfully: " + sdp.type);
                // 设置本地描述
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {
                        Log.d(TAG, "Local description set successfully");
                    }

                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "SDP offer set as local description successfully");
                        // 创建loopback连接：基于offer的SDP内容创建一个answer
                        Log.d(TAG, "Creating loopback connection by creating answer from offer SDP");
                        // 使用offer的SDP内容创建answer（类型为answer，但内容与offer相同）
                        // 需要修改setup属性，将"actpass"改为"active"以满足answerer的要求
                        String sdpContent = sdp.description;
                        // 替换setup属性：将"actpass"改为"active"
                        sdpContent = sdpContent.replace("a=setup:actpass", "a=setup:active");
                        Log.d(TAG, "Modified SDP setup attribute from actpass to active");
                        
                        SessionDescription answer = new SessionDescription(
                            SessionDescription.Type.ANSWER, 
                            sdpContent
                        );
                        // 将answer设置为远程描述以完成loopback连接
                        Log.d(TAG, "Setting answer as remote description to complete loopback connection");
                        peerConnection.setRemoteDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sdp) {
                                Log.d(TAG, "Remote description created successfully");
                            }

                            @Override
                            public void onSetSuccess() {
                                Log.d(TAG, "Loopback connection established - audio capture should be active");
                                // Data channel现在应该已经通过SDP协商打开，开始发送dummy数据
                                sendDummyData();
                            }

                            @Override
                            public void onCreateFailure(String error) {
                                Log.e(TAG, "Failed to create remote description: " + error);
                            }

                            @Override
                            public void onSetFailure(String error) {
                                Log.e(TAG, "Failed to set remote description: " + error);
                            }
                        }, answer);
                    }

                    @Override
                    public void onCreateFailure(String error) {
                        Log.e(TAG, "Failed to create local description: " + error);
                    }

                    @Override
                    public void onSetFailure(String error) {
                        Log.e(TAG, "Failed to set local description: " + error);
                    }
                }, sdp);
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Failed to create SDP offer: " + error);
            }

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "SDP offer set successfully");
            }

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "Failed to set SDP offer: " + error);
            }
        }, sdpConstraints);
    }

    // 创建data channel（在创建SDP offer之前调用，这样data channel的描述会包含在SDP中）
    private void createDataChannel() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection is null, cannot create data channel");
            return;
        }

        Log.d(TAG, "Creating data channel to trigger media flow");
        
        // 创建data channel初始化配置
        org.webrtc.DataChannel.Init init = new org.webrtc.DataChannel.Init();
        init.ordered = true;
        init.negotiated = false;
        
        // 创建data channel
        dataChannel = peerConnection.createDataChannel("audio-trigger", init);
        
        if (dataChannel == null) {
            Log.e(TAG, "Failed to create data channel");
            return;
        }

        Log.d(TAG, "Data channel created successfully");
        
        // 注册data channel状态观察者
        dataChannel.registerObserver(new org.webrtc.DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long previousAmount) {
                Log.d(TAG, "Data channel buffered amount changed: " + previousAmount);
            }

            @Override
            public void onStateChange() {
                Log.d(TAG, "Data channel state changed: " + dataChannel.state());
                
                // 当data channel打开时，发送dummy数据
                if (dataChannel.state() == org.webrtc.DataChannel.State.OPEN) {
                    Log.d(TAG, "Data channel is open, ready to send dummy data");
                }
            }

            @Override
            public void onMessage(org.webrtc.DataChannel.Buffer buffer) {
                Log.d(TAG, "Data channel message received");
            }
        });
    }

    // 发送dummy数据来触发media flow
    private void sendDummyData() {
        if (dataChannel == null) {
            Log.w(TAG, "Data channel is null, cannot send dummy data");
            return;
        }
        
        if (dataChannel.state() != org.webrtc.DataChannel.State.OPEN) {
            Log.w(TAG, "Data channel is not open (state: " + dataChannel.state() + "), cannot send dummy data");
            return;
        }

        Log.d(TAG, "Sending dummy data to trigger audio capture");
        
        // 创建dummy数据
        byte[] dummyData = new byte[16];
        for (int i = 0; i < dummyData.length; i++) {
            dummyData[i] = (byte) i;
        }
        
        // 创建data channel buffer
        org.webrtc.DataChannel.Buffer buffer = new org.webrtc.DataChannel.Buffer(
            java.nio.ByteBuffer.wrap(dummyData), 
            false
        );
        
        // 发送dummy数据
        boolean success = dataChannel.send(buffer);
        Log.d(TAG, "Dummy data sent: " + (success ? "SUCCESS" : "FAILED"));
        
        // 持续发送dummy数据以保持media flow活跃
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (dataChannel != null && dataChannel.state() == org.webrtc.DataChannel.State.OPEN) {
                    sendDummyData();
                }
            }
        }, 1000); // 每秒发送一次
    }

    // 简单的PeerConnection适配器，只处理必要的回调
    private class PeerConnectionAdapter implements PeerConnection.Observer {
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
        public void onIceCandidate(org.webrtc.IceCandidate candidate) {
            Log.d(TAG, "onIceCandidate: " + candidate);
        }

        @Override
        public void onIceCandidatesRemoved(org.webrtc.IceCandidate[] candidates) {
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
            Log.d(TAG, "onDataChannel: " + dataChannel.label());
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "onAddTrack: " + rtpReceiver.track().getClass().getSimpleName());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (isRecording) {
            stopCapture();
        }
        
        if (isPlaying && audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
        }
        
        if (webRTCAudioCapturer != null) {
            webRTCAudioCapturer.release();
            webRTCAudioCapturer = null;
        }
        
        if (aAudioRecorder != null) {
            aAudioRecorder.stopRecording(); // 确保停止录音
            aAudioRecorder = null;
        }
        
        // 释放PeerConnection资源
        if (dataChannel != null) {
            dataChannel.dispose();
            dataChannel = null;
        }
        
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        
        if (audioTrackWebRTC != null) {
            audioTrackWebRTC.dispose();
            audioTrackWebRTC = null;
        }
        
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
    }
}
