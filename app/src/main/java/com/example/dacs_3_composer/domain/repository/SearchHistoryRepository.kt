package com.example.dacs_3_composer.domain.repository

import com.example.dacs_3_composer.core.common.Result
import com.example.dacs_3_composer.domain.model.SearchQuery

interface SearchHistoryRepository {
    suspend fun recordSearchQuery(query: SearchQuery): Result<Boolean>
}
