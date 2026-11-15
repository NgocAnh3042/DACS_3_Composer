package com.example.dacs_3_composer.feature.shared.location

import org.junit.Assert.*
import org.junit.Test

class AddressSearchTest {
    private fun feature(lat: Double, house: String = "", street: String = "Nguyễn Đình Chiểu") =
        """{"properties":{"housenumber":"$house","street":"$street","locality":"Khu dân cư","city":"Đà Nẵng"},"geometry":{"coordinates":[108.0,$lat]}}"""

    private fun collection(vararg features: String) = """{"features":[${features.joinToString(",") }]}"""

    @Test fun nearbyHouseIsShownWithoutChangingChosenCoordinates() {
        val result = AddressSearch.reverseResult(collection(feature(16.0), feature(16.0001, "93")), 16.0, 108.0)!!
        assertTrue(result.address.contains("93 Nguyễn Đình Chiểu"))
        assertTrue(result.address.contains("Khu dân cư, Đà Nẵng"))
        assertEquals(16.0, result.latitude, 0.0)
        assertEquals(108.0, result.longitude, 0.0)
        assertEquals(true, result.hasHouseNumber)
        assertTrue(result.isApproximate)
    }

    @Test fun distantHouseDoesNotReplaceNearbyStreet() {
        val result = AddressSearch.reverseResult(collection(feature(16.0), feature(16.0007, "93")), 16.0, 108.0)!!
        assertFalse(result.address.contains("93"))
        assertEquals(false, result.hasHouseNumber)
    }

    @Test fun nearestNumberedBuildingWins() {
        val result = AddressSearch.reverseResult(collection(feature(16.0002, "95"), feature(16.0001, "93")), 16.0, 108.0)!!
        assertTrue(result.address.startsWith("93 "))
    }

    @Test fun noNearbyAddressReturnsNullRatherThanAnUnrelatedHouse() {
        assertNull(AddressSearch.reverseResult(collection(feature(16.01, "93")), 16.0, 108.0))
        assertNull(AddressSearch.reverseResult(collection(), 16.0, 108.0))
    }

    @Test fun duplicatesAndInvalidCoordinatesAreRemoved() {
        val features = AddressSearch.parseSuggestions(collection(feature(16.0, "93"), feature(16.0, "93"), feature(999.0, "12")))
        assertEquals(1, features.size)
        assertEquals(true, features.single().place.hasHouseNumber)
    }

    @Test fun streetResultDoesNotInventAHouseNumber() {
        val result = AddressSearch.parseSuggestions(collection(feature(16.0))).single()
        assertTrue(result.houseNumber.isEmpty())
        assertEquals(false, result.place.hasHouseNumber)
    }

    @Test fun streetGeometryCentreDoesNotReplaceOrInvalidateTheGpsPoint() {
        val result = AddressSearch.reverseResult(collection(feature(16.01)), 16.0, 108.0)!!
        assertEquals(16.0, result.latitude, 0.0)
        assertEquals(108.0, result.longitude, 0.0)
        assertEquals(false, result.hasHouseNumber)
    }
}
