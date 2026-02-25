package com.example.widgetcalendar

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DateTodosActivity : AppCompatActivity() {

    private var dateMillis: Long = -1L
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val items = mutableListOf<TodoItem>()
    private lateinit var adapter: TodosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_date_todos)

        dateMillis = CalendarRepository.dayStart(
            intent.getLongExtra(CalendarWidgetProvider.EXTRA_DATE_MILLIS, -1L)
        )
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (dateMillis <= 0L) {
            finish()
            return
        }

        val tvDate = findViewById<TextView>(R.id.tvSelectedDate)
        val listTodos = findViewById<ListView>(R.id.listTodos)
        val tvEmpty = findViewById<TextView>(R.id.tvEmptyTodos)
        val btnAdd = findViewById<Button>(R.id.btnAddTodo)
        val btnClose = findViewById<Button>(R.id.btnClose)

        tvDate.text = CalendarRepository.formatPrettyDate(dateMillis)
        listTodos.emptyView = tvEmpty

        adapter = TodosAdapter(
            items = items,
            onToggleDone = { item, checked ->
                CalendarRepository.setTodoCompleted(this, item.id, checked)
                reloadItems()
                notifyWidgetChanged()
            },
            onEdit = { item ->
                openEditor(item.id)
            }
        )
        listTodos.adapter = adapter

        btnAdd.setOnClickListener { openEditor(null) }
        btnClose.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        reloadItems()
    }

    private fun reloadItems() {
        items.clear()
        items += CalendarRepository.getTodoItemsForDate(this, dateMillis)
        adapter.notifyDataSetChanged()
    }

    private fun openEditor(itemId: String?) {
        startActivity(
            Intent(this, EditTodoItemActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(CalendarWidgetProvider.EXTRA_DATE_MILLIS, dateMillis)
                if (!itemId.isNullOrBlank()) {
                    putExtra(EditTodoItemActivity.EXTRA_TODO_ID, itemId)
                }
            }
        )
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
}

private class TodosAdapter(
    private val items: List<TodoItem>,
    private val onToggleDone: (TodoItem, Boolean) -> Unit,
    private val onEdit: (TodoItem) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): TodoItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo_row, parent, false)

        val cbDone = view.findViewById<CheckBox>(R.id.cbDone)
        val tvTitle = view.findViewById<TextView>(R.id.tvTodoTitle)
        val tvMeta = view.findViewById<TextView>(R.id.tvTodoMeta)
        val rowContent = view.findViewById<View>(R.id.todoRowContent)

        val item = getItem(position)

        tvTitle.text = item.title
        tvMeta.text = CalendarRepository.formatItemMeta(item)
        applyStrikeThrough(tvTitle, item.completed)
        applyStrikeThrough(tvMeta, item.completed)

        cbDone.setOnCheckedChangeListener(null)
        cbDone.isChecked = item.completed
        cbDone.setOnCheckedChangeListener { _, isChecked ->
            onToggleDone(item, isChecked)
        }

        rowContent.setOnClickListener {
            onEdit(item)
        }

        return view
    }

    private fun applyStrikeThrough(textView: TextView, completed: Boolean) {
        val baseFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        textView.paintFlags = if (completed) {
            baseFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            baseFlags
        }
    }
}
