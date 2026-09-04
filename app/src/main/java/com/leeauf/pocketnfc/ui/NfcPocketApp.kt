package com.leeauf.pocketnfc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leeauf.pocketnfc.R
import com.leeauf.pocketnfc.model.NfcItem
import com.leeauf.pocketnfc.util.UrlUtils
import kotlinx.coroutines.delay
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
    val allItems by viewModel.items.collectAsStateWithLifecycle()
    val activeItem by viewModel.activeItem.collectAsStateWithLifecycle()
    val readCount by viewModel.readCount.collectAsStateWithLifecycle()
    val sessionStartedAt by viewModel.sessionStartedAt.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftUrl by rememberSaveable { mutableStateOf("") }
    val editingItem = allItems.firstOrNull { it.id == editingId }
    val appPreferences = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    var showOnboarding by rememberSaveable {
        mutableStateOf(!appPreferences.getBoolean("onboarding_complete", false))
    }

    fun openEditor(item: NfcItem? = null, initialUrl: String = "") {
        editingId = item?.id
        draftUrl = initialUrl
        screen = Screen.EDITOR
    }

    fun pasteIntoEditor() {
        val pasted = clipboardText(context)
        val url = pasted?.let(UrlUtils::extract)
        if (url == null) {
            viewModel.showMessage("Le presse-papiers ne contient pas d’URL valide")
        } else {
            openEditor(initialUrl = url)
        }
    }

    fun finishOnboarding() {
        appPreferences.edit().putBoolean("onboarding_complete", true).apply()
        showOnboarding = false
    }

    LaunchedEffect(shareGeneration) {
        sharedText?.let {
            viewModel.handleSharedText(it)
            onShareHandled()
        }
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
                sessionStartedAt = sessionStartedAt,
                onOpenNfcSettings = onOpenNfcSettings,
                onCopy = {
                    copyUrl(context, activeItem!!.url)
                    viewModel.showMessage("URL copiée")
                },
                onShare = { shareUrl(context, activeItem!!.url, viewModel::showMessage) },
                onOpen = { openUrl(context, activeItem!!.url, viewModel::showMessage) },
                onRestart = viewModel::restartEmulation,
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
                onPaste = ::pasteIntoEditor,
                onEdit = { openEditor(it) },
                onDelete = viewModel::delete,
                onFavorite = viewModel::toggleFavorite,
                onEmulate = viewModel::emulate,
                onCopy = {
                    copyUrl(context, it.url)
                    viewModel.showMessage("URL copiée")
                },
                onShare = { shareUrl(context, it.url, viewModel::showMessage) },
                onOpen = { openUrl(context, it.url, viewModel::showMessage) },
                onDuplicate = viewModel::duplicate
            )
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        )
    }

    if (showOnboarding && activeItem == null && shareGeneration == 0) {
        OnboardingDialog(onComplete = ::finishOnboarding)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    items: List<NfcItem>,
    nfcStatus: NfcStatus,
    onOpenNfcSettings: () -> Unit,
    onCreate: () -> Unit,
    onPaste: () -> Unit,
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
    val lastItem = sorted.firstOrNull()
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
                text = { Text("Ajouter") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NfcStatusCard(nfcStatus, onOpenNfcSettings)
                    QuickStartCard(onCreate = onCreate, onPaste = onPaste)
                }
            }
            if (lastItem != null) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle("Dernière URL")
                        Spacer(Modifier.height(8.dp))
                        LastUsedCard(lastItem, onEmulate)
                    }
                }
            }
            if (items.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        label = { Text("Rechercher") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (search.isNotEmpty()) {
                            { IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Effacer la recherche") } }
                        } else null,
                        singleLine = true
                    )
                }
            }
            if (favorites.isNotEmpty()) {
                item { SectionTitle("Favoris", Modifier.padding(horizontal = 16.dp)) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(favorites, key = { it.id }) { item ->
                            FavoriteCard(
                                item = item,
                                onEdit = onEdit,
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
            item { SectionTitle("Récents", Modifier.padding(horizontal = 16.dp)) }
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
            title = { Text("Supprimer ce lien ?") },
            text = { Text("« ${item.title} » sera retiré de NFC Pocket.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(item)
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun QuickStartCard(onCreate: () -> Unit, onPaste: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        painterResource(R.drawable.ic_nfc_material),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(12.dp).size(30.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Émuler une URL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Créez un lien ou collez-le directement.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Nouvelle URL")
            }
            OutlinedButton(onClick = onPaste, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Coller depuis le presse-papiers")
            }
        }
    }
}

@Composable
private fun LastUsedCard(item: NfcItem, onEmulate: (NfcItem) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEmulate(item) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    UrlUtils.defaultTitle(item.url),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(onClick = { onEmulate(item) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Émuler")
            }
        }
    }
}

@Composable
private fun NfcStatusCard(status: NfcStatus, onOpenSettings: () -> Unit) {
    val available = status == NfcStatus.AVAILABLE
    val label = when (status) {
        NfcStatus.AVAILABLE -> "NFC prêt"
        NfcStatus.DISABLED -> "NFC désactivé"
        NfcStatus.HCE_UNSUPPORTED -> "Émulation NFC non compatible"
        NfcStatus.UNAVAILABLE -> "NFC indisponible"
    }
    val detail = when (status) {
        NfcStatus.AVAILABLE -> "Votre téléphone peut partager une URL"
        NfcStatus.DISABLED -> "Activez le NFC avant de rapprocher les téléphones"
        NfcStatus.HCE_UNSUPPORTED -> "Ce modèle ne prend pas en charge Android HCE"
        NfcStatus.UNAVAILABLE -> "Aucun matériel NFC n’a été détecté"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (available) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(12.dp).background(
                    if (available) Color(0xFF16865A) else MaterialTheme.colorScheme.error,
                    CircleShape
                )
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            if (status == NfcStatus.DISABLED) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Ouvrir les paramètres NFC")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FavoriteCard(
    item: NfcItem,
    onEdit: (NfcItem) -> Unit,
    onFavorite: (NfcItem) -> Unit,
    onEmulate: (NfcItem) -> Unit,
    onCopy: (NfcItem) -> Unit,
    onShare: (NfcItem) -> Unit,
    onOpen: (NfcItem) -> Unit,
    onDuplicate: (NfcItem) -> Unit
) {
    Card(
        modifier = Modifier.width(236.dp).clickable { onEmulate(item) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300))
                Spacer(Modifier.width(8.dp))
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ItemMenu(item, onCopy, onShare, onOpen, onDuplicate)
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onEdit(item) }) { Icon(Icons.Default.Edit, "Modifier") }
                IconButton(onClick = { onFavorite(item) }) { Icon(Icons.Default.Close, "Retirer des favoris") }
                IconButton(onClick = { onEmulate(item) }) { Icon(Icons.Default.PlayArrow, "Émuler") }
            }
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
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text("URL", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.url, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onFavorite(item) }) {
                    Icon(Icons.Default.Star, "Ajouter aux favoris", tint = MaterialTheme.colorScheme.outline)
                }
                ItemMenu(item, onCopy, onShare, onOpen, onDuplicate)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.lastUsedAt?.let { "Utilisé ${formatDate(it)}" } ?: "Jamais utilisé",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onEdit(item) }) { Icon(Icons.Default.Edit, "Modifier") }
                IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.Delete, "Supprimer") }
                FilledTonalButton(onClick = { onEmulate(item) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Émuler")
                }
            }
        }
    }
}

