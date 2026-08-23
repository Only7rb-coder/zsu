package com.rifsxd.ksunext.jailbreak;

import android.annotation.SuppressLint;
import android.app.ZygotePreload;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

/** Runs in the app zygote to invoke the bundled userspace late-load command. */
@SuppressLint("NewApi")
public final class JailbreakZygotePreload implements ZygotePreload {
    private static final String TAG = "ZSUJailbreak";

    private static native void forkDontCareAndExecLateLoad(String ksudPath, String packageName);

    @Override
    public void doPreload(@NonNull ApplicationInfo appInfo) {
        try {
            System.loadLibrary("kernelsu");
            File ksud = new File(appInfo.nativeLibraryDir, "libksud.so");
            forkDontCareAndExecLateLoad(ksud.getAbsolutePath(), appInfo.packageName);
        } catch (Throwable error) {
            Log.e(TAG, "Late-load bootstrap failed", error);
        }
    }
}
