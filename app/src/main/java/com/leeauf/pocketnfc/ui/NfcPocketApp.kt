package com.leeauf.pocketnfc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leeauf.pocketnfc.R
import com.leeauf.pocketnfc.model.NfcItem
import com.leeauf.pocketnfc.util.UrlUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class NfcStatus { AVAILABLE, DISABLED, HCE_UNSUPPORTED, UNAVAILABLE }

private enum class Screen { HOME, EDITOR }

@Composable
fun NfcPocketApp(
    viewModel: AppViewModel,
    nfcStatus: NfcStatus,
    sharedText: String?,
    shareGeneration: Int,
    onShareHandled: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onPreferredService: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val allItems by viewModel.items.collectAsStateWithLifecycle()
    val activeItem by viewModel.activeItem.collectAsStateWithLifecycle()
    val readCount by viewModel.readCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftUrl by rememberSaveable { mutableStateOf("") }
    var clipboardSuggestion by rememberSaveable { mutableStateOf<String?>(null) }
    var ignoredClipboardUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val editingItem = allItems.firstOrNull { it.id == editingId }
    fun openEditor(item: NfcItem? = null, initialUrl: String = "") {
        editingId = item?.id
        draftUrl = initialUrl
        screen = Screen.EDITOR
    }

    LaunchedEffect(shareGeneration) {
        sharedText?.let {
            clipboardSuggestion = null
            viewModel.handleSharedText(it)
            onShareHandled()
        }
    }
    DisposableEffect(lifecycleOwner, ignoredClipboardUrl) {
        fun inspectClipboard() {
            val url = clipboardText(context)?.let(UrlUtils::extract)
            clipboardSuggestion = url?.takeUnless { it == ignoredClipboardUrl }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) inspectClipboard()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            inspectClipboard()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(activeItem) { onPreferredService(activeItem != null) }
    DisposableEffect(Unit) { onDispose { onPreferredService(false) } }

    BackHandler(enabled = activeItem != null || screen != Screen.HOME) {
        if (activeItem != null) viewModel.stopEmulation() else screen = Screen.HOME
    }

    Box(Modifier.fillMaxSize()) {
        when {
            activeItem != null -> EmulationScreen(
                item = activeItem!!,
                nfcStatus = nfcStatus,
                readCount = readCount,
                onOpenNfcSettings = onOpenNfcSettings,
                onCopy = {
                    copyUrl(context, activeItem!!.url)
                    ignoredClipboardUrl = activeItem!!.url
                    clipboardSuggestion = null
                    viewModel.showMessage("URL copied")
                },
                onStop = viewModel::stopEmulation
            )
            screen == Screen.EDITOR -> EditorScreen(
                original = editingItem,
                initialUrl = draftUrl,
                onBack = { screen = Screen.HOME },
                onReadClipboard = { clipboardText(context) },
                onSave = { viewModel.save(it) { screen = Screen.HOME } },
                onEmulate = viewModel::saveAndEmulate
            )
            else -> HomeScreen(
                items = allItems,
                nfcStatus = nfcStatus,
                onOpenNfcSettings = onOpenNfcSettings,
                onCreate = { openEditor() },
                onEdit = { openEditor(it) },
                onDelete = viewModel::delete,
                onFavorite = viewModel::toggleFavorite,
                onEmulate = viewModel::emulate,
                onCopy = {
                    copyUrl(context, it.url)
                    ignoredClipboardUrl = it.url
                    clipboardSuggestion = null
                    viewModel.showMessage("URL copied")
                },
                onShare = { shareUrl(context, it.url, viewModel::showMessage) },
                onOpen = { openUrl(context, it.url, viewModel::showMessage) },
                onDuplicate = viewModel::duplicate
            )
        }
        if (clipboardSuggestion != null && screen == Screen.HOME && activeItem == null) {
            val suggestedUrl = clipboardSuggestion!!
            AlertDialog(
                onDismissRequest = {
                    ignoredClipboardUrl = suggestedUrl
                    clipboardSuggestion = null
                },
                title = { Text("Link in clipboard") },
                text = {
                    Text(
                        suggestedUrl,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        ignoredClipboardUrl = suggestedUrl
                        clipboardSuggestion = null
                        openEditor(initialUrl = suggestedUrl)
                    }) { Text("Paste link") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        ignoredClipboardUrl = suggestedUrl
                        clipboardSuggestion = null
                    }) { Text("Not now") }
                }
            )
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    items: List<NfcItem>,
    nfcStatus: NfcStatus,
    onOpenNfcSettings: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (NfcItem) -> Unit,
    onDelete: (NfcItem) -> Unit,
    onFavorite: (NfcItem) -> Unit,
    onEmulate: (NfcItem) -> Unit,
    onCopy: (NfcItem) -> Unit,
    onShare: (NfcItem) -> Unit,
    onOpen: (NfcItem) -> Unit,
    onDuplicate: (NfcItem) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<NfcItem?>(null) }
    val sorted = items.sortedByDescending { it.lastUsedAt ?: it.createdAt }
    val matching = sorted.filter {
        search.isBlank() || it.title.contains(search, true) || it.url.contains(search, true)
    }
    val favorites = matching.filter { it.favorite }
    val recent = matching.filterNot { it.favorite }

    Scaffold(
        topBar = { TopAppBar(title = { Text("NFC Pocket", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    NfcStatusCard(nfcStatus, onOpenNfcSettings)
                }
            }
            if (items.size >= 4) {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (search.isNotEmpty()) {
                            { IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear") } }
                        } else null,
                        singleLine = true
                    )
                }
            }
            if (favorites.isNotEmpty()) {
                item {
                    SectionTitle(
                        "Favorites",
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
                items(favorites, key = { "favorite-${it.id}" }) { item ->
                    FavoriteCard(
                        item = item,
                        onEdit = onEdit,
                        onDelete = { pendingDelete = it },
                        onFavorite = onFavorite,
                        onEmulate = onEmulate,
                        onCopy = onCopy,
                        onShare = onShare,
                        onOpen = onOpen,
                        onDuplicate = onDuplicate,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            item { SectionTitle("History", Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            if (recent.isEmpty()) {
                item {
                    EmptyHistory(
                        searching = search.isNotBlank(),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(recent, key = { it.id }) { item ->
                    SwipeableLinkCard(
                        item = item,
                        onRequestDelete = { pendingDelete = item },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        LinkCard(
                            item = item,
                            onEdit = onEdit,
                            onDelete = { pendingDelete = it },
                            onFavorite = onFavorite,
                            onEmulate = onEmulate,
                            onCopy = onCopy,
                            onShare = onShare,
                            onOpen = onOpen,
                            onDuplicate = onDuplicate
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this link?") },
            text = { Text("“${item.title}” will be removed from NFC Pocket.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(item)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NfcStatusCard(status: NfcStatus, onOpenSettings: () -> Unit) {
    val available = status == NfcStatus.AVAILABLE
    val label = when (status) {
        NfcStatus.AVAILABLE -> "NFC ready"
        NfcStatus.DISABLED -> "NFC disabled"
        NfcStatus.HCE_UNSUPPORTED -> "HCE not supported"
        NfcStatus.UNAVAILABLE -> "NFC unavailable"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (available) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.errorContainer
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).background(
                    if (available) Color(0xFF16865A) else MaterialTheme.colorScheme.error,
                    CircleShape
                )
            )
            Spacer(Modifier.width(10.dp))
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (status == NfcStatus.DISABLED) {
                TextButton(onClick = onOpenSettings) { Text("Enable") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FavoriteCard(
    item: NfcItem,
    onEdit: (NfcItem) -> Unit,
    onDelete: (NfcItem) -> Unit,
    onFavorite: (NfcItem) -> Unit,
    onEmulate: (NfcItem) -> Unit,
    onCopy: (NfcItem) -> Unit,
    onShare: (NfcItem) -> Unit,
    onOpen: (NfcItem) -> Unit,
    onDuplicate: (NfcItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onEmulate(item) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Favicon(item.url, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                item.title,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { onFavorite(item) }) {
                Icon(Icons.Default.Star, contentDescription = "Remove from favorites", tint = Color(0xFFFFB300))
            }
            ItemMenu(item, onEdit, onDelete, onCopy, onShare, onOpen, onDuplicate)
        }
    }
}

@Composable
private fun LinkCard(
    item: NfcItem,
    onEdit: (NfcItem) -> Unit,
    onDelete: (NfcItem) -> Unit,
    onFavorite: (NfcItem) -> Unit,
    onEmulate: (NfcItem) -> Unit,
    onCopy: (NfcItem) -> Unit,
    onShare: (NfcItem) -> Unit,
    onOpen: (NfcItem) -> Unit,
    onDuplicate: (NfcItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEmulate(item) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Favicon(item.url)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    UrlUtils.defaultTitle(item.url),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.lastUsedAt?.let(::formatDate) ?: "Never used",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = { onFavorite(item) }) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Add to favorites",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            ItemMenu(item, onEdit, onDelete, onCopy, onShare, onOpen, onDuplicate)
        }
    }
}

@Composable
private fun ItemMenu(
    item: NfcItem,
    onEdit: (NfcItem) -> Unit,
    onDelete: (NfcItem) -> Unit,
    onCopy: (NfcItem) -> Unit,
    onShare: (NfcItem) -> Unit,
    onOpen: (NfcItem) -> Unit,
    onDuplicate: (NfcItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit(item) }
            )
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null) },
                onClick = { expanded = false; onCopy(item) }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = { expanded = false; onShare(item) }
            )
            DropdownMenuItem(
                text = { Text("Open") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                onClick = { expanded = false; onOpen(item) }
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { expanded = false; onDuplicate(item) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { expanded = false; onDelete(item) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableLinkCard(
    item: NfcItem,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRequestDelete()
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
        content = { content() }
    )
}

@Composable
private fun EmptyHistory(searching: Boolean, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (searching) "No results" else "No links yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (searching) "Try another name or domain."
                else "Add a URL or share a page from your browser.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    original: NfcItem?,
    initialUrl: String,
    onBack: () -> Unit,
    onReadClipboard: () -> String?,
    onSave: (NfcItem) -> Unit,
    onEmulate: (NfcItem) -> Unit
) {
    var title by rememberSaveable(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var url by rememberSaveable(original?.id, initialUrl) { mutableStateOf(original?.url ?: initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }

    fun paste() {
        val extracted = onReadClipboard()?.let(UrlUtils::extract)
        if (extracted == null) {
            error = "No valid URL found in the clipboard."
        } else {
            url = extracted
            if (title.isBlank()) title = UrlUtils.defaultTitle(extracted)
            error = null
        }
    }

    fun buildItem(): NfcItem? {
        val cleanUrl = UrlUtils.normalize(url)
        if (cleanUrl == null) {
            error = "Invalid web address."
            return null
        }
        return NfcItem(
            id = original?.id ?: UUID.randomUUID().toString(),
            title = title.trim().ifBlank { UrlUtils.defaultTitle(cleanUrl) },
            url = cleanUrl,
            favorite = original?.favorite ?: false,
            createdAt = original?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = original?.lastUsedAt
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "New URL" else "Edit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL") },
                placeholder = { Text("example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(onClick = ::paste) {
                        Icon(painterResource(R.drawable.ic_content_paste), contentDescription = "Paste")
                    }
                },
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                singleLine = true
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name (optional)") },
                singleLine = true
            )
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = { buildItem()?.let(onEmulate) },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Emulate")
            }
            TextButton(
                onClick = { buildItem()?.let(onSave) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Save without emulating") }
        }
    }
}

@Composable
private fun EmulationScreen(
    item: NfcItem,
    nfcStatus: NfcStatus,
    readCount: Int,
    onOpenNfcSettings: () -> Unit,
    onCopy: () -> Unit,
    onStop: () -> Unit
) {
    val available = nfcStatus == NfcStatus.AVAILABLE
    val pulse = rememberInfiniteTransition(label = "NFC active")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "NFC pulse"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "NFC glow"
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                if (available) {
                    Surface(
                        modifier = Modifier.size(158.dp).graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }.alpha(pulseAlpha),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {}
                }
                Surface(
                    modifier = Modifier.size(138.dp),
                    shape = CircleShape,
                    color = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nfc_material),
                        contentDescription = "NFC emulation active",
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (readCount > 0) "Content read" else "Hold another phone nearby",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (nfcStatus) {
                    NfcStatus.AVAILABLE -> when (readCount) {
                        0 -> "Emulation active"
                        1 -> "1 read"
                        else -> "$readCount reads"
                    }
                    NfcStatus.DISABLED -> "NFC disabled"
                    NfcStatus.HCE_UNSUPPORTED -> "HCE not supported"
                    NfcStatus.UNAVAILABLE -> "NFC unavailable"
                },
                color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(24.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Favicon(item.url)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.url,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCopy) {
                        Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy URL")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Unlock the other phone and bring the NFC areas close together.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            if (nfcStatus == NfcStatus.DISABLED) {
                OutlinedButton(onClick = onOpenNfcSettings, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Enable NFC")
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop")
            }
        }
    }
}

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun copyUrl(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
}

private fun shareUrl(context: Context, url: String, onError: (String) -> Unit) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(intent, "Share URL"))
    }.onFailure { onError("No app is available to share this URL") }
}

private fun openUrl(context: Context, url: String, onError: (String) -> Unit) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { onError("No browser is available") }
}

private fun formatDate(timestamp: Long): String = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.ENGLISH)
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
