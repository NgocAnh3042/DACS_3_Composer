package com.example.dacs_3_composer.data.remote

import android.util.Log
import com.example.dacs_3_composer.BuildConfig
import com.google.gson.Gson
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Central construction point for the FoodHub API HTTP and Retrofit clients. */
object RemoteNetworkFactory {
    fun createOkHttpClient(authInterceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(SafeNetworkLoggingInterceptor)
            }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()

    fun createRetrofit(baseUrl: String, client: OkHttpClient, gson: Gson): Retrofit {
        require(baseUrl.endsWith('/')) { "Retrofit base URL must end with '/'" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}

/** Logs request method and result only; it deliberately omits URLs, headers and bodies. */
private object SafeNetworkLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = System.nanoTime()
        return try {
            chain.proceed(request).also { response ->
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                Log.d("FoodHubNetwork", "${request.method} -> ${response.code} (${elapsedMs}ms)")
            }
        } catch (error: IOException) {
            Log.w("FoodHubNetwork", "${request.method} failed (${error.javaClass.simpleName})")
            throw error
        }
    }
}
