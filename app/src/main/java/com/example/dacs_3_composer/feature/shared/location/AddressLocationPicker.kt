package com.example.dacs_3_composer.feature.shared.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private val pickerBlue = Color(0xFF2159BC)
private const val mapStyle = "https://tiles.openfreemap.org/styles/liberty"
private const val loadingStyle = "asset://shipper_map_loading_style.json"

@SuppressLint("MissingPermission")
@Composable
fun AddressLocationPicker(
    selected: AddressPlace?,
    onSelected: (AddressPlace) -> Unit,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 190.dp,
    onSearchPendingChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnSelected by rememberUpdatedState(onSelected)
    val latestOnPendingChange by rememberUpdatedState(onSearchPendingChange)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf(selected?.address.orEmpty()) }
    var searchRequested by remember { mutableStateOf(false) }
    var retrySearch by remember { mutableIntStateOf(0) }
    var showMap by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var reverseJob by remember { mutableStateOf<Job?>(null) }
    var gpsRequest by remember { mutableStateOf<CancellationTokenSource?>(null) }
    var cameraRequest by remember { mutableIntStateOf(0) }
    var addressError by remember { mutableStateOf<String?>(null) }

    fun cancelLocationLookup() {
        gpsRequest?.cancel()
        gpsRequest = null
        locating = false
        reverseJob?.cancel()
        resolving = false
    }

    fun chooseCoordinates(latitude: Double, longitude: Double, recenter: Boolean = false) {
        reverseJob?.cancel()
        addressError = null
        resolving = true
        latestOnPendingChange(true)
        val point = AddressPlace("Vị trí đã chọn (${"%.5f".format(latitude)}, ${"%.5f".format(longitude)})", latitude, longitude)
        latestOnSelected(point)
        if (recenter) { showMap = true; cameraRequest++ }
        reverseJob = scope.launch {
            // Keep network work out of camera movement; a new gesture cancels this lookup.
            if (!recenter) delay(450)
            val place = try {
                DeviceAddressLookup.reverse(context, latitude, longitude)
                    ?: AddressSearch.reverse(latitude, longitude)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            latestOnSelected(place ?: point)
            if (place == null) addressError = "Chưa lấy được tên địa chỉ. Hãy thử lại hoặc tìm địa chỉ bằng tên đường."
            resolving = false
            latestOnPendingChange(false)
        }
    }

    fun useGps() {
        cancelLocationLookup()
        locating = true
        latestOnPendingChange(true)
        val request = CancellationTokenSource()
        gpsRequest = request
        fun fail(message: String) {
            if (gpsRequest !== request) return
            locating = false
            gpsRequest = null
            latestOnPendingChange(false)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val options = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(15_000).setDurationMillis(12_000).build()
            client.getCurrentLocation(options, request.token)
                .addOnSuccessListener { location ->
                    if (gpsRequest !== request) return@addOnSuccessListener
                    if (location != null) {
                        locating = false
                        gpsRequest = null
                        chooseCoordinates(location.latitude, location.longitude, recenter = true)
                    } else {
                        client.lastLocation.addOnSuccessListener cachedResult@ { cached ->
                            if (gpsRequest !== request) return@cachedResult
                            if (cached == null || SystemClock.elapsedRealtimeNanos() - cached.elapsedRealtimeNanos > 120_000_000_000L) {
                                fail("Chưa lấy được GPS mới. Hãy bật định vị và thử lại hoặc tìm địa chỉ.")
                                return@cachedResult
                            }
                            locating = false
                            gpsRequest = null
                            chooseCoordinates(cached.latitude, cached.longitude, recenter = true)
                        }.addOnFailureListener { fail("Không đọc được vị trí trên máy.") }
                    }
                }.addOnFailureListener { fail("Không đọc được vị trí trên máy.") }
        }.onFailure { fail("Hãy bật định vị trên điện thoại.") }
    }

    DisposableEffect(Unit) {
        onDispose { gpsRequest?.cancel(); gpsRequest = null; reverseJob?.cancel() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) useGps()
        else Toast.makeText(context, "Bạn vẫn có thể tìm và chọn địa chỉ trên bản đồ.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(selected) {
        if (!searchRequested) query = selected?.address.orEmpty()
    }

    LaunchedEffect(query, searchRequested, retrySearch) {
        suggestions = emptyList()
        searchError = null
        if (!searchRequested) { searching = false; return@LaunchedEffect }
        if (query.trim().length < 3) { searching = false; return@LaunchedEffect }
        searching = true
        delay(400)
        try {
            suggestions = AddressSearch.suggestions(query, selected)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            searchError = "Không tìm được địa chỉ lúc này. Hãy thử lại."
        }
        searching = false
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                cancelLocationLookup()
                addressError = null
                query = it
                suggestions = emptyList()
                searchRequested = it.isNotBlank()
                latestOnPendingChange(searchRequested)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tìm số nhà, tên đường hoặc địa điểm") },
            placeholder = { Text("Ví dụ: 93 Nguyễn Đình Chiểu, Đà Nẵng") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = pickerBlue) }
        )
        Text("Chọn địa chỉ trong gợi ý để tự điền vị trí. Thêm quận hoặc thành phố để tìm chính xác hơn.",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF66758A))
        if (searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = pickerBlue)
        searchError?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { retrySearch++ }) { Text("Thử lại") }
            }
        }
        suggestions.forEach { suggestion ->
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                    // A previous map/GPS lookup must not overwrite the chosen search result.
                    cancelLocationLookup()
                    addressError = null
                    searchRequested = false
                    latestOnPendingChange(false)
                    query = suggestion.place.address
                    suggestions = emptyList()
                    latestOnSelected(suggestion.place)
                    cameraRequest++
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
                color = Color(0xFFF1F5FC), shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(suggestion.title, style = MaterialTheme.typography.bodyMedium, color = pickerBlue)
                    if (suggestion.area.isNotBlank()) Text(suggestion.area, style = MaterialTheme.typography.bodySmall)
                    if (query.trim().firstOrNull()?.isDigit() == true && suggestion.houseNumber.isBlank()) {
                        Text("Chưa có dữ liệu số nhà cho kết quả này", style = MaterialTheme.typography.labelSmall, color = Color(0xFF795500))
                    }
                }
            }
        }
        if (searchRequested && !searching && query.trim().length >= 3 && suggestions.isEmpty() && searchError == null) {
            Text("Chưa tìm thấy địa chỉ. Hãy thêm phường/quận, thành phố hoặc thử tên đường. Dữ liệu có thể chưa có số nhà này.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = {
                searchRequested = false
                latestOnPendingChange(false)
                query = ""
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) useGps()
                else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            modifier = Modifier.fillMaxWidth(), enabled = !locating
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (locating) "Đang lấy vị trí GPS…" else "Dùng vị trí hiện tại")
        }

        TextButton(onClick = { showMap = !showMap }) {
            Text(if (showMap) "Ẩn bản đồ" else "Xem hoặc chỉnh vị trí trên bản đồ (tùy chọn)")
        }
        if (showMap) AddressPickerMap(
            selected = selected,
            resolving = resolving,
            cameraRequest = cameraRequest,
            onInteractionStarted = {
                cancelLocationLookup()
                searchRequested = false
                addressError = null
                latestOnPendingChange(true)
            },
            onPicked = { lat, lon ->
                searchRequested = false
                latestOnPendingChange(false)
                chooseCoordinates(lat, lon)
            },
            modifier = Modifier.fillMaxWidth().height(mapHeight)
        )
        if (resolving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = pickerBlue)
        addressError?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { selected?.let { chooseCoordinates(it.latitude, it.longitude) } }) { Text("Thử lại") }
            }
        }
        Text(
            selected?.let { (if (it.isApproximate) "Địa chỉ gần ghim: " else "Địa chỉ đã chọn: ") + it.address } ?: "Chưa chọn địa chỉ.",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected == null) Color(0xFF66758A) else pickerBlue
        )
        if (selected?.hasHouseNumber == false && !resolving) {
            Text("Dữ liệu tại đây chưa có số nhà. Hãy bổ sung số nhà/cổng ở ô ghi chú bên dưới.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF795500))
        }
        Text("Dữ liệu bản đồ và địa chỉ © OpenStreetMap contributors · OpenMapTiles · Photon",
            style = MaterialTheme.typography.labelSmall, color = Color(0xFF66758A))
    }
}

