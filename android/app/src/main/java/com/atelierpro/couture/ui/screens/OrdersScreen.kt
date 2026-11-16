package com.atelierpro.couture.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.atelierpro.couture.data.AtelierCalculations
import com.atelierpro.couture.data.OrderEntity
import com.atelierpro.couture.data.OrderStatus
import com.atelierpro.couture.ui.AtelierUiState
import com.atelierpro.couture.ui.components.NumericKeypadDialog
import com.atelierpro.couture.ui.components.PdfPreviewDialog
import com.atelierpro.couture.ui.theme.*
import com.atelierpro.couture.util.PdfReceiptGenerator
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun OrdersScreen(
    state: AtelierUiState,
    isTablet: Boolean,
    selectedOrderId: String?,
    onSelectOrder: (String?) -> Unit,
    onNewOrder: () -> Unit,
    onEditOrder: (OrderEntity) -> Unit,
    onDeleteOrder: (OrderEntity) -> Unit,
    onUpdateStatus: (OrderEntity, OrderStatus) -> Unit,
    onUpdatePayment: (OrderEntity, Long) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("En cours") }

    val today = LocalDate.now()
    val filteredOrders = remember(state.orders, state.clients, state.models, search, filter, today) {
        val q = search.trim().lowercase()
        state.orders.filter { ord ->
            val daysLeft = runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(ord.deliveryDate)) }.getOrDefault(99L)
            val matchesFilter = when (filter) {
                "En cours" -> ord.status != OrderStatus.LIVRE.label
                "Urgentes" -> ord.status != OrderStatus.LIVRE.label && daysLeft <= 2L
                "Prêtes" -> ord.status == OrderStatus.PRET.label
                "Livrées" -> ord.status == OrderStatus.LIVRE.label
                else -> true
            }
            if (!matchesFilter) return@filter false
            if (q.isEmpty()) return@filter true
            val clientName = state.clients.find { it.id == ord.clientId }?.fullName?.lowercase() ?: ""
            val modelName = state.models.find { it.id == ord.modelId }?.name?.lowercase() ?: ""
            ord.reference.lowercase().contains(q) || clientName.contains(q) || modelName.contains(q)
        }
    }

    val selectedOrder = filteredOrders.find { it.id == selectedOrderId }
        ?: state.orders.find { it.id == selectedOrderId }
        ?: if (isTablet) filteredOrders.firstOrNull() else null

    if (isTablet) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(0.42f).fillMaxHeight()) {
                OrdersFilterHeader(search, { search = it }, filter, { filter = it }, onNewOrder)
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { ord ->
                        OrderRowCard(state, ord, ord.id == selectedOrder?.id) { onSelectOrder(ord.id) }
                    }
                }
            }
            VerticalDivider(color = StoneBorder)
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                if (selectedOrder != null) {
                    OrderDetailContent(
                        state = state,
                        order = selectedOrder,
                        showBack = false,
                        onBack = {},
                        onEdit = { onEditOrder(selectedOrder) },
                        onDelete = { onDeleteOrder(selectedOrder); onSelectOrder(null) },
                        onUpdateStatus = { onUpdateStatus(selectedOrder, it) },
                        onUpdatePayment = { onUpdatePayment(selectedOrder, it) }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sélectionnez une commande.", color = StoneMuted)
                    }
                }
            }
        }
    } else {
        if (selectedOrder != null) {
            OrderDetailContent(
                state = state,
                order = selectedOrder,
                showBack = true,
                onBack = { onSelectOrder(null) },
                onEdit = { onEditOrder(selectedOrder) },
                onDelete = { onDeleteOrder(selectedOrder); onSelectOrder(null) },
                onUpdateStatus = { onUpdateStatus(selectedOrder, it) },
                onUpdatePayment = { onUpdatePayment(selectedOrder, it) }
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                OrdersFilterHeader(search, { search = it }, filter, { filter = it }, onNewOrder)
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { ord ->
                        OrderRowCard(state, ord, false) { onSelectOrder(ord.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersFilterHeader(
    search: String,
    onSearch: (String) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    onNewOrder: () -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Commandes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(
                onClick = onNewOrder,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nouvelle")
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            placeholder = { Text("Rechercher client, modèle, réf…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("En cours", "Urgentes", "Prêtes", "Livrées", "Toutes")) { f ->
                FilterChip(selected = filter == f, onClick = { onFilter(f) }, label = { Text(f) })
            }
        }
    }
}

@Composable
private fun OrderRowCard(
    state: AtelierUiState,
    order: OrderEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val client = state.clients.find { it.id == order.clientId }
    val model = state.models.find { it.id == order.modelId }
    val balance = (order.totalPrice - order.advancePaid).coerceAtLeast(0L)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) IndigoAtelier.copy(alpha = 0.08f) else Color.White),
        border = BorderStroke(1.dp, if (selected) IndigoAtelier else StoneBorder)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(client?.fullName ?: "Client", fontWeight = FontWeight.SemiBold)
                Text(order.reference, style = MaterialTheme.typography.labelSmall, color = StoneMuted)
            }
            Text(model?.name ?: "", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${order.status} · Liv. ${order.deliveryDate}", style = MaterialTheme.typography.labelMedium, color = IndigoAtelier, fontWeight = FontWeight.Bold)
                Text(
                    if (balance > 0) "Reste $balance ${state.settings.currency}" else "Soldé",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (balance > 0) CrimsonAlert else EmeraldSuccess
                )
            }
        }
    }
}

@Composable
private fun OrderDetailContent(
    state: AtelierUiState,
    order: OrderEntity,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdateStatus: (OrderStatus) -> Unit,
    onUpdatePayment: (Long) -> Unit
) {
    val context = LocalContext.current
    val client = state.clients.find { it.id == order.clientId }
    val model = state.models.find { it.id == order.modelId }
    val employee = state.employees.find { it.id == order.assignedEmployeeId }
    val balance = AtelierCalculations.calculateOrderBalance(order.totalPrice, order.advancePaid)
    val missingMaterials = remember(order.stockDeducted, model, state.inventory) {
        if (order.stockDeducted || model == null) emptyList()
        else model.recipe.mapNotNull { rec ->
            val inv = state.inventory.find { it.id == rec.itemId }
            if (inv == null || inv.quantityInStock < rec.quantity) {
                "${inv?.name ?: rec.itemId} (${inv?.quantityInStock ?: 0}/${rec.quantity})"
            } else null
        }
    }

    var showPaymentKeypad by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<Pair<File, String>?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (showBack) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") }
                    }
                    Column {
                        Text("${order.reference} · Livraison ${order.deliveryDate}", style = MaterialTheme.typography.labelMedium, color = StoneMuted)
                        Text("${client?.fullName ?: "Client"} — ${model?.name ?: ""}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Modifier") }
                    if (confirmDelete) {
                        Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)) {
                            Text("Suppr.")
                        }
                    } else {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = CrimsonAlert)
                        }
                    }
                }
            }
        }

        // Fabric Photo
        if (order.fabricPhotoPath.isNotBlank() && File(order.fabricPhotoPath).exists()) {
            item {
                AsyncImage(
                    model = File(order.fabricPhotoPath),
                    contentDescription = "Photo du tissu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                )
            }
        }

        // Workflow 1-click
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Suivi de production (1 clic)", style = MaterialTheme.typography.labelLarge)
                    if (order.stockDeducted) {
                        Text("Mercerie déduite", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess, fontWeight = FontWeight.Bold)
                    }
                }
                if (missingMaterials.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberAlert.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, AmberAlert.copy(alpha = 0.4f))
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAlert, modifier = Modifier.size(18.dp))
                            Text(
                                "Stock insuffisant avant coupe : ${missingMaterials.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberAlert,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrderStatus.entries.take(3).forEach { st ->
                        FilterChip(selected = order.status == st.label, onClick = { onUpdateStatus(st) }, label = { Text(st.label) }, modifier = Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrderStatus.entries.drop(3).forEach { st ->
                        FilterChip(selected = order.status == st.label, onClick = { onUpdateStatus(st) }, label = { Text(st.label) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Measurements
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Mensurations (${order.measurements.size})", fontWeight = FontWeight.SemiBold)
                    order.measurements.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, color = StoneMuted)
                            Text("$v cm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Payment & Receipts
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Total : ${order.totalPrice} ${state.settings.currency}", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                            Text("Avance : ${order.advancePaid} · Solde : $balance ${state.settings.currency}", fontWeight = FontWeight.Bold, color = if (balance > 0) CrimsonAlert else EmeraldSuccess)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (balance > 0) {
                                OutlinedButton(onClick = { onUpdatePayment(order.totalPrice) }) {
                                    Text("Solder")
                                }
                            }
                            FilledTonalButton(onClick = { showPaymentKeypad = true }) { Text("Paiement") }
                        }
                    }

                    HorizontalDivider(color = StoneBorder)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val file = PdfReceiptGenerator.generateDetailedReceiptPdf(context, order, client, model, employee, state.settings)
                                previewPdfFile = file to "Reçu ${order.reference}"
                            },
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Aperçu")
                        }
                        Button(
                            onClick = {
                                val file = PdfReceiptGenerator.generateDetailedReceiptPdf(context, order, client, model, employee, state.settings)
                                Toast.makeText(context, "PDF créé : ${file.name}", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reçu PDF")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val file = PdfReceiptGenerator.generateDetailedReceiptPdf(context, order, client, model, employee, state.settings)
                                val msg = "Bonjour ${client?.fullName ?: ""}, voici votre reçu détaillé (${order.reference}) pour ${model?.name ?: "votre tenue"}. Solde à payer : $balance ${state.settings.currency}."
                                val ok = PdfReceiptGenerator.sharePdfViaWhatsApp(context, file, msg)
                                if (!ok) Toast.makeText(context, "Aucune application de partage PDF disponible.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Partager PDF WhatsApp")
                        }
                        OutlinedButton(
                            onClick = {
                                val file = PdfReceiptGenerator.generateCutSheetPdf(context, order, client, model, employee, state.inventory, state.settings)
                                previewPdfFile = file to "Fiche de coupe ${order.reference}"
                            },
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text("Fiche coupe")
                        }
                    }

                    // Quick WhatsApp message buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Confirm." to "Bonjour ${client?.fullName ?: ""}, votre commande ${order.reference} (${model?.name ?: ""}) est enregistrée. Livraison prévue le ${order.deliveryDate}.",
                            "Essayage" to "Bonjour ${client?.fullName ?: ""}, rappel de votre essayage pour ${model?.name ?: ""} (${order.reference}) le ${order.fittingDate ?: order.deliveryDate}.",
                            "Prêt" to "Bonjour ${client?.fullName ?: ""}, votre tenue ${model?.name ?: ""} (${order.reference}) est prête ! Solde restant : $balance ${state.settings.currency}."
                        ).forEach { (btnLabel, txt) ->
                            OutlinedButton(
                                onClick = {
                                    val ok = PdfReceiptGenerator.openWhatsAppMessage(context, client?.phone ?: "", txt)
                                    if (!ok) Toast.makeText(context, "WhatsApp introuvable sur l’appareil.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(btnLabel, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }

    if (showPaymentKeypad) {
        NumericKeypadDialog(
            title = "Montant total encaissé",
            unit = state.settings.currency,
            initialValue = order.advancePaid.toDouble(),
            allowDecimal = false,
            step = if (state.settings.currency == "FCFA") 1000.0 else 5.0,
            onDismiss = { showPaymentKeypad = false },
            onConfirm = { onUpdatePayment(it.toLong()) }
        )
    }

    previewPdfFile?.let { (file, title) ->
        PdfPreviewDialog(
            pdfFile = file,
            title = title,
            onDismiss = { previewPdfFile = null },
            onShareWhatsApp = {
                val msg = "Reçu ${order.reference} — ${state.settings.workshopName}"
                PdfReceiptGenerator.sharePdfViaWhatsApp(context, file, msg)
            }
        )
    }
}
