package com.leeauf.pocketnfc.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leeauf.pocketnfc.model.NfcItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.nfcDataStore by preferencesDataStore(name = "nfc_items")

class NfcStore(private val context: Context) {
    private val itemsKey = stringPreferencesKey("items_json")

    val items: Flow<List<NfcItem>> = context.nfcDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> decode(preferences[itemsKey]) }

    suspend fun save(item: NfcItem) = update { current ->
        val existing = current.indexOfFirst { it.id == item.id }
        val updated = if (existing >= 0) current.toMutableList().apply { set(existing, item) }
        else (current + item).toMutableList()
        trimHistory(updated)
    }

    suspend fun delete(id: String) = update { it.filterNot { item -> item.id == id } }

    suspend fun toggleFavorite(id: String) = update { items ->
        items.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }
    }

    suspend fun markUsed(id: String, timestamp: Long = System.currentTimeMillis()) = update { items ->
        items.map { if (it.id == id) it.copy(lastUsedAt = timestamp) else it }
    }

    private suspend fun update(transform: (List<NfcItem>) -> List<NfcItem>) {
        context.nfcDataStore.edit { preferences ->
            preferences[itemsKey] = encode(transform(decode(preferences[itemsKey])))
        }
    }

    private fun trimHistory(items: List<NfcItem>): List<NfcItem> {
        val favorites = items.filter { it.favorite }
        val regular = items.filterNot { it.favorite }
            .sortedByDescending { it.lastUsedAt ?: it.createdAt }
            .take(MAX_REGULAR_ITEMS)
        return favorites + regular
    }

    private fun encode(items: List<NfcItem>): String = JSONArray().apply {
        items.forEach { put(it.toJson()) }
    }.toString()

    private fun decode(raw: String?): List<NfcItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching { NfcItem.fromJson(array.getJSONObject(index)) }.getOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_REGULAR_ITEMS = 100
    }
}
