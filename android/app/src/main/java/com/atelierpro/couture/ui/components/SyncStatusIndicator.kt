package com.atelierpro.couture.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atelierpro.couture.sync.SyncStatusState
import com.atelierpro.couture.ui.theme.*

/**
 * Indicateur visuel de synchronisation pour la barre supérieure (TopAppBar / NavigationRail).
 * - Nuage vert : tout est synchronisé (à jour)
 * - Nuage orange + compteur : X éléments locaux en attente d'envoi
 * - Animation circulaire : synchronisation en cours
 * - Nuage gris : hors-ligne ou compte Cloud non connecté
 */
@Composable
fun SyncStatusIndicator(
    syncStatus: SyncStatusState,
    pendingCount: Int,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor: Color
    val contentColor: Color
    val label: String

    when {
        syncStatus.isSyncing -> {
            bgColor = IndigoAtelier.copy(alpha = 0.1f)
            contentColor = IndigoAtelier
            label = "Synchro…"
        }
        !syncStatus.isAuthenticated -> {
            bgColor = Color(0xFFF5F5F4)
            contentColor = StoneMuted
            label = if (pendingCount > 0) "Local ($pendingCount)" else "Hors-ligne"
        }
        !syncStatus.isOnline -> {
            bgColor = if (pendingCount > 0) AmberAlert.copy(alpha = 0.12f) else Color(0xFFF5F5F4)
            contentColor = if (pendingCount > 0) AmberAlert else StoneMuted
            label = if (pendingCount > 0) "$pendingCount en attente" else "Hors-ligne"
        }
        pendingCount > 0 -> {
            bgColor = AmberAlert.copy(alpha = 0.14f)
            contentColor = AmberAlert
            label = "$pendingCount en attente"
        }
        else -> {
            bgColor = EmeraldSuccess.copy(alpha = 0.12f)
            contentColor = EmeraldSuccess
            label = "À jour"
        }
    }

    Surface(
        onClick = onSyncNow,
        enabled = !syncStatus.isSyncing,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                syncStatus.isSyncing -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
                !syncStatus.isAuthenticated || !syncStatus.isOnline -> Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                pendingCount > 0 -> Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                else -> Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
