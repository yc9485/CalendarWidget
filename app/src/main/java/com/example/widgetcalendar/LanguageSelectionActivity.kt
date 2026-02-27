package com.example.widgetcalendar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LanguageSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_language_selection)

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupLanguage)
        val rbSystem = findViewById<RadioButton>(R.id.rbSystemDefault)
        val rbEnglish = findViewById<RadioButton>(R.id.rbEnglish)
        val rbChinese = findViewById<RadioButton>(R.id.rbChinese)

        // Set current selection
        val currentLanguage = LanguageHelper.getLanguage(this)
        when (currentLanguage) {
            LanguageHelper.LANGUAGE_SYSTEM -> rbSystem.isChecked = true
            LanguageHelper.LANGUAGE_ENGLISH -> rbEnglish.isChecked = true
            LanguageHelper.LANGUAGE_CHINESE -> rbChinese.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedLanguage = when (checkedId) {
                R.id.rbEnglish -> LanguageHelper.LANGUAGE_ENGLISH
                R.id.rbChinese -> LanguageHelper.LANGUAGE_CHINESE
                else -> LanguageHelper.LANGUAGE_SYSTEM
            }

            if (selectedLanguage != currentLanguage) {
                LanguageHelper.setLanguage(this, selectedLanguage)
                Toast.makeText(this, R.string.language_changed, Toast.LENGTH_SHORT).show()
                
                // Restart the app to apply language change
                restartApp()
            } else {
                finish()
            }
        }
    }

    private fun restartApp() {
        // Refresh all widgets
        CalendarWidgetProvider.refreshAllWidgets(this)
        
        // Restart main activity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase))
    }
}
