package com.example.dacs_3_composer.feature.customer.home.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.repository.PersonalizedRecommendationsResult
import com.example.dacs_3_composer.data.repository.RecommendationRefreshBus
import com.example.dacs_3_composer.data.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.google.firebase.auth.FirebaseAuth

data class HomeUiState(
    val restaurantsList: List<RestaurantDetail> = emptyList(),
    val isLoading: Boolean = false,
    val deliciousDishes: List<Dish> = emptyList(),
    val personalizedDishIds: Set<String> = emptySet(),
    val isPersonalizedFeed: Boolean = false,
    val categories: List<HomeViewModel.HomeCategory> = emptyList(),
    val selectedCategory: String? = null,
    val userName: String = "Khách",
    val userAddress: String = "",
    val userImageUrl: String = "",
    val dishesLoading: Boolean = false,
    val dishesFailed: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allRawDishes: List<Dish> = emptyList()

    fun toggleCategory(categoryName: String?) {
        val trimmed = categoryName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Tất cả", ignoreCase = true) }
        val selectedCategory = _uiState.value.selectedCategory
        _uiState.value = _uiState.value.copy(selectedCategory = if (selectedCategory != null && selectedCategory.equals(trimmed, ignoreCase = true)) {
            null
        } else {
            trimmed
        })
    }

    fun setCategoryFilter(categoryName: String?) {
        val trimmed = categoryName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Tất cả", ignoreCase = true) }
        _uiState.value = _uiState.value.copy(selectedCategory = trimmed)
    }

    fun loadHome() {
        loadUserData()
        val needRefreshForSearch = RecommendationRefreshBus.consumeDirty()
        if (!loadedDishes || needRefreshForSearch) {
            fetchDeliciousDishes(force = needRefreshForSearch)
        }
        if (_uiState.value.restaurantsList.isEmpty()) {
            fetchAllRestaurants()
        }
        viewModelScope.launch {
            try {
                val addresses = FoodHubClient.list<com.example.dacs_3_composer.core.model.UserAddress>("addresses")
                val preferred = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
                _uiState.value = _uiState.value.copy(
                    userAddress = preferred?.addressDetail?.ifBlank { preferred.address } ?: ""
                )
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
    fun loadUserData() {
        if (firebaseAuth.currentUser == null) return
        viewModelScope.launch {
            try {
                val user = FoodHubClient.get<com.example.dacs_3_composer.core.model.User>("users", "me")
                _uiState.value = _uiState.value.copy(
                    userName = user.getDisplayName(),
                    userImageUrl = user.avatarUrl
                )
            } catch (exception: Exception) {
                Log.e("HomeViewModel", "Lỗi lấy hồ sơ từ MySQL: ${exception.message}", exception)
            }
        }
    }

    private var loadedDishes = false

    fun ensureDishes() {
        if (!loadedDishes && !_uiState.value.dishesLoading) fetchDeliciousDishes(force = false)
    }

    fun refreshDeliciousDishes() {
        fetchDeliciousDishes(force = true)
    }

    fun findDishById(dishId: String): Dish? {
        return _uiState.value.deliciousDishes.firstOrNull { it.id == dishId }
            ?: allRawDishes.firstOrNull { it.id == dishId }
    }

    fun dishesOfRestaurant(restaurantId: String, excludeDishId: String = ""): List<Dish> {
        val source = allRawDishes.ifEmpty { _uiState.value.deliciousDishes }
        return source.filter { it.restaurantId == restaurantId && it.id != excludeDishId }
    }

    /**
     * Tải danh sách "Món ngon cho bạn":
     * - Ưu tiên hiển thị các món ăn liên quan đến lịch sử tìm kiếm gần đây của người dùng (nếu có).
     * - Các món còn lại được trộn ngẫu nhiên thay vì cố định theo thời gian tạo mới nhất.
     */
    private fun fetchDeliciousDishes(force: Boolean = false) {
        if (_uiState.value.dishesLoading) return
        if (!force && loadedDishes && _uiState.value.deliciousDishes.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(dishesLoading = true, dishesFailed = false)
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
                val activeSelected = _uiState.value.selectedCategory
                if (activeSelected != null && builtCategories.none { it.name.equals(activeSelected, ignoreCase = true) }) {
                    _uiState.value = _uiState.value.copy(selectedCategory = null)
                }

                val composed = composeDeliciousFeed(fetchedDishes, recResult)
                _uiState.value = _uiState.value.copy(
                    categories = builtCategories,
                    deliciousDishes = composed.dishes,
                    personalizedDishIds = composed.personalizedIds,
                    isPersonalizedFeed = composed.isPersonalized
                )
                loadedDishes = true
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(dishesFailed = true)
                Log.e("HomeViewModel", "Lỗi tải món", e)
            } finally {
                _uiState.value = _uiState.value.copy(dishesLoading = false)
            }
        }
    }

    private fun fetchAllRestaurants() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(restaurantsList = FoodHubClient.list("stores"))
            } catch (exception: Exception) {
                Log.e("HomeViewModel", "Lỗi tải cửa hàng từ MySQL: ${exception.message}", exception)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
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
