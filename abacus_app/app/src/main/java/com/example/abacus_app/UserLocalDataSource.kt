package com.example.abacus_app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class UserLocalDataSource(private val dataStore: DataStore<Preferences>) {

    val UUID_KEY = stringPreferencesKey("device_uuid")

    suspend fun getUUID(): String? {
        return dataStore.data.map { it[UUID_KEY] }.firstOrNull()
    }

    suspend fun saveUUID(uuid: String) {
        dataStore.edit { it[UUID_KEY] = uuid }
    }

}