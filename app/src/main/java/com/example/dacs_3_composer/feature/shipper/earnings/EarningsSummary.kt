package com.example.dacs_3_composer.feature.shipper.earnings

import com.example.dacs_3_composer.core.model.Order
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

enum class EarningsPeriod { WEEK, MONTH }

data class EarningsDay(val date: LocalDate, val orders: List<Order>) {
    val total: Double = orders.sumOf { it.shippingFee.coerceAtLeast(0.0) }
}

data class EarningsSummary(val start: LocalDate, val end: LocalDate, val days: List<EarningsDay>, val previousTotal: Double, val undatedCount: Int) {
    val total = days.sumOf { it.total }
    val orderCount = days.sumOf { it.orders.size }
    val averagePerOrder = if (orderCount == 0) 0.0 else total / orderCount
    val bestDay = days.filter { it.orders.isNotEmpty() }.maxByOrNull { it.total }
}

fun completionDate(order: Order): LocalDate? = order.completedAt?.takeIf { it.length >= 10 }
    ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

fun earningsSummary(orders: List<Order>, shipperId: String, anchor: LocalDate, period: EarningsPeriod): EarningsSummary {
    val start = if (period == EarningsPeriod.WEEK) anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else anchor.withDayOfMonth(1)
    val end = if (period == EarningsPeriod.WEEK) start.plusDays(6) else start.plusMonths(1).minusDays(1)
    val previousStart = if (period == EarningsPeriod.WEEK) start.minusWeeks(1) else start.minusMonths(1)
    val completed = orders.filter { shipperId.isNotBlank() && it.shipperId == shipperId && it.status == "COMPLETED" }
    val dated = completed.mapNotNull { order -> completionDate(order)?.let { it to order } }
    val byDay = dated.groupBy({ it.first }, { it.second })
    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
        .map { EarningsDay(it, byDay[it].orEmpty().sortedByDescending { order -> order.completedAt }) }.toList()
    val previousTotal = dated.filter { !it.first.isBefore(previousStart) && it.first.isBefore(start) }
        .sumOf { it.second.shippingFee.coerceAtLeast(0.0) }
    return EarningsSummary(start, end, days, previousTotal, completed.size - dated.size)
}
