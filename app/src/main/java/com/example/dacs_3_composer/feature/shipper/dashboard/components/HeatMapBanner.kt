package com.example.dacs_3_composer.feature.shipper.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.Order

@Composable
fun HeatMapBanner(onMapTouchChange: (Boolean) -> Unit = {}) {
    val previewOrder = remember {
        Order().apply {
            id = "HEATING_MAP_PREVIEW"
            status = "ACCEPTED"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Bản đồ khu vực", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172333))
        Text("Vị trí của bạn khi đang chờ đơn", fontSize = 13.sp, color = Color(0xFF66758A))
        ShipperMapView(order = previewOrder, modifier = Modifier.fillMaxWidth().height(260.dp), onMapTouchChange = onMapTouchChange)
    }
}
