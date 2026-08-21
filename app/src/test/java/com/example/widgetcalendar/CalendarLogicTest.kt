package com.example.widgetcalendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarLogicTest {

    @Test
    fun testCalendarDaysBetween() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 28, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(2026, Calendar.MARCH, 30, 0, 0, 0)
        val end = cal.timeInMillis

        // March 28 to March 30 spans DST spring forward on March 29 in Europe
        val days = CalendarRepository.calendarDaysBetween(start, end)
        assertEquals(2, days)

        val startAgain = CalendarRepository.addCalendarDays(end, -2)
        assertEquals(start, startAgain)
    }

    @Test
    fun testIcsRoundTripDoesNotShiftDates() {
        val icsFile = File("F:/widget_calendar_20260821_1818.ics")
        assertTrue("ICS file should exist", icsFile.exists())

        val content = icsFile.readText(Charsets.UTF_8)
        val items = IcsCalendarCodec.import(content)
        assertTrue("Should have parsed events", items.isNotEmpty())

        // Export and re-import
        val exportedIcs = IcsCalendarCodec.export(items)
        val reimportedItems = IcsCalendarCodec.import(exportedIcs)

        assertEquals("Item count should match", items.size, reimportedItems.size)

        val itemsMap = items.associateBy { it.id }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (reimported in reimportedItems) {
            val original = itemsMap[reimported.id] ?: error("Missing item: ${reimported.id}")
            val origStart = fmt.format(original.startDateMillis)
            val origEnd = fmt.format(original.endDateMillis)
            val reimpStart = fmt.format(reimported.startDateMillis)
            val reimpEnd = fmt.format(reimported.endDateMillis)

            assertEquals(
                "Event '${original.title}' start date shifted after round trip",
                origStart,
                reimpStart
            )
            assertEquals(
                "Event '${original.title}' end date shifted after round trip",
                origEnd,
                reimpEnd
            )
        }
    }

    @Test
    fun testExtendingMultiDayEventDoesNotShiftOtherEvents() {
        val cal = Calendar.getInstance()

        fun makeDate(year: Int, month: Int, day: Int): Long {
            return cal.apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val eventA = TodoItem(
            id = "event-a",
            title = "Conference",
            startDateMillis = makeDate(2026, Calendar.AUGUST, 10),
            endDateMillis = makeDate(2026, Calendar.AUGUST, 12),
            hasTime = false,
            startMinute = -1,
            endMinute = -1,
            completed = false
        )

        val eventB = TodoItem(
            id = "event-b",
            title = "Dentist",
            startDateMillis = makeDate(2026, Calendar.AUGUST, 13),
            endDateMillis = makeDate(2026, Calendar.AUGUST, 13),
            hasTime = false,
            startMinute = -1,
            endMinute = -1,
            completed = false
        )

        val eventC = TodoItem(
            id = "event-c",
            title = "Workshop",
            startDateMillis = makeDate(2026, Calendar.AUGUST, 14),
            endDateMillis = makeDate(2026, Calendar.AUGUST, 16),
            hasTime = false,
            startMinute = -1,
            endMinute = -1,
            completed = false
        )

        val allItemsBefore = listOf(eventA, eventB, eventC)
        val rangeStart = makeDate(2026, Calendar.AUGUST, 1)
        val rangeEnd = makeDate(2026, Calendar.AUGUST, 31)

        val expandedBefore = CalendarRepository.expandItemsForRange(allItemsBefore, rangeStart, rangeEnd)
        assertEquals(3, expandedBefore.size)

        // Extend Event A by 1 day (Aug 10..Aug 13)
        val eventAExtended = eventA.copy(endDateMillis = makeDate(2026, Calendar.AUGUST, 13))
        val allItemsAfter = listOf(eventAExtended, eventB, eventC)

        val expandedAfter = CalendarRepository.expandItemsForRange(allItemsAfter, rangeStart, rangeEnd)
        assertEquals(3, expandedAfter.size)

        val bAfter = expandedAfter.first { it.id == "event-b" }
        val cAfter = expandedAfter.first { it.id == "event-c" }

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        assertEquals("Dentist should still be on Aug 13", "2026-08-13", fmt.format(bAfter.startDateMillis))
        assertEquals("Dentist should still end on Aug 13", "2026-08-13", fmt.format(bAfter.endDateMillis))

        assertEquals("Workshop should still be on Aug 14", "2026-08-14", fmt.format(cAfter.startDateMillis))
        assertEquals("Workshop should still end on Aug 16", "2026-08-16", fmt.format(cAfter.endDateMillis))
    }
}
