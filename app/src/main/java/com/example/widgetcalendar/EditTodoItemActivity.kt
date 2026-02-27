package com.example.widgetcalendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
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
    private var currentDescription: String = ""

    private var startDateMillis: Long = 0L
    private var endDateMillis: Long = 0L
    private var startMinute: Int = 9 * 60
    private var endMinute: Int = 10 * 60
    private var selectedPriority: Int = PRIORITY_NORMAL
    private var selectedRecurrence: String = RECURRENCE_NONE
    private var recurrenceUntilMillis: Long = 0L

    private lateinit var tvDialogTitle: TextView
    private lateinit var etTitle: EditText
    private lateinit var btnEditDescription: Button
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var spPriority: Spinner
    private lateinit var spRecurrence: Spinner
    private lateinit var btnRecurrenceUntil: Button
    private lateinit var cbHasTime: CheckBox
    private lateinit var timeContainer: View
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnDelete: Button

    private val priorityValues = listOf(PRIORITY_NORMAL, PRIORITY_HIGH, PRIORITY_LOW)
    private val recurrenceValues = listOf(
        RECURRENCE_NONE,
        RECURRENCE_DAILY,
        RECURRENCE_WEEKLY,
        RECURRENCE_MONTHLY,
        RECURRENCE_YEARLY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_edit_todo_item)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        todoId = intent.getStringExtra(EXTRA_TODO_ID)

        tvDialogTitle = findViewById(R.id.tvDialogTitle)
        etTitle = findViewById(R.id.etTitle)
        btnEditDescription = findViewById(R.id.btnEditDescription)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        spPriority = findViewById(R.id.spPriority)
        spRecurrence = findViewById(R.id.spRecurrence)
        btnRecurrenceUntil = findViewById(R.id.btnRecurrenceUntil)
        cbHasTime = findViewById(R.id.cbHasTime)
        timeContainer = findViewById(R.id.timeContainer)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        btnDelete = findViewById(R.id.btnDelete)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        bindSelectors()
        initializeState()
        bindStateToViews()

        btnEditDescription.setOnClickListener {
            showDescriptionDialog()
        }

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

        btnRecurrenceUntil.setOnClickListener {
            val initial = if (recurrenceUntilMillis > 0L) recurrenceUntilMillis else endDateMillis
            pickDate(initial) { picked ->
                recurrenceUntilMillis = picked
                bindStateToViews()
            }
        }
        btnRecurrenceUntil.setOnLongClickListener {
            recurrenceUntilMillis = 0L
            bindStateToViews()
            true
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
            currentDescription = ""
            return
        }

        startDateMillis = CalendarRepository.dayStart(existing.startDateMillis)
        endDateMillis = CalendarRepository.dayStart(existing.endDateMillis)
        startMinute = if (existing.startMinute in 0..1439) existing.startMinute else startMinute
        endMinute = if (existing.endMinute in 0..1439) existing.endMinute else endMinute
        selectedPriority = existing.priority
        selectedRecurrence = existing.recurrence
        recurrenceUntilMillis = existing.recurrenceUntilMillis
        existingCompleted = existing.completed
        existingSourceTag = existing.sourceTag
        currentDescription = existing.description
        etTitle.setText(existing.title)
        cbHasTime.isChecked = existing.hasTime
        
        // Update button text based on description
        updateDescriptionButtonText()
    }
    
    private fun updateDescriptionButtonText() {
        btnEditDescription.text = if (currentDescription.isBlank()) {
            getString(R.string.add_description)
        } else {
            getString(R.string.edit_description)
        }
    }
    
    private fun showDescriptionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_description, null)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescriptionDialog)
        val btnSave = dialogView.findViewById<Button>(R.id.btnDescriptionSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDescriptionCancel)
        
        etDescription.setText(currentDescription)
        etDescription.setSelection(currentDescription.length)
        
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnSave.setOnClickListener {
            currentDescription = etDescription.text.toString().trim()
            updateDescriptionButtonText()
            dialog.dismiss()
        }
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    private fun bindStateToViews() {
        val editingExisting = !todoId.isNullOrBlank()
        tvDialogTitle.setText(
            if (editingExisting) R.string.edit_item_title else R.string.add_item_title
        )
        btnDelete.visibility = if (editingExisting) View.VISIBLE else View.GONE

        btnStartDate.text = CalendarRepository.formatDateButton(startDateMillis)
        btnEndDate.text = CalendarRepository.formatDateButton(endDateMillis)

        val priorityIndex = priorityValues.indexOf(selectedPriority).coerceAtLeast(0)
        if (spPriority.selectedItemPosition != priorityIndex) {
            spPriority.setSelection(priorityIndex, false)
        }
        val recurrenceIndex = recurrenceValues.indexOf(selectedRecurrence).coerceAtLeast(0)
        if (spRecurrence.selectedItemPosition != recurrenceIndex) {
            spRecurrence.setSelection(recurrenceIndex, false)
        }

        if (selectedRecurrence == RECURRENCE_NONE) {
            btnRecurrenceUntil.visibility = GONE
        } else {
            btnRecurrenceUntil.visibility = VISIBLE
            btnRecurrenceUntil.text = if (recurrenceUntilMillis > 0L) {
                getString(
                    R.string.repeat_until_date,
                    CalendarRepository.formatDateButton(recurrenceUntilMillis)
                )
            } else {
                getString(R.string.repeat_until_none)
            }
        }

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
        if (
            selectedRecurrence != RECURRENCE_NONE &&
            recurrenceUntilMillis > 0L &&
            CalendarRepository.dayStart(recurrenceUntilMillis) < CalendarRepository.dayStart(startDateMillis)
        ) {
            Toast.makeText(this, R.string.invalid_recurrence_until, Toast.LENGTH_SHORT).show()
            return
        }
        
        val item = TodoItem(
            id = todoId ?: UUID.randomUUID().toString(),
            title = title,
            description = currentDescription,
            startDateMillis = startDateMillis,
            endDateMillis = endDateMillis,
            hasTime = cbHasTime.isChecked,
            startMinute = startMinute,
            endMinute = endMinute,
            completed = existingCompleted,
            sourceTag = existingSourceTag,
            priority = selectedPriority,
            recurrence = selectedRecurrence,
            recurrenceUntilMillis = if (selectedRecurrence == RECURRENCE_NONE) {
                0L
            } else {
                recurrenceUntilMillis
            }
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

    private fun bindSelectors() {
        val priorityLabels = listOf(
            getString(R.string.priority_normal),
            getString(R.string.priority_high),
            getString(R.string.priority_low)
        )
        spPriority.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            priorityLabels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPriority = priorityValues.getOrElse(position) { PRIORITY_NORMAL }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val recurrenceLabels = listOf(
            getString(R.string.repeat_none),
            getString(R.string.repeat_daily),
            getString(R.string.repeat_weekly),
            getString(R.string.repeat_monthly),
            getString(R.string.repeat_yearly)
        )
        spRecurrence.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            recurrenceLabels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spRecurrence.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRecurrence = recurrenceValues.getOrElse(position) { RECURRENCE_NONE }
                if (selectedRecurrence == RECURRENCE_NONE) {
                    recurrenceUntilMillis = 0L
                }
                bindStateToViews()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase))
    }
}
