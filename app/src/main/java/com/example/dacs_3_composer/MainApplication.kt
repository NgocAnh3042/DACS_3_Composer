package com.example.dacs_3_composer

import android.app.Application
import com.cloudinary.android.MediaManager
import okhttp3.OkHttpClient
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    companion object {
        lateinit var instance: MainApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
                .diskCache { DiskCache.Builder().directory(cacheDir.resolve("foodhub_images")).maxSizeBytes(64L * 1024 * 1024).build() }
                .crossfade(true)
                .build()
        )

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
