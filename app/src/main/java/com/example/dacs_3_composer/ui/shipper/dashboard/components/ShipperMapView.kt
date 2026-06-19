package com.example.dacs_3_composer.ui.shipper.dashboard.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.data.model.Order
import com.example.dacs_3_composer.ui.shipper.dashboard.ShipperViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Helper function: Calculates Euclidean distance between two GeoPoints
 */
private fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
    return Math.sqrt(Math.pow(p1.latitude - p2.latitude, 2.0) + Math.pow(p1.longitude - p2.longitude, 2.0))
}

/**
 * ✅ Logic Snap-to-Road:
 * Finds the closest point on the line segment (roadPolyline) to the raw GPS point (rawPoint).
 * This function calculates the perpendicular projection to the line segment.
 */
private fun findClosestPointOnRoute(rawPoint: GeoPoint, routePoints: List<GeoPoint>): GeoPoint {
    if (routePoints.isEmpty()) return rawPoint
    if (routePoints.size == 1) return routePoints[0]

    var minDistance = Double.MAX_VALUE
    var closestPoint = routePoints[0]

    // Iterate through each road segment formed by two points [i] and [i+1]
    for (i in 0 until routePoints.size - 1) {
        val p1 = routePoints[i]
        val p2 = routePoints[i + 1]

        val latPoint = rawPoint.latitude
        val lngPoint = rawPoint.longitude
        val lat1 = p1.latitude
        val lng1 = p1.longitude
        val lat2 = p2.latitude
        val lng2 = p2.longitude

        val dx = lat2 - lat1
        val dy = lng2 - lng1

        if (dx == 0.0 && dy == 0.0) continue // Two duplicate points on the road

        // Formula for perpendicular projection point onto segment p1-p2
        val u = ((latPoint - lat1) * dx + (lngPoint - lng1) * dy) / (dx * dx + dy * dy)

        val projectedPoint = when {
            u < 0.0 -> p1         // Projected before segment
            u > 1.0 -> p2         // Projected after segment
            else -> GeoPoint(lat1 + u * dx, lng1 + u * dy) // Perpendicular projected point on segment
        }

        val distance = distanceBetween(rawPoint, projectedPoint)
        if (distance < minDistance) {
            minDistance = distance
            closestPoint = projectedPoint
        }
    }
    return closestPoint
}

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission", "ClickableViewAccessibility")
@Composable
fun ShipperMapView(
    order: Order,
    modifier: Modifier = Modifier.fillMaxWidth().height(320.dp),
    shipperViewModel: ShipperViewModel = viewModel()
) {
    val context = LocalContext.current
    val locationPermissionState = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

    val gpsSettingLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (order.id.isNotBlank() && order.id != "HEATING_MAP_PREVIEW") {
                shipperViewModel.startLocationUpdates(context, order.id)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
        Configuration.getInstance().userAgentValue = context.packageName
    }

    fun checkAndRequestGPS() {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(context)
        client.checkLocationSettings(builder.build()).addOnSuccessListener {
            if (order.id.isNotBlank() && order.id != "HEATING_MAP_PREVIEW") {
                shipperViewModel.startLocationUpdates(context, order.id)
            }
        }.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    gpsSettingLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(exception.resolution.intentSender).build())
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
    LaunchedEffect(locationPermissionState.status.isGranted, order.id) {
        if (locationPermissionState.status.isGranted) {
            shipperViewModel.fetchCurrentLocationOnce(context)
            checkAndRequestGPS()
        }
    }

    if (locationPermissionState.status.isGranted) {
        val deviceLocation by shipperViewModel.currentShipperLocation.collectAsState()
        val realRoutePoints by shipperViewModel.routePoints.collectAsState()
        var firebaseRawShipperPoint by remember { mutableStateOf<GeoPoint?>(null) }

        val trackingRef = remember(order.id) {
            Firebase.database.getReference("tracking").child(order.id)
        }

        DisposableEffect(order.id) {
            onDispose { shipperViewModel.stopLocationUpdates() }
        }

        DisposableEffect(order.id) {
            var listener: ValueEventListener? = null
            if (order.id.isNotBlank() && order.id != "HEATING_MAP_PREVIEW") {
                listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val lat = (snapshot.child("lat").value as? Number)?.toDouble() ?: return
                            val lng = (snapshot.child("lng").value as? Number)?.toDouble() ?: return
                            firebaseRawShipperPoint = GeoPoint(lat, lng) // raw GPS location from Firebase
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                trackingRef.addValueEventListener(listener)
            }
            onDispose {
                listener?.let { trackingRef.removeEventListener(it) }
                shipperViewModel.clearRoute()
            }
        }

        LaunchedEffect(firebaseRawShipperPoint, deviceLocation, order.id, order.status) {
            val isGoingToRestaurant = order.status == "ACCEPTED"
            val destLat = if (isGoingToRestaurant) order.restaurantLat else order.customerLat
            val destLng = if (isGoingToRestaurant) order.restaurantLng else order.customerLng
            if (destLat == null || destLng == null) return@LaunchedEffect

            // Get Raw location to calculate route, no snap needed here
            val startLat = firebaseRawShipperPoint?.latitude ?: deviceLocation?.get("lat")
            val startLng = firebaseRawShipperPoint?.longitude ?: deviceLocation?.get("lng")
            if (startLat == null || startLng == null) return@LaunchedEffect
            shipperViewModel.fetchOSRMRoute(startLat, startLng, destLat, destLng)
        }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val actualCtx = ctx.findActivity() ?: ctx
                MapView(actualCtx).apply {
                    try {
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        controller.setZoom(17.5) // Higher zoom to see marker stick to road clearly

                        setOnTouchListener { view, event ->
                            if (event.action == MotionEvent.ACTION_DOWN) view.parent.requestDisallowInterceptTouchEvent(true)
                            false
                        }

                        overlays.add(Marker(this).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); id = "destination" })
                        overlays.add(Marker(this).apply {
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "Vị trí của bạn"
                            id = "shipper"
                            icon = actualCtx.resources.getDrawable(android.R.drawable.ic_menu_mylocation, null).apply {
                                setTint(android.graphics.Color.parseColor("#2563EB"))
                            }
                        })
                        overlays.add(Polyline(this).apply { id = "route"; color = android.graphics.Color.parseColor("#FF5722"); width = 12f })
                        onResume()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            },
            update = { mapView ->
                try {
                    val destinationMarker = mapView.overlays.firstOrNull { (it as? Marker)?.id == "destination" } as? Marker
                    val shipperMarker = mapView.overlays.firstOrNull { (it as? Marker)?.id == "shipper" } as? Marker
                    val routePolyline = mapView.overlays.firstOrNull { (it as? Polyline)?.id == "route" } as? Polyline
                    val isGoingToRestaurant = order.status == "ACCEPTED"
                    val destLat = if (isGoingToRestaurant) order.restaurantLat else order.customerLat
                    val destLng = if (isGoingToRestaurant) order.restaurantLng else order.customerLng
                    val destinationPoint = GeoPoint(destLat ?: 15.9733, destLng ?: 108.2517)

                    destinationMarker?.apply {
                        position = destinationPoint
                        title = if (isGoingToRestaurant) "Nhà hàng: ${order.restaurantName}" else "Khách hàng"
                    }


                    val rawShipperPoint = if (order.id != "HEATING_MAP_PREVIEW" && order.id.isNotBlank()) {
                        firebaseRawShipperPoint ?: GeoPoint(deviceLocation?.get("lat") ?: 15.9733, deviceLocation?.get("lng") ?: 108.2517)
                    } else {
                        GeoPoint(deviceLocation?.get("lat") ?: 15.9733, deviceLocation?.get("lng") ?: 108.2517)
                    }

                    val shipperPointOnRoad = if (realRoutePoints.size >= 2) {
                        android.util.Log.d("SNAP", "Applying snap-to-road, route size: ${realRoutePoints.size}")
                        findClosestPointOnRoute(rawShipperPoint, realRoutePoints)
                    } else {
                        android.util.Log.d("SNAP", "No route, raw GPS used")
                        rawShipperPoint
                    }

                    shipperMarker?.position = shipperPointOnRoad

                    if (realRoutePoints.isNotEmpty()) {
                        routePolyline?.setPoints(realRoutePoints)
                    } else {
                        routePolyline?.setPoints(listOf(rawShipperPoint, destinationPoint))
                    }

                    if (order.id != "HEATING_MAP_PREVIEW") {
                        mapView.controller.animateTo(shipperPointOnRoad) // Center camera on road-snapped point
                    } else {
                        mapView.controller.setCenter(rawShipperPoint)
                    }

                    mapView.invalidate()
                } catch (e: Exception) { e.printStackTrace() }
            },
            onRelease = { mapView -> try { mapView.onPause(); mapView.onDetach() } catch (e: Exception) { e.printStackTrace() } }
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Ứng dụng cần quyền truy cập vị trí.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { locationPermissionState.launchPermissionRequest() }) { Text(text = "Cấp quyền ngay") }
            }
        }
    }
}