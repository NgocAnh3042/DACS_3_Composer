package com.example.dacs_3_composer.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ApiEnvelopeDto<T>(
    val success: Boolean? = null,
    val data: T? = null,
    val code: String? = null,
    val message: String? = null,
    @SerializedName("error") val legacyError: String? = null,
    val details: List<JsonElement> = emptyList()
)

data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
    @SerializedName("error") val legacyError: String? = null,
    val details: List<JsonElement> = emptyList()
)
