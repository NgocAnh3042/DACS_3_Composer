package com.example.dacs_3_composer.feature.shared.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class AddressPlace(
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val hasHouseNumber: Boolean? = null,
    val isApproximate: Boolean = false
)

data class AddressSuggestion(
    val place: AddressPlace,
    val title: String,
    val area: String,
    val houseNumber: String
)

/** Photon supports search as you type; its public instance is used only for user-triggered lookups. */
object AddressSearch {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
    private const val userAgent = "FoodHub-Android/1.0 (com.example.dacs_3_composer)"
    private val cache = LinkedHashMap<String, List<AddressSuggestion>>()
    private val reverseCache = LinkedHashMap<String, AddressPlace>()

    suspend fun suggestions(query: String, near: AddressPlace?): List<AddressSuggestion> = withContext(Dispatchers.IO) {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        if (normalized.length < 3) return@withContext emptyList()
        val cacheKey = "${normalized.lowercase(Locale.ROOT)}|${near?.latitude?.let { (it * 100).toInt() }}|${near?.longitude?.let { (it * 100).toInt() }}"
        synchronized(cache) { cache[cacheKey] }?.let { return@withContext it }
        val url = HttpUrl.Builder().scheme("https").host("photon.komoot.io")
            .addPathSegment("api")
            .addQueryParameter("q", normalized)
            .addQueryParameter("limit", "8")
            // The public Photon instance rejects `vi`; `default` uses local OSM names.
            .addQueryParameter("lang", "default")
            .addQueryParameter("countrycode", "VN")
            .apply {
                if (near != null) {
                    addQueryParameter("lat", near.latitude.toString())
                    addQueryParameter("lon", near.longitude.toString())
                }
            }.build()
        val houseNumber = Regex("^([0-9]+[\\p{L}]?(?:[/\\-][0-9]+[\\p{L}]?)*)\\s+")
            .find(normalized)?.groupValues?.get(1).orEmpty()
        val places = parseSuggestions(fetchSuggestions(url))
            .sortedByDescending { houseNumber.isNotEmpty() && it.houseNumber.equals(houseNumber, ignoreCase = true) }
        synchronized(cache) {
            if (cache.size >= 30) cache.remove(cache.keys.first())
            cache[cacheKey] = places
        }
        places
    }

    private suspend fun fetchSuggestions(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use {
                        if (!it.isSuccessful) throw IOException("Không thể tìm địa chỉ (${it.code})")
                        it.body?.string().orEmpty()
                    }
                    if (!continuation.isCancelled) continuation.resume(body)
                } catch (e: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
            }
        })
    }

    internal fun parseSuggestions(json: String): List<AddressSuggestion> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { index ->
            val feature = features.optJSONObject(index) ?: return@mapNotNull null
            val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val lon = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            val p = feature.optJSONObject("properties") ?: return@mapNotNull null
            fun part(key: String) = p.optString(key).takeUnless { it == "null" }.orEmpty().trim()
            val number = part("housenumber")
            val street = listOf(number, part("street")).filter(String::isNotBlank).joinToString(" ")
            val title = listOf(part("name"), street).filter(String::isNotBlank).distinct().joinToString(", ")
            val area = listOf("locality", "district", "county", "city", "state")
                .map(::part).filter(String::isNotBlank).distinct().filter { it != title }.joinToString(", ")
            if (title.isBlank()) return@mapNotNull null
            AddressSuggestion(AddressPlace(listOf(title, area).filter(String::isNotBlank).joinToString(", "), lat, lon,
                hasHouseNumber = number.isNotBlank()), title, area, number)
        }.distinctBy { Triple(it.place.address, it.place.latitude, it.place.longitude) }
    }

    suspend fun reverse(latitude: Double, longitude: Double): AddressPlace? = withContext(Dispatchers.IO) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        // About one metre: avoid reusing a house label from another street/entrance.
        val key = String.format(Locale.ROOT, "%.5f,%.5f", latitude, longitude)
        synchronized(reverseCache) { reverseCache[key] }?.let {
            return@withContext it.copy(latitude = latitude, longitude = longitude)
        }
        val url = HttpUrl.Builder().scheme("https").host("photon.komoot.io")
            .addPathSegment("reverse")
            .addQueryParameter("lat", latitude.toString())
            .addQueryParameter("lon", longitude.toString())
            .addQueryParameter("limit", "5")
            .addQueryParameter("radius", "0.1")
            .addQueryParameter("lang", "default")
            .build()
        val place = reverseResult(fetchSuggestions(url), latitude, longitude) ?: return@withContext null
        synchronized(reverseCache) {
            if (reverseCache.size >= 50) reverseCache.remove(reverseCache.keys.first())
            reverseCache[key] = place
        }
        place
    }

    internal fun reverseResult(json: String, latitude: Double, longitude: Double): AddressPlace? {
        val candidates = parseSuggestions(json).map {
            it to distanceMetres(latitude, longitude, it.place.latitude, it.place.longitude)
        }
        // Prefer a nearby numbered building, never an arbitrary distant house.
        val chosen = candidates.filter { it.first.houseNumber.isNotBlank() && it.second <= 30.0 }.minByOrNull { it.second }
            // Street geometry can be long; its returned centre need not be near the GPS point.
            // Trust the server's radius/order for streets, rather than rejecting their centroids.
            ?: candidates.firstOrNull { it.first.houseNumber.isBlank() } ?: return null
        return chosen.first.place.copy(latitude = latitude, longitude = longitude, isApproximate = true)
    }

    private fun distanceMetres(lat: Double, lon: Double, targetLat: Double, targetLon: Double): Double {
        val dLat = Math.toRadians(targetLat - lat)
        val dLon = Math.toRadians(targetLon - lon)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(targetLat)) * sin(dLon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
