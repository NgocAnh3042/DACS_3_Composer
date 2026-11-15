package com.example.dacs_3_composer.feature.admin.analytics

import com.example.dacs_3_composer.core.model.Order
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AdminRevenueSummaryTest {
    private val today = LocalDate.of(2026, 11, 12)
    private fun order(id: String, completed: String?, total: Double = 100000.0, status: String = "COMPLETED", created: String = "2026-11-01 08:00:00") =
        Order(id = id, completedAt = completed, time = created, totalPrice = total, shippingFee = 20000.0, status = status)

    @Test fun revenueUsesCompletedDateAndOrderTotalInsteadOfShippingFee() {
        val data = listOf(order("a", "2026-11-12 10:00:00"), order("b", "2026-11-12 11:00:00", 220000.0),
            order("c", "2026-11-12 12:00:00", status = "CANCELLED"), order("d", null))
        val result = adminRevenueSummary(data, today, AdminPeriod.WEEK, today)
        assertEquals(320000.0, result.total, 0.0)
        assertEquals(2, result.completedCount)
        assertEquals(160000.0, result.averageOrder, 0.0)
        assertEquals(1, result.undatedCompleted)
        assertEquals(LocalDate.of(2026, 11, 9), result.start)
        assertEquals(7, result.days.size)
    }

    @Test fun partialMonthComparesOnlyEquivalentElapsedDaysAndNeverPlotsFutureRevenue() {
        val data = listOf(order("a", "2026-11-12 10:00:00", 200000.0), order("future", "2026-11-20 10:00:00", 900000.0),
            order("prev", "2026-10-12 10:00:00", 100000.0), order("later", "2026-10-20 10:00:00", 800000.0))
        val result = adminRevenueSummary(data, today, AdminPeriod.MONTH, today)
        assertEquals(12, result.observedDays)
        assertEquals(12, result.comparisonDays)
        assertEquals(200000.0, result.total, 0.0)
        assertEquals(100000.0, result.comparisonPrevious, 0.0)
        assertEquals(100.0, result.growthPercent!!, 0.0)
        assertEquals(12, result.currentCumulative.size)
        assertEquals(12, result.previousCumulative.size)
        assertEquals(200000.0, result.currentCumulative.last(), 0.0)
    }

    @Test fun differentMonthLengthsUseEqualComparisonWindows() {
        val data = listOf(order("early", "2026-03-28 10:00:00", 100000.0), order("extra", "2026-03-31 10:00:00", 200000.0), order("previous", "2026-02-28 10:00:00", 50000.0))
        val result = adminRevenueSummary(data, LocalDate.of(2026, 3, 1), AdminPeriod.MONTH, LocalDate.of(2026, 4, 2))
        assertEquals(31, result.days.size)
        assertEquals(28, result.comparisonDays)
        assertEquals(300000.0, result.total, 0.0)
        assertEquals(100000.0, result.comparisonCurrent, 0.0)
        assertEquals(50000.0, result.comparisonPrevious, 0.0)
        assertEquals(31, result.currentCumulative.size)
        assertEquals(28, result.previousCumulative.size)
    }

    @Test fun cancellationRateIsScopedToOrdersCreatedInThePeriod() {
        val data = listOf(order("old", "2026-11-12 10:00:00"), order("new", null, status = "PENDING", created = "2026-11-10 10:00:00"),
            order("cancelled", null, status = "CANCELLED", created = "2026-11-11 10:00:00"))
        val result = adminRevenueSummary(data, today, AdminPeriod.WEEK, today)
        assertEquals(2, result.createdOrders.size)
        assertEquals(50.0, result.cancellationRate, 0.0)
        assertEquals(1, result.completedCount)
    }

    @Test fun noPreviousRevenueDoesNotInventGrowth() {
        val result = adminRevenueSummary(listOf(order("a", "2026-11-12 10:00:00")), today, AdminPeriod.WEEK, today)
        assertNull(result.growthPercent)
        val empty = adminRevenueSummary(emptyList(), today, AdminPeriod.MONTH, today)
        assertEquals(0.0, empty.averageOrder, 0.0)
        assertEquals(0.0, empty.cancellationRate, 0.0)
        assertEquals(30, empty.days.size)
        assertNull(empty.bestDay)
    }

    @Test fun leapMonthAndWeekAcrossNewYearUseCalendarDates() {
        val leap = adminRevenueSummary(emptyList(), LocalDate.of(2024, 2, 12), AdminPeriod.MONTH, LocalDate.of(2024, 3, 1))
        assertEquals(29, leap.days.size)
        val week = adminRevenueSummary(listOf(order("a", "2026-01-01 10:00:00")), LocalDate.of(2026, 1, 1), AdminPeriod.WEEK, LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2025, 12, 29), week.start)
        assertEquals(4, week.currentCumulative.size)
        assertEquals(100000.0, week.total, 0.0)
    }
}
