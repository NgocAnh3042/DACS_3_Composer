package com.example.dacs_3_composer.domain.usecase

import com.example.dacs_3_composer.core.common.AppError
import com.example.dacs_3_composer.core.common.Result as AppResult
import com.example.dacs_3_composer.domain.model.SearchQuery
import com.example.dacs_3_composer.domain.repository.SearchHistoryRepository
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSearchQueryUseCaseTest {
    @Test
    fun `returns repository result`() = runBlocking {
        val useCase = RecordSearchQueryUseCase(FakeSearchHistoryRepository(result = true))

        assertEquals(AppResult.Success(true), useCase("phở"))
    }

    @Test
    fun `maps io failure to network error`() = runBlocking {
        val useCase = RecordSearchQueryUseCase(FakeSearchHistoryRepository(error = IOException("offline")))

        assertEquals(AppResult.Error(AppError.Network("offline")), useCase("phở"))
    }

    private class FakeSearchHistoryRepository(
        private val result: Boolean = false,
        private val error: Exception? = null
    ) : SearchHistoryRepository {
        override suspend fun recordSearchQuery(query: SearchQuery): Boolean {
            error?.let { throw it }
            return result
        }
    }
}
