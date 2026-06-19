package com.example.dacs_3_composer

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.dacs_3_composer.ui.admin.MainRouteContainerAdmin
import com.example.dacs_3_composer.ui.auth.AuthViewModel
import com.example.dacs_3_composer.ui.auth.login.LoginScreen
import com.example.dacs_3_composer.ui.auth.register.RegisterScreen
import com.example.dacs_3_composer.ui.user.MainRouteContainerUser
import com.example.dacs_3_composer.ui.restaurant.MainRouteContainerRestaurant
import com.example.dacs_3_composer.ui.shipper.MainRouteContainerShipper
import com.example.dacs_3_composer.ui.theme.DACS_3_ComposerTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DACS_3_ComposerTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.authState.collectAsState()
                val context = LocalContext.current
                
                // Trạng thái điều hướng ban đầu
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        startDestination = "login"
                    } else {
                        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val savedRole = sharedPrefs.getString("user_role", null)

                        if (savedRole != null) {
                            startDestination = when (savedRole) {
                                "admin" -> "main_admin"
                                "restaurant" -> "main_restaurant"
                                "shipper" -> "main_shipper"
                                else -> "main_user"
                            }
                        } else {
                            // ✅ TỰ ĐỘNG KHÔI PHỤC ROLE NẾU BỊ MẤT DỮ LIỆU CỤC BỘ
                            FirebaseFirestore.getInstance().collection("users")
                                .document(currentUser.uid).get()
                                .addOnSuccessListener { doc ->
                                    val role = doc.getString("role") ?: "user"
                                    sharedPrefs.edit().putString("user_role", role).apply()
                                    startDestination = when (role) {
                                        "admin" -> "main_admin"
                                        "restaurant" -> "main_restaurant"
                                        "shipper" -> "main_shipper"
                                        else -> "main_user"
                                    }
                                }
                                .addOnFailureListener {
                                    startDestination = "login"
                                }
                        }
                    }
                }

                LaunchedEffect(authState) {
                    if (authState.isNotBlank() && authState != "Loading..." && authState != "Đang kết nối với Google...") {
                        Toast.makeText(context, authState, Toast.LENGTH_SHORT).show()
                        when (authState) {
                            "Đăng nhập User thành công!" -> {
                                navController.navigate("main_user") { popUpTo("login") { inclusive = true } }
                                authViewModel.clearAuthState()
                            }
                            "Đăng nhập Restaurant thành công!" -> {
                                navController.navigate("main_restaurant") { popUpTo("login") { inclusive = true } }
                                authViewModel.clearAuthState()
                            }
                            "Đăng nhập Shipper thành công!" -> {
                                navController.navigate("main_shipper") { popUpTo("login") { inclusive = true } }
                                authViewModel.clearAuthState()
                            }
                            "Đăng nhập Admin thành công!" -> {
                                navController.navigate("main_admin") { popUpTo("login") { inclusive = true } }
                                authViewModel.clearAuthState()
                            }
                            "Đăng ký thành công!" -> {
                                navController.popBackStack()
                                authViewModel.clearAuthState()
                            }
                            "Đã đăng xuất!" -> {
                                navController.navigate("login") { popUpTo(0) { inclusive = true } }
                                authViewModel.clearAuthState()
                            }
                        }
                    }
                }

                if (startDestination == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination!!
                    ) {
                        composable("login") {
                            LoginScreen(
                                onLoginClick = { email, password ->
                                    authViewModel.loginUser(context, email, password)
                                },
                                onSignUpClick = {
                                    navController.navigate("register")
                                },
                                onGoogleClick = {
                                    authViewModel.loginWithGoogle(context)
                                }
                            )
                        }

                        composable("register") {
                            RegisterScreen(
                                onRegisterClick = { fullName, phoneNumber, email, password, confirmPass, role ->
                                    authViewModel.registerUser(context, fullName, phoneNumber, email, password, confirmPass, role)
                                },
                                onNavigateToLogin = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("main_user") {
                            MainRouteContainerUser(
                                onLogout = {
                                    authViewModel.logoutUser(context)
                                }
                            )
                        }

                        composable("main_restaurant") {
                            MainRouteContainerRestaurant(
                                onLogout = {
                                    authViewModel.logoutUser(context)
                                }
                            )
                        }
                        composable("main_admin") {
                            MainRouteContainerAdmin(
                                onLogoutCallback = {
                                    authViewModel.logoutUser(context)
                                }
                            )
                        }

                        composable("main_shipper") {
                            MainRouteContainerShipper(
                                onLogout = {
                                    authViewModel.logoutUser(context)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
