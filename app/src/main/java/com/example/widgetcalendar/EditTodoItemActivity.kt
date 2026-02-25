package com.example.widgetcalendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.UUID

class EditTodoItemActivity : AppCompatActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var todoId: String? = null
    private var existingCompleted: Boolean = false
    private var existingSourceTag: String = ""

    private var startDateMillis: Long = 0L
    private var endDateMillis: Long = 0L
    private var startMinute: Int = 9 * 60
    private var endMinute: Int = 10 * 60

    private lateinit var tvDialogTitle: TextView
    private lateinit var etTitle: EditText
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var cbHasTime: CheckBox
    private lateinit var timeContainer: View
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnDelete: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_todo_item)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        todoId = intent.getStringExtra(EXTRA_TODO_ID)

        tvDialogTitle = findViewById(R.id.tvDialogTitle)
        etTitle = findViewById(R.id.etTitle)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        cbHasTime = findViewById(R.id.cbHasTime)
        timeContainer = findViewById(R.id.timeContainer)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        btnDelete = findViewById(R.id.btnDelete)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        initializeState()
        bindStateToViews()

        btnStartDate.setOnClickListener {
            pickDate(startDateMillis) { picked ->
                startDateMillis = picked
                if (endDateMillis < startDateMillis) {
                    endDateMillis = startDateMillis
                }
                bindStateToViews()
            }
        }

        btnEndDate.setOnClickListener {
            pickDate(endDateMillis) { picked ->
                endDateMillis = picked
                bindStateToViews()
            }
        }

        cbHasTime.setOnCheckedChangeListener { _, _ ->
            bindStateToViews()
        }

        btnStartTime.setOnClickListener {
            pickTime(startMinute) { minute ->
                startMinute = minute
                bindStateToViews()
            }
        }

        btnEndTime.setOnClickListener {
            pickTime(endMinute) { minute ->
                endMinute = minute
                bindStateToViews()
            }
        }

        btnSave.setOnClickListener {
            saveItem()
        }

        btnDelete.setOnClickListener {
            val id = todoId
            if (id != null) {
                CalendarRepository.deleteTodoItem(this, id)
                notifyWidgetChanged()
            }
            finish()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun initializeState() {
        val selectedDate = CalendarRepository.dayStart(
            intent.getLongExtra(CalendarWidgetProvider.EXTRA_DATE_MILLIS, System.currentTimeMillis())
        )

        val existing = todoId?.let { CalendarRepository.getTodoItemById(this, it) }
        if (existing == null) {
            startDateMillis = selectedDate
            endDateMillis = selectedDate
            existingCompleted = false
            existingSourceTag = ""
            return
        }

        startDateMillis = CalendarRepository.dayStart(existing.startDateMillis)
        endDateMillis = CalendarRepository.dayStart(existing.endDateMillis)
        startMinute = if (existing.startMinute in 0..1439) existing.startMinute else startMinute
        endMinute = if (existing.endMinute in 0..1439) existing.endMinute else endMinute
        existingCompleted = existing.completed
        existingSourceTag = existing.sourceTag
        etTitle.setText(existing.title)
        cbHasTime.isChecked = existing.hasTime
    }

    private fun bindStateToViews() {
        val editingExisting = !todoId.isNullOrBlank()
        tvDialogTitle.setText(
            if (editingExisting) R.string.edit_item_title else R.string.add_item_title
        )
        btnDelete.visibility = if (editingExisting) View.VISIBLE else View.GONE

        btnStartDate.text = CalendarRepository.formatDateButton(startDateMillis)
        btnEndDate.text = CalendarRepository.formatDateButton(endDateMillis)

        timeContainer.visibility = if (cbHasTime.isChecked) View.VISIBLE else View.GONE
        btnStartTime.text = CalendarRepository.formatMinute(startMinute)
        btnEndTime.text = CalendarRepository.formatMinute(endMinute)
    }

    private fun saveItem() {
        val title = CalendarRepository.normalizeTitle(etTitle.text.toString())
        if (title.isBlank()) {
            Toast.makeText(this, R.string.todo_title_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (endDateMillis < startDateMillis) {
            Toast.makeText(this, R.string.invalid_date_range, Toast.LENGTH_SHORT).show()
            return
        }
        if (cbHasTime.isChecked && startDateMillis == endDateMillis && endMinute < startMinute) {
            Toast.makeText(this, R.string.invalid_time_range, Toast.LENGTH_SHORT).show()
            return
        }

        val item = TodoItem(
            id = todoId ?: UUID.randomUUID().toString(),
            title = title,
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis,
            hasTime = cbHasTime.isChecked,
            startMinute = startMinute,
            endMinute = endMinute,
            completed = existingCompleted,
            sourceTag = existingSourceTag
        )

        CalendarRepository.upsertTodoItem(this, item)
        notifyWidgetChanged()
        finish()
    }

    private fun pickDate(initialDateMillis: Long, onSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialDateMillis }
        val dialog = DatePickerDialog(
            this,
            android.R.style.Theme_DeviceDefault_Light_Dialog,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }.timeInMillis
                onSelected(CalendarRepository.dayStart(picked))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        ensureDialogButtonsVisible(dialog)
        dialog.show()
    }

    private fun pickTime(initialMinute: Int, onSelected: (Int) -> Unit) {
        val safe = initialMinute.coerceIn(0, 1439)
        val hour = safe / 60
        val minute = safe % 60
        val dialog = TimePickerDialog(
            this,
            android.R.style.Theme_DeviceDefault_Light_Dialog,
            { _, pickedHour, pickedMinute ->
                onSelected((pickedHour * 60 + pickedMinute).coerceIn(0, 1439))
            },
            hour,
            minute,
            true
        )
        ensureDialogButtonsVisible(dialog)
        dialog.show()
    }

    private fun ensureDialogButtonsVisible(dialog: android.app.AlertDialog) {
        dialog.setOnShowListener {
            val color = try {
                ContextCompat.getColor(this, R.color.day_text_primary)
            } catch (_: Exception) {
                Color.BLACK
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        }
    }

    private fun notifyWidgetChanged() {
        sendBroadcast(
            Intent(this, CalendarWidgetProvider::class.java).apply {
                action = CalendarWidgetProvider.ACTION_REMINDER_CHANGED
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        )
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            CalendarWidgetProvider.refreshWidget(this, appWidgetId)
        } else {
            CalendarWidgetProvider.refreshAllWidgets(this)
        }
    }

    companion object {
        const val EXTRA_TODO_ID = "extra_todo_id"
    }
}
