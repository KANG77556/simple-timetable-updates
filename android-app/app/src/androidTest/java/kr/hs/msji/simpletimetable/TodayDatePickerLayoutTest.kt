package kr.hs.msji.simpletimetable

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayDatePickerLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var confirmed: LocalDate? = null
    private var dismissed = false

    private fun show(date: LocalDate, height: Int = 320, fontScale: Float = 1f, width: Int = 640) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenHeightDp = height
                screenWidthDp = width
                orientation = Configuration.ORIENTATION_LANDSCAPE
                this.fontScale = fontScale
            }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale)
            ) {
                MaterialTheme {
                    TimetableDatePickerDialog(date, { dismissed = true }, { confirmed = it })
                }
            }
        }
    }

    private fun assertActionsFit(height: Int) {
        val dialog = compose.onNode(isDialog()).getUnclippedBoundsInRoot()
        assertTrue("dialog must fit short viewport: $dialog", dialog.height <= (height - 48).dp)
        compose.onNodeWithText("확인").assertIsDisplayed()
        compose.onNodeWithText("취소").assertIsDisplayed()
    }

    @Test fun landscape_six_rows_keep_confirm_accessible_and_last_day_selectable() {
        show(LocalDate.of(2026, 8, 1))
        assertActionsFit(320)
        compose.onNodeWithText("31").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("확인").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 8, 31), confirmed) }
    }

    @Test fun large_font_on_short_screen_keeps_cancel_and_confirm_accessible() {
        show(LocalDate.of(2026, 8, 1), height = 280, fontScale = 2f)
        assertActionsFit(280)
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertNull(confirmed) }
    }

    @Test fun full_cell_is_at_least_48dp_even_on_narrow_screens() {
        show(LocalDate.of(2026, 10, 1), height = 800, width = 320)
        compose.onNodeWithText("3").performScrollTo()
        val day = compose.onNodeWithText("3").getUnclippedBoundsInRoot()
        assertTrue("day width must be at least 48dp: $day", day.width >= 48.dp)
        assertTrue("day height must be at least 48dp: $day", day.height >= 48.dp)
    }

    @Test fun holiday_label_tap_selects_date() {
        show(LocalDate.of(2026, 10, 1), height = 800)
        compose.onNodeWithText("개천절", useUnmergedTree = true)
            .performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("확인").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 10, 3), confirmed) }
    }

    @Test fun month_navigation_preserves_holidays_and_selected_date() {
        show(LocalDate.of(2026, 10, 3), height = 800)
        compose.onNodeWithContentDescription("다음 달").performClick()
        compose.onNodeWithText("2026년 11월").assertIsDisplayed()
        compose.onNodeWithContentDescription("이전 달").performClick()
        compose.onNodeWithText("개천절", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("확인").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 10, 3), confirmed) }
    }
}
