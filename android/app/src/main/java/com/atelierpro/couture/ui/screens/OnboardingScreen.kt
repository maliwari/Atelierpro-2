package com.atelierpro.couture.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atelierpro.couture.data.AtelierConstants
import com.atelierpro.couture.ui.theme.*

@Composable
fun OnboardingScreen(
    onComplete: (
        workshopName: String,
        phone: String,
        address: String,
        currency: String,
        pin: String,
        pinEnabled: Boolean,
        loadSampleData: Boolean
    ) -> Unit
) {
    var workshopName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+223 ") }
    var address by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("FCFA") }
    var pinEnabled by remember { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    var loadSample by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = CreamCanvas) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = IndigoAtelier,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color.White)
                    }
                }
                Column {
                    Text("Bienvenue sur AtelierPro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Configuration initiale hors-ligne de votre atelier", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = workshopName,
                        onValueChange = { workshopName = it },
                        label = { Text("Nom de votre atelier *") },
                        placeholder = { Text("Ex : Atelier Couture Élégance") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Téléphone atelier") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Adresse / Quartier") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Devise utilisée", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AtelierConstants.CURRENCIES.forEach { cur ->
                            FilterChip(
                                selected = currency == cur,
                                onClick = { currency = cur },
                                label = { Text(cur) }
                            )
                        }
                    }

                    HorizontalDivider(color = StoneBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Protéger Finances & Salaires par PIN", fontWeight = FontWeight.SemiBold)
                            Text("Empêche les apprentis de consulter le chiffre d'affaires", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                        }
                        Switch(checked = pinEnabled, onCheckedChange = { pinEnabled = it })
                    }

                    if (pinEnabled) {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                            label = { Text("Code PIN personnel (4 chiffres) *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(color = StoneBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Charger des données d’exemple", fontWeight = FontWeight.SemiBold)
                            Text("Modèles, clients et commandes fictifs pour tester", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                        }
                        Switch(checked = loadSample, onCheckedChange = { loadSample = it })
                    }
                }
            }

            if (errorMsg != null) {
                Text(errorMsg!!, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    if (workshopName.isBlank()) {
                        errorMsg = "Veuillez saisir le nom de votre atelier."
                        return@Button
                    }
                    if (pinEnabled && pin.length != 4) {
                        errorMsg = "Veuillez choisir un code PIN à 4 chiffres."
                        return@Button
                    }
                    onComplete(workshopName, phone, address, currency, pin, pinEnabled, loadSample)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Démarrer mon atelier", fontWeight = FontWeight.Bold)
            }
        }
    }
}
