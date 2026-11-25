package com.example.dacs_3_composer.feature.customer.home

import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.repository.PersonalizedDish
import com.example.dacs_3_composer.data.repository.PersonalizedRecommendationsResult
import com.example.dacs_3_composer.feature.customer.home.presentation.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedCompositionTest {

    private fun sampleDishes(count: Int = 12): List<Dish> =
        (1..count).map { i ->
            Dish(
                id = i.toString(),
                name = "Món $i",
                price = 30000.0 + i * 1000,
                category = "Món chính",
                restaurantId = ((i % 3) + 1).toString()
            )
        }

    @Test
    fun `prioritizes search-related dishes first and keeps all remaining dishes without duplicates`() {
        val all = sampleDishes(12)
        val related = listOf(all[7], all[2], all[10]) // IDs: 8, 3, 11
        val rec = PersonalizedRecommendationsResult(
            type = "personalized",
            title = "Dành riêng cho bạn",
            items = related.map { PersonalizedDish(dish = it, restaurantName = "Quán A", score = 0.85) }
        )

        val feed = HomeViewModel.composeDeliciousFeed(all, rec)

        assertTrue(feed.isPersonalized)
        assertEquals(setOf("8", "3", "11"), feed.personalizedIds)
        assertEquals(12, feed.dishes.size)
        assertEquals(12, feed.dishes.map { it.id }.toSet().size)
        // Top 3 slots MUST be the search-related dishes
        assertEquals(setOf("8", "3", "11"), feed.dishes.take(3).map { it.id }.toSet())
    }

    @Test
    fun `cold start or disabled personalization shuffles dishes and marks feed non-personalized`() {
        val all = sampleDishes(12)
        val popularFallback = PersonalizedRecommendationsResult(
            type = "popular",
            title = "Món phổ biến hôm nay",
            items = listOf(PersonalizedDish(dish = all[0]))
        )

        val feed = HomeViewModel.composeDeliciousFeed(all, popularFallback)

        assertFalse(feed.isPersonalized)
        assertTrue(feed.personalizedIds.isEmpty())
        assertEquals(12, feed.dishes.size)
        assertEquals(all.map { it.id }.toSet(), feed.dishes.map { it.id }.toSet())
    }

    @Test
    fun `empty catalog returns empty feed safely`() {
        val feed = HomeViewModel.composeDeliciousFeed(emptyList(), null)
        assertTrue(feed.dishes.isEmpty())
        assertFalse(feed.isPersonalized)
    }

    @Test
    fun `buildCategories groups case-insensitively, counts dishes and sorts descending`() {
        val dishes = listOf(
            Dish(id = "1", name = "Phở bò", category = "Món chính", imageUrl = "https://img/pho.jpg"),
            Dish(id = "2", name = "Bún bò", category = "món chính", imageUrl = "https://img/bun.jpg"),
            Dish(id = "3", name = "Cơm tấm", category = "Món chính", imageUrl = "https://img/com.jpg"),
            Dish(id = "4", name = "Ốc hương bơ tỏi", category = "Hải sản", imageUrl = "https://img/oc.jpg"),
            Dish(id = "5", name = "Nghêu hấp sả", category = "Hải sản", imageUrl = "https://img/ngheu.jpg"),
            Dish(id = "6", name = "Sinh tố bơ", category = "Sinh tố", imageUrl = "https://img/bo.jpg"),
            Dish(id = "7", name = "Món tạm ngưng", category = "Sinh tố", available = false, imageUrl = "https://img/off.jpg")
        )

        val categories = HomeViewModel.buildCategories(dishes)

        assertEquals(3, categories.size)
        assertEquals("Món chính", categories[0].name)
        assertEquals(3, categories[0].dishCount)
        assertEquals("https://img/pho.jpg", categories[0].imageUrl)

        assertEquals("Hải sản", categories[1].name)
        assertEquals(2, categories[1].dishCount)

        assertEquals("Sinh tố", categories[2].name)
        assertEquals(1, categories[2].dishCount)
    }

    @Test
    fun `filterDishesByCategory and filterRestaurantsByCategory match selected category accurately`() {
        val dishes = listOf(
            Dish(id = "1", name = "Ốc hương", category = "Hải sản", restaurantId = "101"),
            Dish(id = "2", name = "Ramen", category = "Món Nhật", restaurantId = "102"),
            Dish(id = "3", name = "Tôm hấp dừa", category = "hải sản", restaurantId = "101")
        )
        val stores = listOf(
            com.example.dacs_3_composer.core.model.RestaurantDetail(id = "101", name = "Hải Sản Mỹ Khê"),
            com.example.dacs_3_composer.core.model.RestaurantDetail(id = "102", name = "Góc Nhật Bản"),
            com.example.dacs_3_composer.core.model.RestaurantDetail(id = "103", name = "Bếp Chay")
        )

        val seafoodDishes = HomeViewModel.filterDishesByCategory(dishes, "Hải sản")
        assertEquals(listOf("1", "3"), seafoodDishes.map { it.id })

        val allDishes = HomeViewModel.filterDishesByCategory(dishes, null)
        assertEquals(3, allDishes.size)

        val seafoodStores = HomeViewModel.filterRestaurantsByCategory(stores, dishes, "Hải sản")
        assertEquals(listOf("101"), seafoodStores.map { it.id })
    }
}
