package com.example.widgetcalendar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase))
    }
}
