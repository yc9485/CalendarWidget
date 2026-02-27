# In-App Language Switcher

## Overview
The app now includes an in-app language switcher that allows users to change the language without going to system settings.

## Features

### Language Options
- **System Default** - Follows device language settings
- **English** - Forces English language
- **中文 (Chinese)** - Forces Chinese language

### Access
Users can access the language switcher from:
1. Tap the "..." button on the widget
2. Select "Language" / "语言" from the menu
3. Choose their preferred language
4. App automatically restarts to apply changes

## Implementation Details

### New Files Created

#### 1. LanguageHelper.kt
Utility class for managing language preferences:
- Stores user's language preference in SharedPreferences
- Applies language to Activity contexts
- Supports system default, English, and Chinese

#### 2. LanguageSelectionActivity.kt
Activity for language selection:
- Radio button interface for language selection
- Automatically restarts app when language changes
- Refreshes all widgets after language change

#### 3. activity_language_selection.xml
Layout for language selection dialog:
- Clean, simple radio button list
- Matches app's dialog theme
- Accessible with proper touch targets

### Modified Files

#### 1. String Resources
Added to both `values/strings.xml` and `values-zh/strings.xml`:
- `language_settings` - Menu button text
- `select_language` - Dialog title
- `language_english` - English option
- `language_chinese` - Chinese option
- `language_system_default` - System default option
- `language_changed` - Confirmation message

#### 2. WidgetActionsActivity.kt
- Added "Language" button to menu
- Opens LanguageSelectionActivity when clicked
- Applies language context override

#### 3. activity_widget_actions.xml
- Added language button to menu layout
- Positioned between "Manage Calendars" and "Close"

#### 4. All Activity Classes
Added `attachBaseContext()` override to apply language:
- MainActivity
- DateTodosActivity
- EditTodoItemActivity
- ImportExportActivity
- ManageCalendarsActivity
- WidgetActionsActivity
- LanguageSelectionActivity

#### 5. AndroidManifest.xml
- Registered LanguageSelectionActivity
- Applied dialog theme

## How It Works

### Language Persistence
```kotlin
// Language preference stored in SharedPreferences
private const val PREFS_NAME = "language_prefs"
private const val KEY_LANGUAGE = "selected_language"
```

### Language Application
```kotlin
override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LanguageHelper.applyLanguage(newBase))
}
```

This method is called before `onCreate()` and applies the language configuration to the Activity's context.

### Language Change Flow
1. User selects language in LanguageSelectionActivity
2. Selection saved to SharedPreferences
3. All widgets refreshed
4. App restarts with MainActivity
5. All activities apply language via attachBaseContext()

## User Experience

### Before Language Change
- User sees app in current language
- Taps "..." → "Language" / "语言"
- Sees current selection highlighted

### During Language Change
- User selects new language
- Toast message: "Language changed. Restarting app..." / "语言已更改。正在重启应用..."
- App restarts automatically

### After Language Change
- All UI text displays in selected language
- Widget updates with new language
- Date/time formatting follows selected locale
- Setting persists across app restarts

## Technical Notes

### Context Configuration
The language is applied using `createConfigurationContext()`:
```kotlin
val config = Configuration(context.resources.configuration)
config.setLocale(locale)
return context.createConfigurationContext(config)
```

### Widget Updates
When language changes, all widgets are refreshed:
```kotlin
CalendarWidgetProvider.refreshAllWidgets(this)
```

### App Restart
The app restarts cleanly to apply language changes:
```kotlin
val intent = Intent(this, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
}
startActivity(intent)
```

## Benefits

### User Convenience
- No need to navigate system settings
- Immediate language change
- Clear visual feedback

### Developer Benefits
- Centralized language management
- Easy to add more languages
- Consistent across all activities

### Testing Benefits
- Easy to test different languages
- No need to change device settings
- Quick switching for QA

## Future Enhancements

### Easy to Add More Languages
To add a new language (e.g., Spanish):

1. Add constant to LanguageHelper:
```kotlin
const val LANGUAGE_SPANISH = "es"
```

2. Add case to locale selection:
```kotlin
LANGUAGE_SPANISH -> Locale("es")
```

3. Create `values-es/strings.xml` with translations

4. Add radio button to layout:
```xml
<RadioButton
    android:id="@+id/rbSpanish"
    android:text="@string/language_spanish" />
```

5. Add string resources:
```xml
<string name="language_spanish">Español</string>
```

## Testing

### Test Cases
1. ✓ Select English - UI changes to English
2. ✓ Select Chinese - UI changes to Chinese
3. ✓ Select System Default - Follows device language
4. ✓ Language persists after app restart
5. ✓ Widget updates after language change
6. ✓ All activities display in selected language
7. ✓ Date/time formatting follows selected locale

### Verification
Run the verification script:
```powershell
./verify_localization.ps1
```

All checks should pass with the new language switcher functionality.
