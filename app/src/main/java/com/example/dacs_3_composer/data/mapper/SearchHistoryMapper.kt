package com.example.dacs_3_composer.data.mapper

import com.example.dacs_3_composer.data.remote.dto.SearchHistoryRecordDto
import com.example.dacs_3_composer.data.remote.dto.SearchHistoryRequestDto
import com.example.dacs_3_composer.domain.model.SearchQuery

fun SearchQuery.toDto(): SearchHistoryRequestDto = SearchHistoryRequestDto(query = text)

fun SearchHistoryRecordDto.toDomainResult(): Boolean = recorded
