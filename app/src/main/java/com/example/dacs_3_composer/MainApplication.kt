package com.example.dacs_3_composer

import android.app.Application
import com.cloudinary.android.MediaManager
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val identifiedRequest = if (request.url.host == "tile.openstreetmap.org" ||
                    request.url.host == "tiles.openfreemap.org") {
                    request.newBuilder()
                        .header("User-Agent", "FoodHub-Android/1.0 (com.example.dacs_3_composer)")
                        .build()
                } else request
                chain.proceed(identifiedRequest)
            }.build()
        )

        // Khởi tạo Cloudinary
        val config = mapOf(
            "cloud_name" to "dhscw17vq", 
            "secure" to true
        )
        try {
            MediaManager.init(this, config)
        } catch (e: Exception) { }
    }
}
