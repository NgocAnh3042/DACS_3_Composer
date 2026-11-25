package com.example.dacs_3_composer.di

import com.example.dacs_3_composer.data.repository.RecommendationRepository
import com.example.dacs_3_composer.domain.repository.SearchHistoryRepository
import com.example.dacs_3_composer.domain.usecase.RecordSearchQueryUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(
        implementation: RecommendationRepository
    ): SearchHistoryRepository

    companion object {
        @Provides
        fun provideRecordSearchQueryUseCase(
            repository: SearchHistoryRepository
        ): RecordSearchQueryUseCase = RecordSearchQueryUseCase(repository)
    }
}
