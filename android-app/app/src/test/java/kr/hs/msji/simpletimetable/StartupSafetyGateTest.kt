package kr.hs.msji.simpletimetable

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StartupSafetyGateTest {
    @Test fun collectionWritesRemainBlockedUntilRestorePublishes() {
        val gate = StartupSafetyGate()
        val persisted = mutableListOf("기존 메모", "기존 TODO", "기존 일정")
        fun replaceWithNewCollection() {
            if (gate.isHydrated()) persisted.clear()
        }
        replaceWithNewCollection()
        assertEquals(listOf("기존 메모", "기존 TODO", "기존 일정"), persisted)
        gate.markHydrated()
        assertTrue(gate.isHydrated())
    }

    @Test fun delayedStartupCannotUndoEarlierDateSelection() {
        val gate = StartupSafetyGate()
        var selected = "2026-10-11"
        gate.runUserNavigation { selected = "2026-10-13" }
        assertFalse(gate.runStartupRefreshIfUntouched { selected = "2026-10-11" })
        assertEquals("2026-10-13", selected)
    }

    @Test fun initialRefreshRunsWhenNoNavigationOccurred() {
        val gate = StartupSafetyGate()
        var refreshes = 0
        assertTrue(gate.runStartupRefreshIfUntouched { refreshes++ })
        assertEquals(1, refreshes)
    }

    @Test fun navigationAndStartupRegistrationAreSerialized() {
        repeat(100) {
            val gate = StartupSafetyGate()
            val pool = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(1)
            var selected = "2026-10-11"
            try {
                val nav = pool.submit { ready.await(); gate.runUserNavigation { selected = "2026-10-13" } }
                val startup = pool.submit { ready.await(); gate.runStartupRefreshIfUntouched { selected = "2026-10-11" } }
                ready.countDown()
                nav.get(5, TimeUnit.SECONDS)
                startup.get(5, TimeUnit.SECONDS)
                assertEquals("2026-10-13", selected)
            } finally {
                pool.shutdownNow()
            }
        }
    }
}
