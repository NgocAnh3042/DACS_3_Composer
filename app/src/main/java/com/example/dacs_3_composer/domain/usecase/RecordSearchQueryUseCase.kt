package com.example.dacs_3_composer.domain.usecase

import com.example.dacs_3_composer.core.common.Result as AppResult
import com.example.dacs_3_composer.domain.model.SearchQuery
import com.example.dacs_3_composer.domain.repository.SearchHistoryRepository

class RecordSearchQueryUseCase(
    private val repository: SearchHistoryRepository
) {
    suspend operator fun invoke(query: String): AppResult<Boolean> =
        if (query.trim().length < 2) AppResult.Success(false)
        else repository.recordSearchQuery(SearchQuery(query.trim()))
}
