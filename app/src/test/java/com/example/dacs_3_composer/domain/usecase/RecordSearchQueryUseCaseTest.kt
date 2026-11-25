package com.example.dacs_3_composer.domain.usecase

import com.example.dacs_3_composer.core.common.AppError
import com.example.dacs_3_composer.core.common.Result as AppResult
import com.example.dacs_3_composer.domain.model.SearchQuery
import com.example.dacs_3_composer.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSearchQueryUseCaseTest {
    @Test
    fun `returns repository result`() = runBlocking {
        val useCase = RecordSearchQueryUseCase(FakeSearchHistoryRepository(result = AppResult.Success(true)))

        assertEquals(AppResult.Success(true), useCase("phở"))
    }

    @Test
    fun `passes repository error to caller`() = runBlocking {
        val useCase = RecordSearchQueryUseCase(FakeSearchHistoryRepository(result = AppResult.Error(AppError.Network("offline"))))

        assertEquals(AppResult.Error(AppError.Network("offline")), useCase("phở"))
    }

    private class FakeSearchHistoryRepository(
        private val result: AppResult<Boolean>
    ) : SearchHistoryRepository {
        override suspend fun recordSearchQuery(query: SearchQuery): AppResult<Boolean> {
            return result
        }
    }
}
