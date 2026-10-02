package kr.hs.msji.simpletimetable

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoSaveUiTest {
    @Test
    fun addMemoSave_resetsThenClearsFocusThenHidesKeyboard() {
        assertSaveCompletionOrder()
    }

    @Test
    fun updateMemoSave_resetsThenClearsFocusThenHidesKeyboard() {
        assertSaveCompletionOrder()
    }

    private fun assertSaveCompletionOrder() {
        val calls = mutableListOf<String>()
        completeMemoSaveUi(
            resetEditor = { calls += "reset" },
            clearFocus = { calls += "clearFocus" },
            hideKeyboard = { calls += "hideKeyboard" }
        )
        assertEquals(listOf("reset", "clearFocus", "hideKeyboard"), calls)
    }
}
