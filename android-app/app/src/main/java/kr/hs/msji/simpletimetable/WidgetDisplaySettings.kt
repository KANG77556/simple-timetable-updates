package kr.hs.msji.simpletimetable

import android.content.Context

object WidgetDisplaySettings {
    private const val PREFS = "appearance"
    private const val KEY_FONT_SCALE = "widget_font_scale"

    val options: List<Pair<Float, String>> = listOf(
        0.85f to "작게",
        1.0f to "보통",
        1.15f to "크게",
        1.30f to "아주 크게"
    )

    fun fontScale(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_FONT_SCALE, 1.0f)
            .coerceIn(0.85f, 1.30f)

    fun setFontScale(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SCALE, scale.coerceIn(0.85f, 1.30f))
            .apply()
    }

    internal fun scaled(baseSp: Float, scale: Float): Float = baseSp * scale.coerceIn(0.85f, 1.30f)

    fun scaled(context: Context, baseSp: Float): Float = scaled(baseSp, fontScale(context))

    fun refreshAllWidgets(context: Context) {
        TimetableWidget.updateAll(context)
        NextClassWidget.updateAll(context)
        TodaySummaryWidget.updateAll(context)
        PinnedMemoWidget.updateAll(context)
    }
}
