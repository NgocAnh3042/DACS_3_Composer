package com.example.dacs_3_composer.data.mapper

import com.example.dacs_3_composer.data.remote.dto.SearchHistoryRecordDto
import com.example.dacs_3_composer.data.remote.dto.SearchHistoryRequestDto
import com.example.dacs_3_composer.domain.model.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryMapperTest {
    @Test
    fun `maps domain query to api request dto`() {
        assertEquals(SearchHistoryRequestDto("pho"), SearchQuery("pho").toDto())
    }

    @Test
    fun `maps server recorded flag to domain result`() {
        assertTrue(SearchHistoryRecordDto(recorded = true).toDomainResult())
        assertFalse(SearchHistoryRecordDto(recorded = false).toDomainResult())
    }
}
