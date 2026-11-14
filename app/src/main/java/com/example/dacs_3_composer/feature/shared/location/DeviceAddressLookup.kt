package com.example.dacs_3_composer.feature.shared.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Device geocoding is optional. It needs no Maps key and falls back to Photon in the picker. */
internal object DeviceAddressLookup {
    private val cache = LinkedHashMap<String, AddressPlace>()
    // Older Android has a blocking API. Bound the work so rapid panning never queues a backlog.
    private val legacyWorker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1),
        { task -> Thread(task, "foodhub-address").apply { isDaemon = true } })

    suspend fun reverse(context: Context, latitude: Double, longitude: Double): AddressPlace? {
        val key = String.format(Locale.ROOT, "%.5f,%.5f", latitude, longitude)
        synchronized(cache) { cache[key] }?.let { return it.copy(latitude = latitude, longitude = longitude) }
        if (!runCatching { Geocoder.isPresent() }.getOrDefault(false)) return null
        val geocoder = runCatching { Geocoder(context.applicationContext, Locale.forLanguageTag("vi-VN")) }.getOrNull() ?: return null
        val address = withTimeoutOrNull(3_000) { lookup(geocoder, latitude, longitude) } ?: return null
        if (!address.subThoroughfare.isNullOrBlank() && address.hasLatitude() && address.hasLongitude()) {
            val distance = FloatArray(1)
            Location.distanceBetween(latitude, longitude, address.latitude, address.longitude, distance)
            if (distance[0] > 50) return null
        }
        val label = address.getAddressLine(0)?.takeIf(String::isNotBlank) ?: listOfNotNull(
            listOfNotNull(address.subThoroughfare, address.thoroughfare).joinToString(" ").takeIf(String::isNotBlank),
            address.subLocality, address.locality, address.adminArea
        ).distinct().joinToString(", ")
        if (label.isBlank()) return null
        val result = AddressPlace(label, latitude, longitude,
            hasHouseNumber = !address.subThoroughfare.isNullOrBlank(), isApproximate = true)
        synchronized(cache) {
            if (cache.size >= 50) cache.remove(cache.keys.first())
            cache[key] = result
        }
        return result
    }

    @Suppress("DEPRECATION")
    private suspend fun lookup(geocoder: Geocoder, latitude: Double, longitude: Double): Address? =
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            fun finish(addresses: List<Address>) {
                if (finished.compareAndSet(false, true)) continuation.resume(addresses.firstOrNull())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                continuation.invokeOnCancellation { finished.set(true) }
                try {
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = finish(addresses)
                        override fun onError(errorMessage: String?) = finish(emptyList())
                    })
                } catch (_: Exception) { finish(emptyList()) }
            } else {
                try {
                    val task = legacyWorker.submit {
                        try { finish(geocoder.getFromLocation(latitude, longitude, 1).orEmpty()) }
                        catch (_: Exception) { finish(emptyList()) }
                    }
                    continuation.invokeOnCancellation {
                        finished.set(true)
                        task.cancel(true)
                        legacyWorker.remove(task as Runnable)
                    }
                } catch (_: Exception) { finish(emptyList()) }
            }
        }
}
