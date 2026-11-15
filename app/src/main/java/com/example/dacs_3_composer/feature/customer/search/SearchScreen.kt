package com.example.dacs_3_composer.feature.customer.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.feature.customer.home.components.RestaurantItem
import com.example.dacs_3_composer.feature.customer.search.components.FilterBar
import com.example.dacs_3_composer.feature.customer.search.components.SearchHeader
import java.util.Locale

@Composable
fun SearchScreen(
    searchQuery: String,
    navController: NavController, // 🌟 Nhận NavController từ màn hình chính để chuyển tiếp trang
    onBackClick: () -> Unit = {},
    searchViewModel: SearchViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    LaunchedEffect(searchQuery) {
        searchViewModel.searchRestaurantsAndDishes(searchQuery)
    }

    val restaurantResults by searchViewModel.restaurantResults.collectAsState()
    val dishResults by searchViewModel.dishResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()

    // Tính tổng số lượng kết quả tìm kiếm được
    val totalResultsCount = restaurantResults.size + dishResults.size

    Scaffold(
        topBar = {
            SearchHeader(
                location = "Vị trí của bạn",
                brandName = "AnChinh Go",
                onSearchClick = { /* Xử lý sự kiện tìm kiếm lại nếu cần */ }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2159BC))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Tiêu đề hiển thị từ khóa thực tế và số lượng kết quả tìm thấy
                    item {
                        com.example.dacs_3_composer.feature.customer.search.components.SearchResultTitle(
                            query = searchQuery,
                            resultCount = totalResultsCount
                        )
                    }

                    // Thanh lọc nhanh
                    item {
                        FilterBar(onFilterClick = {}, onPriceClick = {}, onDistanceClick = {})
                    }

                    // Trường hợp trống kết quả
                    if (restaurantResults.isEmpty() && dishResults.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxWidth().padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "Không tìm thấy kết quả nào phù hợp.", color = Color.Gray)
                            }
                        }
                    }

                    // 🎯 PHẦN 1: DANH SÁCH CỬA HÀNG (HIỂN THỊ PHÍA TRÊN)
                    if (restaurantResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Cửa hàng gợi ý (${restaurantResults.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191C1D),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        items(restaurantResults) { restaurant ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("restaurant_detail/${restaurant.id}")
                                    }
                            ) {
                                RestaurantItem(restaurant = restaurant)
                            }
                        }
                    }

                    // 🎯 PHẦN 2: DANH SÁCH MÓN ĂN / SẢN PHẨM (HIỂN THỊ PHÍA DƯỚI)
                    if (dishResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Sản phẩm gợi ý (${dishResults.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191C1D),
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        items(dishResults) { wrapper ->
                            SearchDishItem(
                                wrapper = wrapper,
                                onItemClick = {
                                    // Bấm vào sản phẩm -> Dẫn thẳng về trang nhà hàng tương ứng qua restaurantId
                                    navController.navigate("restaurant_detail/${wrapper.dish.restaurantId}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchDishItem(
    wrapper: SearchDishWrapper,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dish = wrapper.dish

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sử dụng chính xác trường imageUrl từ Model Dish của bạn
            AsyncImage(
                model = dish.imageUrl.ifBlank { R.drawable.banner1 },
                contentDescription = null,
                placeholder = painterResource(id = R.drawable.banner1),
                error = painterResource(id = R.drawable.banner1),
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dish.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF191C1D)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Quán: ${wrapper.restaurantName}",
                    fontSize = 13.sp,
                    color = Color(0xFF2159BC),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale("vi", "VN"), "%,.0f đ", dish.price),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFFE28743)
                )
            }
        }
    }
}