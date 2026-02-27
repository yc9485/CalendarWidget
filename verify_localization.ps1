# Localization Verification Script
Write-Host "=== Widget Calendar - Chinese Localization Verification ===" -ForegroundColor Cyan
Write-Host ""

# Check Chinese strings file
$zhStringsPath = "app/src/main/res/values-zh/strings.xml"
if (Test-Path $zhStringsPath) {
    Write-Host "[OK] Chinese strings file exists" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Chinese strings file missing" -ForegroundColor Red
    exit 1
}

# Count strings
$enCount = (Select-String -Path "app/src/main/res/values/strings.xml" -Pattern '<string name=').Count
$zhCount = (Select-String -Path $zhStringsPath -Pattern '<string name=').Count

Write-Host "[OK] English strings: $enCount" -ForegroundColor Green
Write-Host "[OK] Chinese strings: $zhCount" -ForegroundColor Green

if ($enCount -eq $zhCount) {
    Write-Host "[OK] String counts match!" -ForegroundColor Green
} else {
    Write-Host "[FAIL] String counts differ!" -ForegroundColor Red
    exit 1
}

# Check for hardcoded strings
Write-Host ""
Write-Host "Checking layouts..." -ForegroundColor Yellow
$hardcoded = Select-String -Path "app/src/main/res/layout/*.xml" -Pattern 'android:text="[A-Za-z]' -ErrorAction SilentlyContinue

if ($hardcoded) {
    Write-Host "[FAIL] Found hardcoded strings" -ForegroundColor Red
    exit 1
} else {
    Write-Host "[OK] No hardcoded strings in layouts" -ForegroundColor Green
}

# Check CalendarRepository
Write-Host ""
Write-Host "Checking CalendarRepository..." -ForegroundColor Yellow
$repo = Get-Content "app/src/main/java/com/example/widgetcalendar/CalendarRepository.kt" -Raw

if ($repo -match 'fun formatItemMeta\(context: Context') {
    Write-Host "[OK] formatItemMeta has Context parameter" -ForegroundColor Green
} else {
    Write-Host "[FAIL] formatItemMeta missing Context" -ForegroundColor Red
    exit 1
}

if ($repo -match 'fun priorityLabel\(context: Context') {
    Write-Host "[OK] priorityLabel has Context parameter" -ForegroundColor Green
} else {
    Write-Host "[FAIL] priorityLabel missing Context" -ForegroundColor Red
    exit 1
}

if ($repo -match 'fun recurrenceLabel\(context: Context') {
    Write-Host "[OK] recurrenceLabel has Context parameter" -ForegroundColor Green
} else {
    Write-Host "[FAIL] recurrenceLabel missing Context" -ForegroundColor Red
    exit 1
}

# Summary
Write-Host ""
Write-Host "=== All Checks Passed ===" -ForegroundColor Cyan
Write-Host "Chinese localization is ready!" -ForegroundColor Green
