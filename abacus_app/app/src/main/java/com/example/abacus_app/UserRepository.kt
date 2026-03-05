package com.example.abacus_app

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UserRepository(
    private val localDataSource: UserLocalDataSource,
    private val firestore: FirebaseFirestore
) {

    // called by Kotlin / suspend context
    suspend fun initializeUser() {
        var uuid = localDataSource.getUUID()
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            localDataSource.saveUUID(uuid)
        }
        // sign in anonymously
        FirebaseAuth.getInstance().signInAnonymously().await()
        // create Firestore doc if not exists
        val doc = firestore.collection("users").document(uuid).get().await()
        if (!doc.exists()) {
            firestore.collection("users").document(uuid).set(
                mapOf("deviceId" to uuid, "createdAt" to Timestamp.now())
            ).await()
        }
    }

    // called by Java (MainActivity.java)
    fun initializeUserAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            initializeUser()
        }
    }
}