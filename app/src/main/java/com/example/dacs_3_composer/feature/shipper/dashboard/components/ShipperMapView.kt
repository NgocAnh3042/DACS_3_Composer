package com.example.dacs_3_composer.feature.shipper.dashboard.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.feature.shipper.dashboard.ShipperViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.concurrent.TimeUnit

private val MapBlue = Color(0xFF2159BC)
private val MapInk = Color(0xFF172333)
private const val SHIPPER_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SHIPPER_MAP_LOADING_STYLE = "asset://shipper_map_loading_style.json"

private fun Map<String, Double>?.toLatLng(): LatLng? {
    val coordinates = this ?: return null
    val lat = coordinates["lat"] ?: return null
    val lng = coordinates["lng"] ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return LatLng(lat, lng)
}

private fun Order.destination(): LatLng? {
    val lat = if (status == "ACCEPTED") restaurantLat else customerLat
    val lng = if (status == "ACCEPTED") restaurantLng else customerLng
    if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return LatLng(lat, lng)
}

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun ShipperMapView(
    order: Order,
    modifier: Modifier = Modifier.fillMaxWidth().height(320.dp),
    enableTracking: Boolean = true,
    shipperViewModel: ShipperViewModel = viewModel(),
    onMapTouchChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val latestMapTouchChange by rememberUpdatedState(onMapTouchChange)
    val permission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val isPreview = order.id == "HEATING_MAP_PREVIEW"
    val isGoingToRestaurant = order.status == "ACCEPTED"
    val destination = order.destination()
    val destinationLabel = if (isGoingToRestaurant) order.restaurantName.ifBlank { "Nhà hàng" }
        else order.customerAddress.ifBlank { "Khách hàng" }
    val deviceLocation by shipperViewModel.currentShipperLocation.collectAsState()
    val routePoints by shipperViewModel.routePoints.collectAsState()
    var liveLocation by remember(order.id) { mutableStateOf<LatLng?>(null) }
    val current = deviceLocation.toLatLng() ?: liveLocation
    var followDriver by remember(order.id) { mutableStateOf(true) }
    var frameMapTouch by remember(order.id) { mutableStateOf(false) }
    var nativeMapTouch by remember(order.id) { mutableStateOf(false) }
    var recenterRequest by remember(order.id) { mutableIntStateOf(0) }
    var fitRouteRequest by remember(order.id) { mutableIntStateOf(0) }
    var lastRouteStart by remember(order.id, order.status) { mutableStateOf<LatLng?>(null) }

    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (enableTracking) shipperViewModel.startLocationUpdates(context, order.id)
            else shipperViewModel.fetchCurrentLocationOnce(context)
        }
    }

    fun requestGps() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000).build()
        val settings = LocationSettingsRequest.Builder().addLocationRequest(request).build()
        LocationServices.getSettingsClient(context).checkLocationSettings(settings)
            .addOnSuccessListener {
                if (enableTracking) shipperViewModel.startLocationUpdates(context, order.id)
                else shipperViewModel.fetchCurrentLocationOnce(context)
            }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) {
                    runCatching {
                        gpsLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(error.resolution.intentSender).build())
                    }
                }
            }
    }

    LaunchedEffect(permission.status.isGranted, order.id, enableTracking) {
        if (permission.status.isGranted) {
            shipperViewModel.fetchCurrentLocationOnce(context)
            requestGps()
        } else {
            permission.launchPermissionRequest()
        }
    }

    DisposableEffect(order.id, enableTracking) {
        if (!enableTracking || isPreview) {
            onDispose {
                if (isPreview) shipperViewModel.stopLocationUpdates()
                shipperViewModel.clearRoute()
            }
        } else {
            val ref = Firebase.database.getReference("tracking").child(order.id)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = (snapshot.child("lat").value as? Number)?.toDouble() ?: return
                    val lng = (snapshot.child("lng").value as? Number)?.toDouble() ?: return
                    if (lat in -90.0..90.0 && lng in -180.0..180.0) liveLocation = LatLng(lat, lng)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            ref.addValueEventListener(listener)
            onDispose {
                ref.removeEventListener(listener)
                shipperViewModel.stopLocationUpdates()
                shipperViewModel.clearRoute()
            }
        }
    }

    LaunchedEffect(current, destination, order.status) {
        if (isPreview || current == null || destination == null) return@LaunchedEffect
        val previous = lastRouteStart
        val moved = previous == null || kotlin.math.abs(previous.latitude - current.latitude) > 0.00035 ||
            kotlin.math.abs(previous.longitude - current.longitude) > 0.00035
        if (moved || routePoints.isEmpty()) {
            lastRouteStart = current
            shipperViewModel.fetchOSRMRoute(current.latitude, current.longitude, destination.latitude, destination.longitude)
        }
    }

    when {
        !permission.status.isGranted -> MapMessage(modifier, "Cần quyền truy cập vị trí", "Cho phép vị trí để xem bản đồ giao hàng.") {
            permission.launchPermissionRequest()
        }
        current == null -> MapMessage(modifier, "Đang xác định vị trí của bạn", "Kiểm tra GPS trên điện thoại rồi thử lại.") {
            shipperViewModel.fetchCurrentLocationOnce(context)
            requestGps()
        }
        else -> {
            val route = if (isPreview) emptyList() else routePoints.map { LatLng(it.latitude, it.longitude) }

            Box(modifier = modifier.pointerInput(order.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    followDriver = false
                    frameMapTouch = true
                    latestMapTouchChange(true)
                    try {
                        do { val event = awaitPointerEvent(PointerEventPass.Initial) }
                        while (event.changes.any { it.pressed })
                    } finally { frameMapTouch = false; latestMapTouchChange(nativeMapTouch) }
                }
            }.clip(RoundedCornerShape(20.dp)).background(Color(0xFFE8F0FA))) {
                NativeShipperMap(
                    modifier = Modifier.fillMaxSize(),
                    current = current,
                    destination = if (isPreview) null else destination,
                    destinationLabel = destinationLabel,
                    route = route,
                    followDriver = followDriver,
                    recenterRequest = recenterRequest,
                    fitRouteRequest = fitRouteRequest,
                    onGesture = { followDriver = false },
                    onMapTouchChange = { touched ->
                        nativeMapTouch = touched
                        latestMapTouchChange(touched || frameMapTouch)
                    }
                )

                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 3.dp
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MapBlue, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (isPreview) "Vị trí hiện tại" else if (isGoingToRestaurant) "Đang đến quán" else "Đang đến khách",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MapInk)
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapAction(Icons.Default.MyLocation, "Định vị lại") {
                        followDriver = true
                        recenterRequest++
                    }
                    if (!isPreview && destination != null) {
                        MapAction(Icons.Default.ZoomOutMap, "Xem toàn tuyến") {
                            followDriver = false
                            fitRouteRequest++
                        }
                    }
                }

                Text(
                    text = "© OpenMapTiles · © OpenStreetMap contributors",
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = if (isPreview) 12.dp else 82.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.94f))
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openfreemap.org/"))) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MapInk
                )

                if (!isPreview) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                        shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 4.dp
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(if (isGoingToRestaurant) "Điểm lấy hàng" else "Điểm giao hàng",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xFF66758A))
                            Text(destinationLabel, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = MapInk, maxLines = 1)
                            if (destination == null) Text("Chưa có tọa độ điểm đến", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66758A))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeShipperMap(
    current: LatLng,
    destination: LatLng?,
    destinationLabel: String,
    route: List<LatLng>,
    followDriver: Boolean,
    recenterRequest: Int,
    fitRouteRequest: Int,
    onGesture: () -> Unit,
    onMapTouchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnGesture by rememberUpdatedState(onGesture)
    val latestOnTouchChange by rememberUpdatedState(onMapTouchChange)
    var mapGeneration by remember { mutableIntStateOf(0) }
    val mapView = remember(context, mapGeneration) { ShipperGestureMapView(context).apply { onCreate(null) } }
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember(mapView) { mutableStateOf(false) }
    var driverMarker by remember(mapView) { mutableStateOf<Marker?>(null) }
    var usingLoadingStyle by remember(mapView) { mutableStateOf(true) }
    var mapError by remember(mapView) { mutableStateOf<String?>(null) }
    var probing by remember(mapView) { mutableStateOf(true) }
    var retryRequest by remember(mapView) { mutableIntStateOf(0) }
    DisposableEffect(mapView) {
        mapView.onTouchChange = { touched ->
            if (touched) { latestOnGesture(); map?.cancelTransitions() }
            latestOnTouchChange(touched)
        }
        onDispose { mapView.onTouchChange = {}; latestOnTouchChange(false) }
    }
    val driverIcon = remember(mapView) {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(24f, 24f, 21f, paint)
        paint.color = android.graphics.Color.rgb(33, 89, 188)
        canvas.drawCircle(24f, 24f, 16f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(24f, 24f, 5f, paint)
        IconFactory.getInstance(context).fromBitmap(bitmap)
    }

    DisposableEffect(mapView, lifecycleOwner) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart(); started = true
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume(); resumed = true
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    DisposableEffect(mapView) {
        var active = true
        mapView.addOnDidFailLoadingMapListener { error ->
            mapView.post {
                if (active && !usingLoadingStyle && error.contains("Unable to resolve host", ignoreCase = true)) {
                    mapError = "Không kết nối được máy chủ bản đồ. Kiểm tra Internet hoặc DNS trên điện thoại."
                    usingLoadingStyle = true
                    styleReady = false
                    map?.setStyle(SHIPPER_MAP_LOADING_STYLE) { if (active) styleReady = true }
                }
            }
        }
        mapView.getMapAsync { readyMap ->
            if (active) {
                readyMap.setPrefetchesTiles(false)
                readyMap.uiSettings.setCompassEnabled(false)
                readyMap.uiSettings.isRotateGesturesEnabled = false
                readyMap.uiSettings.isTiltGesturesEnabled = false
                readyMap.uiSettings.setAttributionEnabled(true)
                val bottomMargin = (context.resources.displayMetrics.density * 82).toInt()
                readyMap.uiSettings.setAttributionMargins(8, 8, 8, bottomMargin)
                readyMap.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) latestOnGesture()
                }
                readyMap.setStyle(SHIPPER_MAP_LOADING_STYLE) {
                    if (active) { styleReady = true; map = readyMap }
                }
            }
        }
        onDispose { active = false; map = null; styleReady = false }
    }

    LaunchedEffect(mapView) {
        delay(10_000)
        if (map == null && mapError == null) {
            mapError = "Không khởi tạo được bản đồ trên thiết bị này."
        }
    }

    LaunchedEffect(map, retryRequest) {
        val readyMap = map ?: return@LaunchedEffect
        mapError = null
        probing = true
        val reachable = withContext(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient.Builder().callTimeout(7, TimeUnit.SECONDS).build()
                client.newCall(
                    Request.Builder().url(SHIPPER_MAP_STYLE_URL)
                        .header("User-Agent", "FoodHub-Android/1.0 (com.example.dacs_3_composer)")
                        .build()
                )
                    .execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
        probing = false
        if (reachable) {
            usingLoadingStyle = false
            styleReady = false
            readyMap.setStyle(SHIPPER_MAP_STYLE_URL) {
                if (!usingLoadingStyle) { styleReady = true; mapError = null }
            }
        } else {
            mapError = "Không kết nối được máy chủ bản đồ. Kiểm tra Internet hoặc DNS trên điện thoại."
        }
    }

    LaunchedEffect(map, usingLoadingStyle, probing, styleReady) {
        if (map != null && !usingLoadingStyle && !probing && !styleReady) {
            delay(10_000)
            if (!styleReady && !usingLoadingStyle) {
                mapError = "Bản đồ tải quá lâu. Kiểm tra Internet hoặc DNS trên điện thoại."
                usingLoadingStyle = true
                map?.setStyle(SHIPPER_MAP_LOADING_STYLE) { styleReady = true }
            }
        }
    }

    LaunchedEffect(map, styleReady, destination, destinationLabel, route) {
        val readyMap = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        readyMap.removeAnnotations()
        if (route.size > 1) {
            readyMap.addPolyline(PolylineOptions().addAll(route).color(android.graphics.Color.WHITE).width(19f))
            readyMap.addPolyline(PolylineOptions().addAll(route).color(android.graphics.Color.rgb(33, 89, 188)).width(11f))
        }
        driverMarker = readyMap.addMarker(MarkerOptions().position(current).title("Vị trí của bạn").icon(driverIcon))
        if (destination != null) readyMap.addMarker(MarkerOptions().position(destination).title(destinationLabel))
    }

    LaunchedEffect(map, current, driverMarker) {
        val readyMap = map ?: return@LaunchedEffect
        val marker = driverMarker ?: return@LaunchedEffect
        marker.position = current
        readyMap.updateMarker(marker)
    }

    LaunchedEffect(map, styleReady, current, followDriver, recenterRequest) {
        val readyMap = map ?: return@LaunchedEffect
        if (styleReady && followDriver) {
            readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 16.0), 600)
        }
    }

    LaunchedEffect(map, styleReady, fitRouteRequest) {
        val readyMap = map ?: return@LaunchedEffect
        if (!styleReady || fitRouteRequest == 0 || destination == null) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().include(current).include(destination).apply {
            route.forEach { include(it) }
        }.build()
        val padding = (context.resources.displayMetrics.density * 64).toInt()
        runCatching { readyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 600) }
            .onFailure { Toast.makeText(context, "Chưa thể hiển thị toàn tuyến", Toast.LENGTH_SHORT).show() }
    }

    Box(modifier = modifier.background(Color(0xFFE8F0FA))) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (mapError != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 4.dp
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Chưa tải được bản đồ", color = MapInk, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(mapError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color(0xFF66758A))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { if (map == null) mapGeneration++ else retryRequest++ }) { Text("Thử lại") }
                }
            }
        } else if (probing || !styleReady) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MapBlue)
        }
    }
}

/** Give the native map exclusive ownership until the finger leaves the gesture. */
private class ShipperGestureMapView(context: Context) : MapView(context) {
    var onTouchChange: (Boolean) -> Unit = {}
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouchChange(true)
        if (event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
            onTouchChange(false)
        }
        return handled
    }
}

@Composable
private fun MapAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color.White, shadowElevation = 4.dp) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = label, tint = MapBlue)
        }
    }
}

@Composable
private fun MapMessage(modifier: Modifier, title: String, detail: String, onRetry: (() -> Unit)?) {
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFE8F0FA)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
            Icon(Icons.Default.MyLocation, contentDescription = null, tint = MapBlue, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = MapInk, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF66758A))
            if (onRetry != null) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRetry) { Text("Thử lại") }
            }
        }
    }
}
