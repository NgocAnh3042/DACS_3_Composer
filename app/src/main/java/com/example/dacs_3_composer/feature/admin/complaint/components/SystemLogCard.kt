package com.example.dacs_3_composer.feature.admin.complaint.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SystemLogCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F6F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "NHẬT KÝ HỆ THỐNG", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

            LogLine(dotColor = Color(0xFF3B82F6), boldText = "Admin-02", normalText = " đã xác nhận đơn #ORD-88270")
            LogLine(dotColor = Color(0xFFE74C3C), boldText = "Hệ thống", normalText = " tự động hủy đơn #ORD-88102 (Quá hạn)")
            LogLine(dotColor = Color(0xFFF1C40F), boldText = "Sakura Sushi", normalText = " cập nhật trạng thái đơn #ORD-88285")
        }
    }
}

@Composable
private fun LogLine(dotColor: Color, boldText: String, normalText: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(top = 5.dp).size(6.dp).background(dotColor, CircleShape))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                fontSize = 12.sp,
                color = Color.DarkGray,
                text = buildAnnotatedString {

                }
            )
            // Viết đơn giản:
            Row {
                Text(text = boldText, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                Text(text = normalText, fontSize = 12.sp, color = Color.DarkGray)
            }
        }
    }
}