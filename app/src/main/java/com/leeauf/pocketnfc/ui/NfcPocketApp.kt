package com.leeauf.pocketnfc.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leeauf.pocketnfc.R
import com.leeauf.pocketnfc.model.NfcItem
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
    val allItems by viewModel.items.collectAsStateWithLifecycle()
    val activeItem by viewModel.activeItem.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingItem = allItems.firstOrNull { it.id == editingId }

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
                activeItem!!,
                nfcStatus,
                onOpenNfcSettings,
                viewModel::stopEmulation
            )
            screen == Screen.EDITOR -> EditorScreen(
                original = editingItem,
                onBack = { screen = Screen.HOME },
                onSave = { viewModel.save(it) { screen = Screen.HOME } },
                onEmulate = viewModel::saveAndEmulate
            )
            else -> HomeScreen(
                items = allItems,
                nfcStatus = nfcStatus,
                onOpenNfcSettings = onOpenNfcSettings,
                onCreate = {
                    editingId = null
                    screen = Screen.EDITOR
                },
                onEdit = {
                    editingId = it.id
                    screen = Screen.EDITOR
                },
                onDelete = viewModel::delete,
                onFavorite = viewModel::toggleFavorite,
                onEmulate = viewModel::emulate
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
    onEmulate: (NfcItem) -> Unit
) {
    val favorites = items.filter { it.favorite }.sortedByDescending { it.lastUsedAt ?: it.createdAt }
    val recent = items.filterNot { it.favorite }.sortedByDescending { it.lastUsedAt ?: it.createdAt }
    var pendingDelete by remember { mutableStateOf<NfcItem?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("NFC Pocket", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Ajouter un lien") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { NfcStatusCard(nfcStatus, onOpenNfcSettings) } }
            if (favorites.isNotEmpty()) {
                item { SectionTitle("Favoris", Modifier.padding(horizontal = 16.dp)) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(favorites, key = { it.id }) { item ->
                            FavoriteCard(item, onEdit, onFavorite, onEmulate)
                        }
                    }
                }
            }
            item { SectionTitle("Récents", Modifier.padding(horizontal = 16.dp)) }
            if (recent.isEmpty()) {
                item {
                    EmptyHistory(Modifier.padding(horizontal = 16.dp))
                }
            } else {
                items(recent, key = { it.id }) { item ->
                    LinkCard(
                        item,
                        onEdit,
                        { pendingDelete = it },
                        onFavorite,
                        onEmulate,
                        Modifier.padding(horizontal = 16.dp)
                    )
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
private fun NfcStatusCard(status: NfcStatus, onOpenSettings: () -> Unit) {
    val available = status == NfcStatus.AVAILABLE
    val label = when (status) {
        NfcStatus.AVAILABLE -> "NFC disponible"
        NfcStatus.DISABLED -> "NFC désactivé"
        NfcStatus.HCE_UNSUPPORTED -> "HCE non supporté"
        NfcStatus.UNAVAILABLE -> "NFC indisponible"
    }
    val detail = when (status) {
        NfcStatus.AVAILABLE -> "Prêt à partager vos liens"
        NfcStatus.DISABLED -> "Activez le NFC pour permettre la lecture"
        NfcStatus.HCE_UNSUPPORTED -> "Ce téléphone ne peut pas émuler de carte NFC"
        NfcStatus.UNAVAILABLE -> "Aucun adaptateur NFC détecté"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (available) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(12.dp).background(
                    if (available) Color(0xFF1B8A5A) else MaterialTheme.colorScheme.error,
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
    onEmulate: (NfcItem) -> Unit
) {
    Card(
        modifier = Modifier.width(220.dp).clickable { onEmulate(item) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300))
                Spacer(Modifier.width(8.dp))
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(Modifier.align(Alignment.End)) {
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onEmulate(item) },
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
                    Icon(Icons.Default.Star, "Ajouter aux favoris", tint = MaterialTheme.colorScheme.outlineVariant)
                }
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
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Émuler")
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Aucun lien", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Ajoutez une URL ou partagez une page depuis votre navigateur.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    original: NfcItem?,
    onBack: () -> Unit,
    onSave: (NfcItem) -> Unit,
    onEmulate: (NfcItem) -> Unit
) {
    var title by rememberSaveable(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var url by rememberSaveable(original?.id) { mutableStateOf(original?.url.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun buildItem(): NfcItem? {
        val cleanUrl = url.trim()
        if (!isValidHttpUrl(cleanUrl)) {
            error = "Saisissez une URL complète commençant par http:// ou https://."
            return null
        }
        val defaultName = Uri.parse(cleanUrl).host?.removePrefix("www.") ?: "Lien"
        return NfcItem(
            id = original?.id ?: UUID.randomUUID().toString(),
            title = title.trim().ifBlank { defaultName },
            url = cleanUrl,
            favorite = original?.favorite ?: false,
            createdAt = original?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = original?.lastUsedAt
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Ajouter un lien" else "Modifier le lien") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Donnez un nom court au lien pour le retrouver facilement.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nom (optionnel)") },
                placeholder = { Text("Mon site") },
                singleLine = true
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL") },
                placeholder = { Text("https://example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                singleLine = true
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { buildItem()?.let(onSave) },
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Enregistrer") }
                Button(
                    onClick = { buildItem()?.let(onEmulate) },
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
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
    onOpenNfcSettings: () -> Unit,
    onStop: () -> Unit
) {
    val available = nfcStatus == NfcStatus.AVAILABLE
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(216.dp), contentAlignment = Alignment.Center) {
                if (available) {
                    Surface(
                        modifier = Modifier.size(190.dp).graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }.alpha(pulseAlpha),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {}
                }
                Surface(
                    modifier = Modifier.size(168.dp),
                    shape = CircleShape,
                    color = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nfc_material),
                        contentDescription = "NFC",
                        modifier = Modifier.fillMaxSize().padding(38.dp),
                        tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Approchez un autre téléphone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                when (nfcStatus) {
                    NfcStatus.AVAILABLE -> "Émulation active"
                    NfcStatus.DISABLED -> "Activez le NFC pour commencer"
                    NfcStatus.HCE_UNSUPPORTED -> "HCE n’est pas pris en charge"
                    NfcStatus.UNAVAILABLE -> "NFC indisponible sur ce téléphone"
                },
                color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(18.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(item.url, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(24.dp))
            if (nfcStatus == NfcStatus.DISABLED) {
                OutlinedButton(onClick = onOpenNfcSettings, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Activer le NFC")
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Close, null)
                Spacer(Modifier.width(8.dp))
                Text("Arrêter")
            }
        }
    }
}

private fun isValidHttpUrl(value: String): Boolean = runCatching {
    val uri = Uri.parse(value)
    (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private fun formatDate(timestamp: Long): String = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.FRENCH)
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
