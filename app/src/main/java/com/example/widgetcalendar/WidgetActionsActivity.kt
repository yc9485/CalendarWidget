package com.example.widgetcalendar

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WidgetActionsActivity : AppCompatActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_widget_actions)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        val btnImport = findViewById<Button>(R.id.btnActionsImport)
        val btnExport = findViewById<Button>(R.id.btnActionsExport)
        val btnManage = findViewById<Button>(R.id.btnActionsManage)
        val btnClose = findViewById<Button>(R.id.btnActionsClose)

        btnImport.setOnClickListener {
            openImportExport(ImportExportActivity.MODE_IMPORT)
        }
        btnExport.setOnClickListener {
            openImportExport(ImportExportActivity.MODE_EXPORT)
        }
        btnManage.setOnClickListener {
            startActivity(Intent(this, ManageCalendarsActivity::class.java))
            finish()
        }
        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun openImportExport(mode: String) {
        startActivity(
            Intent(this, ImportExportActivity::class.java).apply {
                putExtra(ImportExportActivity.EXTRA_MODE, mode)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        )
        finish()
    }
}

