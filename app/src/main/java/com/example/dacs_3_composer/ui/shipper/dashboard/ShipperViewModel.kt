package com.example.dacs_3_composer.ui.shipper.dashboard

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
import com.example.dacs_3_composer.data.model.Order
import com.example.dacs_3_composer.data.model.OrderStatus
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ShipperViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = Firebase.database.getReference("tracking")

    private val _isReadyToWork = MutableStateFlow(false)
    val isReadyToWork: StateFlow<Boolean> = _isReadyToWork.asStateFlow()

    private val _availableOrders = MutableStateFlow<List<Order>>(emptyList())
    val availableOrders: StateFlow<List<Order>> = _availableOrders.asStateFlow()

    private val _activeDeliveryOrder = MutableStateFlow<Order?>(null)
    val activeDeliveryOrder: StateFlow<Order?> = _activeDeliveryOrder.asStateFlow()

    private val _historyOrders = MutableStateFlow<List<Order>>(emptyList())
    val historyOrders: StateFlow<List<Order>> = _historyOrders.asStateFlow()

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
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val totalToday = list.filter { order ->
                order.status == OrderStatus.COMPLETED.name && order.time.contains(today)
            }.sumOf { order -> order.shippingFee }
            "${String.format("%,.0f", totalToday)}đ"
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

    private var availableOrdersListener: ListenerRegistration? = null
    private var activeOrderListener: ListenerRegistration? = null
    private var historyOrdersListener: ListenerRegistration? = null

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

        firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val isAvailable = snapshot.getBoolean("isAvailable") ?: false
                    _isReadyToWork.value = isAvailable
                    if (isAvailable) startListeningAvailableOrders()
                    else stopListeningAvailableOrders()
                }
            }
    }

    fun toggleWorkStatus(isReady: Boolean) {
        val uid = currentShipperId.ifBlank { return }
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update("isAvailable", isReady)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startListeningAvailableOrders() {
        if (availableOrdersListener != null) return

        // 🌟 LƯU Ý: Sàn đơn tự do lấy các đơn có trạng thái ACCEPTED nhưng CHƯA có ai giao (shipperId rỗng)
        availableOrdersListener = firestore.collection("orders")
            .whereEqualTo("status", OrderStatus.ACCEPTED.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = mutableListOf<Order>()
                    for (document in snapshot.documents) {
                        try {
                            val order = document.toObject(Order::class.java)?.apply {
                                id = document.id
                                restaurantLat = (document.get("restaurantLat") as? Number)?.toDouble()
                                restaurantLng = (document.get("restaurantLng") as? Number)?.toDouble()
                                customerLat = (document.get("customerLat") as? Number)?.toDouble()
                                customerLng = (document.get("customerLng") as? Number)?.toDouble()
                            }
                            if (order != null && order.shipperId.isBlank()) {
                                list.add(order)
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    _availableOrders.value = list
                }
            }
    }

    private fun stopListeningAvailableOrders() {
        availableOrdersListener?.remove()
        availableOrdersListener = null
        _availableOrders.value = emptyList()
    }

    private fun listenToActiveDelivery() {
        val uid = currentShipperId.ifBlank { return }
        if (activeOrderListener != null) return

        activeOrderListener = firestore.collection("orders")
            .whereEqualTo("shipperId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val activeOrder = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)?.apply {
                                id = doc.id
                                restaurantLat = (doc.get("restaurantLat") as? Number)?.toDouble()
                                restaurantLng = (doc.get("restaurantLng") as? Number)?.toDouble()
                                customerLat = (doc.get("customerLat") as? Number)?.toDouble()
                                customerLng = (doc.get("customerLng") as? Number)?.toDouble()
                            }
                        } catch (e: Exception) { null }
                    }.firstOrNull {
                        // 🌟 FIX 2: Đồng bộ chuẩn hóa chữ hoa/thường hoặc tên Enum trạng thái đang xử lý
                        it.status.equals("ACCEPTED", ignoreCase = true) ||
                                it.status.equals("SHIPPING", ignoreCase = true) ||
                                it.status.equals("DELIVERING", ignoreCase = true)
                    }

                    _activeDeliveryOrder.value = activeOrder
                }
            }
    }

    private fun listenToHistoryOrders() {
        val uid = currentShipperId.ifBlank { return }
        if (historyOrdersListener != null) return

        // 🌟 FIX 3: Loại bỏ điều kiện .whereEqualTo("status") ở tầng Query Firestore
        // để có thể kéo về cả đơn hoàn thành (COMPLETED) lẫn đơn bị huỷ (CANCELLED) theo logic UI của bạn.
        historyOrdersListener = firestore.collection("orders")
            .whereEqualTo("shipperId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Order::class.java)?.apply { id = doc.id }
                        } catch (e: Exception) { null }
                    }.filter {
                        // Thực hiện lọc ở Client-side để gom cả 2 trạng thái kết thúc chuyến đi
                        it.status.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                                it.status.equals("CANCELLED", ignoreCase = true)
                    }
                    _historyOrders.value = list
                }
            }
    }

    fun acceptOrder(orderId: String) {
        val uid = currentShipperId.ifBlank { return }
        viewModelScope.launch {
            try {
                isLoading = true
                firestore.collection("orders").document(orderId)
                    .update(mapOf(
                        "shipperId" to uid,
                        "status" to OrderStatus.ACCEPTED.name // Hoặc gán trực tiếp chuỗi "ACCEPTED" tùy thuộc DB
                    ))
                    .addOnSuccessListener {
                        isLoading = false
                        Log.d("FIRESTORE_SUCCESS", "Nhận đơn thành công, tiến trình cập nhật Realtime tự động kích hoạt.")
                    }
                    .addOnFailureListener { isLoading = false }
            } catch (e: Exception) { isLoading = false }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context, orderId: String) {
        if (orderId == "HEATING_MAP_PREVIEW") return
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

                if (currentTrackingOrderId != null && currentTrackingOrderId != "HEATING_MAP_PREVIEW") {
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
        activeOrderListener?.remove()
        historyOrdersListener?.remove()
    }
}