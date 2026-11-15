package com.example.dacs_3_composer.feature.admin.analytics

import com.example.dacs_3_composer.core.model.Order
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class AdminPeriod { WEEK, MONTH }

data class AdminRevenueDay(val date: LocalDate, val orders: List<Order>) {
    val revenue = orders.sumOf { it.totalPrice.coerceAtLeast(0.0) }
}

data class AdminRevenueSummary(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<AdminRevenueDay>,
    val previousDays: List<AdminRevenueDay>,
    val observedDays: Int,
    val comparisonDays: Int,
    val createdOrders: List<Order>,
    val undatedCompleted: Int
) {
    val total = days.sumOf { it.revenue }
    val completedCount = days.sumOf { it.orders.size }
    val averageOrder = if (completedCount == 0) 0.0 else total / completedCount
    val cancelledCount = createdOrders.count { it.status == "CANCELLED" }
    val cancellationRate = if (createdOrders.isEmpty()) 0.0 else cancelledCount * 100.0 / createdOrders.size
    val bestDay = days.filter { it.orders.isNotEmpty() }.maxByOrNull { it.revenue }
    val comparisonCurrent = days.take(comparisonDays).sumOf { it.revenue }
    val comparisonPrevious = previousDays.take(comparisonDays).sumOf { it.revenue }
    val growthPercent: Double? = comparisonPrevious.takeIf { it > 0.0 }?.let { (comparisonCurrent - it) / it * 100.0 }
    val currentCumulative = cumulative(days.take(observedDays))
    val previousCumulative = cumulative(previousDays.take(comparisonDays))
}

private fun cumulative(days: List<AdminRevenueDay>): List<Double> {
    var sum = 0.0
    return days.map { sum += it.revenue; sum }
}

internal fun adminOrderDate(value: String?): LocalDate? = value?.takeIf { it.length >= 10 }
    ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

fun adminRevenueSummary(orders: List<Order>, anchor: LocalDate, period: AdminPeriod, today: LocalDate = LocalDate.now()): AdminRevenueSummary {
    val start = if (period == AdminPeriod.WEEK) anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else anchor.withDayOfMonth(1)
    val end = if (period == AdminPeriod.WEEK) start.plusDays(6) else start.plusMonths(1).minusDays(1)
    val previousStart = if (period == AdminPeriod.WEEK) start.minusWeeks(1) else start.minusMonths(1)
    val completed = orders.filter { it.status == "COMPLETED" }
    val dated = completed.mapNotNull { order -> adminOrderDate(order.completedAt)?.let { it to order } }
    val byDate = dated.filter { !it.first.isAfter(today) }.groupBy({ it.first }, { it.second })
    fun daysBetween(from: LocalDate, until: LocalDate) = generateSequence(from) { it.plusDays(1) }
        .takeWhile { it.isBefore(until) }.map { AdminRevenueDay(it, byDate[it].orEmpty()) }.toList()
    val days = daysBetween(start, end.plusDays(1))
    val previous = daysBetween(previousStart, start)
    val observed = (ChronoUnit.DAYS.between(start, minOf(end, today)) + 1).toInt().coerceIn(0, days.size)
    val created = orders.filter { order -> adminOrderDate(order.time)?.let { !it.isBefore(start) && !it.isAfter(minOf(end, today)) } ?: false }
        .sortedByDescending { it.time }
    return AdminRevenueSummary(start, end, days, previous, observed, minOf(observed, previous.size), created, completed.size - dated.size)
}
