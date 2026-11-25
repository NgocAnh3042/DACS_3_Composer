package com.example.dacs_3_composer.data.remote.dto

data class SearchHistoryRequestDto(val query: String)

data class SearchHistoryRecordDto(
    val recorded: Boolean = false,
    val query: String? = null,
    val normalizedQuery: String? = null,
    val searchedAt: String? = null
)
