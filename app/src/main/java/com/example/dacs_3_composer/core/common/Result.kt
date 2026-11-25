package com.example.dacs_3_composer.core.common

sealed interface Result<out T> {
    data class Success<T>(val value: T) : Result<T>
    data class Error(val error: AppError) : Result<Nothing>
    data object Loading : Result<Nothing>
}

sealed interface AppError {
    data class Network(val message: String? = null) : AppError
    data object Unauthorized : AppError
    data class Validation(val message: String, val details: List<String> = emptyList()) : AppError
    data class Server(
        val code: Int? = null,
        val message: String? = null,
        val details: List<String> = emptyList()
    ) : AppError
    data class Unknown(val message: String? = null) : AppError
}
