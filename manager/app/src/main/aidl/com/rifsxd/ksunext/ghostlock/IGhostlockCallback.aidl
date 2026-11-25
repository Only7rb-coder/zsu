package com.rifsxd.ksunext.ghostlock;

oneway interface IGhostlockCallback {
    void onLog(String line);
    void onComplete(int exitCode, boolean timedOut);
}
