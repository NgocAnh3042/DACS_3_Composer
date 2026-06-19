package com.example.dacs_3_composer.ui.auth

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.dacs_3_composer.ui.auth.login.LoginScreen
import com.example.dacs_3_composer.ui.auth.register.RegisterScreen
import com.example.dacs_3_composer.ui.user.MainRouteContainerUser

// Định nghĩa tên các màn hình (Routes)
sealed class AuthScreen(val route: String) {
    object Login : AuthScreen("login")
    object SignUp : AuthScreen("signup")
}

@Composable
fun AuthNavHost(
    authViewModel: AuthViewModel = viewModel() // Khởi tạo ViewModel chung cho luồng Auth
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Lắng nghe trạng thái thông báo lỗi/thành công từ Firebase qua ViewModel
    val authState by authViewModel.authState.collectAsState()

    // Hiển thị Toast thông báo và xử lý nhảy màn hình theo Phân Quyền
    LaunchedEffect(authState) {
        if (authState.isNotEmpty()) {
            if (authState != "Loading..." && authState != "Đợi xí, đang xử lý..." && authState != "Đang kết nối với Google...") {
                Toast.makeText(context, authState, Toast.LENGTH_SHORT).show()
            }

            // 1. Luồng xử lý sau khi ĐĂNG KÝ thành công
            if (authState == "Đăng ký thành công!") {
                navController.navigate(AuthScreen.Login.route) {
                    popUpTo(AuthScreen.SignUp.route) { inclusive = true }
                }
                authViewModel.clearAuthState()
            }

            // 2. Luồng xử lý sau khi ĐĂNG NHẬP thành công tùy vào từng ROLE
            else if (authState == "Đăng nhập User thành công!") {
                navController.navigate("user_home") {
                    popUpTo(AuthScreen.Login.route) { inclusive = true }
                }
                authViewModel.clearAuthState()
            }
            else if (authState == "Đăng nhập Restaurant thành công!") {
                navController.navigate("restaurant_home") {
                    popUpTo(AuthScreen.Login.route) { inclusive = true }
                }
                authViewModel.clearAuthState()
            }
            else if (authState == "Đăng nhập Shipper thành công!") {
                navController.navigate("shipper_home") {
                    popUpTo(AuthScreen.Login.route) { inclusive = true }
                }
                authViewModel.clearAuthState()
            }
            else if (authState == "Đăng nhập Admin thành công!") {
                navController.navigate("admin_home") {
                    popUpTo(AuthScreen.Login.route) { inclusive = true }
                }
                authViewModel.clearAuthState()
            }

            else if (authState != "Loading..." && authState != "Đợi xí, đang xử lý..." && authState != "Đang kết nối với Google...") {
                authViewModel.clearAuthState()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AuthScreen.Login.route
    ) {
        composable(route = AuthScreen.Login.route) {
            LoginScreen(
                onLoginClick = { email, password ->
                    // ✅ ĐÃ FIX: Thêm context vào đầu
                    authViewModel.loginUser(context, email, password)
                },
                onSignUpClick = {
                    navController.navigate(AuthScreen.SignUp.route)
                },
                onGoogleClick = {
                    authViewModel.loginWithGoogle(context)
                }
            )
        }

        composable(route = AuthScreen.SignUp.route) {
            RegisterScreen(
                onRegisterClick = { fullName, phoneNumber, email, password, confirmPass, role ->
                    // ✅ ĐÃ FIX: Thêm context vào đầu
                    authViewModel.registerUser(context, fullName, phoneNumber, email, password, confirmPass, role)
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(route = "user_home") {
            MainRouteContainerUser(
                onLogout = {
                    // ✅ ĐÃ FIX: Thêm context vào đầu
                    authViewModel.logoutUser(context)
                    navController.navigate(AuthScreen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(route = "restaurant_home") {
            androidx.compose.material3.Text("Giao diện Quản lý của Nhà hàng (Đang phát triển)")
        }

        composable(route = "shipper_home") {
            androidx.compose.material3.Text("Giao diện Nhận đơn của Shipper (Đang phát triển)")
        }

        composable(route = "admin_home") {
            androidx.compose.material3.Text("Giao diện Tổng quản lý Admin Hệ Thống (Đang phát triển)")
        }
    }
}
