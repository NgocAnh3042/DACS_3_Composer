package com.example.dacs_3_composer.data.mapper

import com.example.dacs_3_composer.core.common.AppError
import com.example.dacs_3_composer.data.remote.dto.ApiErrorDto
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

object ErrorMapper {
    private val gson = Gson()

    fun fromThrowable(error: Throwable): AppError = when (error) {
        is HttpException -> {
            val body = error.response()?.errorBody()?.let { responseBody ->
                runCatching { responseBody.string() }.getOrNull()
            }
            fromHttpError(error.code(), body)
        }
        is IOException -> AppError.Network(error.message)
        is SecurityException -> AppError.Unauthorized
        is IllegalArgumentException -> AppError.Validation(error.message.orEmpty())
        else -> AppError.Unknown(error.message)
    }

    fun fromHttpError(statusCode: Int, body: String?): AppError {
        val payload = body?.let { runCatching { gson.fromJson(it, ApiErrorDto::class.java) }.getOrNull() }
        val message = payload?.message ?: payload?.legacyError
        val details = payload?.details.orEmpty().map { detail ->
            if (detail.isJsonPrimitive && detail.asJsonPrimitive.isString) detail.asString else detail.toString()
        }
        return when (statusCode) {
            401, 403 -> AppError.Unauthorized
            400, 422 -> AppError.Validation(message ?: "Yêu cầu không hợp lệ.", details)
            else -> AppError.Server(statusCode, message ?: "Lỗi máy chủ.", details)
        }
    }
}
