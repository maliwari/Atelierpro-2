package com.atelierpro.couture.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atelierpro.couture.data.*
import com.atelierpro.couture.ui.AtelierUiState
import com.atelierpro.couture.ui.components.NumericKeypadDialog
import com.atelierpro.couture.ui.components.PhotoPickerCard
import com.atelierpro.couture.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderEditorDialog(
    state: AtelierUiState,
    initialOrder: OrderEntity? = null,
    onDismiss: () -> Unit,
    onQuickAddClient: (ClientEntity) -> Unit,
    onSaveOrder: (OrderEntity) -> Unit
) {
    val today = LocalDate.now()
    var selectedClientId by remember { mutableStateOf(initialOrder?.clientId ?: state.clients.firstOrNull()?.id ?: "") }
    var selectedModelId by remember { mutableStateOf(initialOrder?.modelId ?: state.models.firstOrNull()?.id ?: "") }
    var fabricPhotoPath by remember { mutableStateOf(initialOrder?.fabricPhotoPath ?: "") }
    var fabricNotes by remember { mutableStateOf(initialOrder?.fabricNotes ?: "") }

    val selectedModel = state.models.find { it.id == selectedModelId }
    val selectedClient = state.clients.find { it.id == selectedClientId }

    var measurements by remember {
        mutableStateOf(initialOrder?.measurements ?: emptyMap())
    }
    var prefilledNotice by remember { mutableStateOf(false) }

    var orderDate by remember { mutableStateOf(initialOrder?.orderDate ?: today.toString()) }
    var fittingDate by remember {
        mutableStateOf(
            initialOrder?.fittingDate ?: today.plusDays(((selectedModel?.estimatedDays ?: 5) - 2).coerceAtLeast(1).toLong()).toString()
        )
    }
    var deliveryDate by remember {
        mutableStateOf(
            initialOrder?.deliveryDate ?: today.plusDays((selectedModel?.estimatedDays ?: 5).toLong()).toString()
        )
    }
    var assignedEmployeeId by remember { mutableStateOf(initialOrder?.assignedEmployeeId ?: state.employees.firstOrNull()?.id ?: "") }
    var totalPrice by remember { mutableStateOf(initialOrder?.totalPrice ?: selectedModel?.defaultPrice ?: 0L) }
    var advancePaid by remember { mutableStateOf(initialOrder?.advancePaid ?: 0L) }
    var notes by remember { mutableStateOf(initialOrder?.notes ?: "") }

    // Inline Quick Client creator
    var showQuickClient by remember { mutableStateOf(false) }
    var newClientName by remember { mutableStateOf("") }
    var newClientPhone by remember { mutableStateOf("+223") }
    var newClientAddress by remember { mutableStateOf("") }

    // Keypad state
    var keypadTarget by remember { mutableStateOf<Triple<String, String, Double>?>(null) } // title, unit, initial
    var keypadCallback by remember { mutableStateOf<(Double) -> Unit>({}) }
    var keypadDecimal by remember { mutableStateOf(true) }
    var keypadStep by remember { mutableStateOf(0.5) }

    // Date picker target: "fitting" or "delivery"
    var datePickerTarget by remember { mutableStateOf<String?>(null) }

    // Auto-prefill required measurements when model/client changes for a new order
    LaunchedEffect(selectedClientId, selectedModelId) {
        if (initialOrder == null && selectedModel != null) {
            val next = mutableMapOf<String, Double>()
            var hadSaved = false
            selectedModel.measurementKeys.forEach { key ->
                val saved = selectedClient?.latestMeasurements?.get(key)
                if (saved != null && saved > 0.0) {
                    next[key] = saved
                    hadSaved = true
                } else {
                    next[key] = measurements[key] ?: 0.0
                }
            }
            measurements = next
            prefilledNotice = hadSaved
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(22.dp),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (initialOrder != null) "Modifier ${initialOrder.reference}" else "Nouvelle commande",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fermer") }
                }
                HorizontalDivider(color = StoneBorder)

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Client
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("1. Client", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showQuickClient = !showQuickClient }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showQuickClient) "Annuler" else "Nouveau client")
                        }
                    }

                    if (showQuickClient) {
                        Card(colors = CardDefaults.cardColors(containerColor = CreamCanvas), border = BorderStroke(1.dp, StoneBorder)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = newClientName, onValueChange = { newClientName = it }, label = { Text("Nom complet") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = newClientPhone, onValueChange = { newClientPhone = it }, label = { Text("Téléphone (+223...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = newClientAddress, onValueChange = { newClientAddress = it }, label = { Text("Quartier / Adresse") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = {
                                        if (newClientName.isNotBlank()) {
                                            val created = ClientEntity(
                                                id = "cli-${System.currentTimeMillis()}",
                                                fullName = newClientName.trim(),
                                                phone = newClientPhone.trim(),
                                                address = newClientAddress.trim()
                                            )
                                            onQuickAddClient(created)
                                            selectedClientId = created.id
                                            showQuickClient = false
                                            newClientName = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Enregistrer et sélectionner") }
                            }
                        }
                    } else if (state.clients.isEmpty()) {
                        Text("Aucun client enregistré. Cliquez sur « Nouveau client » ci-dessus.", color = AmberAlert, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.clients.forEach { cli ->
                                val active = cli.id == selectedClientId
                                Surface(
                                    onClick = { selectedClientId = cli.id },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) IndigoAtelier.copy(alpha = 0.08f) else Color(0xFFF5F5F4),
                                    border = BorderStroke(1.dp, if (active) IndigoAtelier else Color.Transparent)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cli.fullName, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                                        Text(cli.phone, style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = StoneBorder)

                    // 2. Model
                    Text("2. Modèle à confectionner", fontWeight = FontWeight.SemiBold)
                    if (state.models.isEmpty()) {
                        Text("Créez d’abord un modèle dans l’onglet Catalogue.", color = AmberAlert, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.models.forEach { mod ->
                                val active = mod.id == selectedModelId
                                Surface(
                                    onClick = {
                                        selectedModelId = mod.id
                                        if (initialOrder == null) {
                                            totalPrice = mod.defaultPrice
                                            deliveryDate = today.plusDays(mod.estimatedDays.toLong()).toString()
                                            fittingDate = today.plusDays((mod.estimatedDays - 2).coerceAtLeast(1).toLong()).toString()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) IndigoAtelier.copy(alpha = 0.08f) else Color(0xFFF5F5F4),
                                    border = BorderStroke(1.dp, if (active) IndigoAtelier else Color.Transparent)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(mod.name, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                                            Text("${mod.category} · ${mod.estimatedDays}j · ${mod.measurementKeys.size} mesures", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                                        }
                                        Text("${mod.defaultPrice} ${state.settings.currency}", fontWeight = FontWeight.Bold, color = IndigoAtelier)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = StoneBorder)

                    // 3. Fabric Photo
                    PhotoPickerCard(
                        label = "3. Photo du tissu apporté par le client",
                        photoPath = fabricPhotoPath,
                        onPhotoChanged = { fabricPhotoPath = it }
                    )
                    OutlinedTextField(
                        value = fabricNotes,
                        onValueChange = { fabricNotes = it },
                        label = { Text("Précisions tissu (métrage, couleur...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = StoneBorder)

                    // 4. Dynamic Measurements
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("4. Mensurations requises", fontWeight = FontWeight.SemiBold)
                        if (prefilledNotice) {
                            Text("Pré-rempli (client)", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess, fontWeight = FontWeight.Bold)
                        }
                    }

                    val requiredKeys = selectedModel?.measurementKeys ?: emptyList()
                    if (requiredKeys.isEmpty()) {
                        Text("Sélectionnez un modèle avec gabarit de mesures.", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                    } else {
                        requiredKeys.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { key ->
                                    val valNum = measurements[key] ?: 0.0
                                    Card(
                                        modifier = Modifier.weight(1f).clickable {
                                            keypadTarget = Triple(key, "cm", valNum)
                                            keypadDecimal = true
                                            keypadStep = 0.5
                                            keypadCallback = { next ->
                                                measurements = measurements.toMutableMap().apply { put(key, next) }
                                            }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = CreamCanvas),
                                        border = BorderStroke(1.dp, StoneBorder)
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(key, style = MaterialTheme.typography.labelSmall, color = StoneMuted, maxLines = 1)
                                            Text(
                                                if (valNum > 0.0) "$valNum cm" else "— cm",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    HorizontalDivider(color = StoneBorder)

                    // 5. Planning & Tailor
                    Text("5. Planning & Tailleur", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            modifier = Modifier.weight(1f).clickable { datePickerTarget = "fitting" },
                            colors = CardDefaults.cardColors(containerColor = CreamCanvas),
                            border = BorderStroke(1.dp, StoneBorder)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Essayage", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                    Text(fittingDate.ifBlank { "—" }, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = IndigoAtelier, modifier = Modifier.size(18.dp))
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f).clickable { datePickerTarget = "delivery" },
                            colors = CardDefaults.cardColors(containerColor = CreamCanvas),
                            border = BorderStroke(1.dp, StoneBorder)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Livraison", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                    Text(deliveryDate, fontWeight = FontWeight.Bold, color = IndigoAtelier)
                                }
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = IndigoAtelier, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (state.employees.isNotEmpty()) {
                        Text("Tailleur assigné :", style = MaterialTheme.typography.labelMedium, color = StoneMuted)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.employees.take(4).forEach { emp ->
                                FilterChip(
                                    selected = assignedEmployeeId == emp.id,
                                    onClick = { assignedEmployeeId = emp.id },
                                    label = { Text(emp.fullName.substringBefore(" ")) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = StoneBorder)

                    // 6. Financial
                    Text("6. Tarification & Avance", fontWeight = FontWeight.SemiBold)
                    val balance = (totalPrice - advancePaid).coerceAtLeast(0L)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            modifier = Modifier.weight(1f).clickable {
                                keypadTarget = Triple("Prix total", state.settings.currency, totalPrice.toDouble())
                                keypadDecimal = false
                                keypadStep = if (state.settings.currency == "FCFA") 1000.0 else 5.0
                                keypadCallback = { totalPrice = it.toLong() }
                            },
                            colors = CardDefaults.cardColors(containerColor = CreamCanvas),
                            border = BorderStroke(1.dp, StoneBorder)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Prix total", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                Text("$totalPrice ${state.settings.currency}", fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f).clickable {
                                keypadTarget = Triple("Avance versée", state.settings.currency, advancePaid.toDouble())
                                keypadDecimal = false
                                keypadStep = if (state.settings.currency == "FCFA") 1000.0 else 5.0
                                keypadCallback = { advancePaid = it.toLong().coerceAtMost(totalPrice) }
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Avance", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                                Text("$advancePaid ${state.settings.currency}", fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, StoneBorder)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Reste", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                Text("$balance ${state.settings.currency}", fontWeight = FontWeight.Bold, color = if (balance > 0) CrimsonAlert else EmeraldSuccess)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes atelier") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = StoneBorder)
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("Annuler") }
                    Button(
                        enabled = selectedClientId.isNotBlank() && selectedModelId.isNotBlank(),
                        onClick = {
                            val ref = initialOrder?.reference ?: AtelierCalculations.generateNextOrderReference(today.year, state.orders)
                            onSaveOrder(
                                OrderEntity(
                                    id = initialOrder?.id ?: "ord-${System.currentTimeMillis()}",
                                    reference = ref,
                                    clientId = selectedClientId,
                                    modelId = selectedModelId,
                                    fabricPhotoPath = fabricPhotoPath,
                                    fabricNotes = fabricNotes.trim(),
                                    measurements = measurements,
                                    status = initialOrder?.status ?: OrderStatus.EN_ATTENTE.label,
                                    stockDeducted = initialOrder?.stockDeducted ?: false,
                                    assignedEmployeeId = assignedEmployeeId.ifBlank { null },
                                    orderDate = orderDate,
                                    fittingDate = fittingDate.ifBlank { null },
                                    deliveryDate = deliveryDate,
                                    totalPrice = totalPrice,
                                    advancePaid = advancePaid,
                                    notes = notes.trim()
                                )
                            )
                        },
                        modifier = Modifier.weight(1.4f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)
                    ) { Text(if (initialOrder != null) "Enregistrer" else "Créer la commande") }
                }
            }
        }
    }

    keypadTarget?.let { (title, unit, initVal) ->
        NumericKeypadDialog(
            title = title,
            unit = unit,
            initialValue = initVal,
            allowDecimal = keypadDecimal,
            step = keypadStep,
            onDismiss = { keypadTarget = null },
            onConfirm = keypadCallback
        )
    }

    datePickerTarget?.let { target ->
        val currentIso = if (target == "fitting") fittingDate else deliveryDate
        val initialMillis = runCatching {
            LocalDate.parse(currentIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrElse {
            today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (target == "fitting") fittingDate = picked else deliveryDate = picked
                    }
                    datePickerTarget = null
                }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
