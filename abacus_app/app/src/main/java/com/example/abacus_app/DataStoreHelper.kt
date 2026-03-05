package com.example.abacus_app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Architecture Layer: Storage
 *
 * Provides a DataStore instance for storing user preferences. Used to persist the device UUID for user identification.
 *
 * Used by: UserLocalDataSource
 *
 * User Story: US 01.07.01 – Be identified by device
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")