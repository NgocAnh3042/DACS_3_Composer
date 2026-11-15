package com.example.dacs_3_composer.feature.shipper.dashboard.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.feature.shipper.dashboard.components.ShipperMapView

@Composable
fun MapRouteSection(order: Order, enableTracking: Boolean, onMapTouchChange: (Boolean) -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Lộ trình giao hàng", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172333))
        Spacer(Modifier.height(12.dp))
        ShipperMapView(order = order, enableTracking = enableTracking, modifier = Modifier.fillMaxWidth().height(380.dp), onMapTouchChange = onMapTouchChange)
    }
}
