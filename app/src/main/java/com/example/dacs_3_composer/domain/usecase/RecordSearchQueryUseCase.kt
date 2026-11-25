package com.example.dacs_3_composer.domain.usecase

import com.example.dacs_3_composer.core.common.AppError
import com.example.dacs_3_composer.core.common.Result as AppResult
import com.example.dacs_3_composer.domain.model.SearchQuery
import com.example.dacs_3_composer.domain.repository.SearchHistoryRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException

class RecordSearchQueryUseCase(
    private val repository: SearchHistoryRepository
) {
    suspend operator fun invoke(query: String): AppResult<Boolean> = try {
        AppResult.Success(repository.recordSearchQuery(SearchQuery(query)))
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        AppResult.Error(AppError.Network(error.message))
    } catch (error: SecurityException) {
        AppResult.Error(AppError.Unauthorized)
    } catch (error: IllegalArgumentException) {
        AppResult.Error(AppError.Validation(error.message.orEmpty()))
    } catch (error: Exception) {
        AppResult.Error(AppError.Unknown(error.message))
    }
}
