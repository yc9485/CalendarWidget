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
    val sourceTag: String = ""
)
