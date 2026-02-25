package com.example.widgetcalendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CalendarRepository {
    private const val PREFS_NAME = "widget_calendar_prefs"
    private const val MONTH_OFFSET_PREFIX = "month_offset_"
    private const val TODO_ITEMS_KEY = "todo_items_json"

    private const val MAX_TITLE_LENGTH = 60

    enum class SpanPosition {
        SINGLE,
        START,
        MIDDLE,
        END
    }

    fun getDisplayedMonthTitle(context: Context, appWidgetId: Int): String {
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            .format(getDisplayedMonthCalendar(context, appWidgetId).time)
    }

    fun getDisplayedMonthCalendar(context: Context, appWidgetId: Int): Calendar {
        val monthOffset = getMonthOffset(context, appWidgetId)
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, monthOffset)
        }
    }

    fun shiftMonthOffset(context: Context, appWidgetId: Int, delta: Int) {
        val next = getMonthOffset(context, appWidgetId) + delta
        prefs(context).edit().putInt("$MONTH_OFFSET_PREFIX$appWidgetId", next).apply()
    }

    fun setMonthOffset(context: Context, appWidgetId: Int, offset: Int) {
        prefs(context).edit().putInt("$MONTH_OFFSET_PREFIX$appWidgetId", offset).apply()
    }

    fun removeMonthOffset(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove("$MONTH_OFFSET_PREFIX$appWidgetId").apply()
    }

    fun dayStart(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getAllTodoItems(context: Context): List<TodoItem> {
        val raw = prefs(context).getString(TODO_ITEMS_KEY, "[]") ?: "[]"
        return parseTodoItems(raw).sortedWith(todoComparator())
    }

    fun sortItemsForDisplay(items: List<TodoItem>): List<TodoItem> {
        return items.sortedWith(todoComparator())
    }

    fun exportTodoItemsJson(context: Context): String {
        return prefs(context).getString(TODO_ITEMS_KEY, "[]") ?: "[]"
    }

    fun importTodoItemsJson(context: Context, rawJson: String): Int {
        val parsed = parseTodoItemsOrThrow(rawJson).sortedWith(todoComparator())
        saveTodoItems(context, parsed)
        return parsed.size
    }

    fun replaceAllTodoItems(context: Context, items: List<TodoItem>): Int {
        val sorted = items.map { normalizeTodoItem(it) }.sortedWith(todoComparator())
        saveTodoItems(context, sorted)
        return sorted.size
    }

    fun getTodoItemsForDate(context: Context, dateMillis: Long): List<TodoItem> {
        return getTodoItemsForDate(dateMillis, getAllTodoItems(context))
    }

    fun getTodoItemsForDate(dateMillis: Long, allItems: List<TodoItem>): List<TodoItem> {
        val targetDay = dayStart(dateMillis)
        return allItems.filter { item ->
            val start = dayStart(item.startDateMillis)
            val end = dayStart(item.endDateMillis)
            targetDay in start..end
        }.sortedWith(todoComparator())
    }

    fun getTodoItemById(context: Context, itemId: String): TodoItem? {
        return getAllTodoItems(context).firstOrNull { it.id == itemId }
    }

    fun upsertTodoItem(context: Context, item: TodoItem) {
        val normalized = normalizeTodoItem(item)
        val mutable = getAllTodoItems(context).toMutableList()
        val idx = mutable.indexOfFirst { it.id == normalized.id }
        if (idx >= 0) {
            mutable[idx] = normalized
        } else {
            mutable += normalized
        }
        saveTodoItems(context, mutable)
    }

    fun deleteTodoItem(context: Context, itemId: String) {
        val mutable = getAllTodoItems(context).toMutableList()
        val changed = mutable.removeAll { it.id == itemId }
        if (changed) {
            saveTodoItems(context, mutable)
        }
    }

    fun countImportedCalendarItems(context: Context, countryCode: String): Int {
        val cc = countryCode.uppercase(Locale.US)
        return getAllTodoItems(context).count { isImportedForCountry(it, cc) }
    }

    fun removeImportedCalendarItems(context: Context, countryCode: String): Int {
        val cc = countryCode.uppercase(Locale.US)
        val mutable = getAllTodoItems(context).toMutableList()
        val before = mutable.size
        mutable.removeAll { isImportedForCountry(it, cc) }
        val removed = before - mutable.size
        if (removed > 0) {
            saveTodoItems(context, mutable)
        }
        return removed
    }

    fun setTodoCompleted(context: Context, itemId: String, completed: Boolean) {
        val mutable = getAllTodoItems(context).toMutableList()
        val idx = mutable.indexOfFirst { it.id == itemId }
        if (idx < 0) return
        mutable[idx] = mutable[idx].copy(completed = completed)
        saveTodoItems(context, mutable)
    }

    data class DaySummaryLine(
        val text: String,
        val completed: Boolean,
        val spanPosition: SpanPosition
    )

    fun buildDaySummaryLines(items: List<TodoItem>, dayMillis: Long): List<DaySummaryLine> {
        if (items.isEmpty()) return emptyList()
        val targetDay = dayStart(dayMillis)

        val lines = items.take(3).map { item ->
            val timePrefix = if (item.hasTime && item.startMinute in 0..1439) {
                "${formatMinute(item.startMinute)} "
            } else {
                ""
            }
            val start = dayStart(item.startDateMillis)
            val end = dayStart(item.endDateMillis)
            val spanPosition = when {
                start == end -> SpanPosition.SINGLE
                targetDay == start -> SpanPosition.START
                targetDay == end -> SpanPosition.END
                else -> SpanPosition.MIDDLE
            }
            DaySummaryLine(
                text = "$timePrefix${item.title}",
                completed = item.completed,
                spanPosition = spanPosition
            )
        }.toMutableList()

        val hiddenCount = items.size - lines.size
        if (hiddenCount > 0 && lines.isNotEmpty()) {
            val last = lines.last()
            lines[lines.lastIndex] = last.copy(text = "${last.text} +$hiddenCount")
        }

        return lines
    }

    fun normalizeTitle(text: String): String {
        val normalized = text.trim().replace("\\s+".toRegex(), " ")
        return if (normalized.length <= MAX_TITLE_LENGTH) {
            normalized
        } else {
            normalized.substring(0, MAX_TITLE_LENGTH)
        }
    }

    fun formatPrettyDate(timeMillis: Long): String {
        return SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(timeMillis)
    }

    fun formatShortDate(timeMillis: Long): String {
        return SimpleDateFormat("MMM d", Locale.getDefault()).format(timeMillis)
    }

    fun formatDateButton(timeMillis: Long): String {
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(timeMillis)
    }

    fun formatMinute(minute: Int): String {
        val safe = minute.coerceIn(0, 1439)
        val hour = safe / 60
        val minutePart = safe % 60
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minutePart)
    }

    fun formatItemMeta(item: TodoItem): String {
        val datePart = if (dayStart(item.startDateMillis) == dayStart(item.endDateMillis)) {
            formatShortDate(item.startDateMillis)
        } else {
            "${formatShortDate(item.startDateMillis)} - ${formatShortDate(item.endDateMillis)}"
        }

        val schedulePart = if (item.hasTime) {
            "${formatMinute(item.startMinute)} - ${formatMinute(item.endMinute)}"
        } else {
            "All day"
        }
        return "$datePart | $schedulePart"
    }

    fun dateKey(timeMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(timeMillis)
    }

    private fun saveTodoItems(context: Context, items: List<TodoItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("startDateMillis", item.startDateMillis)
                    put("endDateMillis", item.endDateMillis)
                    put("hasTime", item.hasTime)
                    put("startMinute", item.startMinute)
                    put("endMinute", item.endMinute)
                    put("completed", item.completed)
                    put("sourceTag", item.sourceTag)
                }
            )
        }
        prefs(context).edit().putString(TODO_ITEMS_KEY, array.toString()).apply()
    }

    private fun parseTodoItems(raw: String): List<TodoItem> {
        return try {
            val arr = JSONArray(raw)
            parseTodoArray(arr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTodoItemsOrThrow(raw: String): List<TodoItem> {
        val arr = JSONArray(raw)
        return parseTodoArray(arr)
    }

    private fun parseTodoArray(arr: JSONArray): List<TodoItem> {
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val title = normalizeTitle(obj.optString("title"))
                if (id.isBlank() || title.isBlank()) continue

                val parsed = normalizeTodoItem(
                    TodoItem(
                        id = id,
                        title = title,
                        startDateMillis = obj.optLong("startDateMillis"),
                        endDateMillis = obj.optLong("endDateMillis"),
                        hasTime = obj.optBoolean("hasTime", false),
                        startMinute = obj.optInt("startMinute", -1),
                        endMinute = obj.optInt("endMinute", -1),
                        completed = obj.optBoolean("completed", false),
                        sourceTag = obj.optString("sourceTag", "")
                    )
                )
                add(parsed)
            }
        }
    }

    private fun normalizeTodoItem(item: TodoItem): TodoItem {
        val normalizedTitle = normalizeTitle(item.title)
        var startDate = dayStart(item.startDateMillis)
        var endDate = dayStart(item.endDateMillis)
        if (endDate < startDate) {
            val tmp = startDate
            startDate = endDate
            endDate = tmp
        }

        val hasTime = item.hasTime && item.startMinute in 0..1439 && item.endMinute in 0..1439
        var startMinute = if (hasTime) item.startMinute else -1
        var endMinute = if (hasTime) item.endMinute else -1

        if (hasTime && startDate == endDate && endMinute < startMinute) {
            val tmp = startMinute
            startMinute = endMinute
            endMinute = tmp
        }

        return item.copy(
            title = normalizedTitle,
            startDateMillis = startDate,
            endDateMillis = endDate,
            hasTime = hasTime,
            startMinute = startMinute,
            endMinute = endMinute,
            sourceTag = item.sourceTag.trim()
        )
    }

    private fun isImportedForCountry(item: TodoItem, countryCodeUpper: String): Boolean {
        val sourceTag = item.sourceTag.uppercase(Locale.US)
        val legacyId = item.id.uppercase(Locale.US)
        return sourceTag.startsWith("HOLIDAY:$countryCodeUpper") ||
            legacyId.startsWith("HOLIDAY_${countryCodeUpper}_")
    }

    private fun todoComparator(): Comparator<TodoItem> {
        return compareBy<TodoItem>(
            { it.completed },
            { if (it.hasTime) 0 else 1 },
            { if (it.hasTime) it.startMinute else Int.MAX_VALUE },
            { it.title.lowercase(Locale.getDefault()) }
        )
    }

    private fun getMonthOffset(context: Context, appWidgetId: Int): Int {
        return prefs(context).getInt("$MONTH_OFFSET_PREFIX$appWidgetId", 0)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
