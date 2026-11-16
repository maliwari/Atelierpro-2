package com.atelierpro.couture.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atelierpro.couture.data.OrderEntity
import com.atelierpro.couture.data.OrderStatus
import com.atelierpro.couture.ui.AtelierUiState
import com.atelierpro.couture.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun HomeAgendaScreen(
    state: AtelierUiState,
    onOpenOrder: (String) -> Unit,
    onNewOrder: () -> Unit,
    onNavigateMercerie: () -> Unit
) {
    val today = LocalDate.now()
    var selectedDate by remember { mutableStateOf(today) }

    val scrollerDays = remember(today) { (-2L..9L).map { today.plusDays(it) } }

    val activeOrders = state.orders.filter { it.status != OrderStatus.LIVRE.label }
    val urgentOrders = activeOrders.mapNotNull { ord ->
        val daysLeft = runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(ord.deliveryDate)) }.getOrNull()
        if (daysLeft != null && daysLeft <= 2L) ord to daysLeft else null
    }.sortedBy { it.second }

    val lowStock = state.inventory.filter { it.quantityInStock <= it.criticalThreshold }

    val eventsOnSelected = state.orders.flatMap { ord ->
        val list = mutableListOf<Pair<OrderEntity, String>>()
        if (ord.fittingDate == selectedDate.toString()) list.add(ord to "Essayage")
        if (ord.deliveryDate == selectedDate.toString()) list.add(ord to "Livraison")
        list
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.settings.workshopName, style = MaterialTheme.typography.labelMedium, color = StoneMuted)
                    Text("Tableau d’atelier & Agenda", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onNewOrder,
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nouvelle")
                }
            }
        }

        // KPI Row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeKpiCard("Commandes actives", "${activeOrders.size}", IndigoAtelier, Modifier.weight(1f))
                HomeKpiCard("Urgences < 48h", "${urgentOrders.size}", if (urgentOrders.isNotEmpty()) CrimsonAlert else StoneInk, Modifier.weight(1f))
                HomeKpiCard("Stock bas", "${lowStock.size}", if (lowStock.isNotEmpty()) AmberAlert else StoneInk, Modifier.weight(1f), onClick = onNavigateMercerie)
            }
        }

        // Horizontal Date Scroller Agenda
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, StoneBorder),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Agenda des essayages & livraisons", fontWeight = FontWeight.SemiBold)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(scrollerDays) { day ->
                            val isSelected = day == selectedDate
                            val iso = day.toString()
                            val count = state.orders.count {
                                it.status != OrderStatus.LIVRE.label && (it.fittingDate == iso || it.deliveryDate == iso)
                            }
                            Surface(
                                onClick = { selectedDate = day },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) IndigoAtelier else Color(0xFFF5F5F4),
                                border = if (!isSelected && day == today) BorderStroke(1.dp, IndigoAtelier) else null
                            ) {
                                Column(
                                    modifier = Modifier.width(58.dp).padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).take(3),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else StoneMuted
                                    )
                                    Text(
                                        "${day.dayOfMonth}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else StoneInk
                                    )
                                    Text(
                                        if (count > 0) "$count rdv" else "—",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color(0xFFFDE68A) else if (count > 0) IndigoAtelier else Color.Transparent
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = StoneBorder)
                    Text("Prévu le $selectedDate", style = MaterialTheme.typography.labelMedium, color = StoneMuted)

                    if (eventsOnSelected.isEmpty()) {
                        Text("Aucun essayage ni livraison prévu pour cette date.", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                    } else {
                        eventsOnSelected.forEach { (ord, type) ->
                            val client = state.clients.find { it.id == ord.clientId }
                            val model = state.models.find { it.id == ord.modelId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenOrder(ord.id) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${client?.fullName ?: "Client"} — ${model?.name ?: ""}", fontWeight = FontWeight.SemiBold)
                                    Text("$type · ${ord.status}", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                                }
                                Text(ord.reference, style = MaterialTheme.typography.labelMedium, color = IndigoAtelier, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Urgent Alerts
        item {
            Text("Livraisons prioritaires (retards & 48h)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (urgentOrders.isEmpty()) {
            item {
                Text("Toutes les commandes sont dans les délais.", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
            }
        } else {
            items(urgentOrders, key = { it.first.id }) { (ord, daysLeft) ->
                val client = state.clients.find { it.id == ord.clientId }
                val model = state.models.find { it.id == ord.modelId }
                val color = if (daysLeft < 0) CrimsonAlert else AmberAlert
                val label = when {
                    daysLeft < 0 -> "En retard (${-daysLeft}j)"
                    daysLeft == 0L -> "Livraison aujourd’hui"
                    else -> "Sous ${daysLeft * 24}h"
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenOrder(ord.id) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = color)
                        Column(Modifier.weight(1f)) {
                            Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("${client?.fullName ?: "Client"} — ${model?.name ?: ""}", fontWeight = FontWeight.SemiBold)
                            Text("${ord.reference} · ${ord.status}", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeKpiCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, StoneBorder)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = StoneMuted, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}
