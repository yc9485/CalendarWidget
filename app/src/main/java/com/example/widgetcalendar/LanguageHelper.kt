package com.example.widgetcalendar

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LanguageHelper {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_CHINESE = "zh"
    
    fun setLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
    }
    
    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
    }
    
    fun applyLanguage(context: Context): Context {
        val languageCode = getLanguage(context)
        
        if (languageCode == LANGUAGE_SYSTEM) {
            return context
        }
        
        val locale = when (languageCode) {
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            LANGUAGE_CHINESE -> Locale.CHINESE
            else -> return context
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    fun updateConfiguration(context: Context) {
        val languageCode = getLanguage(context)
        
        if (languageCode == LANGUAGE_SYSTEM) {
            return
        }
        
        val locale = when (languageCode) {
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            LANGUAGE_CHINESE -> Locale.CHINESE
            else -> return
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
