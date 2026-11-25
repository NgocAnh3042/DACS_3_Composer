package com.example.dacs_3_composer.data.mapper

import com.example.dacs_3_composer.core.common.AppError
import com.google.gson.JsonObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorMapperTest {
    @Test
    fun `maps io exception to network error`() {
        assertEquals(AppError.Network("offline"), ErrorMapper.fromThrowable(IOException("offline")))
    }

    @Test
    fun `maps php validation payload with legacy error field`() {
        val error = ErrorMapper.fromHttpError(422, """{"error":"Thiếu query","details":["query"]}""")

        assertEquals(AppError.Validation("Thiếu query", listOf("query")), error)
    }

    @Test
    fun `maps http exception response`() {
        val response = Response.error<JsonObject>(
            503,
            """{"code":"TEMPORARY","message":"Thử lại","details":["db"]}"""
                .toResponseBody("application/json".toMediaType())
        )

        assertEquals(
            AppError.Server(503, "Thử lại", listOf("db")),
            ErrorMapper.fromThrowable(HttpException(response))
        )
    }
}
