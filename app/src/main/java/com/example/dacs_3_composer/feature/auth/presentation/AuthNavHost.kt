package com.example.dacs_3_composer.feature.auth.presentation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.dacs_3_composer.feature.auth.presentation.login.LoginScreen
import com.example.dacs_3_composer.feature.auth.presentation.register.RegisterScreen
import com.example.dacs_3_composer.feature.customer.MainRouteContainerUser

// Định nghĩa tên các màn hình (Routes)
sealed class AuthScreen(val route: String) {
    object Login : AuthScreen("login")
    object SignUp : AuthScreen("signup")
}

@Composable
fun AuthNavHost(
    authViewModel: AuthViewModel = hiltViewModel() // Khởi tạo ViewModel chung cho luồng Auth
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authUiState by authViewModel.uiState.collectAsState()

    LaunchedEffect(authViewModel) {
        authViewModel.events.collect { event ->
            when (event) {
                is AuthUiEvent.ShowMessage -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is AuthUiEvent.Authenticated -> {
                    val destination = when (event.role) {
                        "admin" -> "admin_home"
                        "restaurant", "seller" -> "restaurant_home"
                        "shipper", "driver" -> "shipper_home"
                        else -> "user_home"
                    }
                    navController.navigate(destination) {
                        popUpTo(AuthScreen.Login.route) { inclusive = true }
                    }
                }
                AuthUiEvent.RegistrationCompleted -> navController.navigate(AuthScreen.Login.route) {
                    popUpTo(AuthScreen.SignUp.route) { inclusive = true }
                }
                AuthUiEvent.LoggedOut -> navController.navigate(AuthScreen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AuthScreen.Login.route
    ) {
        composable(route = AuthScreen.Login.route) {
            LoginScreen(
                isLoading = authUiState.isLoading,
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
                isLoading = authUiState.isLoading,
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
