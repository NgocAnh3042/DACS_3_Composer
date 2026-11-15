package com.example.dacs_3_composer.feature.shipper.earnings

import com.example.dacs_3_composer.core.model.Order
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EarningsSummaryTest {
    private fun order(date: String?, fee: Double = 25000.0, status: String = "COMPLETED", driver: String = "driver-1") =
        Order(id = "$date-$fee", time = "2026-10-01 09:00:00", completedAt = date, shippingFee = fee, status = status, shipperId = driver)

    @Test fun countsByCompletionDateAndOnlyForTheSignedInDriver() {
        val orders = listOf(order("2026-11-12 10:00:00"), order("2026-11-12 11:00:00", 35000.0),
            order("2026-11-12 12:00:00", status = "CANCELLED"), order("2026-11-12 12:00:00", driver = "other"))
        val result = earningsSummary(orders, "driver-1", LocalDate.of(2026, 11, 12), EarningsPeriod.WEEK)
        assertEquals(LocalDate.of(2026, 11, 9), result.start)
        assertEquals(7, result.days.size)
        assertEquals(60000.0, result.total, 0.0)
        assertEquals(2, result.orderCount)
        assertEquals(30000.0, result.averagePerOrder, 0.0)
        assertEquals(60000.0, result.bestDay!!.total, 0.0)
        assertEquals(6, result.days.count { it.total == 0.0 })
    }

    @Test fun weeklyComparisonCrossesYearBoundaryCorrectly() {
        val result = earningsSummary(listOf(order("2025-12-27 10:00:00", 10000.0), order("2026-01-01 10:00:00", 20000.0)),
            "driver-1", LocalDate.of(2026, 1, 1), EarningsPeriod.WEEK)
        assertEquals(LocalDate.of(2025, 12, 29), result.start)
        assertEquals(20000.0, result.total, 0.0)
        assertEquals(10000.0, result.previousTotal, 0.0)
    }

    @Test fun leapMonthAndPreviousMonthHaveTheCorrectLengths() {
        val result = earningsSummary(listOf(order("2024-02-29 20:00:00"), order("2024-01-31 20:00:00", 12000.0)),
            "driver-1", LocalDate.of(2024, 2, 1), EarningsPeriod.MONTH)
        assertEquals(29, result.days.size)
        assertEquals(25000.0, result.total, 0.0)
        assertEquals(12000.0, result.previousTotal, 0.0)
    }

    @Test fun missingOrInvalidCompletionDatesAreNotCountedAsOrderPlacementDates() {
        val result = earningsSummary(listOf(order(null), order("invalid"), order("2026-02-30 10:00:00")),
            "driver-1", LocalDate.of(2026, 10, 1), EarningsPeriod.MONTH)
        assertEquals(3, result.undatedCount)
        assertEquals(0.0, result.total, 0.0)
        assertEquals(0.0, result.averagePerOrder, 0.0)
        assertNull(result.bestDay)
    }

    @Test fun anonymousUserAndEmptyPeriodHaveNoEarnings() {
        val result = earningsSummary(listOf(order("2026-11-12 10:00:00")), "", LocalDate.of(2026, 11, 12), EarningsPeriod.MONTH)
        assertEquals(0, result.orderCount)
        assertEquals(30, result.days.size)
    }
}
