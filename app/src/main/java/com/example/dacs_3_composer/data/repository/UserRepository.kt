package com.example.dacs_3_composer.data.repository

import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

class UserRepository {

    private val auth = FirebaseAuth.getInstance()

    suspend fun getCurrentUser(): User {
        val uid = auth.currentUser?.uid ?: throw Exception("Chưa đăng nhập")
        return getUserById(uid)
    }

    suspend fun getUserById(uid: String): User {
        val firebaseUid = auth.currentUser?.uid
        val user = if (uid == firebaseUid) FoodHubClient.get<User>("users", "me")
        else FoodHubClient.get<User>("users", uid)
        return user.copy(uid = user.uid.ifBlank { uid })
    }

    suspend fun updateProfile(user: User) {
        val id = if (user.uid == auth.currentUser?.uid) "me" else user.uid
        FoodHubClient.update("users", id, user)
    }

    suspend fun uploadAvatar(
        imageFile: File,
        uploadPreset: String = "ml_default"
    ): String = withContext(Dispatchers.IO) {
        val cloudName = "dhscw17vq"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                imageFile.name,
                imageFile.asRequestBody("image/*".toMediaType())
            )
            .addFormDataPart("upload_preset", uploadPreset)
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            .post(requestBody)
            .build()

        val client = OkHttpClient()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Cloudinary từ chối: Code ${response.code} - ${response.message}")
            }

            val body = response.body?.string() ?: throw Exception("Phản hồi từ Cloudinary rỗng")
            val json = JSONObject(body)

            return@withContext json.getString("secure_url")
        }
    }
}
