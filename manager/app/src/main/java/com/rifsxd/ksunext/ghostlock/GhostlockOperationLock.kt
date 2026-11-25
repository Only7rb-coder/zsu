package com.rifsxd.ksunext.ghostlock

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/** Serializes direct, Shizuku, and automatic native operations in this process. */
internal object GhostlockOperationLock {
    private val lock = ReentrantLock(true)

    fun tryAcquire(): Closeable? {
        if (!lock.tryLock()) return null
        val released = AtomicBoolean(false)
        return Closeable {
            if (released.compareAndSet(false, true)) {
                try {
                    lock.unlock()
                } catch (error: Throwable) {
                    released.set(false)
                    throw error
                }
            }
        }
    }
}
