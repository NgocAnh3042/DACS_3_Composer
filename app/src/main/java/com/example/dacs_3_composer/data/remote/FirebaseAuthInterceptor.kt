package com.example.dacs_3_composer.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** OkHttp delivers IOExceptions to Retrofit; unchecked token failures can kill its worker. */
internal class FirebaseAuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = try {
            tokenProvider()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Đã dừng xác thực đăng nhập. Vui lòng thử lại.").apply { initCause(error) }
        } catch (error: TimeoutException) {
            throw SocketTimeoutException("Xác thực đăng nhập phản hồi quá lâu. Kiểm tra kết nối mạng rồi thử lại.").apply { initCause(error) }
        } catch (error: ExecutionException) {
            throw IOException("Chưa thể xác thực đăng nhập. Kiểm tra kết nối mạng rồi thử lại.", error.cause ?: error)
        } catch (error: CancellationException) {
            throw IOException("Yêu cầu xác thực đã bị hủy. Vui lòng thử lại.", error)
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Chưa thể xác thực phiên đăng nhập. Vui lòng thử lại.", error)
        }
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            header("Accept", "application/json")
        }.build()
        val path = request.url.encodedPath
        return if (path.endsWith("/ai/meal-advisor") || path.contains("/ai/order-drafts")) {
            chain.withReadTimeout(60, TimeUnit.SECONDS).proceed(request)
        } else {
            chain.proceed(request)
        }
    }
}
