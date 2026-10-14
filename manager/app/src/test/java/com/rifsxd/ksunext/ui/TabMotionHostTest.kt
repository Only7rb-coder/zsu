package com.rifsxd.ksunext.ui

/** Standalone host test; no Android SDK, device, or root permission required. */
fun main() {
    val routes = listOf("home", "manage", "ghostlock", "settings")
    for (from in routes.indices) {
        for (to in routes.indices) {
            check(tabMotionDirection(routes[from], routes[to], routes) == to.compareTo(from)) {
                "Incorrect direction from ${routes[from]} to ${routes[to]}"
            }
        }
    }
    check(tabMotionDirection(null, "home", routes) == 0)
    check(tabMotionDirection("home", null, routes) == 0)
    check(tabMotionDirection("addons", "home", routes) == 0)
    check(tabMotionDirection("home", "modules", routes) == 0)
    check(tabMotionDirection("settings", "home", routes) == -1)
    check(tabMotionDirection("home", "settings", routes) == 1)
    check(tabMotionDirection("home", "home", emptyList()) == 0)
    println("Tab motion host tests passed: all 16 tab pairs plus nested/null/back routes")
}
