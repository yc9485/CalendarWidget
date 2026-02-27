# Widget Calendar (Android)

Home-screen monthly calendar widget where each date is clickable.  
Tap a date to manage reminders/todos and the brief status appears on each date cell in the widget.

## Build and run

1. Open this folder in Android Studio.
2. Let Gradle sync and download dependencies.
3. Run `app` on your Android phone (USB or wireless debugging).
4. On your phone home screen, long-press and add the **Widget Calendar** widget.
5. Tap any date to create/edit/delete a short reminder directly from the widget flow.

## Build without Android Studio

You can build/install from terminal with the script in repo root:

```powershell
.\build_and_install.ps1 -JavaHome "C:\Java\jdk-17" -AndroidHome "$env:LOCALAPPDATA\Android\Sdk"
```

Optional flags:

- `-SkipSdkInstall` if SDK packages are already installed.
- `-BuildOnly` to only create APK and skip `adb install`.

## Features

### Core Functionality
- Previous/next month navigation
- Multiple items per day
- Optional time periods for each item
- Multi-day task spanning (start/end date range)
- Task completion checkbox with strikethrough display
- Local data storage using `SharedPreferences`

### Task Management
- **Task Descriptions**: Add detailed descriptions to any task (up to 500 characters)
  - Short descriptions show preview in task list
  - Longer descriptions accessible via "View Description" button
  - Edit descriptions anytime via popup dialog
- **Priority Levels**: Set task priority (Low, Normal, High)
- **Recurrence**: Set recurring tasks (Daily, Weekly, Monthly, Yearly)

### User Experience
- **Completion Sound**: Cheerful upward SMS ringtone plays when marking tasks complete
  - Bright C6 → E6 → G6 melody
  - Toggle sound on/off in widget menu
  - Enabled by default
- **Standardized UI**: Consistent button sizes across all dialogs for better visual harmony

## Language Support

The app supports multiple languages with both automatic and manual selection:
- **English** (default)
- **Chinese** (Simplified & Traditional) - 中文支持

### Automatic Language Detection
The app automatically displays in your device's language.

### Manual Language Selection (New!)
You can also change the language directly in the app:
1. Tap the "..." button on the widget
2. Select "Language" / "语言"
3. Choose your preferred language:
   - System Default (follows device settings)
   - English
   - 中文 (Chinese)
4. App will restart automatically with the new language

For more details, see:
- [CHINESE_LOCALIZATION.md](CHINESE_LOCALIZATION.md) - Translation implementation
- [IN_APP_LANGUAGE_SWITCHER.md](IN_APP_LANGUAGE_SWITCHER.md) - Language switcher feature
- [TASK_DESCRIPTION_FEATURE.md](TASK_DESCRIPTION_FEATURE.md) - Task description feature
- [COMPLETION_SOUND_FEATURE.md](COMPLETION_SOUND_FEATURE.md) - Completion sound implementation

## Widget Menu

Access additional features by tapping the "..." button on the widget:
- **Import/Export**: Backup and restore your tasks via ICS calendar files
- **Manage Calendars**: Organize tasks into different calendars
- **Language**: Switch between System Default, English, or Chinese
- **Sound Effects**: Toggle completion sound on/off


