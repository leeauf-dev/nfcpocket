package dev.leeauf.nfcpocket.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.leeauf.nfcpocket.data.NfcStore
import dev.leeauf.nfcpocket.model.NfcItem
import dev.leeauf.nfcpocket.nfc.NdefEncoder
import dev.leeauf.nfcpocket.nfc.NfcEmulationController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = NfcStore(application)
    val items: StateFlow<List<NfcItem>> = store.items.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _activeItem = MutableStateFlow<NfcItem?>(null)
    val activeItem = _activeItem.asStateFlow()

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

    fun emulate(item: NfcItem) {
        runCatching {
            val timestamp = System.currentTimeMillis()
            val used = item.copy(lastUsedAt = timestamp)
            NfcEmulationController.activate(getApplication(), NdefEncoder.encode(used))
            _activeItem.value = used
            viewModelScope.launch { store.save(used) }
        }.onFailure { _message.value = it.message ?: "Impossible d’émuler ce contenu" }
    }

    fun saveAndEmulate(item: NfcItem) = emulate(item)

    fun stopEmulation() {
        NfcEmulationController.stop(getApplication())
        _activeItem.value = null
    }

    fun handleSharedText(text: String) {
        val url = URL_PATTERN.find(text.trim())?.value?.trimEnd('.', ',', ';', ')')
        if (url == null) {
            _message.value = "Le contenu partagé ne contient pas d’URL http(s)"
            return
        }
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()
        val item = NfcItem(title = host?.removePrefix("www.") ?: "Lien partagé", url = url)
        emulate(item)
    }

    fun notifyRead() {
        _message.value = "Contenu lu"
    }

    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        val URL_PATTERN = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    }
}
