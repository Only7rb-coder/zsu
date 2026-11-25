package com.rifsxd.ksunext.ghostlock;

import com.rifsxd.ksunext.ghostlock.IGhostlockCallback;
import com.rifsxd.ksunext.ghostlock.IGhostlockStatusCallback;

interface IGhostlockUserService {
    void destroy() = 16777114;
    void runExploit(in byte[] profileBlob, @nullable String managerPackage,
        IGhostlockCallback callback, IGhostlockStatusCallback statusCallback) = 2;
}
