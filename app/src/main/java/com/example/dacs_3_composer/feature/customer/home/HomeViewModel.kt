package com.example.dacs_3_composer.feature.customer.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.repository.PersonalizedRecommendationsResult
import com.example.dacs_3_composer.data.repository.RecommendationRefreshBus
import com.example.dacs_3_composer.data.repository.RecommendationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val recommendationRepository: RecommendationRepository = RecommendationRepository()
) : ViewModel() {

    private val _restaurantsList = MutableStateFlow<List<RestaurantDetail>>(emptyList())
    val restaurantsList: StateFlow<List<RestaurantDetail>> = _restaurantsList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _deliciousDishes = MutableStateFlow<List<Dish>>(emptyList())
    val deliciousDishes: StateFlow<List<Dish>> = _deliciousDishes.asStateFlow()

    private val _personalizedDishIds = MutableStateFlow<Set<String>>(emptySet())
    val personalizedDishIds: StateFlow<Set<String>> = _personalizedDishIds.asStateFlow()

    private val _isPersonalizedFeed = MutableStateFlow(false)
    val isPersonalizedFeed: StateFlow<Boolean> = _isPersonalizedFeed.asStateFlow()

    private val _categories = MutableStateFlow<List<HomeCategory>>(emptyList())
    val categories: StateFlow<List<HomeCategory>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private var allRawDishes: List<Dish> = emptyList()

    fun toggleCategory(categoryName: String?) {
        val trimmed = categoryName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Tất cả", ignoreCase = true) }
        _selectedCategory.value = if (_selectedCategory.value != null && _selectedCategory.value.equals(trimmed, ignoreCase = true)) {
            null
        } else {
            trimmed
        }
    }

    fun setCategoryFilter(categoryName: String?) {
        val trimmed = categoryName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Tất cả", ignoreCase = true) }
        _selectedCategory.value = trimmed
    }

    fun loadHome() {
        loadUserData()
        val needRefreshForSearch = RecommendationRefreshBus.consumeDirty()
        if (!loadedDishes || needRefreshForSearch) {
            fetchDeliciousDishes(force = needRefreshForSearch)
        }
        if (_restaurantsList.value.isEmpty()) {
            fetchAllRestaurants()
        }
        viewModelScope.launch {
            try {
                val addresses = FoodHubClient.list<com.example.dacs_3_composer.core.model.UserAddress>("addresses")
                val preferred = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
                userAddress = preferred?.addressDetail?.ifBlank { preferred.address } ?: ""
                if (preferred != null &&
                    com.example.dacs_3_composer.feature.shared.location.UserLocationCache.cachedCoordinates == null &&
                    com.example.dacs_3_composer.feature.shared.location.DeliveryEstimate.isValidCoordinate(preferred.latitude, preferred.longitude)
                ) {
                    com.example.dacs_3_composer.feature.shared.location.UserLocationCache.cachedCoordinates = preferred.latitude to preferred.longitude
                }
            } catch (exception: Exception) {
                Log.e("HomeViewModel", "Lỗi tải địa chỉ", exception)
            }
        }
    }

    // --- QUẢN LÝ THÔNG TIN NGƯỜI DÙNG ---
    var userName by mutableStateOf("Khách")
        private set

    var userAddress by mutableStateOf("")
        private set

    var userImageUrl by mutableStateOf("")
        private set

    fun loadUserData() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        viewModelScope.launch {
            try {
                val user = FoodHubClient.get<com.example.dacs_3_composer.core.model.User>("users", "me")
                userName = user.getDisplayName()
                userImageUrl = user.avatarUrl
            } catch (exception: Exception) {
                Log.e("HomeViewModel", "Lỗi lấy hồ sơ từ MySQL: ${exception.message}", exception)
            }
        }
    }

    var dishesLoading by mutableStateOf(false)
        private set
    var dishesFailed by mutableStateOf(false)
        private set
    private var loadedDishes = false

    fun ensureDishes() {
        if (!loadedDishes && !dishesLoading) fetchDeliciousDishes(force = false)
    }

    fun refreshDeliciousDishes() {
        fetchDeliciousDishes(force = true)
    }

    fun findDishById(dishId: String): Dish? {
        return _deliciousDishes.value.firstOrNull { it.id == dishId }
            ?: allRawDishes.firstOrNull { it.id == dishId }
    }

    fun dishesOfRestaurant(restaurantId: String, excludeDishId: String = ""): List<Dish> {
        val source = allRawDishes.ifEmpty { _deliciousDishes.value }
        return source.filter { it.restaurantId == restaurantId && it.id != excludeDishId }
    }

    /**
     * Tải danh sách "Món ngon cho bạn":
     * - Ưu tiên hiển thị các món ăn liên quan đến lịch sử tìm kiếm gần đây của người dùng (nếu có).
     * - Các món còn lại được trộn ngẫu nhiên thay vì cố định theo thời gian tạo mới nhất.
     */
    private fun fetchDeliciousDishes(force: Boolean = false) {
        if (dishesLoading) return
        if (!force && loadedDishes && _deliciousDishes.value.isNotEmpty()) return
        dishesLoading = true
        dishesFailed = false
        viewModelScope.launch {
            try {
                val dishesDeferred = async { FoodHubClient.list<Dish>("dishes") }
                val recDeferred = async {
                    runCatching { recommendationRepository.getPersonalizedRecommendations(limit = 20) }
                        .onFailure { Log.w("HomeViewModel", "Fallback không có gợi ý tìm kiếm: ${it.message}") }
                        .getOrNull()
                }
                val fetchedDishes = dishesDeferred.await()
                val recResult = recDeferred.await()
                allRawDishes = fetchedDishes

                val builtCategories = buildCategories(fetchedDishes)
                _categories.value = builtCategories
                val activeSelected = _selectedCategory.value
                if (activeSelected != null && builtCategories.none { it.name.equals(activeSelected, ignoreCase = true) }) {
                    _selectedCategory.value = null
                }

                val composed = composeDeliciousFeed(fetchedDishes, recResult)
                _deliciousDishes.value = composed.dishes
                _personalizedDishIds.value = composed.personalizedIds
                _isPersonalizedFeed.value = composed.isPersonalized
                loadedDishes = true
            } catch (e: Exception) {
                dishesFailed = true
                Log.e("HomeViewModel", "Lỗi tải món", e)
            } finally {
                dishesLoading = false
            }
        }
    }

    private fun fetchAllRestaurants() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                _restaurantsList.value = FoodHubClient.list("stores")
            } catch (exception: Exception) {
                Log.e("HomeViewModel", "Lỗi tải cửa hàng từ MySQL: ${exception.message}", exception)
            }
            _isLoading.value = false
        }
    }

    data class HomeCategory(
        val name: String,
        val dishCount: Int,
        val imageUrl: String = ""
    )

    companion object {
        data class ComposedFeed(
            val dishes: List<Dish>,
            val personalizedIds: Set<String>,
            val isPersonalized: Boolean
        )

        fun buildCategories(allDishes: List<Dish>): List<HomeCategory> {
            val active = allDishes.filter { it.available }
            if (active.isEmpty()) return emptyList()

            return active
                .groupBy { it.category.trim().ifBlank { "Khác" }.lowercase(java.util.Locale.ROOT) }
                .values
                .map { group ->
                    val displayName = group.firstNotNullOfOrNull { d ->
                        d.category.trim().takeIf { it.isNotEmpty() && !it.equals("Tất cả", ignoreCase = true) }
                    } ?: "Khác"
                    val repImage = group.firstNotNullOfOrNull { d -> d.imageUrl.trim().takeIf { it.isNotEmpty() } } ?: ""
                    HomeCategory(
                        name = displayName,
                        dishCount = group.size,
                        imageUrl = repImage
                    )
                }
                .sortedWith(compareByDescending<HomeCategory> { it.dishCount }.thenBy { it.name })
        }

        fun filterDishesByCategory(dishes: List<Dish>, selectedCategory: String?): List<Dish> {
            val target = selectedCategory?.trim()
            if (target.isNullOrEmpty() || target.equals("Tất cả", ignoreCase = true)) {
                return dishes
            }
            return dishes.filter { dish ->
                val cat = dish.category.trim().ifBlank { "Khác" }
                cat.equals(target, ignoreCase = true)
            }
        }

        fun filterRestaurantsByCategory(
            restaurants: List<RestaurantDetail>,
            allDishes: List<Dish>,
            selectedCategory: String?
        ): List<RestaurantDetail> {
            val target = selectedCategory?.trim()
            if (target.isNullOrEmpty() || target.equals("Tất cả", ignoreCase = true)) {
                return restaurants
            }
            val matchingStoreIds = filterDishesByCategory(allDishes, target)
                .map { it.restaurantId }
                .filter { it.isNotBlank() }
                .toSet()
            return restaurants.filter { it.id in matchingStoreIds }
        }

        /**
         * Quy luật hiển thị "Món ngon cho bạn":
         * 1. Nếu có lịch sử tìm kiếm liên quan (type == "personalized"):
         *    - Nhóm món liên quan lên đầu, xáo trộn nhẹ trong từng cụm 3 món để tự nhiên nhưng vẫn giữ ưu tiên.
         *    - Các món còn lại trộn ngẫu nhiên hoàn toàn (shuffled) nối phía sau.
         * 2. Nếu chưa có lịch sử tìm kiếm / tắt cá nhân hóa:
         *    - Trộn ngẫu nhiên toàn bộ danh sách món ăn thay vì sắp theo thời gian tạo.
         */
        fun composeDeliciousFeed(
            allDishes: List<Dish>,
            personalized: PersonalizedRecommendationsResult?
        ): ComposedFeed {
            if (allDishes.isEmpty()) {
                return ComposedFeed(emptyList(), emptySet(), false)
            }

            if (personalized != null && personalized.type == "personalized" && personalized.items.isNotEmpty()) {
                val byId = allDishes.associateBy { it.id }
                val relatedOrdered = personalized.items
                    .mapNotNull { item -> byId[item.dish.id] ?: item.dish.takeIf { it.id.isNotBlank() } }
                    .distinctBy { it.id }

                if (relatedOrdered.isNotEmpty()) {
                    val relatedIds = relatedOrdered.map { it.id }.toSet()
                    val gentlyShuffledRelated = relatedOrdered
                        .chunked(3)
                        .flatMap { group -> group.shuffled() }
                    val remainingShuffled = allDishes
                        .filterNot { it.id in relatedIds }
                        .shuffled()
                    return ComposedFeed(
                        dishes = gentlyShuffledRelated + remainingShuffled,
                        personalizedIds = relatedIds,
                        isPersonalized = true
                    )
                }
            }

            return ComposedFeed(
                dishes = allDishes.shuffled(),
                personalizedIds = emptySet(),
                isPersonalized = false
            )
        }
    }
}
