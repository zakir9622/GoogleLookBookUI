package com.zakir.vestra.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM guard on the tab list and pager-index math.
 *
 * Hiding a tab by filtering [HomeTab.entries] makes `ordinal` (declaration order) diverge from
 * the visible-list index the pager actually uses. Every page index must therefore come from
 * `visible.indexOf(tab)`, never `tab.ordinal`. This asserts the two genuinely differ, so the
 * test would fail if someone reintroduced `.ordinal` as a page index.
 */
class HomeTabVisibilityTest {

    @Test
    fun tryOnIsHiddenWhileTheFlagIsOff() {
        assertFalse(
            "TRY_ON_TAB_ENABLED is the single revert switch — flipping it back belongs in its own change",
            HomeTab.TRY_ON_TAB_ENABLED,
        )
        assertFalse(HomeTab.TRY_ON in HomeTab.visible)
        assertTrue("hiding try-on must not empty the tab row", HomeTab.visible.isNotEmpty())
    }

    @Test
    fun everyOtherTabSurvivesTheFilter() {
        val expected = HomeTab.entries.filter { it != HomeTab.TRY_ON }
        assertEquals(expected, HomeTab.visible)
    }

    @Test
    fun ordinalIsNotUsableAsAPageIndexOnceATabIsHidden() {
        // The regression this guards: TRY_ON sits first in the enum, so hiding it shifts every
        // later tab down by one. If these were equal the bug would be invisible.
        HomeTab.visible.forEach { tab ->
            assertNotEquals(
                "$tab: ordinal must differ from its visible index, or this test proves nothing",
                tab.ordinal,
                HomeTab.visible.indexOf(tab),
            )
        }
    }

    @Test
    fun visibleIndicesAreContiguousAndStartAtZero() {
        assertEquals(HomeTab.visible.indices.toList(), HomeTab.visible.map { HomeTab.visible.indexOf(it) })
    }

    @Test
    fun routeKeysResolveToTheirOwnTab() {
        HomeTab.visible.forEach { tab ->
            assertEquals(tab, HomeTab.fromRouteKey(tab.routeKey))
            assertEquals(tab, HomeTab.fromRouteKey(tab.routeKey.uppercase()))
        }
    }

    @Test
    fun unknownAndHiddenRouteKeysFallBackToAVisibleTab() {
        // The fallback must never hand back the hidden tab — that was the pre-fix behaviour.
        listOf(null, "", "nonsense", HomeTab.TRY_ON.routeKey).forEach { key ->
            val resolved = HomeTab.fromRouteKey(key)
            assertTrue("fromRouteKey($key) returned a hidden tab", resolved in HomeTab.visible)
        }
    }
}
