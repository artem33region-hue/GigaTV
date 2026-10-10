package com.artem.gigatv.data.channels

import com.artem.gigatv.data.history.WatchItem
import org.junit.Assert.*
import org.junit.Test

class WatchNextPlanTest {
    private fun movie(url: String) = WatchItem(url, url, position = 10000, duration = 100000)

    @Test fun existingRowsKeepTheirIds() {
        val plan = planWatchNext(listOf(WatchNextRow(42, "a", false)), listOf(movie("a")), emptySet())
        assertTrue(plan.deleteIds.isEmpty())
        assertEquals(42L, plan.upserts.single().first)
    }

    @Test fun dismissalSurvivesDeletingTheProviderRow() {
        val first = planWatchNext(listOf(WatchNextRow(42, "a", true)), listOf(movie("a")), emptySet())
        assertEquals(setOf("a"), first.hiddenUrls)
        assertEquals(listOf(42L), first.deleteIds)
        assertTrue(first.upserts.isEmpty())
        val next = planWatchNext(emptyList(), listOf(movie("a"), movie("b")), first.hiddenUrls)
        assertEquals(listOf("b"), next.upserts.map { it.second.url })
    }

    @Test fun duplicateRowsAreRemovedWithoutReplacingTheRetainedRow() {
        val plan = planWatchNext(
            listOf(WatchNextRow(1, "a", false), WatchNextRow(2, "a", false), WatchNextRow(3, "old", false)),
            listOf(movie("a"), movie("a"), movie("b")), emptySet()
        )
        assertEquals(listOf(2L, 3L), plan.deleteIds)
        assertEquals(listOf(1L, null), plan.upserts.map { it.first })
    }

    @Test fun hiddenItemsDoNotUseVisibleSlots() {
        val plan = planWatchNext(emptyList(), listOf(movie("a"), movie("b")), setOf("a"), maxItems = 1)
        assertEquals("b", plan.upserts.single().second.url)
    }
}
