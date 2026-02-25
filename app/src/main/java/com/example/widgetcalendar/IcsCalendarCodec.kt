package com.example.widgetcalendar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object IcsCalendarCodec {

    private val dateOnly = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        isLenient = false
    }
    private val dateTimeLocal = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
        isLenient = false
    }
    private val dateTimeUtc = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val dtStampFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun export(items: List<TodoItem>): String {
        val builder = StringBuilder()
        builder.append("BEGIN:VCALENDAR\r\n")
        builder.append("VERSION:2.0\r\n")
        builder.append("PRODID:-//WidgetCalendar//EN\r\n")
        builder.append("CALSCALE:GREGORIAN\r\n")
        builder.append("METHOD:PUBLISH\r\n")

        val nowStamp = dtStampFormat.format(System.currentTimeMillis())
        items.forEach { item ->
            val startDay = CalendarRepository.dayStart(item.startDateMillis)
            val endDay = CalendarRepository.dayStart(item.endDateMillis)

            builder.append("BEGIN:VEVENT\r\n")
            builder.append("UID:${escapeText(item.id)}@widgetcalendar\r\n")
            builder.append("DTSTAMP:$nowStamp\r\n")
            builder.append("SUMMARY:${escapeText(item.title)}\r\n")
            builder.append(if (item.completed) "STATUS:COMPLETED\r\n" else "STATUS:CONFIRMED\r\n")
            if (item.sourceTag.isNotBlank()) {
                builder.append("X-WIDGET-SOURCE:${escapeText(item.sourceTag)}\r\n")
            }
            builder.append("PRIORITY:${toIcsPriority(item.priority)}\r\n")
            builder.append("X-WIDGET-PRIORITY:${item.priority}\r\n")

            val recurrence = normalizeRecurrence(item.recurrence)
            if (recurrence != RECURRENCE_NONE) {
                builder.append("RRULE:${buildRRule(recurrence, item.recurrenceUntilMillis)}\r\n")
                builder.append("X-WIDGET-RECURRENCE:${recurrence}\r\n")
                val until = CalendarRepository.dayStart(item.recurrenceUntilMillis)
                if (until > 0L) {
                    builder.append("X-WIDGET-RECURRENCE-UNTIL:${dateOnly.format(until)}\r\n")
                }
            }

            if (item.hasTime && item.startMinute in 0..1439 && item.endMinute in 0..1439) {
                val startMs = startDay + item.startMinute * 60_000L
                val endMs = endDay + item.endMinute * 60_000L
                builder.append("DTSTART:${dateTimeLocal.format(startMs)}\r\n")
                builder.append("DTEND:${dateTimeLocal.format(endMs)}\r\n")
            } else {
                builder.append("DTSTART;VALUE=DATE:${dateOnly.format(startDay)}\r\n")
                val endExclusive = endDay + 86_400_000L
                builder.append("DTEND;VALUE=DATE:${dateOnly.format(endExclusive)}\r\n")
            }
            builder.append("END:VEVENT\r\n")
        }

        builder.append("END:VCALENDAR\r\n")
        return builder.toString()
    }

    fun import(content: String): List<TodoItem> {
        val lines = unfoldLines(content)
        val events = mutableListOf<List<PropertyLine>>()
        var inEvent = false
        var current = mutableListOf<PropertyLine>()

        lines.forEach { raw ->
            val line = raw.trim()
            if (line.equals("BEGIN:VEVENT", ignoreCase = true)) {
                inEvent = true
                current = mutableListOf()
                return@forEach
            }
            if (line.equals("END:VEVENT", ignoreCase = true)) {
                if (inEvent) events += current.toList()
                inEvent = false
                return@forEach
            }
            if (inEvent) {
                parseProperty(line)?.let { current += it }
            }
        }

        return events.mapNotNull { props -> buildTodoFromEvent(props) }
    }

    private fun buildTodoFromEvent(props: List<PropertyLine>): TodoItem? {
        val summary = props.firstOrNull { it.name == "SUMMARY" }?.value?.let(::unescapeText) ?: return null
        if (summary.isBlank()) return null

        val uidRaw = props.firstOrNull { it.name == "UID" }?.value?.let(::unescapeText).orEmpty()
        val id = uidRaw.substringBefore("@").ifBlank { UUID.randomUUID().toString() }
        val status = props.firstOrNull { it.name == "STATUS" }?.value.orEmpty().uppercase(Locale.US)
        val completed = status == "COMPLETED"
        val sourceTag = props.firstOrNull { it.name == "X-WIDGET-SOURCE" }?.value?.let(::unescapeText).orEmpty()
        val priority = parsePriority(props)
        val recurrence = parseRecurrence(props)
        val recurrenceUntilMillis = parseRecurrenceUntil(props)

        val dtStart = props.firstOrNull { it.name == "DTSTART" } ?: return null
        val dtEnd = props.firstOrNull { it.name == "DTEND" }

        val startIsDateOnly = dtStart.params["VALUE"]?.equals("DATE", ignoreCase = true) == true ||
            dtStart.value.length == 8

        return if (startIsDateOnly) {
            val startDay = parseDateOnly(dtStart.value) ?: return null
            val endDay = dtEnd?.value?.let { parseDateOnly(it)?.minus(86_400_000L) } ?: startDay
            TodoItem(
                id = id,
                title = CalendarRepository.normalizeTitle(summary),
                startDateMillis = CalendarRepository.dayStart(startDay),
                endDateMillis = CalendarRepository.dayStart(endDay),
                hasTime = false,
                startMinute = -1,
                endMinute = -1,
                completed = completed,
                sourceTag = sourceTag,
                priority = priority,
                recurrence = recurrence,
                recurrenceUntilMillis = recurrenceUntilMillis
            )
        } else {
            val startMs = parseDateTime(dtStart.value) ?: return null
            val endMs = dtEnd?.value?.let { parseDateTime(it) } ?: startMs
            val startCal = Calendar.getInstance().apply { timeInMillis = startMs }
            val endCal = Calendar.getInstance().apply { timeInMillis = endMs }
            TodoItem(
                id = id,
                title = CalendarRepository.normalizeTitle(summary),
                startDateMillis = CalendarRepository.dayStart(startMs),
                endDateMillis = CalendarRepository.dayStart(endMs),
                hasTime = true,
                startMinute = startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE),
                endMinute = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE),
                completed = completed,
                sourceTag = sourceTag,
                priority = priority,
                recurrence = recurrence,
                recurrenceUntilMillis = recurrenceUntilMillis
            )
        }
    }

    private fun parseDateOnly(value: String): Long? {
        return runCatching { dateOnly.parse(value)?.time }.getOrNull()
    }

    private fun parseDateTime(value: String): Long? {
        return runCatching {
            if (value.endsWith("Z")) {
                dateTimeUtc.parse(value)?.time
            } else {
                dateTimeLocal.parse(value)?.time
            }
        }.getOrNull()
    }

    private fun unfoldLines(content: String): List<String> {
        val raw = content.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val result = mutableListOf<String>()
        raw.forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.trimStart()
            } else {
                result += line
            }
        }
        return result
    }

    private fun parseProperty(line: String): PropertyLine? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null

        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val headParts = head.split(';')
        if (headParts.isEmpty()) return null
        val name = headParts.first().uppercase(Locale.US)
        val params = mutableMapOf<String, String>()
        headParts.drop(1).forEach { p ->
            val eq = p.indexOf('=')
            if (eq > 0) {
                params[p.substring(0, eq).uppercase(Locale.US)] = p.substring(eq + 1)
            }
        }
        return PropertyLine(name = name, params = params, value = value)
    }

    private fun escapeText(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }

    private fun unescapeText(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private fun toIcsPriority(priority: Int): Int {
        return when (priority) {
            PRIORITY_HIGH -> 1
            PRIORITY_LOW -> 9
            else -> 5
        }
    }

    private fun parsePriority(props: List<PropertyLine>): Int {
        val custom = props.firstOrNull { it.name == "X-WIDGET-PRIORITY" }?.value?.toIntOrNull()
        if (custom != null) return custom

        return when (props.firstOrNull { it.name == "PRIORITY" }?.value?.toIntOrNull()) {
            null -> PRIORITY_NORMAL
            in 1..3 -> PRIORITY_HIGH
            in 7..9 -> PRIORITY_LOW
            else -> PRIORITY_NORMAL
        }
    }

    private fun parseRecurrence(props: List<PropertyLine>): String {
        val custom = props.firstOrNull { it.name == "X-WIDGET-RECURRENCE" }
            ?.value
            ?.let(::unescapeText)
            ?.uppercase(Locale.US)
            .orEmpty()
        if (custom in RECURRENCE_TYPES) return custom

        val rrule = props.firstOrNull { it.name == "RRULE" }?.value.orEmpty()
        val freq = rrule.split(';')
            .firstOrNull { it.startsWith("FREQ=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.uppercase(Locale.US)
            .orEmpty()
        return normalizeRecurrence(freq)
    }

    private fun parseRecurrenceUntil(props: List<PropertyLine>): Long {
        val custom = props.firstOrNull { it.name == "X-WIDGET-RECURRENCE-UNTIL" }
            ?.value
            ?.let(::parseFlexibleDate)
        if (custom != null) return custom

        val untilRaw = props.firstOrNull { it.name == "RRULE" }?.value.orEmpty()
            .split(';')
            .firstOrNull { it.startsWith("UNTIL=", ignoreCase = true) }
            ?.substringAfter('=')
            ?: return 0L
        return parseFlexibleDate(untilRaw) ?: 0L
    }

    private fun parseFlexibleDate(value: String): Long? {
        return when {
            value.length == 8 -> parseDateOnly(value)
            value.endsWith("Z") -> parseDateTime(value)
            value.contains("T") -> parseDateTime(value)
            else -> null
        }?.let(CalendarRepository::dayStart)
    }

    private fun buildRRule(recurrence: String, recurrenceUntilMillis: Long): String {
        val freq = when (recurrence) {
            RECURRENCE_DAILY -> "DAILY"
            RECURRENCE_WEEKLY -> "WEEKLY"
            RECURRENCE_MONTHLY -> "MONTHLY"
            RECURRENCE_YEARLY -> "YEARLY"
            else -> "DAILY"
        }
        val until = CalendarRepository.dayStart(recurrenceUntilMillis)
        return if (until > 0L) {
            "FREQ=$freq;UNTIL=${dateOnly.format(until)}"
        } else {
            "FREQ=$freq"
        }
    }

    private fun normalizeRecurrence(value: String): String {
        val normalized = value.trim().uppercase(Locale.US)
        return if (normalized in RECURRENCE_TYPES) normalized else RECURRENCE_NONE
    }
}

private data class PropertyLine(
    val name: String,
    val params: Map<String, String>,
    val value: String
)
