package com.atelierpro.couture.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atelierpro.couture.data.*
import com.atelierpro.couture.ui.AtelierUiState
import com.atelierpro.couture.ui.components.NumericKeypadDialog
import com.atelierpro.couture.ui.components.PhotoPickerCard
import com.atelierpro.couture.ui.theme.*
import com.atelierpro.couture.util.PdfReceiptGenerator
import java.io.File

@Composable
fun CatalogClientsInventoryScreen(
    state: AtelierUiState,
    initialSubTab: Int = 0,
    onSaveModel: (GarmentModelEntity) -> Unit,
    onDeleteModel: (GarmentModelEntity) -> Unit,
    onSaveClient: (ClientEntity) -> Unit,
    onDeleteClient: (ClientEntity) -> Unit,
    onSaveInventoryItem: (HaberdasheryItemEntity) -> Unit,
    onAdjustStock: (HaberdasheryItemEntity, Int, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }

    var editingModel by remember { mutableStateOf<GarmentModelEntity?>(null) }
    var showModelModal by remember { mutableStateOf(false) }

    var editingClient by remember { mutableStateOf<ClientEntity?>(null) }
    var showClientModal by remember { mutableStateOf(false) }

    var editingItem by remember { mutableStateOf<HaberdasheryItemEntity?>(null) }
    var showItemModal by remember { mutableStateOf(false) }
    var stockActionTarget by remember { mutableStateOf<Pair<HaberdasheryItemEntity, Boolean>?>(null) } // item, isEntry

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab, containerColor = Color.White) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Modèles") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Clients") })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Mercerie") })
        }

        when (subTab) {
            0 -> {
                var catFilter by remember { mutableStateOf("Tous") }
                val models = state.models.filter { catFilter == "Tous" || it.category == catFilter }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Catalogue Modèles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(onClick = { editingModel = null; showModelModal = true }, colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Modèle")
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                            items(listOf("Tous") + AtelierConstants.GARMENT_CATEGORIES) { c ->
                                FilterChip(selected = catFilter == c, onClick = { catFilter = c }, label = { Text(c) })
                            }
                        }
                    }
                    items(models, key = { it.id }) { m ->
                        val recipeCost = m.recipe.sumOf { rec ->
                            val item = state.inventory.find { it.id == rec.itemId }
                            (item?.unitPrice ?: 0L) * rec.quantity
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (m.photoPath.isNotBlank() && File(m.photoPath).exists()) {
                                    AsyncImage(
                                        model = File(m.photoPath),
                                        contentDescription = m.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${m.category} · ${m.estimatedDays} jours", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                        Text(m.name, fontWeight = FontWeight.Bold)
                                    }
                                    Row {
                                        IconButton(onClick = { editingModel = m; showModelModal = true }) { Icon(Icons.Default.Edit, contentDescription = "Modifier") }
                                        IconButton(onClick = { onDeleteModel(m) }) { Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = CrimsonAlert) }
                                    }
                                }
                                Text("${m.defaultPrice} ${state.settings.currency} · ${m.measurementKeys.size} mesures · Coût mercerie $recipeCost ${state.settings.currency}", color = IndigoAtelier, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            1 -> {
                var query by remember { mutableStateOf("") }
                val clients = state.clients.filter {
                    query.isBlank() || it.fullName.contains(query, ignoreCase = true) || it.phone.contains(query)
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Clients & Mensurations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(onClick = { editingClient = null; showClientModal = true }, colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Client")
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Rechercher client…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                    items(clients, key = { it.id }) { c ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(c.fullName, fontWeight = FontWeight.Bold)
                                        Text("${c.phone} · ${c.address}", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                                        Text("${c.latestMeasurements.count { it.value > 0.0 }} mensuration(s) mémorisée(s)", style = MaterialTheme.typography.labelSmall, color = IndigoAtelier)
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            val ok = PdfReceiptGenerator.openWhatsAppMessage(context, c.phone, "Bonjour ${c.fullName}, message de ${state.settings.workshopName}.")
                                            if (!ok) Toast.makeText(context, "WhatsApp introuvable", Toast.LENGTH_SHORT).show()
                                        }) { Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = EmeraldSuccess) }
                                        IconButton(onClick = { editingClient = c; showClientModal = true }) { Icon(Icons.Default.Edit, contentDescription = "Modifier") }
                                        IconButton(onClick = { onDeleteClient(c) }) { Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = CrimsonAlert) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Mercerie & Stocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(onClick = { editingItem = null; showItemModal = true }, colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Article")
                            }
                        }
                    }
                    items(state.inventory, key = { it.id }) { item ->
                        val isLow = item.quantityInStock <= item.criticalThreshold
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, if (isLow) AmberAlert else StoneBorder)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold)
                                        Text("${item.unitPrice} ${state.settings.currency}/${item.unit} · Seuil min : ${item.criticalThreshold}", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                                        if (isLow) Text("Stock bas — Réapprovisionner", style = MaterialTheme.typography.labelSmall, color = AmberAlert, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${item.quantityInStock} ${item.unit}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isLow) AmberAlert else StoneInk)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { stockActionTarget = item to true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)) {
                                        Text("+ Entrée")
                                    }
                                    OutlinedButton(onClick = { stockActionTarget = item to false }, modifier = Modifier.weight(1f)) {
                                        Text("- Sortie")
                                    }
                                    IconButton(onClick = { editingItem = item; showItemModal = true }) { Icon(Icons.Default.Edit, contentDescription = "Modifier") }
                                }
                            }
                        }
                    }
                    item {
                        Text("Mouvements récents", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(state.movements.take(12), key = { it.id }) { mov ->
                        val itemName = state.inventory.find { it.id == mov.itemId }?.name ?: "Article"
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("$itemName — ${mov.note}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("${mov.date} · ${mov.type}", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                            }
                            Text(
                                "${if (mov.type == "entree") "+" else "-"}${mov.quantity}",
                                color = if (mov.type == "entree") EmeraldSuccess else CrimsonAlert,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showModelModal) {
        ModelEditorModal(
            state = state,
            initial = editingModel,
            onDismiss = { showModelModal = false },
            onSave = { onSaveModel(it); showModelModal = false }
        )
    }

    if (showClientModal) {
        ClientEditorModal(
            initial = editingClient,
            onDismiss = { showClientModal = false },
            onSave = { onSaveClient(it); showClientModal = false }
        )
    }

    if (showItemModal) {
        InventoryItemModal(
            currency = state.settings.currency,
            initial = editingItem,
            onDismiss = { showItemModal = false },
            onSave = { onSaveInventoryItem(it); showItemModal = false }
        )
    }

    stockActionTarget?.let { (item, isEntry) ->
        var qty by remember { mutableStateOf("5") }
        var note by remember { mutableStateOf("") }
        var logExpense by remember { mutableStateOf(isEntry) }
        AlertDialog(
            onDismissRequest = { stockActionTarget = null },
            title = { Text("${if (isEntry) "Entrée" else "Sortie"} — ${item.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it.filter(Char::isDigit) }, label = { Text("Quantité (${item.unit})") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note / Motif") }, singleLine = true)
                    if (isEntry) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = logExpense, onCheckedChange = { logExpense = it })
                            Text("Comptabiliser en dépense", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val q = qty.toIntOrNull() ?: 0
                    if (q > 0) {
                        onAdjustStock(item, if (isEntry) q else -q, note.ifBlank { if (isEntry) "Achat mercerie" else "Sortie atelier" }, isEntry && logExpense)
                    }
                    stockActionTarget = null
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { stockActionTarget = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun ModelEditorModal(
    state: AtelierUiState,
    initial: GarmentModelEntity?,
    onDismiss: () -> Unit,
    onSave: (GarmentModelEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Femme") }
    var photoPath by remember { mutableStateOf(initial?.photoPath ?: "") }
    var defaultPrice by remember { mutableStateOf(initial?.defaultPrice ?: 25000L) }
    var estimatedDays by remember { mutableStateOf((initial?.estimatedDays ?: 5).toString()) }
    var measurementKeys by remember { mutableStateOf(initial?.measurementKeys ?: listOf("Tour de poitrine", "Tour de taille", "Longueur robe / boubou")) }
    var customMeas by remember { mutableStateOf("") }
    var recipe by remember { mutableStateOf(initial?.recipe ?: emptyList()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showPriceKeypad by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Modifier modèle" else "Nouveau modèle") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom du modèle *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AtelierConstants.GARMENT_CATEGORIES.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPriceKeypad = true }, modifier = Modifier.weight(1f)) {
                        Text("$defaultPrice ${state.settings.currency}")
                    }
                    OutlinedTextField(value = estimatedDays, onValueChange = { estimatedDays = it.filter(Char::isDigit) }, label = { Text("Jours") }, singleLine = true, modifier = Modifier.weight(0.7f))
                }
                PhotoPickerCard("Photo de référence", photoPath) { photoPath = it }
                Text("Gabarit de mesures :", fontWeight = FontWeight.SemiBold)
                (AtelierConstants.STANDARD_MEASUREMENTS + measurementKeys).distinct().forEach { m ->
                    val checked = measurementKeys.contains(m)
                    Row(Modifier.fillMaxWidth().clickable {
                        measurementKeys = if (checked) measurementKeys - m else measurementKeys + m
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = {
                            measurementKeys = if (checked) measurementKeys - m else measurementKeys + m
                        })
                        Text(m, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = customMeas, onValueChange = { customMeas = it }, label = { Text("Mesure perso") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (customMeas.isNotBlank()) {
                            measurementKeys = measurementKeys + customMeas.trim()
                            customMeas = ""
                        }
                    }) { Text("+") }
                }
                if (state.inventory.isNotEmpty()) {
                    Text("Recette de mercerie :", fontWeight = FontWeight.SemiBold)
                    state.inventory.forEach { item ->
                        val qty = recipe.find { it.itemId == item.id }?.quantity ?: 0
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    val next = (qty - 1).coerceAtLeast(0)
                                    recipe = recipe.filterNot { it.itemId == item.id } + if (next > 0) listOf(RecipeEntry(item.id, next)) else emptyList()
                                }) { Text("-") }
                                Text("$qty", fontWeight = FontWeight.Bold)
                                TextButton(onClick = {
                                    val next = qty + 1
                                    recipe = recipe.filterNot { it.itemId == item.id } + RecipeEntry(item.id, next)
                                }) { Text("+") }
                            }
                        }
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave(
                    GarmentModelEntity(
                        id = initial?.id ?: "mod-${System.currentTimeMillis()}",
                        name = name.trim(),
                        category = category,
                        photoPath = photoPath,
                        defaultPrice = defaultPrice,
                        estimatedDays = (estimatedDays.toIntOrNull() ?: 5).coerceAtLeast(1),
                        measurementKeys = measurementKeys,
                        recipe = recipe,
                        notes = notes.trim()
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )

    if (showPriceKeypad) {
        NumericKeypadDialog("Prix par défaut", state.settings.currency, defaultPrice.toDouble(), false, 1000.0, { showPriceKeypad = false }) {
            defaultPrice = it.toLong()
        }
    }
}

@Composable
private fun ClientEditorModal(
    initial: ClientEntity?,
    onDismiss: () -> Unit,
    onSave: (ClientEntity) -> Unit
) {
    var fullName by remember { mutableStateOf(initial?.fullName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "+223") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var measurements by remember { mutableStateOf(initial?.latestMeasurements ?: emptyMap()) }
    var keypadMeas by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Modifier client" else "Nouveau client") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nom complet *") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone (+223...) *") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse / Quartier") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                Text("Mensurations de base (toucher pour modifier) :", fontWeight = FontWeight.SemiBold)
                AtelierConstants.STANDARD_MEASUREMENTS.take(8).forEach { m ->
                    val v = measurements[m] ?: 0.0
                    Row(Modifier.fillMaxWidth().clickable { keypadMeas = m }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m, style = MaterialTheme.typography.bodySmall)
                        Text(if (v > 0.0) "$v cm" else "—", fontWeight = FontWeight.Bold, color = IndigoAtelier)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = fullName.isNotBlank(), onClick = {
                onSave(
                    ClientEntity(
                        id = initial?.id ?: "cli-${System.currentTimeMillis()}",
                        fullName = fullName.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        notes = notes.trim(),
                        latestMeasurements = measurements
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )

    keypadMeas?.let { key ->
        NumericKeypadDialog(key, "cm", measurements[key] ?: 0.0, true, 0.5, { keypadMeas = null }) { next ->
            measurements = measurements.toMutableMap().apply { put(key, next) }
        }
    }
}

@Composable
private fun InventoryItemModal(
    currency: String,
    initial: HaberdasheryItemEntity?,
    onDismiss: () -> Unit,
    onSave: (HaberdasheryItemEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pièce") }
    var unitPrice by remember { mutableStateOf((initial?.unitPrice ?: 500L).toString()) }
    var qty by remember { mutableStateOf((initial?.quantityInStock ?: 10).toString()) }
    var threshold by remember { mutableStateOf((initial?.criticalThreshold ?: 5).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Modifier article" else "Nouvelle fourniture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AtelierConstants.HABERDASHERY_UNITS.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                    }
                }
                OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it.filter(Char::isDigit) }, label = { Text("Prix unitaire ($currency)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it.filter(Char::isDigit) }, label = { Text("Stock") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = threshold, onValueChange = { threshold = it.filter(Char::isDigit) }, label = { Text("Seuil alerte") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave(
                    HaberdasheryItemEntity(
                        id = initial?.id ?: "merc-${System.currentTimeMillis()}",
                        name = name.trim(),
                        unit = unit,
                        unitPrice = unitPrice.toLongOrNull() ?: 0L,
                        quantityInStock = qty.toIntOrNull() ?: 0,
                        criticalThreshold = threshold.toIntOrNull() ?: 0
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
