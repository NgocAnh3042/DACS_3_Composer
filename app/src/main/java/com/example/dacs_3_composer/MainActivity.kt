package com.example.dacs_3_composer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.dacs_3_composer.feature.admin.MainRouteContainerAdmin
import com.example.dacs_3_composer.feature.auth.presentation.AuthViewModel
import com.example.dacs_3_composer.feature.auth.presentation.AuthUiEvent
import com.example.dacs_3_composer.feature.auth.presentation.login.LoginScreen
import com.example.dacs_3_composer.feature.auth.presentation.register.RegisterScreen
import com.example.dacs_3_composer.feature.customer.MainRouteContainerUser
import com.example.dacs_3_composer.feature.restaurant.MainRouteContainerRestaurant
import com.example.dacs_3_composer.feature.shipper.MainRouteContainerShipper
import com.example.dacs_3_composer.core.designsystem.DACS_3_ComposerTheme
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.firebase.FirebasePushService
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DACS_3_ComposerTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel()
                val authUiState by authViewModel.uiState.collectAsState()
                val context = LocalContext.current
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { }
                )
                val requestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                
                // Trạng thái điều hướng ban đầu
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        startDestination = "login"
                    } else {
                        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        val savedRole = sharedPrefs.getString("user_role", null)
                        val savedUid = sharedPrefs.getString("firebase_uid", null)

                        if (savedRole != null && savedUid == currentUser.uid) {
                            FirebasePushService.syncCurrentToken()
                            requestNotificationPermission()
                            startDestination = when (savedRole) {
                                "admin" -> "main_admin"
                                "restaurant" -> "main_restaurant"
                                "shipper" -> "main_shipper"
                                else -> "main_user"
                            }
                        } else {
                            // Ignore a cached role belonging to a different Firebase account.
                            sharedPrefs.edit().remove("user_role").remove("firebase_uid").apply()
                            // Khôi phục role từ hồ sơ MySQL; Firebase chỉ cung cấp danh tính.
                            try {
                                val result = FoodHubClient.api.get("users", "me")
                                var role = if (result.isSuccessful) {
                                    result.body()?.getAsJsonObject("data")?.get("role")?.asString ?: "user"
                                } else if (result.code() == 404) {
                                    val payload = JsonObject().apply {
                                        addProperty("fullName", currentUser.displayName ?: "")
                                        addProperty("email", currentUser.email ?: "")
                                        addProperty("avatarUrl", currentUser.photoUrl?.toString() ?: "")
                                        addProperty("role", "customer")
                                    }
                                    val created = FoodHubClient.api.create("users", payload)
                                    if (!created.isSuccessful) error("Không khởi tạo được hồ sơ MySQL")
                                    created.body()?.getAsJsonObject("data")?.get("role")?.asString ?: "user"
                                } else error("Không tải được hồ sơ MySQL")
                                if (role == "seller") role = "restaurant"
                                if (role == "driver") role = "shipper"
                                sharedPrefs.edit()
                                    .putString("user_role", role)
                                    .putString("firebase_uid", currentUser.uid)
                                    .apply()
                                FirebasePushService.syncCurrentToken()
                                requestNotificationPermission()
                                startDestination = when (role) {
                                    "admin" -> "main_admin"
                                    "restaurant" -> "main_restaurant"
                                    "shipper" -> "main_shipper"
                                    else -> "main_user"
                                }
                            } catch (_: Exception) {
                                // Không điều hướng nhầm role khi API chưa sẵn sàng.
                                startDestination = "login"
                            }
                        }
                    }
                }

                LaunchedEffect(authViewModel) {
                    authViewModel.events.collect { event ->
                        when (event) {
                            is AuthUiEvent.ShowMessage -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                            is AuthUiEvent.Authenticated -> {
                                requestNotificationPermission()
                                val destination = when (event.role) {
                                    "admin" -> "main_admin"
                                    "restaurant", "seller" -> "main_restaurant"
                                    "shipper", "driver" -> "main_shipper"
                                    else -> "main_user"
                                }
                                navController.navigate(destination) { popUpTo("login") { inclusive = true } }
                            }
                            AuthUiEvent.RegistrationCompleted -> navController.popBackStack()
                            AuthUiEvent.LoggedOut -> navController.navigate("login") { popUpTo(0) { inclusive = true } }
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
                                isLoading = authUiState.isLoading,
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
                                isLoading = authUiState.isLoading,
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
