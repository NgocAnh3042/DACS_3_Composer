package com.example.dacs_3_composer.di

import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.remote.SearchHistoryApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Bridges the staged Hilt graph to the existing Retrofit client without rebuilding it. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideSearchHistoryApi(): SearchHistoryApi = FoodHubClient.searchHistoryApi
}
