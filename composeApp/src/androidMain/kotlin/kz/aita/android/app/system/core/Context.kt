package kz.aita.android.app.system.core

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.tokensDataStore by preferencesDataStore(name = "store_tokens")