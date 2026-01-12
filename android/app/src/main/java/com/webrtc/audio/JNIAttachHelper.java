package com.webrtc.audio;

import android.os.Build;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * JNI线程附加辅助工具类（解决WebRTC native线程未附加JNI环境问题）
 */
public class JNIAttachHelper {
    private static final String TAG = "JNIAttachHelper";
    private static final String THREAD_CLASS_NAME = "android.os.Thread";
    private static final String ATTACH_METHOD_NAME = "attachNative";
    private static final String DETACH_METHOD_NAME = "detachNative";

    private static Method attachMethod;
    private static Method detachMethod;

    static {
        // 反射获取Thread的attachNative/detachNative方法（手动附加/分离JNI）
        try {
            Class<?> threadClass = Class.forName(THREAD_CLASS_NAME);
            attachMethod = threadClass.getDeclaredMethod(ATTACH_METHOD_NAME);
            attachMethod.setAccessible(true);

            detachMethod = threadClass.getDeclaredMethod(DETACH_METHOD_NAME);
            detachMethod.setAccessible(true);
            Log.d(TAG, "JNI附加/分离方法反射获取成功");
        } catch (Exception e) {
            Log.e(TAG, "反射获取JNI方法失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 手动附加当前线程到JNI环境
     */
    public static void attachCurrentThread() {
        if (attachMethod == null) return;
        try {
            attachMethod.invoke(Thread.currentThread());
            Log.d(TAG, "当前线程已附加到JNI环境: " + Thread.currentThread().getName());
        } catch (Exception e) {
            Log.e(TAG, "附加JNI环境失败: " + e.getMessage());
        }
    }

    /**
     * 手动分离当前线程与JNI环境
     */
    public static void detachCurrentThread() {
        if (detachMethod == null) return;
        try {
            detachMethod.invoke(Thread.currentThread());
            Log.d(TAG, "当前线程已与JNI环境分离: " + Thread.currentThread().getName());
        } catch (Exception e) {
            Log.e(TAG, "分离JNI环境失败: " + e.getMessage());
        }
    }
}