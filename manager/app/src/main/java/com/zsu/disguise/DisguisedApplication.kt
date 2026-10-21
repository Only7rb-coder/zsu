package com.zsu.disguise

import com.rifsxd.ksunext.KernelSUApplication

/**
 * Stable application entry point for a generated identity clone.
 *
 * The implementation remains the normal ZSU application; only the manifest
 * entry point is outside the manager namespace so a renamed clone does not
 * expose the original manager class as its declared application identity.
 */
class DisguisedApplication : KernelSUApplication()
