package kr.hs.msji.simpletimetable

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDisplaySettingsTest {
    @Test
    fun fontScaleOptions_keepRegressionPresets() {
        assertEquals(
            listOf(
                0.85f to "작게",
                1.0f to "보통",
                1.15f to "크게",
                1.30f to "아주 크게"
            ),
            WidgetDisplaySettings.options
        )
    }

    @Test
    fun scale_appliesSmallDefaultLargeAndExtraLarge() {
        val base = 20f
        assertEquals(17f, WidgetDisplaySettings.scaled(base, 0.85f), 0.0001f)
        assertEquals(20f, WidgetDisplaySettings.scaled(base, 1.0f), 0.0001f)
        assertEquals(23f, WidgetDisplaySettings.scaled(base, 1.15f), 0.0001f)
        assertEquals(26f, WidgetDisplaySettings.scaled(base, 1.30f), 0.0001f)
    }
}
