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
cd F:\chenyang
.\build_and_install.ps1 -JavaHome "C:\Java\jdk-17" -AndroidHome "$env:LOCALAPPDATA\Android\Sdk"
```

Optional flags:

- `-SkipSdkInstall` if SDK packages are already installed.
- `-BuildOnly` to only create APK and skip `adb install`.

## Notes

- The widget supports previous/next month navigation.
- Multiple items can be stored under the same day.
- Each item can have an optional specific time period.
- Each item can span multiple days (start/end date range).
- Each item has a completion checkbox and completed items are shown with strikethrough in the day list.
- Data is stored locally using `SharedPreferences`.