@Composable
private fun ItemMenu(
    item: NfcItem,
    onCopy: (NfcItem) -> Unit,
    onShare: (NfcItem) -> Unit,
    onOpen: (NfcItem) -> Unit,
    onDuplicate: (NfcItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Plus d’actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Copier l’URL") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = { expanded = false; onCopy(item) }
            )
            DropdownMenuItem(
                text = { Text("Partager") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = { expanded = false; onShare(item) }
            )
            DropdownMenuItem(
                text = { Text("Ouvrir") },
                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                onClick = { expanded = false; onOpen(item) }
            )
            DropdownMenuItem(
                text = { Text("Dupliquer") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { expanded = false; onDuplicate(item) }
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
                    Text("Supprimer", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
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
            Text(if (searching) "Aucun résultat" else "Aucun lien", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (searching) "Essayez un autre nom ou domaine."
                else "Ajoutez une URL ou partagez une page depuis votre navigateur.",
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
    val normalizedPreview = UrlUtils.normalize(url)

    fun paste() {
        val pasted = onReadClipboard()
        val extracted = pasted?.let(UrlUtils::extract)
        if (extracted == null) {
            error = "Le presse-papiers ne contient pas d’URL web valide."
        } else {
            url = extracted
            if (title.isBlank()) title = UrlUtils.defaultTitle(extracted)
            error = null
        }
    }

    fun buildItem(): NfcItem? {
        val cleanUrl = UrlUtils.normalize(url)
        if (cleanUrl == null) {
            error = "Saisissez une adresse web valide, par exemple example.com."
            return null
        }
        url = cleanUrl
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
                title = { Text(if (original == null) "Nouvelle URL" else "Modifier le lien") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Collez une adresse ou saisissez-la. NFC Pocket ajoutera https:// si nécessaire.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = ::paste, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Coller une URL")
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL") },
                placeholder = { Text("example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                singleLine = true
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nom (optionnel)") },
                placeholder = { Text(normalizedPreview?.let(UrlUtils::defaultTitle) ?: "Mon lien") },
                singleLine = true
            )
            if (normalizedPreview != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("URL valide", fontWeight = FontWeight.SemiBold)
                            Text(normalizedPreview, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { buildItem()?.let(onSave) },
                    modifier = Modifier.weight(1f).height(54.dp)
                ) { Text("Enregistrer") }
                Button(
                    onClick = { buildItem()?.let(onEmulate) },
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Émuler")
                }
            }
        }
    }
}

@Composable
private fun EmulationScreen(
    item: NfcItem,
    nfcStatus: NfcStatus,
    readCount: Int,
    sessionStartedAt: Long,
    onOpenNfcSettings: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit
) {
    val available = nfcStatus == NfcStatus.AVAILABLE
    var showHelp by remember(sessionStartedAt) { mutableStateOf(false) }
    LaunchedEffect(sessionStartedAt, readCount, available) {
        showHelp = false
        if (available && readCount == 0) {
            delay(10_000)
            showHelp = true
        }
    }
    val pulse = rememberInfiniteTransition(label = "NFC active")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "NFC pulse scale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "NFC pulse alpha"
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                if (available) {
                    Surface(
                        modifier = Modifier.size(168.dp).graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }.alpha(pulseAlpha),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {}
                }
                Surface(
                    modifier = Modifier.size(146.dp),
                    shape = CircleShape,
                    color = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nfc_material),
                        contentDescription = "Émulation NFC active",
                        modifier = Modifier.fillMaxSize().padding(34.dp),
                        tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (readCount > 0) "URL lue !" else "Approchez un autre téléphone",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (nfcStatus) {
                    NfcStatus.AVAILABLE -> if (readCount > 0) {
                        if (readCount == 1) "1 lecture détectée" else "$readCount lectures détectées"
                    } else "Prêt à être lu · actif depuis ${formatTime(sessionStartedAt)}"
                    NfcStatus.DISABLED -> "Le NFC doit être activé"
                    NfcStatus.HCE_UNSUPPORTED -> "Ce téléphone ne prend pas en charge HCE"
                    NfcStatus.UNAVAILABLE -> "Aucun matériel NFC détecté"
                },
                color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(readCount > 0) {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16865A))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Contenu lu", fontWeight = FontWeight.Bold)
                            Text("Vous pouvez laisser l’écran ouvert pour un autre téléphone.")
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(18.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(item.url, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.Default.ContentCopy, "Copier", onCopy, Modifier.weight(1f))
                QuickAction(Icons.Default.Share, "Partager", onShare, Modifier.weight(1f))
                QuickAction(Icons.Default.OpenInNew, "Ouvrir", onOpen, Modifier.weight(1f))
            }
            AnimatedVisibility(showHelp && readCount == 0) {
                Card(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Aucune lecture détectée. Déverrouillez l’autre téléphone et déplacez lentement sa zone NFC près du haut ou de l’appareil photo de ce téléphone.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (nfcStatus == NfcStatus.DISABLED) {
                OutlinedButton(onClick = onOpenNfcSettings, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Activer le NFC")
                }
                Spacer(Modifier.height(10.dp))
            }
            if (readCount > 0 && available) {
                OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Replay, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nouvelle session")
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Arrêter")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun OnboardingDialog(onComplete: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    val titles = listOf("Ajoutez une URL", "Lancez l’émulation", "Rapprochez les téléphones")
    val descriptions = listOf(
        "Collez une adresse, saisissez-la ou partagez une page depuis votre navigateur.",
        "Appuyez sur Émuler. NFC Pocket prépare alors un tag NDEF temporaire.",
        "Déverrouillez l’autre téléphone et placez les zones NFC l’une contre l’autre."
    )
    AlertDialog(
        onDismissRequest = onComplete,
        icon = {
            if (step == 1) {
                Icon(painterResource(R.drawable.ic_nfc_material), contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Icon(if (step == 0) Icons.Default.Add else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        },
        title = { Text(titles[step], textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(descriptions[step], textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Text("${step + 1} / 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (step < 2) step++ else onComplete()
            }) { Text(if (step < 2) "Suivant" else "Commencer") }
        },
        dismissButton = {
            TextButton(onClick = onComplete) { Text("Passer") }
        }
    )
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
        context.startActivity(Intent.createChooser(intent, "Partager l’URL"))
    }.onFailure { onError("Aucune application disponible pour partager cette URL") }
}

private fun openUrl(context: Context, url: String, onError: (String) -> Unit) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { onError("Aucun navigateur disponible") }
}

private fun formatDate(timestamp: Long): String = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.FRENCH)
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

private fun formatTime(timestamp: Long): String = if (timestamp > 0) {
    DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
} else "maintenant"
