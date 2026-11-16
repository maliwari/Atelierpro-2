package com.atelierpro.couture.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelierpro.couture.data.UsedActivationCodeEntity
import com.atelierpro.couture.license.LicenseEvaluation
import com.atelierpro.couture.license.LicenseManager
import com.atelierpro.couture.license.LicenseStatus
import com.atelierpro.couture.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Écran de blocage sécurité horloge : affiché si la date système du téléphone
 * est antérieure à derniere_date_connue enregistrée dans SQLite.
 */
@Composable
fun ClockTamperLockScreen(
    license: LicenseEvaluation,
    onRecheckClock: () -> Unit
) {
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = CreamCanvas) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CrimsonAlert.copy(alpha = 0.12f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CrimsonAlert,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Date du téléphone incorrecte",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = StoneInk,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "La date système actuelle est antérieure à la dernière ouverture connue (${license.lastKnownIso.take(16).replace('T', ' ')} UTC). Veuillez régler la date et l'heure du téléphone pour continuer.",
                style = MaterialTheme.typography.bodyMedium,
                color = StoneMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Régler l'heure du téléphone")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRecheckClock,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Vérifier à nouveau")
            }
        }
    }
}

/**
 * Écran Abonnement & Activation manuelle par WhatsApp.
 * Peut être affiché en écran bloquant (lorsque l'essai/abonnement est expiré)
 * ou en écran complet depuis les Réglages.
 */
@Composable
fun SubscriptionScreen(
    license: LicenseEvaluation,
    usedCodes: List<UsedActivationCodeEntity>,
    isBlocking: Boolean,
    onClose: (() -> Unit)? = null,
    onActivateCode: suspend (String) -> Result<LicenseEvaluation>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var codeInput by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var feedbackError by remember { mutableStateOf<String?>(null) }
    var feedbackSuccess by remember { mutableStateOf<String?>(null) }

    val statusColor = when (license.status) {
        LicenseStatus.ACTIVE -> EmeraldSuccess
        LicenseStatus.TRIAL -> AmberAlert
        LicenseStatus.EXPIRED -> CrimsonAlert
    }

    val statusIcon = when (license.status) {
        LicenseStatus.ACTIVE -> Icons.Default.Verified
        LicenseStatus.TRIAL -> Icons.Default.HourglassBottom
        LicenseStatus.EXPIRED -> Icons.Default.Lock
    }

    Surface(modifier = Modifier.fillMaxSize(), color = CreamCanvas) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        Text(
                            text = "Abonnement & Licence",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = StoneInk
                        )
                        Text(
                            text = if (isBlocking) {
                                "Votre période d'accès est expirée. Activez AtelierPro pour débloquer l'atelier."
                            } else {
                                "Activation hors-ligne sécurisée par WhatsApp"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = StoneMuted
                        )
                    }
                    if (!isBlocking && onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }
                }
            }

            // Carte statut & ID Atelier
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, StoneBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ID unique de l'atelier", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                Text(
                                    text = license.workshopCode,
                                    fontSize = 22.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoAtelier
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("ID AtelierPro", license.workshopCode))
                                    Toast.makeText(context, "ID ${license.workshopCode} copié", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copier")
                            }
                        }

                        HorizontalDivider(color = StoneBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Statut actuel", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                    Text(
                                        text = license.status.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Date d'expiration", style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                Text(
                                    text = license.expirationDate,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StoneInk
                                )
                                Text(
                                    text = if (license.status == LicenseStatus.EXPIRED) {
                                        "Expiré"
                                    } else {
                                        "${license.remainingDays} jour(s) restant(s)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }

            // Bouton Payer / Activer sur WhatsApp
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, StoneBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("1. Commander un code d'activation", fontWeight = FontWeight.Bold, color = StoneInk)
                        Text(
                            "Envoyez votre identifiant ${license.workshopCode} sur WhatsApp pour recevoir votre code mensuel (M1 - 30 jours) ou annuel (A1 - 365 jours).",
                            style = MaterialTheme.typography.bodySmall,
                            color = StoneMuted
                        )
                        Button(
                            onClick = {
                                val url = LicenseManager.buildWhatsAppActivationUrl(license.workshopCode)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                runCatching { context.startActivity(intent) }.onFailure {
                                    Toast.makeText(context, "Impossible d'ouvrir WhatsApp", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Payer / Activer sur WhatsApp", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Saisie du code d'activation hors-ligne
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, StoneBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("2. Activer hors-ligne", fontWeight = FontWeight.Bold, color = StoneInk)
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = {
                                codeInput = it.uppercase()
                                feedbackError = null
                                feedbackSuccess = null
                            },
                            label = { Text("Entrer un code d'activation") },
                            placeholder = { Text("Ex : M1-8F3A1B ou A1-4C9E2D") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (feedbackError != null) {
                            Text(
                                text = feedbackError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = CrimsonAlert,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (feedbackSuccess != null) {
                            Text(
                                text = feedbackSuccess!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            enabled = !isValidating && codeInput.isNotBlank(),
                            onClick = {
                                feedbackError = null
                                feedbackSuccess = null
                                isValidating = true
                                scope.launch {
                                    val result = onActivateCode(codeInput)
                                    isValidating = false
                                    result.onSuccess { updated ->
                                        codeInput = ""
                                        feedbackSuccess = "Licence activée jusqu'au ${updated.expirationDate} !"
                                        Toast.makeText(context, "AtelierPro activé jusqu'au ${updated.expirationDate}", Toast.LENGTH_LONG).show()
                                    }.onFailure { err ->
                                        feedbackError = err.message ?: "Code invalide"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (isValidating) "Vérification…" else "Valider", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (usedCodes.isNotEmpty()) {
                item {
                    Text(
                        "Historique des activations",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = StoneInk,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(usedCodes, key = { it.code }) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, StoneBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(entry.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text(
                                    "+${entry.daysAdded} jours · ${entry.usedAt.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StoneMuted
                                )
                            }
                            Text(
                                text = if (entry.isSynced == 1) "Synchronisé" else "En attente synchro",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.isSynced == 1) EmeraldSuccess else AmberAlert,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
