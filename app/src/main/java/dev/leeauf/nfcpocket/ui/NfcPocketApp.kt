package dev.leeauf.nfcpocket.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.leeauf.nfcpocket.model.NfcItem
import dev.leeauf.nfcpocket.model.NfcPayload
import dev.leeauf.nfcpocket.model.NfcType
import dev.leeauf.nfcpocket.R
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                item = activeItem!!,
                nfcStatus = nfcStatus,
                onStop = viewModel::stopEmulation
            )
            screen == Screen.EDITOR -> EditorScreen(
                original = editingItem,
                onBack = { screen = Screen.HOME },
                onSave = {
                    viewModel.save(it) { screen = Screen.HOME }
                },
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
    Scaffold(
        topBar = { TopAppBar(title = { Text("NFC Pocket", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Créer") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { NfcStatusCard(nfcStatus, onOpenNfcSettings) }
            if (favorites.isNotEmpty()) {
                item { SectionTitle("Favoris") }
                items(favorites, key = { it.id }) { item ->
                    ItemCard(item, false, onEdit, onDelete, onFavorite, onEmulate)
                }
            }
            item { SectionTitle("Récents") }
            if (recent.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Aucun contenu", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Créez votre premier tag ou partagez un lien vers NFC Pocket.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recent, key = { it.id }) { item ->
                    ItemCard(item, true, onEdit, onDelete, onFavorite, onEmulate)
                }
            }
        }
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
        NfcStatus.AVAILABLE -> "Prêt à émuler un tag NDEF"
        NfcStatus.DISABLED -> "Activez le NFC pour permettre la lecture"
        NfcStatus.HCE_UNSUPPORTED -> "Ce téléphone ne peut pas émuler de carte NFC"
        NfcStatus.UNAVAILABLE -> "Aucun adaptateur NFC détecté"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (available) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ItemCard(
    item: NfcItem,
    showPreview: Boolean,
    onEdit: (NfcItem) -> Unit,
    onDelete: (NfcItem) -> Unit,
    onFavorite: (NfcItem) -> Unit,
    onEmulate: (NfcItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEmulate(item) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(item.type.label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showPreview) {
                        Text(
                            item.preview(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { onFavorite(item) }) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = if (item.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (item.favorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.lastUsedAt?.let { "Utilisé ${formatDate(it)}" } ?: "Jamais utilisé",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onEdit(item) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier")
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                }
                FilledTonalButton(onClick = { onEmulate(item) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Émuler")
                }
            }
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
    var type by rememberSaveable(original?.id) { mutableStateOf(original?.type ?: NfcType.URL) }
    var title by rememberSaveable(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var first by rememberSaveable(original?.id) { mutableStateOf(original.firstField()) }
    var second by rememberSaveable(original?.id) { mutableStateOf(original.secondField()) }
    var third by rememberSaveable(original?.id) { mutableStateOf(original.thirdField()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun changeType(newType: NfcType) {
        if (newType != type) {
            type = newType
            first = ""
            second = ""
            third = ""
            error = null
        }
    }

    fun buildItem(): NfcItem? {
        error = validate(type, first, second, third)
        if (error != null) return null
        val payload = buildPayload(type, first.trim(), second.trim(), third.trim())
        val fallbackTitle = when (payload) {
            is NfcPayload.Contact -> payload.name
            is NfcPayload.Text -> payload.text.take(42)
            else -> first.trim().take(42)
        }.ifBlank { type.label }
        return NfcItem(
            id = original?.id ?: java.util.UUID.randomUUID().toString(),
            type = type,
            title = title.trim().ifBlank { fallbackTitle },
            payload = payload,
            favorite = original?.favorite ?: false,
            createdAt = original?.createdAt ?: System.currentTimeMillis(),
            lastUsedAt = original?.lastUsedAt
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Nouveau contenu" else "Modifier") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NfcType.entries) { candidate ->
                    FilterChip(
                        selected = type == candidate,
                        onClick = { changeType(candidate) },
                        label = { Text(candidate.label) }
                    )
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Titre (optionnel)") },
                singleLine = true
            )
            PayloadFields(
                type = type,
                first = first,
                second = second,
                third = third,
                onFirst = { first = it; error = null },
                onSecond = { second = it; error = null },
                onThird = { third = it; error = null }
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
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
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Émuler")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PayloadFields(
    type: NfcType,
    first: String,
    second: String,
    third: String,
    onFirst: (String) -> Unit,
    onSecond: (String) -> Unit,
    onThird: (String) -> Unit
) {
    when (type) {
        NfcType.URL -> Field(first, onFirst, "Adresse URL", "https://example.com", KeyboardType.Uri)
        NfcType.PHONE -> Field(first, onFirst, "Numéro", "+33612345678", KeyboardType.Phone)
        NfcType.SMS -> {
            Field(first, onFirst, "Numéro", "+33612345678", KeyboardType.Phone)
            Field(second, onSecond, "Message (optionnel)", "Votre message", KeyboardType.Text, singleLine = false)
        }
        NfcType.EMAIL -> {
            Field(first, onFirst, "Destinataire", "nom@example.com", KeyboardType.Email)
            Field(second, onSecond, "Sujet (optionnel)", "Sujet", KeyboardType.Text)
            Field(third, onThird, "Corps (optionnel)", "Votre message", KeyboardType.Text, singleLine = false)
        }
        NfcType.LOCATION -> {
            Field(first, onFirst, "Latitude", "48.8566", KeyboardType.Decimal)
            Field(second, onSecond, "Longitude", "2.3522", KeyboardType.Decimal)
        }
        NfcType.TEXT -> Field(first, onFirst, "Texte", "Contenu à partager", KeyboardType.Text, singleLine = false)
        NfcType.CONTACT -> {
            Field(first, onFirst, "Nom", "Marie Dupont", KeyboardType.Text)
            Field(second, onSecond, "Téléphone (optionnel)", "+33612345678", KeyboardType.Phone)
            Field(third, onThird, "Email (optionnel)", "marie@example.com", KeyboardType.Email)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3
    )
}

@Composable
private fun EmulationScreen(item: NfcItem, nfcStatus: NfcStatus, onStop: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(196.dp),
                shape = CircleShape,
                color = if (nfcStatus == NfcStatus.AVAILABLE) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nfc_material),
                    contentDescription = "NFC",
                    modifier = Modifier.fillMaxSize().padding(42.dp),
                    tint = if (nfcStatus == NfcStatus.AVAILABLE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(32.dp))
            Text("Approchez un autre téléphone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                if (nfcStatus == NfcStatus.AVAILABLE) "Émulation active" else "Émulation prête — NFC indisponible",
                color = if (nfcStatus == NfcStatus.AVAILABLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(item.type.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.preview(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Arrêter")
            }
        }
    }
}

private fun validate(type: NfcType, first: String, second: String, third: String): String? = when (type) {
    NfcType.URL -> if (!first.trim().matches(Regex("https?://.+", RegexOption.IGNORE_CASE))) "Saisissez une URL http:// ou https:// valide." else null
    NfcType.PHONE -> if (first.isBlank()) "Le numéro est obligatoire." else null
    NfcType.SMS -> if (first.isBlank()) "Le numéro est obligatoire." else null
    NfcType.EMAIL -> if (!android.util.Patterns.EMAIL_ADDRESS.matcher(first.trim()).matches()) "Saisissez une adresse email valide." else null
    NfcType.LOCATION -> {
        val latitude = first.replace(',', '.').toDoubleOrNull()
        val longitude = second.replace(',', '.').toDoubleOrNull()
        when {
            latitude == null || latitude !in -90.0..90.0 -> "La latitude doit être comprise entre -90 et 90."
            longitude == null || longitude !in -180.0..180.0 -> "La longitude doit être comprise entre -180 et 180."
            else -> null
        }
    }
    NfcType.TEXT -> if (first.isBlank()) "Le texte ne peut pas être vide." else null
    NfcType.CONTACT -> when {
        first.isBlank() -> "Le nom est obligatoire."
        third.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(third.trim()).matches() -> "L’adresse email du contact est invalide."
        else -> null
    }
}

private fun buildPayload(type: NfcType, first: String, second: String, third: String): NfcPayload = when (type) {
    NfcType.URL -> NfcPayload.Url(first)
    NfcType.PHONE -> NfcPayload.Phone(first)
    NfcType.SMS -> NfcPayload.Sms(first, second)
    NfcType.EMAIL -> NfcPayload.Email(first, second, third)
    NfcType.LOCATION -> NfcPayload.Location(first.replace(',', '.'), second.replace(',', '.'))
    NfcType.TEXT -> NfcPayload.Text(first)
    NfcType.CONTACT -> NfcPayload.Contact(first, second, third)
}

private fun NfcItem?.firstField(): String = when (val payload = this?.payload) {
    is NfcPayload.Url -> payload.url
    is NfcPayload.Phone -> payload.number
    is NfcPayload.Sms -> payload.number
    is NfcPayload.Email -> payload.recipient
    is NfcPayload.Location -> payload.latitude
    is NfcPayload.Text -> payload.text
    is NfcPayload.Contact -> payload.name
    null -> ""
}

private fun NfcItem?.secondField(): String = when (val payload = this?.payload) {
    is NfcPayload.Sms -> payload.message
    is NfcPayload.Email -> payload.subject
    is NfcPayload.Location -> payload.longitude
    is NfcPayload.Contact -> payload.phone
    else -> ""
}

private fun NfcItem?.thirdField(): String = when (val payload = this?.payload) {
    is NfcPayload.Email -> payload.body
    is NfcPayload.Contact -> payload.email
    else -> ""
}

private fun formatDate(timestamp: Long): String = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.FRENCH)
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
