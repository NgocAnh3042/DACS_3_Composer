package com.example.dacs_3_composer.feature.shipper.dashboard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.OrderStatus
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.dacs_3_composer.feature.shipper.earnings.completionDate
import java.time.LocalDate
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class DriverStatus(val isAvailable: Boolean = false)

class ShipperViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val realtimeDb = Firebase.database.getReference("tracking")

    private val _isReadyToWork = MutableStateFlow(false)
    val isReadyToWork: StateFlow<Boolean> = _isReadyToWork.asStateFlow()

    private val _availableOrders = MutableStateFlow<List<Order>>(emptyList())
    val availableOrders: StateFlow<List<Order>> = _availableOrders.asStateFlow()

    private val _activeDeliveryOrder = MutableStateFlow<Order?>(null)
    val activeDeliveryOrder: StateFlow<Order?> = _activeDeliveryOrder.asStateFlow()

    private val _historyOrders = MutableStateFlow<List<Order>>(emptyList())
    val historyOrders: StateFlow<List<Order>> = _historyOrders.asStateFlow()
    private val _historyLoading = MutableStateFlow(true)
    val historyLoading = _historyLoading.asStateFlow()
    private val _historyError = MutableStateFlow<String?>(null)
    val historyError = _historyError.asStateFlow()
    private val historyMutex = Mutex()
    private var historyLoaded = false

    private val _currentShipperLocation = MutableStateFlow<Map<String, Double>?>(null)
    val currentShipperLocation: StateFlow<Map<String, Double>?> = _currentShipperLocation.asStateFlow()

    private val _routePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val routePoints: StateFlow<List<GeoPoint>> = _routePoints.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var currentTrackingOrderId: String? = null

    val todayIncomeStr: StateFlow<String> = _historyOrders.map { list ->
        try {
            val today = LocalDate.now()
            val totalToday = list.filter { order ->
                order.status == OrderStatus.COMPLETED.name && completionDate(order) == today
            }.sumOf { order -> order.shippingFee }
            "${NumberFormat.getNumberInstance(Locale("vi", "VN")).format(totalToday.toLong())}đ"
        } catch (e: Exception) { "0đ" }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "0đ"
    )

    val completedOrdersCountStr: StateFlow<String> = _historyOrders.map { list ->
        list.count { it.status == OrderStatus.COMPLETED.name }.toString()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "0"
    )

    private var availableOrdersJob: Job? = null
    private var activeOrderJob: Job? = null
    private var historyOrdersJob: Job? = null

    var isLoading by mutableStateOf(false)
        private set

    val currentShipperId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        observeShipperStatus()
        // 🌟 FIX 1: Tự động kích hoạt lắng nghe Đơn hàng đang giao và Lịch sử ngay khi khởi tạo ViewModel
        startAllListeners()
    }

    fun startAllListeners() {
        val uid = currentShipperId
        if (uid.isNotBlank()) {
            listenToActiveDelivery()
            listenToHistoryOrders()
        }
    }

    fun fetchOSRMRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val serverUrls = listOf(
                "https://router.project-osrm.org/route/v1/driving/${startLng},${startLat};${endLng},${endLat}?overview=full&geometries=polyline",
                "https://routing.openstreetmap.de/routed-car/route/v1/driving/${startLng},${startLat};${endLng},${endLat}?overview=full&geometries=polyline"
            )

            Log.d("OSRM", "=== fetchOSRMRoute called ===")
            var success = false

            for ((index, url) in serverUrls.withIndex()) {
                if (success) break
                try {
                    val request = Request.Builder().url(url).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val responseData = response.body?.string() ?: return@use
                        val jsonObject = JSONObject(responseData)
                        if (jsonObject.optString("code", "") != "Ok") return@use

                        val routes = jsonObject.getJSONArray("routes")
                        if (routes.length() == 0) return@use

                        val geometry = routes.getJSONObject(0).getString("geometry")
                        val decodedPoints = decodePolylineToGeoPoints(geometry)

                        if (decodedPoints.isNotEmpty()) {
                            _routePoints.value = decodedPoints
                            success = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OSRM", "Server ${index + 1} exception: ${e.message}")
                }
            }
        }
    }

    private fun decodePolylineToGeoPoints(encoded: String): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result ushr 1).inv() else result ushr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result ushr 1).inv() else result ushr 1
            lng += dlng

            val pLat = lat.toDouble() / 1E5
            val pLng = lng.toDouble() / 1E5
            poly.add(GeoPoint(pLat, pLng))
        }
        return poly
    }

    fun clearRoute() {
        _routePoints.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocationOnce(context: Context) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        _currentShipperLocation.value = mapOf(
                            "lat" to location.latitude,
                            "lng" to location.longitude
                        )
                    }
                }
                .addOnFailureListener { e -> e.printStackTrace() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun observeShipperStatus() {
        val uid = currentShipperId
        if (uid.isBlank()) return
        viewModelScope.launch {
            while (true) {
                runCatching { FoodHubClient.get<DriverStatus>("drivers", "me") }
                    .onSuccess { _isReadyToWork.value=it.isAvailable; if(it.isAvailable)startListeningAvailableOrders() else stopListeningAvailableOrders() }
                    .onFailure { Log.e("ShipperVM", "Không tải được trạng thái MySQL: ${it.message}") }
                delay(5_000)
            }
        }
    }

    fun toggleWorkStatus(isReady: Boolean) {
        val uid = currentShipperId.ifBlank { return }
        viewModelScope.launch {
            try {
                FoodHubClient.update("drivers", "me", mapOf("isAvailable" to isReady))
                _isReadyToWork.value=isReady
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startListeningAvailableOrders() {
        if (availableOrdersJob?.isActive == true) return

        // 🌟 LƯU Ý: Sàn đơn tự do lấy các đơn có trạng thái ACCEPTED nhưng CHƯA có ai giao (shipperId rỗng)
        availableOrdersJob=viewModelScope.launch { while(true){runCatching{FoodHubClient.list<Order>("orders/available")}.onSuccess{_availableOrders.value=it}.onFailure{Log.e("ShipperVM","Lỗi tải đơn trống: ${it.message}")};delay(4_000)} }
    }

    private fun stopListeningAvailableOrders() {
        availableOrdersJob?.cancel()
        availableOrdersJob = null
        _availableOrders.value = emptyList()
    }

    private fun listenToActiveDelivery() {
        val uid = currentShipperId.ifBlank { return }
        if (activeOrderJob?.isActive == true) return
        activeOrderJob=viewModelScope.launch { while(true){runCatching{FoodHubClient.list<Order>("orders")}.onSuccess{_activeDeliveryOrder.value=it.firstOrNull{o->o.status in setOf("ACCEPTED","SHIPPING")}}.onFailure{Log.e("ShipperVM","Lỗi chuyến đang giao: ${it.message}")};delay(4_000)} }
    }

    private fun listenToHistoryOrders() {
        val uid = currentShipperId.ifBlank { return }
        if (historyOrdersJob?.isActive == true) return

        // 🌟 FIX 3: Loại bỏ điều kiện .whereEqualTo("status") ở tầng Query Firestore
        // để có thể kéo về cả đơn hoàn thành (COMPLETED) lẫn đơn bị huỷ (CANCELLED) theo logic UI của bạn.
        historyOrdersJob=viewModelScope.launch { while(true){ loadHistory(); delay(10_000) } }
    }

    fun refreshEarnings() { viewModelScope.launch { loadHistory(showLoading = true) } }

    private suspend fun loadHistory(showLoading: Boolean = false) = historyMutex.withLock {
        _historyLoading.value = showLoading || !historyLoaded
        try {
            _historyOrders.value = FoodHubClient.list<Order>("orders").filter {
                it.shipperId == currentShipperId && it.status in setOf("COMPLETED", "CANCELLED")
            }
            _historyError.value = null
            historyLoaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { _historyError.value = "Không tải được thu nhập. Hãy kiểm tra kết nối và thử lại."; Log.e("ShipperVM", "Lỗi tải lịch sử", e) }
        finally { _historyLoading.value = false }
    }

    fun acceptOrder(orderId: String) {
        val uid = currentShipperId.ifBlank { return }
        viewModelScope.launch {
            try {
                isLoading = true
                val response=FoodHubClient.api.create("deliveries/$orderId/accept",JsonObject())
                if(!response.isSuccessful)error(response.errorBody()?.string() ?: "Không nhận được đơn")
                isLoading = false
            } catch (e: Exception) { isLoading = false;Log.e("ShipperVM","Nhận đơn thất bại: ${e.message}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context, orderId: String) {
        if (currentTrackingOrderId == orderId) return
        stopLocationUpdates()

        currentTrackingOrderId = orderId
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
            .setMinUpdateIntervalMillis(2000)
            .setMinUpdateDistanceMeters(2f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLocation = locationResult.lastLocation ?: return
                val coordinates = mapOf(
                    "lat" to lastLocation.latitude,
                    "lng" to lastLocation.longitude
                )
                _currentShipperLocation.value = coordinates

                if (currentTrackingOrderId == orderId && orderId != "HEATING_MAP_PREVIEW") {
                    try {
                        realtimeDb.child(orderId).setValue(coordinates)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopLocationUpdates() {
        try {
            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient?.removeLocationUpdates(locationCallback!!)
            }
        } catch (e: Exception) { e.printStackTrace() }
        currentTrackingOrderId = null
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
        stopListeningAvailableOrders()
        activeOrderJob?.cancel()
        historyOrdersJob?.cancel()
    }
}
