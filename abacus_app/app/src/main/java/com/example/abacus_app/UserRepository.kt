package com.example.abacus_app

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 *  Architecture Layer: Repository
 *
 * Coordinates user initialization and synchronization between local storage and Firebase services.
 *
 * Responsibilities:
 * - Retrieve or generate the device UUID
 * - Persist UUID locally
 * - Authenticate user anonymously with Firebase
 * - Ensure a Firestore user document exists
 *
 * Uses:
 * - UserLocalDataSource (local persistence)
 * - FirebaseAuth (authentication)
 * - FirebaseFirestore (remote storage)
 *
 * User Story: US 01.07.01 – Be identified by device
 * Sprint: Sprint 1
 * Backlog: https://github.com/orgs/CMPUT301W26abacus/projects/5/views/5?pane=issue&itemId=156435080&issue=CMPUT301W26abacus|Abacus|29
 *
 * @param localDataSource The local data source for user data.
 * @param firestore The Firebase Firestore instance.
 */
class UserRepository(private val localDataSource: UserLocalDataSource, private val firestore: FirebaseFirestore) {

    // called by Kotlin / suspend context
    /**
     * Initializes the user by ensuring a device UUID is stored locally and a corresponding user document exists in Firestore.
     * If the device UUID doesn't exist, it generates a new one and saves it.
     * It then signs in anonymously with Firebase Auth and creates a Firestore document if it doesn't already exist.
     */
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
    /**
     * Asynchronously initializes the user by ensuring a device UUID is stored locally and a corresponding user document exists in Firestore.
     */

    fun initializeUserAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            initializeUser()
        }
    }
}