@Composable
private fun AddressPickerMap(
    selected: AddressPlace?,
    resolving: Boolean,
    cameraRequest: Int,
    onInteractionStarted: () -> Unit,
    onPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPicked by rememberUpdatedState(onPicked)
    val latestOnInteractionStarted by rememberUpdatedState(onInteractionStarted)
    val latestSelected by rememberUpdatedState(selected)
    val mapView = remember(context) {
        AddressMapView(context).apply { onCreate(null) }
    }
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var mapError by remember(mapView) { mutableStateOf(false) }
    var mapReady by remember(mapView) { mutableStateOf(false) }
    var mapMoving by remember(mapView) { mutableStateOf(false) }
    var userMoved by remember(mapView) { mutableStateOf(false) }
    var retryMap by remember(mapView) { mutableIntStateOf(0) }

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
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !started) { mapView.onStart(); started = true }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !resumed) { mapView.onResume(); resumed = true }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    DisposableEffect(mapView) {
        var active = true
        mapView.addOnDidFailLoadingMapListener {
            mapView.post {
                if (active) {
                    mapError = true
                    // Keep already rendered tiles when an individual request fails.
                }
            }
        }
        mapView.getMapAsync { readyMap ->
            if (active) {
                readyMap.setPrefetchesTiles(false)
                readyMap.uiSettings.isRotateGesturesEnabled = false
                readyMap.uiSettings.isTiltGesturesEnabled = false
                readyMap.uiSettings.setAttributionMargins(8, 8, 8, 8)
                readyMap.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        userMoved = true
                        mapMoving = true
                        latestOnInteractionStarted()
                    }
                }
                readyMap.addOnCameraIdleListener {
                    if (userMoved) {
                        userMoved = false
                        mapMoving = false
                        readyMap.cameraPosition.target?.let { latestOnPicked(it.latitude, it.longitude) }
                    }
                }
                readyMap.addOnMapClickListener { point ->
                    userMoved = false
                    mapMoving = false
                    readyMap.animateCamera(CameraUpdateFactory.newLatLng(point), 200)
                    latestOnPicked(point.latitude, point.longitude)
                    true
                }
                val initialPoint = selected?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(16.0471, 108.2068)
                readyMap.cameraPosition = CameraPosition.Builder().target(initialPoint)
                    .zoom(if (selected == null) 5.0 else 16.0).build()
                map = readyMap
                readyMap.setStyle(loadingStyle)
            }
        }
        onDispose { active = false; map = null }
    }

    LaunchedEffect(map, retryMap) {
        val readyMap = map ?: return@LaunchedEffect
        mapError = false
        mapReady = false
        readyMap.setStyle(mapStyle) { if (map === readyMap) { mapReady = true; mapError = false } }
    }

    // Only a GPS/search selection can recenter. Panning and reverse-geocoding never reset zoom.
    LaunchedEffect(map, cameraRequest) {
        val readyMap = map ?: return@LaunchedEffect
        val point = latestSelected ?: return@LaunchedEffect
        if (cameraRequest > 0) {
            userMoved = false
            mapMoving = false
            val current = readyMap.cameraPosition
            val target = current.target
            if (target == null || kotlin.math.abs(target.latitude - point.latitude) > 0.00001 ||
                kotlin.math.abs(target.longitude - point.longitude) > 0.00001 || current.zoom < 15.0) {
                readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0), 300)
            }
        }
    }

    LaunchedEffect(map, mapReady, retryMap) {
        if (map != null && !mapReady) {
            delay(10_000)
            if (!mapReady) mapError = true
        }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F0FA))) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Icon(Icons.Default.LocationOn, contentDescription = "Ghim vị trí đã chọn",
            modifier = Modifier.align(Alignment.Center).offset(y = (-18).dp).size(36.dp), tint = pickerBlue)
        Surface(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
            shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.95f)) {
            Text(when {
                mapMoving -> "Thả tay để xem địa chỉ tại ghim"
                resolving -> "Đang tìm tên đường, số nhà…"
                else -> selected?.address ?: "Kéo bản đồ để chọn vị trí"
            }, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (mapError) {
            Surface(modifier = Modifier.align(Alignment.BottomCenter), color = Color.White) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Không tải được bản đồ. Có thể chọn GPS hoặc địa chỉ tìm kiếm.",
                        modifier = Modifier.weight(1f).padding(6.dp), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = {
                        retryMap++
                    }) { Text("Thử lại") }
                }
            }
        } else if (!mapReady) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp).size(22.dp), color = pickerBlue)
        }
    }
}

/** Capture the gesture before a rendering child receives it, including multi-touch. */
private class AddressMapView(context: Context) : MapView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }
}
