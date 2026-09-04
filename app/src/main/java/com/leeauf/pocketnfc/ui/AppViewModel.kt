package com.leeauf.pocketnfc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leeauf.pocketnfc.data.NfcStore
import com.leeauf.pocketnfc.model.NfcItem
import com.leeauf.pocketnfc.nfc.NdefEncoder
import com.leeauf.pocketnfc.nfc.NfcEmulationController
import com.leeauf.pocketnfc.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = NfcStore(application)
    private val restoredSession = NfcEmulationController.activeSession(application)

    val items: StateFlow<List<NfcItem>> = store.items.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _activeItem = MutableStateFlow(restoredSession?.item)
    val activeItem = _activeItem.asStateFlow()

    private val _readCount = MutableStateFlow(restoredSession?.readCount ?: 0)
    val readCount = _readCount.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun save(item: NfcItem, afterSave: (() -> Unit)? = null) {
        viewModelScope.launch {
            store.save(item)
            afterSave?.invoke()
        }
    }

    fun delete(item: NfcItem) {
        viewModelScope.launch { store.delete(item.id) }
    }

    fun toggleFavorite(item: NfcItem) {
        viewModelScope.launch { store.toggleFavorite(item.id) }
    }

    fun duplicate(item: NfcItem) {
        val copy = item.copy(
            id = UUID.randomUUID().toString(),
            title = "${item.title} (copie)",
            favorite = false,
            createdAt = System.currentTimeMillis(),
            lastUsedAt = null
        )
        save(copy)
        _message.value = "Copie ajoutée"
    }

    fun emulate(item: NfcItem) {
        runCatching {
            val timestamp = System.currentTimeMillis()
            val used = item.copy(lastUsedAt = timestamp)
            NfcEmulationController.activate(getApplication(), used, NdefEncoder.encode(used))
            _activeItem.value = used
            _readCount.value = 0
            viewModelScope.launch { store.save(used) }
        }.onFailure { _message.value = it.message ?: "Impossible d’émuler cette URL" }
    }

    fun saveAndEmulate(item: NfcItem) = emulate(item)

    fun stopEmulation() {
        NfcEmulationController.stop(getApplication())
        _activeItem.value = null
        _readCount.value = 0
    }

    fun refreshActiveSession() {
        val session = NfcEmulationController.activeSession(getApplication())
        _activeItem.value = session?.item
        _readCount.value = session?.readCount ?: 0
    }

    fun handleSharedText(text: String) {
        val url = UrlUtils.extract(text)
        if (url == null) {
            _message.value = "Le contenu partagé ne contient pas d’URL web valide"
            return
        }
        emulate(NfcItem(title = UrlUtils.defaultTitle(url), url = url))
    }

    fun notifyRead(reportedCount: Int) {
        val count = reportedCount.takeIf { it > 0 }
            ?: NfcEmulationController.activeSession(getApplication())?.readCount
            ?: (_readCount.value + 1)
        _readCount.value = count
        _message.value = if (count == 1) "URL lue" else "URL lue $count fois"
        _activeItem.value?.let { current ->
            val used = current.copy(lastUsedAt = System.currentTimeMillis())
            _activeItem.value = used
            viewModelScope.launch { store.save(used) }
        }
    }

    fun showMessage(value: String) {
        _message.value = value
    }

    fun consumeMessage() {
        _message.value = null
    }
}
