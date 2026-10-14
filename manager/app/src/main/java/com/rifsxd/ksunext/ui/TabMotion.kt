package com.rifsxd.ksunext.ui

/** Positive means moving to a tab on the right; unknown/nested routes only fade. */
internal fun tabMotionDirection(
    fromRoute: String?,
    toRoute: String?,
    orderedRoutes: List<String>,
): Int {
    val fromIndex = orderedRoutes.indexOf(fromRoute)
    val toIndex = orderedRoutes.indexOf(toRoute)
    if (fromIndex < 0 || toIndex < 0) return 0
    return toIndex.compareTo(fromIndex)
}
