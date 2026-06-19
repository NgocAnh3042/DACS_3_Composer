package com.example.dacs_3_composer.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.data.repository.ActivityLogRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val activityLogRepository = ActivityLogRepository()

    private val _authState = MutableStateFlow<String>("")
    val authState: StateFlow<String> = _authState

    private val webClientId = "963036038937-oc3omdisdodgmdfoqn24ko5keeat5tsi.apps.googleusercontent.com"
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun loginWithGoogle(context: Context) {
        _authState.value = "Đang kết nối với Google..."
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential

                if (credential is GoogleIdTokenCredential) {
                    val googleIdToken = credential.idToken
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)

                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid
                                val email = auth.currentUser?.email ?: ""

                                if (uid != null) {
                                    db.collection("users").document(uid).get()
                                        .addOnSuccessListener { document ->
                                            if (document != null && document.exists()) {
                                                val role = document.getString("role") ?: "user"
                                                if (role == "restaurant") {
                                                    logLogin(uid)
                                                }
                                                triggerLoginSuccessByRole(context, role)
                                            } else {
                                                val userMap = hashMapOf(
                                                    "email" to email,
                                                    "role" to "user"
                                                )
                                                db.collection("users").document(uid).set(userMap)
                                                    .addOnSuccessListener {
                                                        triggerLoginSuccessByRole(context, "user")
                                                    }
                                                    .addOnFailureListener { e ->
                                                        _authState.value = "Lỗi tạo thông tin dữ liệu Google: ${e.message}"
                                                    }
                                            }
                                        }
                                }
                            } else {
                                _authState.value = "Lỗi Firebase: ${task.exception?.message}"
                            }
                        }
                }
            } catch (e: Exception) {
                _authState.value = "Đã hủy đăng nhập Google"
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
                        val userMap = hashMapOf(
                            "fullName" to fullName,
                            "phoneNumber" to phoneNumber,
                            "email" to email,
                            "role" to role
                        )
                        db.collection("users").document(uid).set(userMap)
                            .addOnSuccessListener {
                                // ✅ LƯU ROLE VÀO MÁY NGAY KHI ĐĂNG KÝ
                                val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                sharedPrefs.edit().putString("user_role", role).apply()

                                _authState.value = "Đăng ký thành công!"
                            }.addOnFailureListener { e->
                                _authState.value = "Tạo tài khoản thành công nhưng lỗi database: ${e.message}."
                            }
                    }
                } else {
                    _authState.value = "Lỗi: ${task.exception?.message}"
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
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val role = document.getString("role") ?: "user"
                                    if (role == "restaurant") {
                                        logLogin(uid)
                                    }
                                    triggerLoginSuccessByRole(context, role)
                                } else {
                                    triggerLoginSuccessByRole(context, "user")
                                }
                            }
                            .addOnFailureListener { e ->
                                _authState.value = "Lỗi kết nối phân quyền: ${e.message}"
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
        sharedPrefs.edit().putString("user_role", role).apply()

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
        sharedPrefs.edit().remove("user_role").apply()
        _authState.value = "Đã đăng xuất!"
    }

    fun clearAuthState() {
        _authState.value = ""
    }
}
