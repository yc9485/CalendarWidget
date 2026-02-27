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
        val btnLanguage = findViewById<Button>(R.id.btnActionsLanguage)
        val btnSound = findViewById<Button>(R.id.btnActionsSound)
        val btnClose = findViewById<Button>(R.id.btnActionsClose)

        // Update sound button text based on current state
        updateSoundButtonText(btnSound)

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
        btnLanguage.setOnClickListener {
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
            finish()
        }
        btnSound.setOnClickListener {
            toggleSound(btnSound)
        }
        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun updateSoundButtonText(button: Button) {
        val isEnabled = SoundManager.isSoundEnabled(this)
        button.text = if (isEnabled) {
            getString(R.string.sound_effects_enabled)
        } else {
            getString(R.string.sound_effects_disabled)
        }
    }

    private fun toggleSound(button: Button) {
        val currentState = SoundManager.isSoundEnabled(this)
        SoundManager.setSoundEnabled(this, !currentState)
        updateSoundButtonText(button)
        
        // Play sound if just enabled
        if (!currentState) {
            SoundManager.playCompletionSound(this)
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

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase))
    }
}
