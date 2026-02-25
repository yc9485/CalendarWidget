package com.example.widgetcalendar

import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ManageCalendarsActivity : AppCompatActivity() {

    private lateinit var cbChina: CheckBox
    private lateinit var cbSweden: CheckBox
    private lateinit var tvSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manage_calendars)

        cbChina = findViewById(R.id.cbManageChina)
        cbSweden = findViewById(R.id.cbManageSweden)
        tvSummary = findViewById(R.id.tvManageSummary)

        val btnRemove = findViewById<Button>(R.id.btnManageRemoveSelected)
        val btnClose = findViewById<Button>(R.id.btnManageClose)

        btnRemove.setOnClickListener {
            removeSelected()
        }
        btnClose.setOnClickListener {
            finish()
        }

        refreshCounts()
    }

    private fun removeSelected() {
        val removeChina = cbChina.isChecked
        val removeSweden = cbSweden.isChecked
        if (!removeChina && !removeSweden) {
            Toast.makeText(this, R.string.select_calendar_to_remove, Toast.LENGTH_SHORT).show()
            return
        }

        var removed = 0
        if (removeChina) {
            removed += CalendarRepository.removeImportedCalendarItems(this, "CN")
        }
        if (removeSweden) {
            removed += CalendarRepository.removeImportedCalendarItems(this, "SE")
        }

        CalendarWidgetProvider.refreshAllWidgets(this)
        Toast.makeText(this, getString(R.string.removed_items_count, removed), Toast.LENGTH_SHORT).show()

        cbChina.isChecked = false
        cbSweden.isChecked = false
        refreshCounts()
    }

    private fun refreshCounts() {
        val chinaCount = CalendarRepository.countImportedCalendarItems(this, "CN")
        val swedenCount = CalendarRepository.countImportedCalendarItems(this, "SE")
        cbChina.text = getString(R.string.manage_china_calendar_count, chinaCount)
        cbSweden.text = getString(R.string.manage_sweden_calendar_count, swedenCount)
        tvSummary.text = getString(R.string.manage_summary_total, chinaCount + swedenCount)
    }
}

