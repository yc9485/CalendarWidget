package com.example.widgetcalendar

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ImportExportActivity : AppCompatActivity() {

    private var mode: String = MODE_EXPORT

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        runCatching {
            exportToUri(uri)
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
            CalendarWidgetProvider.refreshAllWidgets(this)
        }.onFailure {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        runCatching {
            val importedCount = importFromUri(uri)
            Toast.makeText(
                this,
                getString(R.string.import_success_count, importedCount),
                Toast.LENGTH_SHORT
            ).show()
            CalendarWidgetProvider.refreshAllWidgets(this)
        }.onFailure {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_EXPORT

        if (mode == MODE_IMPORT) {
            setContentView(R.layout.activity_import_options)
            bindImportOptions()
        } else {
            exportLauncher.launch(defaultExportFileName())
        }
    }

    private fun bindImportOptions() {
        val btnManual = findViewById<Button>(R.id.btnImportManual)
        val cbChina = findViewById<CheckBox>(R.id.cbImportChina)
        val cbSweden = findViewById<CheckBox>(R.id.cbImportSweden)
        val btnImportSelected = findViewById<Button>(R.id.btnImportSelectedHolidays)
        val btnCancel = findViewById<Button>(R.id.btnImportOptionsCancel)

        btnManual.setOnClickListener {
            importLauncher.launch(arrayOf("text/calendar", "text/plain", "*/*"))
        }
        btnImportSelected.setOnClickListener {
            val countries = buildList {
                if (cbChina.isChecked) add("CN")
                if (cbSweden.isChecked) add("SE")
            }
            if (countries.isEmpty()) {
                Toast.makeText(this, R.string.select_holiday_source, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            importPublicHolidays(countries)
        }
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun importPublicHolidays(countries: List<String>) {
        setImportButtonsEnabled(false)
        Toast.makeText(this, R.string.importing_holidays, Toast.LENGTH_SHORT).show()

        Thread {
            runCatching {
                val year = Calendar.getInstance().get(Calendar.YEAR)
                var total = 0
                countries.forEach { country ->
                    total += importCountryHolidays(year, country)
                }
                total
            }.onSuccess { count ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.holidays_imported_total, count),
                        Toast.LENGTH_SHORT
                    ).show()
                    CalendarWidgetProvider.refreshAllWidgets(this)
                    finish()
                }
            }.onFailure {
                runOnUiThread {
                    setImportButtonsEnabled(true)
                    Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun importCountryHolidays(year: Int, countryCode: String): Int {
        val endpoint = "https://date.nager.at/api/v3/PublicHolidays/$year/$countryCode"
        val payload = httpGet(endpoint)
        val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }

        val holidays = JSONArray(payload)
        var count = 0
        for (i in 0 until holidays.length()) {
            val obj = holidays.optJSONObject(i) ?: continue
            val dateText = obj.optString("date")
            if (dateText.isBlank()) continue
            val date = runCatching { dateParser.parse(dateText) }.getOrNull() ?: continue
            val localName = obj.optString("localName")
            val fallbackName = obj.optString("name")
            val title = CalendarRepository.normalizeTitle(
                if (localName.isNotBlank()) localName else fallbackName
            )
            if (title.isBlank()) continue

            val dayMillis = CalendarRepository.dayStart(date.time)
            CalendarRepository.upsertTodoItem(
                this,
                TodoItem(
                    id = "holiday_${countryCode.uppercase(Locale.US)}_$dateText",
                    title = title,
                    startDateMillis = dayMillis,
                    endDateMillis = dayMillis,
                    hasTime = false,
                    startMinute = -1,
                    endMinute = -1,
                    completed = false,
                    sourceTag = "holiday:${countryCode.uppercase(Locale.US)}"
                )
            )
            count += 1
        }
        return count
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            doInput = true
        }
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            error("HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return body
    }

    private fun setImportButtonsEnabled(enabled: Boolean) {
        if (mode != MODE_IMPORT) return
        findViewById<View>(R.id.btnImportManual).isEnabled = enabled
        findViewById<View>(R.id.cbImportChina).isEnabled = enabled
        findViewById<View>(R.id.cbImportSweden).isEnabled = enabled
        findViewById<View>(R.id.btnImportSelectedHolidays).isEnabled = enabled
        findViewById<View>(R.id.btnImportOptionsCancel).isEnabled = enabled
    }

    private fun exportToUri(uri: Uri) {
        val payload = IcsCalendarCodec.export(CalendarRepository.getAllTodoItems(this))
        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(payload)
        } ?: error("Unable to open output stream")
    }

    private fun importFromUri(uri: Uri): Int {
        val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to open input stream")
        val items = IcsCalendarCodec.import(content)
        return CalendarRepository.replaceAllTodoItems(this, items)
    }

    private fun defaultExportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "widget_calendar_$stamp.ics"
    }

    companion object {
        const val EXTRA_MODE = "extra_import_export_mode"
        const val MODE_IMPORT = "import"
        const val MODE_EXPORT = "export"
    }
}
