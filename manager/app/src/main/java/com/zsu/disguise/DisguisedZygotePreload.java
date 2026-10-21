package com.zsu.disguise;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;

import com.rifsxd.ksunext.jailbreak.JailbreakZygotePreload;
import android.app.ZygotePreload;

/**
 * Package-neutral manifest entry point for the generated identity clone.
 * The existing preload implementation remains the single behavior source.
 */
@SuppressLint("NewApi")
public final class DisguisedZygotePreload implements ZygotePreload {
    private final JailbreakZygotePreload delegate = new JailbreakZygotePreload();

    @Override
    public void doPreload(@NonNull ApplicationInfo appInfo) {
        delegate.doPreload(appInfo);
    }
}
