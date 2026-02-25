package com.example.widgetcalendar

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import java.util.Calendar

class CalendarGridRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return CalendarGridRemoteViewsFactory(applicationContext, widgetId)
    }
}

private data class DayCell(
    val timeMillis: Long,
    val dayNumber: String,
    val lines: List<DayLine?>,
    val inDisplayedMonth: Boolean,
    val isToday: Boolean
)

private data class DayLine(
    val text: String,
    val completed: Boolean,
    val spanPosition: CalendarRepository.SpanPosition,
    val isMultiDay: Boolean,
    val priority: Int = PRIORITY_NORMAL,
    val bitmap: Bitmap? = null
)

private data class MultiLanePlacement(
    val item: TodoItem,
    val startIndex: Int,
    val endIndex: Int,
    val lane: Int
)

private class CalendarGridRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private val cells = mutableListOf<DayCell>()
    private var visibleRowCount = 6
    private var cachedTaskTextWidthPx = 0f
    private val taskLineHeightPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        12f,
        context.resources.displayMetrics
    ).toInt().coerceAtLeast(1)
    private val multiTextPaddingPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        2f,
        context.resources.displayMetrics
    )
    private val multiCornerRadiusPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        4f,
        context.resources.displayMetrics
    )
    private val taskTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            8f,
            context.resources.displayMetrics
        )
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        cells.clear()
        cachedTaskTextWidthPx = estimateTaskTextWidthPx()
        cells.addAll(buildCells())
    }

    override fun onDestroy() {
        cells.clear()
    }

    override fun getCount(): Int = cells.size

    override fun getViewAt(position: Int): RemoteViews {
        val cell = cells[position]
        val layoutRes = if (visibleRowCount <= 5) {
            R.layout.widget_calendar_day_5
        } else {
            R.layout.widget_calendar_day_6
        }

        return RemoteViews(context.packageName, layoutRes).apply {
            setTextViewText(R.id.tvDayNumber, cell.dayNumber)
            setTaskLine(this, R.id.tvTask1, R.id.ivTask1, cell.lines[0])
            setTaskLine(this, R.id.tvTask2, R.id.ivTask2, cell.lines[1])
            setTaskLine(this, R.id.tvTask3, R.id.ivTask3, cell.lines[2])

            val dayColor = if (cell.inDisplayedMonth) {
                ContextCompat.getColor(context, R.color.day_text_primary)
            } else {
                ContextCompat.getColor(context, R.color.day_text_muted)
            }
            setTextColor(R.id.tvDayNumber, dayColor)

            val bgColor = when {
                cell.isToday -> ContextCompat.getColor(context, R.color.today_background)
                cell.inDisplayedMonth -> ContextCompat.getColor(context, R.color.day_background_current_month)
                else -> ContextCompat.getColor(context, R.color.day_background_other_month)
            }
            setInt(R.id.dayContainer, "setBackgroundColor", bgColor)

            val fillIntent = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(CalendarWidgetProvider.EXTRA_DATE_MILLIS, cell.timeMillis)
            }
            setOnClickFillInIntent(R.id.dayContainer, fillIntent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = cells[position].timeMillis / 86_400_000L

    override fun hasStableIds(): Boolean = true

    private fun buildCells(): List<DayCell> {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return emptyList()

        val displayedMonth = CalendarRepository.getDisplayedMonthCalendar(context, appWidgetId)
        val targetMonth = displayedMonth.get(Calendar.MONTH)
        val targetYear = displayedMonth.get(Calendar.YEAR)
        visibleRowCount = calculateVisibleRowCount(displayedMonth)
        val totalDays = visibleRowCount * 7

        val firstCellDate = displayedMonth.clone() as Calendar
        val offsetFromSunday = firstCellDate.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        firstCellDate.add(Calendar.DAY_OF_MONTH, -offsetFromSunday)

        val dayMillisList = buildList(totalDays) {
            val c = firstCellDate.clone() as Calendar
            repeat(totalDays) {
                add(CalendarRepository.dayStart(c.timeInMillis))
                c.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val visibleStart = dayMillisList.first()
        val visibleEnd = dayMillisList.last()

        val allItems = CalendarRepository.getAllTodoItems(context)
        val visibleItems = CalendarRepository.expandItemsForRange(
            allItems,
            visibleStart,
            visibleEnd
        )

        val multiLanePlacements = assignMultiDayLanes(visibleItems, visibleStart, totalDays)
        val multiDayCache = buildMultiDayLineCache(multiLanePlacements)
        val todayKey = CalendarRepository.dateKey(System.currentTimeMillis())
        val result = ArrayList<DayCell>(totalDays)

        dayMillisList.forEachIndexed { dayIndex, dayMillis ->
            val laneLines = arrayOfNulls<DayLine>(3)
            val laneItemIds = mutableSetOf<String>()

            multiLanePlacements.forEach { placement ->
                if (dayIndex in placement.startIndex..placement.endIndex) {
                    laneLines[placement.lane] = multiDayCache[lineKey(dayIndex, placement.lane)]
                        ?: buildLineForPlacement(placement, dayIndex)
                    laneItemIds += placement.item.id
                }
            }

            val dayItemsSorted = CalendarRepository.sortItemsForDisplay(
                visibleItems.filter { item ->
                    val start = CalendarRepository.dayStart(item.startDateMillis)
                    val end = CalendarRepository.dayStart(item.endDateMillis)
                    dayMillis in start..end
                }
            )
            val extras = dayItemsSorted.filter { it.id !in laneItemIds }

            val freeLanes = (0..2).filter { laneLines[it] == null }
            freeLanes.forEachIndexed { idx, lane ->
                if (idx < extras.size) {
                    laneLines[lane] = buildLineForDay(extras[idx], dayMillis)
                }
            }

            val hiddenCount = (extras.size - freeLanes.size).coerceAtLeast(0)
            if (hiddenCount > 0) {
                val lastLaneWithContent = (2 downTo 0).firstOrNull {
                    laneLines[it] != null && laneLines[it]?.isMultiDay == false
                } ?: (2 downTo 0).firstOrNull { laneLines[it] != null }
                if (lastLaneWithContent != null) {
                    val base = laneLines[lastLaneWithContent]!!
                    laneLines[lastLaneWithContent] = base.copy(text = "${base.text} +$hiddenCount")
                }
            }

            val dayCal = Calendar.getInstance().apply { timeInMillis = dayMillis }
            val key = CalendarRepository.dateKey(dayMillis)
            result += DayCell(
                timeMillis = dayMillis,
                dayNumber = dayCal.get(Calendar.DAY_OF_MONTH).toString(),
                lines = laneLines.toList(),
                inDisplayedMonth = dayCal.get(Calendar.MONTH) == targetMonth &&
                    dayCal.get(Calendar.YEAR) == targetYear,
                isToday = key == todayKey
            )
        }

        return result
    }

    private fun assignMultiDayLanes(
        items: List<TodoItem>,
        visibleStart: Long,
        totalDays: Int
    ): List<MultiLanePlacement> {
        val lastIndex = totalDays - 1
        val laneIntervals = Array(3) { mutableListOf<IntRange>() }

        val multiItems = items
            .filter {
                CalendarRepository.dayStart(it.startDateMillis) !=
                    CalendarRepository.dayStart(it.endDateMillis)
            }
            .sortedWith(
                compareBy<TodoItem>(
                    { CalendarRepository.dayStart(it.startDateMillis) },
                    { CalendarRepository.dayStart(it.endDateMillis) },
                    { it.id }
                )
            )

        val placements = mutableListOf<MultiLanePlacement>()
        multiItems.forEach { item ->
            val rawStartIndex = dayIndex(item.startDateMillis, visibleStart)
            val rawEndIndex = dayIndex(item.endDateMillis, visibleStart)
            val startIndex = rawStartIndex.coerceIn(0, lastIndex)
            val endIndex = rawEndIndex.coerceIn(0, lastIndex)
            val interval = startIndex..endIndex

            val lane = (0..2).firstOrNull { lane ->
                laneIntervals[lane].none { rangesOverlap(it, interval) }
            } ?: return@forEach

            laneIntervals[lane] += interval
            placements += MultiLanePlacement(
                item = item,
                startIndex = startIndex,
                endIndex = endIndex,
                lane = lane
            )
        }

        return placements
    }

    private fun rangesOverlap(a: IntRange, b: IntRange): Boolean {
        return a.first <= b.last && b.first <= a.last
    }

    private fun dayIndex(timeMillis: Long, visibleStart: Long): Int {
        val dayMs = 86_400_000L
        return ((CalendarRepository.dayStart(timeMillis) - visibleStart) / dayMs).toInt()
    }

    private fun buildLineForDay(
        item: TodoItem,
        dayMillis: Long
    ): DayLine {
        val start = CalendarRepository.dayStart(item.startDateMillis)
        val end = CalendarRepository.dayStart(item.endDateMillis)
        val spanPosition = when {
            start == end -> CalendarRepository.SpanPosition.SINGLE
            dayMillis == start -> CalendarRepository.SpanPosition.START
            dayMillis == end -> CalendarRepository.SpanPosition.END
            else -> CalendarRepository.SpanPosition.MIDDLE
        }
        val timePrefix = if (item.hasTime && item.startMinute in 0..1439) {
            "${CalendarRepository.formatMinute(item.startMinute)} "
        } else {
            ""
        }
        return DayLine(
            text = "$timePrefix${item.title}",
            completed = item.completed,
            spanPosition = spanPosition,
            isMultiDay = false,
            priority = item.priority
        )
    }

    private fun buildLineForPlacement(
        placement: MultiLanePlacement,
        dayIndex: Int
    ): DayLine {
        val rowStart = (dayIndex / 7) * 7
        val rowEnd = rowStart + 6
        val segmentStart = maxOf(placement.startIndex, rowStart)
        val segmentEnd = minOf(placement.endIndex, rowEnd)
        val isSegmentStart = dayIndex == segmentStart
        val isSegmentEnd = dayIndex == segmentEnd

        val spanPosition = when {
            segmentStart == segmentEnd -> CalendarRepository.SpanPosition.SINGLE
            isSegmentStart -> CalendarRepository.SpanPosition.START
            isSegmentEnd -> CalendarRepository.SpanPosition.END
            else -> CalendarRepository.SpanPosition.MIDDLE
        }

        val segmentSourceText = if (segmentStart == placement.startIndex &&
            placement.item.hasTime &&
            placement.item.startMinute in 0..1439
        ) {
            "${CalendarRepository.formatMinute(placement.item.startMinute)} ${placement.item.title}"
        } else {
            placement.item.title
        }
        val text = splitTextForSegmentDay(
            sourceText = segmentSourceText,
            segmentStart = segmentStart,
            segmentEnd = segmentEnd,
            dayIndex = dayIndex
        )

        return DayLine(
            text = text,
            completed = placement.item.completed,
            spanPosition = spanPosition,
            isMultiDay = true,
            priority = placement.item.priority,
            bitmap = null
        )
    }

    private fun buildMultiDayLineCache(
        placements: List<MultiLanePlacement>
    ): Map<Long, DayLine> {
        val cache = HashMap<Long, DayLine>()
        placements.forEach { placement ->
            var currentStart = placement.startIndex
            while (currentStart <= placement.endIndex) {
                val rowStart = (currentStart / 7) * 7
                val rowEnd = rowStart + 6
                val segmentEnd = minOf(placement.endIndex, rowEnd)
                val totalDays = segmentEnd - currentStart + 1

                val segmentText = if (
                    currentStart == placement.startIndex &&
                    placement.item.hasTime &&
                    placement.item.startMinute in 0..1439
                ) {
                    "${CalendarRepository.formatMinute(placement.item.startMinute)} ${placement.item.title}"
                } else {
                    placement.item.title
                }

                val bitmaps = buildMultiDaySegmentBitmaps(
                    text = segmentText,
                    totalDays = totalDays,
                    completed = placement.item.completed,
                    priority = placement.item.priority
                )

                for (offset in 0 until totalDays) {
                    val spanPosition = when {
                        totalDays == 1 -> CalendarRepository.SpanPosition.SINGLE
                        offset == 0 -> CalendarRepository.SpanPosition.START
                        offset == totalDays - 1 -> CalendarRepository.SpanPosition.END
                        else -> CalendarRepository.SpanPosition.MIDDLE
                    }
                    val dayIndex = currentStart + offset
                    cache[lineKey(dayIndex, placement.lane)] = DayLine(
                        text = "",
                        completed = placement.item.completed,
                        spanPosition = spanPosition,
                        isMultiDay = true,
                        priority = placement.item.priority,
                        bitmap = bitmaps.getOrNull(offset)
                    )
                }

                currentStart = segmentEnd + 1
            }
        }
        return cache
    }

    private fun lineKey(dayIndex: Int, lane: Int): Long {
        return dayIndex.toLong() * 10L + lane.toLong()
    }

    private fun buildMultiDaySegmentBitmaps(
        text: String,
        totalDays: Int,
        completed: Boolean,
        priority: Int
    ): List<Bitmap> {
        if (totalDays <= 0) return emptyList()

        val dayWidthPx = cachedTaskTextWidthPx.takeIf { it > 0f } ?: estimateTaskTextWidthPx()
        val fullWidthPx = maxOf(1, (dayWidthPx * totalDays).toInt())
        val bitmap = Bitmap.createBitmap(fullWidthPx, taskLineHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (completed) {
                0xFFCBD5E1.toInt()
            } else {
                when (priority) {
                    PRIORITY_HIGH -> 0xFFDC2626.toInt()
                    PRIORITY_LOW -> 0xFF64748B.toInt()
                    else -> 0xFF5C6BC0.toInt()
                }
            }
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, fullWidthPx.toFloat(), taskLineHeightPx.toFloat())
        val radius = multiCornerRadiusPx
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        val textPaint = TextPaint(taskTextPaint).apply {
            color = if (completed) {
                ContextCompat.getColor(context, R.color.task_chip_text_completed)
            } else {
                ContextCompat.getColor(context, R.color.task_chip_text)
            }
            isStrikeThruText = completed
        }
        val availableTextWidth = (fullWidthPx - (multiTextPaddingPx * 2f)).coerceAtLeast(1f)
        val displayText = TextUtils.ellipsize(
            text,
            textPaint,
            availableTextWidth,
            TextUtils.TruncateAt.END
        ).toString()
        val fontMetrics = textPaint.fontMetrics
        val baseline = (taskLineHeightPx - fontMetrics.bottom - fontMetrics.top) / 2f
        canvas.drawText(displayText, multiTextPaddingPx, baseline, textPaint)

        val slices = ArrayList<Bitmap>(totalDays)
        for (i in 0 until totalDays) {
            val left = (i * dayWidthPx).toInt().coerceIn(0, fullWidthPx - 1)
            val right = if (i == totalDays - 1) {
                fullWidthPx
            } else {
                ((i + 1) * dayWidthPx).toInt().coerceIn(left + 1, fullWidthPx)
            }
            val width = (right - left).coerceAtLeast(1)
            slices += Bitmap.createBitmap(bitmap, left, 0, width, taskLineHeightPx)
        }
        bitmap.recycle()
        return slices
    }

    private fun calculateVisibleRowCount(displayedMonth: Calendar): Int {
        val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOffset = displayedMonth.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val neededRows = (firstDayOffset + daysInMonth + 6) / 7
        return neededRows.coerceIn(5, 6)
    }

    private fun setTaskLine(
        views: RemoteViews,
        textViewId: Int,
        imageViewId: Int,
        line: DayLine?
    ) {
        if (line == null) {
            views.setViewVisibility(textViewId, android.view.View.GONE)
            views.setViewVisibility(imageViewId, android.view.View.GONE)
            return
        }

        if (line.isMultiDay && line.bitmap != null) {
            views.setViewVisibility(imageViewId, android.view.View.VISIBLE)
            views.setImageViewBitmap(imageViewId, line.bitmap)
            views.setViewVisibility(textViewId, android.view.View.GONE)
            return
        }

        views.setViewVisibility(imageViewId, android.view.View.GONE)
        views.setViewVisibility(textViewId, android.view.View.VISIBLE)
        views.setTextViewText(textViewId, line.text)
        views.setTextColor(
            textViewId,
            if (line.completed) {
                ContextCompat.getColor(context, R.color.task_chip_text_completed)
            } else {
                ContextCompat.getColor(context, R.color.task_chip_text)
            }
        )
        views.setInt(
            textViewId,
            "setPaintFlags",
            if (line.completed) Paint.STRIKE_THRU_TEXT_FLAG else 0
        )
        views.setInt(
            textViewId,
            "setBackgroundResource",
            backgroundFor(line.spanPosition, line.completed, line.isMultiDay, line.priority)
        )
        val (leftPad, rightPad) = if (line.isMultiDay) {
            0 to 0
        } else {
            when (line.spanPosition) {
                CalendarRepository.SpanPosition.SINGLE -> 2 to 2
                CalendarRepository.SpanPosition.START -> 0 to 0
                CalendarRepository.SpanPosition.MIDDLE -> 0 to 0
                CalendarRepository.SpanPosition.END -> 0 to 0
            }
        }
        views.setViewPadding(textViewId, leftPad, 0, rightPad, 0)
    }

    private fun backgroundFor(
        spanPosition: CalendarRepository.SpanPosition,
        completed: Boolean,
        isMultiDay: Boolean,
        priority: Int
    ): Int {
        if (isMultiDay) {
            if (completed) {
                return when (spanPosition) {
                    CalendarRepository.SpanPosition.SINGLE -> R.drawable.task_multi_chip_done_single
                    CalendarRepository.SpanPosition.START -> R.drawable.task_multi_chip_done_start
                    CalendarRepository.SpanPosition.MIDDLE -> R.drawable.task_multi_chip_done_middle
                    CalendarRepository.SpanPosition.END -> R.drawable.task_multi_chip_done_end
                }
            }
            return when (spanPosition) {
                CalendarRepository.SpanPosition.SINGLE -> R.drawable.task_multi_chip_single
                CalendarRepository.SpanPosition.START -> R.drawable.task_multi_chip_start
                CalendarRepository.SpanPosition.MIDDLE -> R.drawable.task_multi_chip_middle
                CalendarRepository.SpanPosition.END -> R.drawable.task_multi_chip_end
            }
        }
        if (completed) {
            return when (spanPosition) {
                CalendarRepository.SpanPosition.SINGLE -> R.drawable.task_chip_done_single
                CalendarRepository.SpanPosition.START -> R.drawable.task_chip_done_start
                CalendarRepository.SpanPosition.MIDDLE -> R.drawable.task_chip_done_middle
                CalendarRepository.SpanPosition.END -> R.drawable.task_chip_done_end
            }
        }
        if (priority == PRIORITY_HIGH) {
            return when (spanPosition) {
                CalendarRepository.SpanPosition.SINGLE -> R.drawable.task_chip_high_single
                CalendarRepository.SpanPosition.START -> R.drawable.task_chip_high_start
                CalendarRepository.SpanPosition.MIDDLE -> R.drawable.task_chip_high_middle
                CalendarRepository.SpanPosition.END -> R.drawable.task_chip_high_end
            }
        }
        if (priority == PRIORITY_LOW) {
            return when (spanPosition) {
                CalendarRepository.SpanPosition.SINGLE -> R.drawable.task_chip_low_single
                CalendarRepository.SpanPosition.START -> R.drawable.task_chip_low_start
                CalendarRepository.SpanPosition.MIDDLE -> R.drawable.task_chip_low_middle
                CalendarRepository.SpanPosition.END -> R.drawable.task_chip_low_end
            }
        }
        return when (spanPosition) {
            CalendarRepository.SpanPosition.SINGLE -> R.drawable.task_chip_single
            CalendarRepository.SpanPosition.START -> R.drawable.task_chip_start
            CalendarRepository.SpanPosition.MIDDLE -> R.drawable.task_chip_middle
            CalendarRepository.SpanPosition.END -> R.drawable.task_chip_end
        }
    }

    private fun splitTextForSegmentDay(
        sourceText: String,
        segmentStart: Int,
        segmentEnd: Int,
        dayIndex: Int
    ): String {
        if (sourceText.isEmpty()) return ""
        val totalDays = segmentEnd - segmentStart + 1
        val relativeDay = dayIndex - segmentStart
        if (relativeDay !in 0 until totalDays) return ""

        val slices = MutableList(totalDays) { "" }
        val starts = IntArray(totalDays) { -1 }
        val widthPx = cachedTaskTextWidthPx.takeIf { it > 0f } ?: estimateTaskTextWidthPx()

        var cursor = 0
        for (i in 0 until totalDays) {
            if (cursor >= sourceText.length) break
            starts[i] = cursor

            val remaining = sourceText.substring(cursor)
            var take = taskTextPaint.breakText(remaining, true, widthPx, null)
            if (take <= 0) take = 1
            take = minOf(take, remaining.length)
            if (take <= 0) take = 1

            val end = cursor + take
            slices[i] = sourceText.substring(cursor, end)
            cursor = end
        }

        if (cursor < sourceText.length) {
            val lastUsedIndex = (totalDays - 1 downTo 0).firstOrNull { starts[it] >= 0 } ?: 0
            val overflowStart = starts[lastUsedIndex].coerceAtLeast(0)
            slices[lastUsedIndex] = TextUtils.ellipsize(
                sourceText.substring(overflowStart),
                taskTextPaint,
                widthPx,
                TextUtils.TruncateAt.END
            ).toString()
        }

        return slices[relativeDay]
    }

    private fun estimateTaskTextWidthPx(): Float {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val widgetWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }
            ?: 250
        val contentWidthDp = (widgetWidthDp - 8f).coerceAtLeast(140f)
        val dayWidthDp = contentWidthDp / 7f
        val dayWidthPx = dayWidthDp * context.resources.displayMetrics.density
        val stitchBonusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1.25f,
            context.resources.displayMetrics
        )
        return (dayWidthPx + stitchBonusPx).coerceAtLeast(24f)
    }

}


