package com.example.dacs_3_composer.feature.shared.location

import com.example.dacs_3_composer.core.model.RestaurantDetail
import org.junit.Assert.*
import org.junit.Test

class DeliveryEstimateTest {

    @Test
    fun shippingFeeFollowsTieredRuleFromOneToThreeKmAndPlusTenKPerExtraKm() {
        assertEquals(15_000.0, DeliveryEstimate.shippingFee(null), 0.0)
        assertEquals(15_000.0, DeliveryEstimate.shippingFee(0.5), 0.0)
        assertEquals(15_000.0, DeliveryEstimate.shippingFee(1.0), 0.0)
        assertEquals(15_000.0, DeliveryEstimate.shippingFee(2.7), 0.0)
        assertEquals(15_000.0, DeliveryEstimate.shippingFee(3.0), 0.0)

        // > 3km up to 4km -> +10k = 25k
        assertEquals(25_000.0, DeliveryEstimate.shippingFee(3.1), 0.0)
        assertEquals(25_000.0, DeliveryEstimate.shippingFee(4.0), 0.0)

        // > 4km up to 5km -> +20k = 35k
        assertEquals(35_000.0, DeliveryEstimate.shippingFee(4.1), 0.0)
        assertEquals(35_000.0, DeliveryEstimate.shippingFee(5.0), 0.0)

        // 6km -> 45k
        assertEquals(45_000.0, DeliveryEstimate.shippingFee(6.0), 0.0)
    }

    @Test
    fun haversineDistanceAndDeliveryTimeEstimateWorkForDaNangCoordinates() {
        // FPT City (15.97938, 108.26336) -> Lê Văn Hiến (16.01245, 108.24852) ~ 4.0 km
        val km = DeliveryEstimate.distanceKm(15.9793866, 108.2633644, 16.0124500, 108.2485200)
        assertNotNull(km)
        assertTrue("Expected ~4.0 km in Da Nang, got $km", km!! in 3.8..4.2)
        assertEquals(25_000.0, DeliveryEstimate.shippingFee(km), 0.0)

        val store = RestaurantDetail(
            id = "60002",
            name = "Phở & Bún Bò Cố Đô",
            rating = 4.8,
            deliveryTime = "15",
            latitude = 16.0124500,
            longitude = 108.2485200
        )
        val enriched = DeliveryEstimate.enrichRestaurant(store, 15.9793866, 108.2633644)
        assertTrue(enriched.distance.endsWith(" km"))
        assertTrue(enriched.deliveryTime.contains("phút"))
        assertEquals(25_000.0, enriched.shippingFee, 0.0)
    }

    @Test
    fun invalidOrZeroCoordinatesReturnNullDistanceAndBaseFee() {
        assertNull(DeliveryEstimate.distanceKm(0.0, 0.0, 15.98, 108.26))
        assertNull(DeliveryEstimate.distanceKm(null, 108.26, 15.98, 108.26))
    }
}
