package com.example.dacs_3_composer.feature.shared.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.core.model.UserAddress
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.*

/**
 * Tính khoảng cách thực tế (km), thời gian giao hàng dự kiến và phí giao hàng theo quy luật:
 * - Từ 0 đến 3.0 km: 15.000đ
 * - Trên 3.0 km: cứ tăng thêm mỗi 1 km (làm tròn lên theo bậc km) cộng thêm 10.000đ
 *   (Ví dụ: 3.1–4.0 km = 25.000đ; 4.1–5.0 km = 35.000đ).
 */
object DeliveryEstimate {
    const val BASE_SHIPPING_FEE = 15_000.0
    const val BASE_DISTANCE_KM = 3.0
    const val EXTRA_PER_KM_FEE = 10_000.0

    private val vnLocale: Locale = Locale.forLanguageTag("vi-VN")

    fun isValidCoordinate(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite()) return false
        if (abs(lat) < 0.0001 && abs(lng) < 0.0001) return false
        return lat in -90.0..90.0 && lng in -180.0..180.0
    }

    fun isInVietnam(lat: Double, lng: Double): Boolean {
        return lat in 8.0..24.0 && lng in 102.0..110.5
    }

    /**
     * Khoảng cách đường chim bay Haversine làm tròn 1 chữ số thập phân (tối thiểu 0.1 km).
     */
    fun distanceKm(lat1: Double?, lng1: Double?, lat2: Double?, lng2: Double?): Double? {
        if (!isValidCoordinate(lat1, lng1) || !isValidCoordinate(lat2, lng2)) return null
        val r = 6371.0
        val dLat = Math.toRadians(lat2!! - lat1!!)
        val dLng = Math.toRadians(lng2!! - lng1!!)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val rawKm = 2 * r * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        val clamped = rawKm.coerceAtLeast(0.1)
        return round(clamped * 10.0) / 10.0
    }

    /**
     * Quy luật tính phí giao hàng:
     * - <= 3.0 km (hoặc chưa rõ vị trí): 15.000đ
     * - > 3.0 km: 15.000đ + ceil(distanceKm - 3.0) * 10.000đ (VD: 4 km -> 25.000đ)
     */
    fun shippingFee(distanceKm: Double?, baseFee: Double = BASE_SHIPPING_FEE): Double {
        if (distanceKm == null || !distanceKm.isFinite() || distanceKm <= BASE_DISTANCE_KM) {
            return baseFee
        }
        val extraKm = ceil((distanceKm - BASE_DISTANCE_KM) - 1e-9).toInt().coerceAtLeast(1)
        return baseFee + extraKm * EXTRA_PER_KM_FEE
    }

    /**
     * Ước tính thời gian chuẩn bị món + di chuyển thực tế theo km.
     */
    fun estimateDeliveryMinutes(distanceKm: Double?, rawPrepTime: String? = null): String {
        val parsedPrep = rawPrepTime
            ?.trim()
            ?.takeWhile { it.isDigit() }
            ?.toIntOrNull()
        val prepMinutes = when {
            parsedPrep == null || parsedPrep <= 0 -> 12
            parsedPrep >= 28 -> 12 // Giá trị mặc định 30 từ DB khi quán chưa cấu hình riêng
            else -> parsedPrep.coerceIn(8, 25)
        }
        if (distanceKm == null || !distanceKm.isFinite()) {
            return "${prepMinutes + 8}–${prepMinutes + 15} phút"
        }
        // Tốc độ giao hàng nội đô trung bình ~24 km/h (~2.5 phút/km) + 3 phút lấy/giao món
        val travelMinutes = ceil(distanceKm * 2.5).toInt() + 3
        val minTotal = (prepMinutes + travelMinutes).coerceAtLeast(12)
        val buffer = if (distanceKm > 5.0) 7 else 5
        val maxTotal = minTotal + buffer
        return "$minTotal–$maxTotal phút"
    }

    fun formatDistance(distanceKm: Double?): String {
        if (distanceKm == null || !distanceKm.isFinite()) return "Chưa rõ vị trí"
        return String.format(Locale.US, "%.1f km", distanceKm)
    }

    fun formatFee(fee: Double): String {
        return NumberFormat.getNumberInstance(vnLocale).apply {
            maximumFractionDigits = 0
        }.format(fee) + "đ"
    }

    fun enrichRestaurant(
        restaurant: RestaurantDetail,
        userLat: Double?,
        userLng: Double?
    ): RestaurantDetail {
        val km = distanceKm(restaurant.latitude, restaurant.longitude, userLat, userLng)
        val fee = shippingFee(km)
        return restaurant.copy(
            distance = formatDistance(km),
            deliveryTime = estimateDeliveryMinutes(km, restaurant.deliveryTime),
            distanceKm = km,
            shippingFee = fee
        )
    }
}

