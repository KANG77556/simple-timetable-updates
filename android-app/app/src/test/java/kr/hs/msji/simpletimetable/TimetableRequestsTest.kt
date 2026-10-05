package kr.hs.msji.simpletimetable

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

class TimetableRequestsTest {
    private val today = "2026-10-02"
    private val yesterday = "2026-10-01"
    private fun rows(date: String, subject: String = "회계") =
        listOf(TimetableItem(date, 1, "경영1", 1, subject, "교사", "101"))

    @Test fun connectionAbortRetriesButTimeoutAndDnsDoNot() {
        assertTrue(shouldRetryScerpGet(SocketException("Software caused connection abort")))
        assertTrue(shouldRetryScerpGet(SocketException("Connection reset")))
        assertFalse(shouldRetryScerpGet(SocketTimeoutException("Read timed out")))
        assertFalse(shouldRetryScerpGet(UnknownHostException("offline")))
    }

    @Test fun timetableTimeoutsStayWithinFastUiBudget() {
        assertEquals(5_000, SCERP_CONNECT_TIMEOUT_MS)
        assertEquals(8_000, SCERP_READ_TIMEOUT_MS)
        assertEquals(200L, SCERP_RETRY_DELAY_MS)
    }

    @Test fun loginKeepsLoadingEvenWhenPersistedRowsAreVisible() {
        val persisted = rows(today)
        assertTrue(shouldShowTimetableLoading(forceLoading = true, cachedRows = null, visibleRows = persisted))
    }

    @Test fun cachedNavigationDoesNotShowBlockingLoading() {
        val cached = rows(yesterday)
        assertFalse(shouldShowTimetableLoading(forceLoading = false, cachedRows = cached, visibleRows = cached))
    }

    @Test fun uncachedNavigationStillShowsLoadingWhenNothingIsVisible() {
        assertTrue(shouldShowTimetableLoading(forceLoading = false, cachedRows = null, visibleRows = emptyList()))
    }

    @Test fun rolloverUpdatesTodayCacheWithoutFalseAlert() {
        val requests = TimetableRequests()
        var cache = rows(yesterday)
        var alerts = 0
        val request = requests.begin(today)
        val fetched = rows(today)
        assertTrue(requests.applyIfCurrent(request) {
            if (timetableChangedForDate(today, cache, fetched)) alerts++
            cache = fetched
        })
        assertEquals(fetched, cache)
        assertEquals(0, alerts)
    }

    @Test fun normalStartupAppliesFreshRowsAndSameDayChangeAlert() {
        val requests = TimetableRequests()
        var selected = today
        var displayed = rows(today)
        var cache = displayed
        var alerts = 0
        val startup = requests.begin(today) { selected = today }
        val fresh = rows(today, "세무")
        assertTrue(requests.applyIfCurrent(startup) {
            if (timetableChangedForDate(today, cache, fresh)) alerts++
            cache = fresh
            displayed = fresh
            selected = startup.date
        })
        assertEquals(today, selected)
        assertEquals(fresh, displayed)
        assertEquals(fresh, cache)
        assertEquals(1, alerts)
    }

    @Test fun navigationRejectsLateStartupBeforeNavigationResponse() {
        for (destination in listOf(yesterday, "2026-10-03")) {
            val requests = TimetableRequests()
            var selected = today
            var displayed = rows(today)
            val startup = requests.begin(today)
            val navigation = requests.begin(destination) {
                selected = destination
                displayed = emptyList()
            }
            assertFalse(requests.applyIfCurrent(startup) {
                selected = today
                displayed = rows(today)
                fail("Stale startup must not write UI, cache, widget or notification")
            })
            assertEquals(destination, selected)
            assertTrue(displayed.isEmpty())
            assertTrue(requests.applyIfCurrent(navigation) { displayed = rows(destination) })
            assertEquals(rows(destination), displayed)
        }
    }

    @Test fun navigationResponseSurvivesStartupCompletingLast() {
        val requests = TimetableRequests()
        var displayed = rows(today)
        val startup = requests.begin(today)
        val navigation = requests.begin(yesterday)
        requests.applyIfCurrent(navigation) { displayed = rows(yesterday) }
        assertFalse(requests.applyIfCurrent(startup) { displayed = rows(today) })
        assertEquals(rows(yesterday), displayed)
    }

    @Test fun returningToTodayStillRejectsOriginalStartup() {
        val requests = TimetableRequests()
        val startup = requests.begin(today)
        requests.begin(yesterday)
        val returnToToday = requests.begin(today)
        assertFalse(requests.applyIfCurrent(startup) { fail("Date equality alone is insufficient") })
        assertTrue(requests.applyIfCurrent(returnToToday) {})
    }

