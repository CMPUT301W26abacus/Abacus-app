package com.example.abacus_app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Architecture Layer: Local Data Source
 *
 * Local data source responsible for storing and retrieving user-related data using the Android DataStore Preferences API.
 * This class manages the persistence of the device UUID, which is used to uniquely identify a user across application sessions without requiring login credentials.
 *
 * Used by: UserRepository
 * Uses: DataStore (Context.dataStore)
 *
 * User Story: US 01.07.01 – Be identified by device
 * Sprint: Sprint 1
 * Backlog: https://github.com/orgs/CMPUT301W26abacus/projects/5/views/5?pane=issue&itemId=156435080&issue=CMPUT301W26abacus|Abacus|29
 *
 * @param dataStore The DataStore instance to use.
 */
class UserLocalDataSource(private val dataStore: DataStore<Preferences>) {

    private val UUID_KEY = stringPreferencesKey("device_uuid")

    /**
     * Retrieves the device UUID from the DataStore.
     *
     * @return The device UUID as a String, or null if it doesn't exist.
     */
    suspend fun getUUID(): String? {
        return dataStore.data.map { it[UUID_KEY] }.firstOrNull()
    }

    /**
     * Saves the device UUID to the DataStore.
     *
     * @param uuid The device UUID to save.
     */
    suspend fun saveUUID(uuid: String) {
        dataStore.edit { it[UUID_KEY] = uuid }
    }

}