object UserLocationCache {
    var cachedCoordinates by mutableStateOf<Pair<Double, Double>?>(null)
    var askedPermissionInSession: Boolean = false

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun readDeviceGps(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val client = runCatching { LocationServices.getFusedLocationProviderClient(context.applicationContext) }.getOrNull()
            ?: return null

        val last = suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null && DeliveryEstimate.isValidCoordinate(loc.latitude, loc.longitude)) {
                        cont.resume(loc.latitude to loc.longitude)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }
        if (last != null && DeliveryEstimate.isInVietnam(last.first, last.second)) {
            return last
        }

        val tokenSource = CancellationTokenSource()
        val fresh = suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
            cont.invokeOnCancellation { tokenSource.cancel() }
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(4_000)
                .build()
            client.getCurrentLocation(req, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null && DeliveryEstimate.isValidCoordinate(loc.latitude, loc.longitude)) {
                        cont.resume(loc.latitude to loc.longitude)
                    } else {
                        cont.resume(last)
                    }
                }
                .addOnFailureListener { cont.resume(last) }
        }
        return fresh ?: last
    }
}

/**
 * Lấy tọa độ người dùng để tính khoảng cách đến quán:
 * - Ưu tiên vị trí GPS thực tế trên thiết bị (nếu nằm tại Việt Nam);
 * - Dự phòng sang tọa độ địa chỉ giao hàng mặc định đã lưu của tài khoản (tránh lỗi GPS mặc định Mỹ trên máy ảo).
 */
@Composable
fun rememberUserCoordinates(requestPermissionIfNeeded: Boolean = false): Pair<Double, Double>? {
    val context = LocalContext.current
    var permissionTrigger by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionTrigger++
    }

    LaunchedEffect(requestPermissionIfNeeded) {
        if (requestPermissionIfNeeded &&
            !UserLocationCache.hasLocationPermission(context) &&
            !UserLocationCache.askedPermissionInSession
        ) {
            UserLocationCache.askedPermissionInSession = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(permissionTrigger) {
        var savedCoords: Pair<Double, Double>? = null
        if (FirebaseAuth.getInstance().currentUser != null) {
            runCatching { FoodHubClient.list<UserAddress>("addresses") }.onSuccess { list ->
                val chosen = list.firstOrNull { it.isDefault && DeliveryEstimate.isValidCoordinate(it.latitude, it.longitude) }
                    ?: list.firstOrNull { DeliveryEstimate.isValidCoordinate(it.latitude, it.longitude) }
                if (chosen != null) {
                    savedCoords = chosen.latitude to chosen.longitude
                    if (UserLocationCache.cachedCoordinates == null) {
                        UserLocationCache.cachedCoordinates = savedCoords
                    }
                }
            }
        }

        val gps = runCatching { UserLocationCache.readDeviceGps(context) }.getOrNull()
        val chosen = when {
            gps != null && DeliveryEstimate.isInVietnam(gps.first, gps.second) -> gps
            savedCoords != null -> savedCoords
            gps != null -> gps
            else -> null
        }
        if (chosen != null) {
            UserLocationCache.cachedCoordinates = chosen
        }
    }

    return UserLocationCache.cachedCoordinates
}
