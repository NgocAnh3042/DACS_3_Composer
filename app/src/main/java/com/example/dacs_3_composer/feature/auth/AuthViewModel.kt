package com.example.dacs_3_composer.feature.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.data.repository.ActivityLogRepository
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.firebase.FirebasePushService
import com.example.dacs_3_composer.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val activityLogRepository = ActivityLogRepository()

    private val _authState = MutableStateFlow<String>("")
    val authState: StateFlow<String> = _authState

    private suspend fun syncUserProfile(fullName: String = "", phone: String = "", requestedRole: String = "customer"): String {
        val current = auth.currentUser ?: error("Chưa đăng nhập Firebase")
        val response = FoodHubClient.api.get("users", "me")
        if (response.isSuccessful) return response.body()?.getAsJsonObject("data")?.get("role")?.asString ?: "user"
        if (response.code() != 404) error(response.errorBody()?.string() ?: "Không tải được hồ sơ MySQL")
        val payload = JsonObject().apply {
            addProperty("fullName", fullName.ifBlank { current.displayName ?: "" })
            addProperty("email", current.email ?: "")
            addProperty("phoneNumber", phone)
            addProperty("role", requestedRole)
            addProperty("avatarUrl", current.photoUrl?.toString() ?: "")
        }
        val created = FoodHubClient.api.create("users", payload)
        if (!created.isSuccessful) error(created.errorBody()?.string() ?: "Không tạo được hồ sơ MySQL")
        return created.body()?.getAsJsonObject("data")?.get("role")?.asString ?: requestedRole
    }

    fun loginWithGoogle(context: Context) {
        _authState.value = "Đang kết nối với Google..."
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    error("Google trả về loại thông tin đăng nhập không được hỗ trợ")
                }
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)

                auth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            viewModelScope.launch {
                                try {
                                    val role = syncUserProfile()
                                    if (role == "restaurant") auth.currentUser?.uid?.let(::logLogin)
                                    triggerLoginSuccessByRole(context, role)
                                } catch (e: Exception) {
                                    _authState.value = "Đăng nhập Google thành công nhưng lỗi đồng bộ hồ sơ: ${e.message}"
                                }
                            }
                        } else {
                            _authState.value = "Lỗi Firebase: ${task.exception?.localizedMessage ?: "không xác định"}"
                        }
                }
            } catch (_: GetCredentialCancellationException) {
                _authState.value = "Bạn đã hủy đăng nhập Google"
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google sign-in failed", e)
                _authState.value = "Không thể đăng nhập Google: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
        }
    }

    fun registerUser(context: Context, fullName: String, phoneNumber: String, email: String, password: String, confirmPass: String, role: String) {
        if (fullName.isEmpty() || phoneNumber.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPass.isEmpty()) {
            _authState.value = "Vui lòng nhập đầy đủ thông tin!"
            return
        }
        if (password != confirmPass) {
            _authState.value = "Mật khẩu nhập lại không khớp!"
            return
        }

        _authState.value = "Đợi xí, đang xử lý..."
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null){
                        viewModelScope.launch {
                            try {
                                val syncedRole = syncUserProfile(fullName, phoneNumber, role)
                                // ✅ LƯU ROLE VÀO MÁY NGAY KHI ĐĂNG KÝ
                                val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                sharedPrefs.edit()
                                    .putString("user_role", syncedRole)
                                    .putString("firebase_uid", uid)
                                    .apply()
                                _authState.value = "Đăng ký thành công!"
                            } catch (e: Exception) {
                                _authState.value = "Tạo tài khoản thành công nhưng lỗi database: ${e.message}."
                            }
                        }
                    }
                } else {
                    val error = task.exception
                    val firebaseError = error as? com.google.firebase.auth.FirebaseAuthException
                    val alreadyInUse = error is com.google.firebase.auth.FirebaseAuthUserCollisionException ||
                        firebaseError?.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" ||
                        error?.localizedMessage?.contains("email address is already in use", ignoreCase = true) == true
                    if (alreadyInUse) {
                        // Firebase Auth may already contain the identity even when its MySQL profile is missing.
                        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { signIn ->
                            if (signIn.isSuccessful) {
                                viewModelScope.launch {
                                    try {
                                        val syncedRole = syncUserProfile(fullName, phoneNumber, role)
                                        triggerLoginSuccessByRole(context, syncedRole)
                                    } catch (e: Exception) {
                                        _authState.value = "Đăng nhập được nhưng chưa đồng bộ hồ sơ MySQL: ${e.message}"
                                    }
                                }
                            } else {
                                _authState.value = "Email đã có trong Firebase nhưng mật khẩu vừa nhập không đăng nhập được. Hãy dùng mật khẩu cũ hoặc phương thức đã đăng ký (ví dụ Google)."
                            }
                        }
                    } else {
                        _authState.value = "Không thể tạo tài khoản: ${error?.localizedMessage ?: "lỗi không xác định"}"
                    }
                }
            }
    }

    fun loginUser(context: Context, email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _authState.value = "Vui lòng không để trống Email/Password!"
            return
        }

        _authState.value = "Loading..."
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        viewModelScope.launch {
                            try {
                                val role = syncUserProfile()
                                if (role == "restaurant") logLogin(uid)
                                triggerLoginSuccessByRole(context, role)
                            } catch (e: Exception) {
                                _authState.value = "Lỗi kết nối phân quyền: ${e.message}"
                            }
                        }
                    }
                } else {
                    _authState.value = "Sai tài khoản hoặc mật khẩu!"
                }
            }
    }

    private fun logLogin(uid: String) {
        activityLogRepository.logActivity(
            restaurantId = uid,
            type = "login",
            title = "Đăng nhập hệ thống",
            description = "Phiên làm việc mới đã bắt đầu",
            details = "Thiết bị: Android Device • Đăng nhập thành công"
        )
    }

    private fun triggerLoginSuccessByRole(context: Context, role: String) {
        // ✅ LƯU ROLE VÀO MÁY ĐỂ KHI MỞ LẠI APP KHÔNG BỊ NHẦM THÀNH USER
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("user_role", role)
            .putString("firebase_uid", auth.currentUser?.uid)
            .apply()
        FirebasePushService.syncCurrentToken()

        when (role) {
            "admin" -> _authState.value = "Đăng nhập Admin thành công!"
            "restaurant" -> _authState.value = "Đăng nhập Restaurant thành công!"
            "shipper" -> _authState.value = "Đăng nhập Shipper thành công!"
            else -> _authState.value = "Đăng nhập User thành công!"
        }
    }

    fun logoutUser(context: Context) {
        auth.signOut()
        // ✅ XÓA ROLE KHI ĐĂNG XUẤT
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("user_role").remove("firebase_uid").apply()
        _authState.value = "Đã đăng xuất!"
    }

    fun clearAuthState() {
        _authState.value = ""
    }
}
