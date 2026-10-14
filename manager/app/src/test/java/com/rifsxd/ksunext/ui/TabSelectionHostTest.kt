package com.rifsxd.ksunext.ui

/** Standalone host test for nested bottom-tab ownership. */
fun main() {
    val nested = listOf(
        setOf("install"),
        setOf("module", "superUser", "moduleRepo", "metaModule", "addons", "sulog"),
        emptySet(),
        setOf("appProfileTemplate", "developer", "customization", "disguise")
    )

    check(nestedTabIndexForRoute("customization", nested) == 3)
    check(nestedTabIndexForRoute("customization?section=appearance", nested) == 3)
    check(nestedTabIndexForRoute("developer", nested) == 3)
    check(nestedTabIndexForRoute("moduleRepo", nested) == 1)
    check(nestedTabIndexForRoute("appProfileTemplate/template-42", nested) == 3)
    check(nestedTabIndexForRoute("backupRestore", nested) == null)
    check(nestedTabIndexForRoute("unknown", nested) == null)
    check(nestedTabIndexForRoute(null, nested) == null)

    check(destinationRouteMatches("appProfile/template-42", "appProfile/{packageName}"))
    check(!destinationRouteMatches("appProfileX/template-42", "appProfile/{packageName}"))
    println("Nested tab selection host tests passed")
}