    @Test fun unchangedOrEmptyCacheDoesNotAlert() {
        assertFalse(timetableChangedForDate(today, rows(today), rows(today)))
        assertFalse(timetableChangedForDate(today, emptyList(), rows(today)))
        assertFalse(timetableChangedForDate(today, rows(yesterday) + rows(today), rows(today)))
    }

    @Test fun sameDayRemovedLessonsStillAlert() {
        assertTrue(timetableChangedForDate(today, rows(today), emptyList()))
    }

    // Execute the same lifecycle runner used by MainViewModel, with blocked network phases.
    private fun lateLoginCompletion(error: Exception?, navigationCompletesFirst: Boolean) {
        val requests = TimetableRequests()
        val executor = Executors.newSingleThreadExecutor()
        val loggedInAndFetching = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        var loggedIn = false
        var loading = false
        var selected = today
        var displayed = rows(today)
        var cacheWrites = 0
        var widgets = 0
        var alerts = 0
        var errorMessage = ""
        val login = requests.begin(today) { loading = true }
        val future = executor.submit {
            requests.execute(login, block = {
                requests.applyIfCurrent(login) { loggedIn = true }
                loggedInAndFetching.countDown()
                check(releaseFetch.await(5, TimeUnit.SECONDS))
                if (error != null) throw error
                requests.applyIfCurrent(login) {
                    selected = today
                    displayed = rows(today, "stale")
                    cacheWrites++
                    widgets++
                    alerts++
                }
            }, onError = {
                errorMessage = it.message.orEmpty()
                if ((it as? ScerpApiException)?.code == "authentication_required") loggedIn = false
            }, onFinished = { loading = false })
        }
        try {
            assertTrue(loggedInAndFetching.await(5, TimeUnit.SECONDS))
            assertTrue(loggedIn)
            val navigation = requests.begin(yesterday) {
                selected = yesterday
                displayed = emptyList()
                loading = true
            }
            if (navigationCompletesFirst) requests.execute(navigation, block = {
                requests.applyIfCurrent(navigation) { displayed = rows(yesterday) }
            }, onError = { fail("Navigation must succeed") }, onFinished = { loading = false })
            releaseFetch.countDown()
            future.get(5, TimeUnit.SECONDS)
            assertTrue(loggedIn)
            assertEquals("", errorMessage)
            assertEquals(yesterday, selected)
            assertEquals(0, cacheWrites)
            assertEquals(0, widgets)
            assertEquals(0, alerts)
            assertEquals(!navigationCompletesFirst, loading)
            if (!navigationCompletesFirst) {
                assertTrue(displayed.isEmpty())
                requests.execute(navigation, block = {
                    requests.applyIfCurrent(navigation) { displayed = rows(yesterday) }
                }, onError = { fail("Navigation must succeed") }, onFinished = { loading = false })
            }
            assertEquals(rows(yesterday), displayed)
            assertFalse(loading)
        } finally {
            releaseFetch.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun slowLoginRefreshCannotOverwriteNavigationOrEndItsLoading() {
        lateLoginCompletion(null, false)
    }

    @Test fun staleLoginAuthenticationRequiredCannotLogoutOrEndNewLoading() {
        lateLoginCompletion(ScerpApiException(401, "authentication_required", "expired"), false)
    }

    @Test fun staleLoginErrorCannotReplaceSuccessfulNavigation() {
        lateLoginCompletion(IllegalStateException("old network error"), true)
    }

    @Test fun staleLoginSuccessCannotReplaceSuccessfulNavigation() {
        lateLoginCompletion(null, true)
    }

    @Test fun latestAuthenticationErrorIsHandledAndLoadingEnds() {
        val requests = TimetableRequests()
        val request = requests.begin(today)
        var loggedIn = true
        var loading = true
        var errorCode = ""
        requests.execute(request,
            block = { throw ScerpApiException(401, "authentication_required", "expired") },
            onError = { loggedIn = false; errorCode = (it as ScerpApiException).code },
            onFinished = { loading = false })
        assertFalse(loggedIn)
        assertFalse(loading)
        assertEquals("authentication_required", errorCode)
    }

    @Test fun supersededRequestDoesNotStartNetworkOrFinishLatestLoading() {
        val requests = TimetableRequests()
        val old = requests.begin(today)
        requests.begin(yesterday)
        requests.execute(old, block = { fail("Stale queued IO must not start") },
            onError = { fail("Stale error must not apply") },
            onFinished = { fail("Stale completion must not apply") })
    }

    @Test fun cancellationPropagatesWithoutErrorMessage() {
        val requests = TimetableRequests()
        val request = requests.begin(today)
        var finished = false
        try {
            requests.execute(request, block = { throw CancellationException("cancelled") },
                onError = { fail("Cancellation is not a user error") },
                onFinished = { finished = true })
            fail("Must propagate cancellation")
        } catch (_: CancellationException) {
            assertTrue(finished)
        }
    }
}
