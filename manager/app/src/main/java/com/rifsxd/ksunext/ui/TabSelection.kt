package com.rifsxd.ksunext.ui

/** Match a concrete Navigation route against a generated route pattern. */
internal fun destinationRouteMatches(currentRoute: String?, destinationRoute: String): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == destinationRoute) return true

    val patternBase = destinationRoute
        .substringBefore("/{")
        .substringBefore("?")
        .trimEnd('/')
    if (patternBase.isEmpty()) return false

    val currentBase = currentRoute.substringBefore("?").trimEnd('/')
    val currentLeaf = currentBase.substringAfterLast('/')
    val patternLeaf = patternBase.substringAfterLast('/')
    return currentBase == patternBase ||
        currentBase.startsWith("$patternBase/") ||
        currentBase.startsWith("$patternBase?") ||
        currentBase.contains("/$patternBase/") ||
        currentLeaf == patternLeaf
}

/**
 * Returns the bottom-tab index for a nested destination, or null when its owner
 * is ambiguous/unknown and the caller should retain the previous root selection.
 */
internal fun nestedTabIndexForRoute(
    currentRoute: String?,
    nestedRoutesByTab: List<Set<String>>,
): Int? {
    val matches = nestedRoutesByTab.mapIndexedNotNull { index, routes ->
        index.takeIf { routes.any { destinationRouteMatches(currentRoute, it) } }
    }
    return matches.singleOrNull()
}
