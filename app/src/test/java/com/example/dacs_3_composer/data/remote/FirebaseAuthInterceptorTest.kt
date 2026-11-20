package com.example.dacs_3_composer.data.remote

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class FirebaseAuthInterceptorTest {
    private val request = Request.Builder().url("https://foodhub.invalid/api/ai/meal-advisor").build()

    private fun client(provider: () -> String?, onRequest: (Request, Int) -> Unit = { _, _ -> }): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(FirebaseAuthInterceptor(provider)).addInterceptor { chain ->
            onRequest(chain.request(), chain.readTimeoutMillis())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body("{}".toResponseBody()).build()
        }.build()

    @Test fun firebaseNetworkFailureReachesAsyncCallbackWithoutUncaughtException() {
        val cause = IOException("simulated Firebase network failure")
        val uncaught = AtomicReference<Throwable?>()
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "auth-regression-test").apply {
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> uncaught.set(error) }
            }
        }
        val callsToServer = AtomicInteger()
        val failed = AtomicReference<IOException?>()
        val completed = CountDownLatch(1)
        val client = client({ throw ExecutionException(cause) }) { _, _ -> callsToServer.incrementAndGet() }
            .newBuilder().dispatcher(Dispatcher(executor)).build()
        try {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { failed.set(e); completed.countDown() }
                override fun onResponse(call: Call, response: Response) { response.close(); completed.countDown() }
            })
            assertTrue("callback must complete", completed.await(5, TimeUnit.SECONDS))
            executor.shutdown()
            assertTrue("worker must stop", executor.awaitTermination(5, TimeUnit.SECONDS))
            assertNotNull(failed.get())
            assertSame(cause, failed.get()!!.cause)
            assertNull("token failure must not crash the dispatcher", uncaught.get())
            assertEquals("do not send an unauthenticated request after token failure", 0, callsToServer.get())
        } finally { executor.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun timedOutTokenCanBeRetriedAndHeadersAndAiTimeoutRemainCorrect() {
        var attempts = 0
        val client = client({ if (attempts++ == 0) throw TimeoutException() else "fresh-test-token" }) { sent, timeout ->
            assertEquals("Bearer fresh-test-token", sent.header("Authorization"))
            assertEquals("application/json", sent.header("Accept"))
            assertEquals(60000, timeout)
        }
        try {
            client.newCall(request).execute().close()
            fail("timeout must become an IOException")
        } catch (expected: SocketTimeoutException) { assertTrue(expected.cause is TimeoutException) }
        client.newCall(request).execute().use { assertEquals(200, it.code) }
    }

    @Test fun interruptedTokenWaitPreservesInterruptFlag() {
        try {
            client({ throw InterruptedException() }).newCall(request).execute().close()
            fail("interruption must become an IOException")
        } catch (expected: InterruptedIOException) {
            assertTrue(expected.cause is InterruptedException)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }

    @Test fun cancelledOrUncheckedAuthFailureBecomesIOException() {
        for (cause in listOf(CancellationException(), IllegalStateException("simulated auth failure"))) {
            try {
                client({ throw cause }).newCall(request).execute().close()
                fail("auth failures must not escape as unchecked exceptions")
            } catch (expected: IOException) { assertSame(cause, expected.cause) }
        }
    }

    @Test fun ordinaryApiCallsKeepTheirOriginalReadTimeout() {
        client({ "test-token" }) { _, timeout -> assertEquals(30000, timeout) }.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url("https://foodhub.invalid/api/dishes").build()).execute().close()
    }
}
