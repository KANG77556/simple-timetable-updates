package kr.hs.msji.simpletimetable

/**
 * Startup local collection writes remain blocked until the complete restore publishes.
 * Serializes initial refresh registration with explicit date navigation.
 */
internal class StartupSafetyGate {
    @Volatile private var hydrated = false
    private var userNavigated = false

    fun isHydrated(): Boolean = hydrated

    fun markHydrated() {
        hydrated = true
    }

    @Synchronized fun navigationUntouched(): Boolean = !userNavigated

    @Synchronized fun runUserNavigation(action: () -> Unit) {
        userNavigated = true
        action()
    }

    @Synchronized fun runStartupRefreshIfUntouched(action: () -> Unit): Boolean {
        if (userNavigated) return false
        action()
        return true
    }
}
