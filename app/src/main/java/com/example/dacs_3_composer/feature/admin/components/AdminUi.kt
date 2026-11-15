package com.example.dacs_3_composer.feature.admin.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

val AdminBlue = Color(0xFF2159BC)
val AdminInk = Color(0xFF172333)
val AdminMuted = Color(0xFF66758A)
val AdminBackground = Color(0xFFF5F8FC)
val AdminLocale: Locale = Locale.forLanguageTag("vi-VN")
val AdminShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", AdminLocale)
val AdminFullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", AdminLocale)
fun adminMoney(value: Double): String = NumberFormat.getNumberInstance(AdminLocale).apply { maximumFractionDigits = 0 }.format(value) + " đ"
fun adminCompactMoney(value: Double): String = when {
    value >= 1_000_000_000 -> String.format(AdminLocale, "%.1f tỷ", value / 1_000_000_000)
    value >= 1_000_000 -> String.format(AdminLocale, "%.1f tr", value / 1_000_000)
    value >= 1_000 -> String.format(AdminLocale, "%.0fk", value / 1_000)
    else -> value.toInt().toString()
}

@Composable
fun AdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = AdminBlue, onPrimary = Color.White,
        primaryContainer = Color(0xFFE8EFFB), onPrimaryContainer = AdminBlue,
        secondary = AdminBlue, onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDF3FF), onSecondaryContainer = AdminBlue,
        background = AdminBackground, onBackground = AdminInk,
        surface = Color.White, onSurface = AdminInk,
        surfaceVariant = Color(0xFFF0F4FA), onSurfaceVariant = AdminMuted,
        outline = Color(0xFFBBC8DA), outlineVariant = Color(0xFFDCE4EF)
    ), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}

@Composable
fun AdminPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun AdminMetric(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    AdminPanel(modifier) {
        Text(label, color = AdminMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = AdminInk)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = AdminMuted)
    }
}
