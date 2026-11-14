package com.example.dacs_3_composer.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

/** Presence is high-frequency session state, so it stays in Realtime Database. */
class PresenceManager(
    private val realtime: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val currentUid get() = auth.currentUser?.uid

    fun updateUserStatus(isOnline: Boolean) {
        val uid = currentUid ?: return
        val status = mapOf<String, Any>("isOnline" to isOnline, "lastSeen" to ServerValue.TIMESTAMP)
        val ref = realtime.getReference("presence").child(uid)
        ref.setValue(status)
        if (isOnline) ref.onDisconnect().setValue(mapOf("isOnline" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }

    fun startTracking() = updateUserStatus(true)
    fun stopTracking() = updateUserStatus(false)
}
