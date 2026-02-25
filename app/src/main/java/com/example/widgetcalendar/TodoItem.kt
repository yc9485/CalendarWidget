package com.example.widgetcalendar

data class TodoItem(
    val id: String,
    val title: String,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val hasTime: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val completed: Boolean,
    val sourceTag: String = "",
    val priority: Int = PRIORITY_NORMAL,
    val recurrence: String = RECURRENCE_NONE,
    val recurrenceUntilMillis: Long = 0L,
    val seriesId: String = "",
    val generatedOccurrence: Boolean = false
)

const val PRIORITY_LOW = 0
const val PRIORITY_NORMAL = 1
const val PRIORITY_HIGH = 2

const val RECURRENCE_NONE = "NONE"
const val RECURRENCE_DAILY = "DAILY"
const val RECURRENCE_WEEKLY = "WEEKLY"
const val RECURRENCE_MONTHLY = "MONTHLY"
const val RECURRENCE_YEARLY = "YEARLY"

val RECURRENCE_TYPES = setOf(
    RECURRENCE_NONE,
    RECURRENCE_DAILY,
    RECURRENCE_WEEKLY,
    RECURRENCE_MONTHLY,
    RECURRENCE_YEARLY
)
