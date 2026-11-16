package com.atelierpro.couture.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.atelierpro.couture.data.*
import com.atelierpro.couture.license.LicenseEvaluation
import com.atelierpro.couture.license.LicenseStatus
import com.atelierpro.couture.sync.SyncStatusState
import com.atelierpro.couture.ui.AtelierUiState
import com.atelierpro.couture.ui.components.NumericKeypadDialog
import com.atelierpro.couture.ui.components.PhotoPickerCard
import com.atelierpro.couture.ui.components.SyncStatusIndicator
import com.atelierpro.couture.ui.theme.*
import com.atelierpro.couture.util.BackupManager
import com.atelierpro.couture.util.CsvExporter
import com.atelierpro.couture.util.PinSecurityHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

@Composable
fun PinLockScreen(
    settings: WorkshopSettingsEntity,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canBiometric = remember(settings.biometricEnabled) {
        settings.biometricEnabled && activity != null && PinSecurityHelper.canUseBiometrics(context)
    }

    var entered by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockoutSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000)
            lockoutSeconds -= 1
        }
    }

    LaunchedEffect(canBiometric) {
        if (canBiometric && activity != null) {
            PinSecurityHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = onUnlock,
                onError = { /* User falls back to PIN */ }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = IndigoAtelier.copy(alpha = 0.1f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = IndigoAtelier, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Accès Patron sécurisé", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Finances, salaires RH et sauvegardes", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
        Spacer(Modifier.height(18.dp))

        Text(
            "•".repeat(entered.length).padEnd(4, '○'),
            style = MaterialTheme.typography.headlineLarge,
            letterSpacing = 8.sp,
            color = IndigoAtelier
        )

        if (lockoutSeconds > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Trop d’essais. Réessayez dans ${lockoutSeconds}s.",
                color = CrimsonAlert,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        } else if (errorMsg != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMsg!!, color = CrimsonAlert, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(18.dp))

        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        enabled = lockoutSeconds == 0,
                        onClick = {
                            errorMsg = null
                            when (key) {
                                "C" -> entered = ""
                                "⌫" -> if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                else -> {
                                    val next = entered + key
                                    if (next.length == 4) {
                                        val ok = PinSecurityHelper.verifyPin(
                                            enteredPin = next,
                                            storedHash = settings.pinHash,
                                            storedSalt = settings.pinSalt,
                                            legacyPlainPin = settings.pinCode
                                        )
                                        if (ok) {
                                            failedAttempts = 0
                                            onUnlock()
                                        } else {
                                            failedAttempts++
                                            entered = ""
                                            if (failedAttempts >= 5) {
                                                lockoutSeconds = 30
                                                failedAttempts = 0
                                            } else {
                                                errorMsg = "Code PIN incorrect ($failedAttempts/5)"
                                            }
                                        }
                                    } else {
                                        entered = next
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(76.dp, 56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (canBiometric && activity != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    PinSecurityHelper.showBiometricPrompt(
                        activity = activity,
                        onSuccess = onUnlock,
                        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                    )
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Déverrouiller par empreinte")
            }
        }
    }
}

@Composable
fun FinancesHrBackupScreen(
    state: AtelierUiState,
    licenseEvaluation: LicenseEvaluation,
    onOpenSubscription: () -> Unit,
    syncStatus: SyncStatusState,
    pendingSyncCount: Int,
    defaultApiUrl: String,
    connectedEmail: String?,
    onSyncNow: () -> Unit,
    onAuthenticateCloud: suspend (
        baseUrl: String,
        email: String,
        password: String,
        registerName: String?,
        registerPhone: String?
    ) -> Result<String>,
    onDisconnectCloud: () -> Unit,
    onLock: () -> Unit,
    onSaveExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onSaveEmployee: (EmployeeEntity) -> Unit,
    onDeleteEmployee: (EmployeeEntity) -> Unit,
    onUpdateSettings: (
        workshopName: String,
        workshopPhone: String,
        workshopAddress: String,
        workshopLogoPath: String,
        currency: String,
        pinEnabled: Boolean,
        biometricEnabled: Boolean,
        newPin: String?
    ) -> Unit,
    onDatabaseChanged: () -> Unit,
    onLoadDemoData: () -> Unit,
    onResetClean: () -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) }

    var showExpenseModal by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var showEmployeeModal by remember { mutableStateOf(false) }

    val encaisse = state.orders.sumOf { it.advancePaid }
    val creances = state.orders.filter { it.status != OrderStatus.LIVRE.label }
        .sumOf { (it.totalPrice - it.advancePaid).coerceAtLeast(0L) }
    val expensesTotal = state.expenses.sumOf { it.amount }
    val salariesTotal = remember(state.employees, state.orders) {
        state.employees.filter { it.active }.sumOf { emp ->
            AtelierCalculations.calculateEmployeeEarnings(emp, state.orders)
        }
    }
    val inventoryById = remember(state.inventory) { state.inventory.associateBy { it.id } }
    val totalCharges = expensesTotal + salariesTotal
    val netProfit = encaisse - totalCharges

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Finances, RH & Atelier", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (state.settings.pinEnabled) {
                OutlinedButton(onClick = onLock) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Verrouiller")
                }
            }
        }

        TabRow(selectedTabIndex = subTab, containerColor = Color.White) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Bilan", maxLines = 1) })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Dépenses", maxLines = 1) })
            Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("Équipe", maxLines = 1) })
            Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text("Réglages", maxLines = 1) })
        }

        when (subTab) {
            0 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FinanceMetricCard("Chiffre encaissé", "$encaisse ${state.settings.currency}", IndigoAtelier, Modifier.weight(1f))
                            FinanceMetricCard("Créances clients", "$creances ${state.settings.currency}", AmberAlert, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FinanceMetricCard("Dépenses + Salaires", "$totalCharges ${state.settings.currency}", CrimsonAlert, Modifier.weight(1f))
                            FinanceMetricCard(
                                "Bénéfice Net Réel",
                                "$netProfit ${state.settings.currency}",
                                if (netProfit >= 0) EmeraldSuccess else CrimsonAlert,
                                Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Text("Rentabilité par modèle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (state.models.isEmpty()) {
                        item { Text("Aucun modèle enregistré pour analyser les marges.", style = MaterialTheme.typography.bodySmall, color = StoneMuted) }
                    } else {
                        items(state.models, key = { it.id }) { m ->
                            val prof = AtelierCalculations.calculateModelProfitability(m, state.orders, inventoryById)

                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(m.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Prix moy. ${prof.averageSellingPrice} ${state.settings.currency} · Mercerie ${prof.haberdasheryCost} · ${prof.orderCount} cmd",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StoneMuted
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("+${prof.averageMargin} ${state.settings.currency}", fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                                        Text("${prof.marginPercent} % marge", style = MaterialTheme.typography.labelSmall, color = IndigoAtelier)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Journal des dépenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Total : $expensesTotal ${state.settings.currency}", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                            }
                            Button(onClick = { showExpenseModal = true }, colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Dépense")
                            }
                        }
                    }
                    if (state.expenses.isEmpty()) {
                        item { Text("Aucune dépense saisie.", style = MaterialTheme.typography.bodySmall, color = StoneMuted) }
                    } else {
                        items(state.expenses, key = { it.id }) { exp ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(exp.category, style = MaterialTheme.typography.labelSmall, color = IndigoAtelier, fontWeight = FontWeight.Bold)
                                            Text(exp.description.ifBlank { exp.category }, fontWeight = FontWeight.SemiBold)
                                            Text(exp.date, style = MaterialTheme.typography.labelSmall, color = StoneMuted)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${exp.amount} ${state.settings.currency}", fontWeight = FontWeight.Bold, color = CrimsonAlert)
                                            IconButton(onClick = { onDeleteExpense(exp) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = CrimsonAlert)
                                            }
                                        }
                                    }
                                    if (exp.receiptPhotoPath.isNotBlank() && File(exp.receiptPhotoPath).exists()) {
                                        AsyncImage(
                                            model = File(exp.receiptPhotoPath),
                                            contentDescription = "Justificatif",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Ressources Humaines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Masse estimée : $salariesTotal ${state.settings.currency}", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                            }
                            Button(onClick = { editingEmployee = null; showEmployeeModal = true }, colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Salarié")
                            }
                        }
                    }
                    if (state.employees.isEmpty()) {
                        item { Text("Aucun employé enregistré.", style = MaterialTheme.typography.bodySmall, color = StoneMuted) }
                    } else {
                        items(state.employees, key = { it.id }) { emp ->
                            val assignedOrders = state.orders.filter { it.assignedEmployeeId == emp.id }
                            val activeCount = assignedOrders.count { it.status != OrderStatus.LIVRE.label }
                            val earnings = AtelierCalculations.calculateEmployeeEarnings(emp, state.orders)
                            val modeLabel = when (emp.payMode) {
                                "fixe" -> "Fixe ${emp.payRate} ${state.settings.currency}/mois"
                                "piece" -> "${emp.payRate} ${state.settings.currency}/tenue"
                                else -> "Commission ${emp.payRate}%"
                            }

                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${emp.fullName} · ${emp.role}", fontWeight = FontWeight.Bold)
                                        Text("${emp.phone} · $modeLabel", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                                        Text(
                                            "$activeCount tenue(s) en cours · Gains : $earnings ${state.settings.currency}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = IndigoAtelier,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { editingEmployee = emp; showEmployeeModal = true }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Modifier")
                                        }
                                        IconButton(onClick = { onDeleteEmployee(emp) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = CrimsonAlert)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                SettingsAndBackupTab(
                    state = state,
                    licenseEvaluation = licenseEvaluation,
                    onOpenSubscription = onOpenSubscription,
                    syncStatus = syncStatus,
                    pendingSyncCount = pendingSyncCount,
                    defaultApiUrl = defaultApiUrl,
                    connectedEmail = connectedEmail,
                    onSyncNow = onSyncNow,
                    onAuthenticateCloud = onAuthenticateCloud,
                    onDisconnectCloud = onDisconnectCloud,
                    onUpdateSettings = onUpdateSettings,
                    onDatabaseChanged = onDatabaseChanged,
                    onLoadDemoData = onLoadDemoData,
                    onResetClean = onResetClean
                )
            }
        }
    }

    if (showExpenseModal) {
        ExpenseEditorModal(
            currency = state.settings.currency,
            onDismiss = { showExpenseModal = false },
            onSave = { onSaveExpense(it); showExpenseModal = false }
        )
    }

    if (showEmployeeModal) {
        EmployeeEditorModal(
            currency = state.settings.currency,
            initial = editingEmployee,
            onDismiss = { showEmployeeModal = false },
            onSave = { onSaveEmployee(it); showEmployeeModal = false }
        )
    }
}

@Composable
private fun FinanceMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = StoneMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun ExpenseEditorModal(
    currency: String,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit
) {
    var category by remember { mutableStateOf(AtelierConstants.EXPENSE_CATEGORIES.first()) }
    var amount by remember { mutableStateOf(5000L) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var description by remember { mutableStateOf("") }
    var receiptPhotoPath by remember { mutableStateOf("") }
    var showKeypad by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle dépense") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Catégorie :", fontWeight = FontWeight.SemiBold)
                AtelierConstants.EXPENSE_CATEGORIES.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                OutlinedButton(onClick = { showKeypad = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Montant : $amount $currency", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (AAAA-MM-JJ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description / Libellé") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PhotoPickerCard("Justificatif photo (optionnel)", receiptPhotoPath) { receiptPhotoPath = it }
            }
        },
        confirmButton = {
            Button(enabled = amount > 0L, onClick = {
                onSave(
                    ExpenseEntity(
                        id = "exp-${System.currentTimeMillis()}",
                        category = category,
                        amount = amount,
                        date = date,
                        description = description.trim(),
                        receiptPhotoPath = receiptPhotoPath
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )

    if (showKeypad) {
        NumericKeypadDialog("Montant dépense", currency, amount.toDouble(), false, if (currency == "FCFA") 1000.0 else 5.0, { showKeypad = false }) {
            amount = it.toLong()
        }
    }
}

@Composable
private fun EmployeeEditorModal(
    currency: String,
    initial: EmployeeEntity?,
    onDismiss: () -> Unit,
    onSave: (EmployeeEntity) -> Unit
) {
    var fullName by remember { mutableStateOf(initial?.fullName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "+223") }
    var role by remember { mutableStateOf(initial?.role ?: "Tailleur") }
    var payMode by remember { mutableStateOf(initial?.payMode ?: "piece") }
    var payRate by remember { mutableStateOf(initial?.payRate ?: 8500L) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var showKeypad by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Modifier salarié" else "Nouveau salarié") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nom complet *") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, singleLine = true)
                Text("Poste :", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AtelierConstants.EMPLOYEE_ROLES) { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                    }
                }
                Text("Mode de rémunération :", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("fixe" to "Fixe", "piece" to "À la pièce", "commission" to "% Comm.").forEach { (k, label) ->
                        FilterChip(selected = payMode == k, onClick = { payMode = k }, label = { Text(label) })
                    }
                }
                OutlinedButton(onClick = { showKeypad = true }, modifier = Modifier.fillMaxWidth()) {
                    val unitLabel = if (payMode == "commission") "%" else currency
                    Text("Taux / Montant : $payRate $unitLabel", fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Employé actif")
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            Button(enabled = fullName.isNotBlank(), onClick = {
                onSave(
                    EmployeeEntity(
                        id = initial?.id ?: "emp-${System.currentTimeMillis()}",
                        fullName = fullName.trim(),
                        phone = phone.trim(),
                        role = role,
                        payMode = payMode,
                        payRate = payRate,
                        active = active
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )

    if (showKeypad) {
        val unitLabel = if (payMode == "commission") "%" else currency
        NumericKeypadDialog("Rémunération", unitLabel, payRate.toDouble(), false, if (payMode == "commission") 5.0 else 1000.0, { showKeypad = false }) {
            payRate = it.toLong()
        }
    }
}

@Composable
private fun SettingsAndBackupTab(
    state: AtelierUiState,
    licenseEvaluation: LicenseEvaluation,
    onOpenSubscription: () -> Unit,
    syncStatus: SyncStatusState,
    pendingSyncCount: Int,
    defaultApiUrl: String,
    connectedEmail: String?,
    onSyncNow: () -> Unit,
    onAuthenticateCloud: suspend (
        baseUrl: String,
        email: String,
        password: String,
        registerName: String?,
        registerPhone: String?
    ) -> Result<String>,
    onDisconnectCloud: () -> Unit,
    onUpdateSettings: (String, String, String, String, String, Boolean, Boolean, String?) -> Unit,
    onDatabaseChanged: () -> Unit,
    onLoadDemoData: () -> Unit,
    onResetClean: () -> Unit
) {
    val settings = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiBaseUrl by remember(defaultApiUrl) { mutableStateOf(defaultApiUrl) }
    var cloudEmail by remember(connectedEmail) { mutableStateOf(connectedEmail ?: "") }
    var cloudPassword by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var isAuthLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var name by remember(settings.workshopName) { mutableStateOf(settings.workshopName) }
    var phone by remember(settings.workshopPhone) { mutableStateOf(settings.workshopPhone) }
    var address by remember(settings.workshopAddress) { mutableStateOf(settings.workshopAddress) }
    var logoPath by remember(settings.workshopLogoPath) { mutableStateOf(settings.workshopLogoPath) }
    var currency by remember(settings.currency) { mutableStateOf(settings.currency) }
    var pinEnabled by remember(settings.pinEnabled) { mutableStateOf(settings.pinEnabled) }
    var biometricEnabled by remember(settings.biometricEnabled) { mutableStateOf(settings.biometricEnabled) }
    var newPin by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val zipExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val ok = BackupManager.exportBackupZip(context, uri)
            onDatabaseChanged()
            Toast.makeText(context, if (ok) "Sauvegarde .zip exportée" else "Échec de l’export .zip", Toast.LENGTH_LONG).show()
        }
    }
    val zipImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = BackupManager.importBackupZip(context, uri)
            onDatabaseChanged()
            Toast.makeText(context, if (ok) "Sauvegarde restaurée avec succès" else "Archive .zip invalide ou corrompue", Toast.LENGTH_LONG).show()
        }
    }
    val ordersCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val csv = CsvExporter.buildOrdersCsv(
                orders = state.orders,
                clientsById = state.clients.associateBy { it.id },
                modelsById = state.models.associateBy { it.id },
                currency = settings.currency
            )
            val ok = CsvExporter.writeCsvToUri(context, uri, csv)
            Toast.makeText(context, if (ok) "Export CSV Commandes enregistré" else "Échec export CSV", Toast.LENGTH_LONG).show()
        }
    }
    val expensesCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val csv = CsvExporter.buildExpensesCsv(state.expenses, settings.currency)
            val ok = CsvExporter.writeCsvToUri(context, uri, csv)
            Toast.makeText(context, if (ok) "Export CSV Dépenses enregistré" else "Échec export CSV", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Identité de l’atelier (Reçus PDF)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de l’atelier") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse / Quartier") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AtelierConstants.CURRENCIES.forEach { c ->
                            FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
                        }
                    }

                    PhotoPickerCard("Logo officiel de l’atelier", logoPath) { logoPath = it }

                    HorizontalDivider(color = StoneBorder)
                    Text("Sécurité Patron", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Verrouillage PIN actif")
                        Switch(checked = pinEnabled, onCheckedChange = { pinEnabled = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Empreinte biométrique")
                        Switch(checked = biometricEnabled, onCheckedChange = { biometricEnabled = it })
                    }
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
                        label = { Text("Nouveau code PIN (4 chiffres, optionnel)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onUpdateSettings(name, phone, address, logoPath, currency, pinEnabled, biometricEnabled, newPin.takeIf { it.length == 4 })
                            newPin = ""
                            Toast.makeText(context, "Paramètres enregistrés", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)
                    ) { Text("Enregistrer les paramètres") }
                }
            }
        }

        item {
            val licColor = when (licenseEvaluation.status) {
                LicenseStatus.ACTIVE -> EmeraldSuccess
                LicenseStatus.TRIAL -> AmberAlert
                LicenseStatus.EXPIRED -> CrimsonAlert
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Abonnement & Licence WhatsApp", fontWeight = FontWeight.Bold)
                            Text(
                                "ID : ${licenseEvaluation.workshopCode} · ${licenseEvaluation.status.label}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = licColor
                            )
                            Text(
                                "Expiration : ${licenseEvaluation.expirationDate} (${licenseEvaluation.remainingDays} j restants)",
                                style = MaterialTheme.typography.labelSmall,
                                color = StoneMuted
                            )
                        }
                    }
                    Button(
                        onClick = onOpenSubscription,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gérer l'abonnement / Activer")
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Synchronisation Cloud (Serveur PHP/MySQL)", fontWeight = FontWeight.Bold)
                            Text(
                                "Sauvegarde multi-appareils silencieuse dès le retour d'internet",
                                style = MaterialTheme.typography.bodySmall,
                                color = StoneMuted
                            )
                        }
                        SyncStatusIndicator(
                            syncStatus = syncStatus,
                            pendingCount = pendingSyncCount,
                            onSyncNow = onSyncNow
                        )
                    }

                    if (syncStatus.isAuthenticated) {
                        Text(
                            "Compte connecté : ${connectedEmail ?: cloudEmail.ifBlank { "Atelier actif" }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldSuccess
                        )
                        Text(
                            "Dernière synchro : ${syncStatus.lastSyncTime ?: "En attente"} · $pendingSyncCount modification(s) locale(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = StoneMuted
                        )
                        if (!syncStatus.lastError.isNullOrBlank()) {
                            Text(
                                "Dernière erreur : ${syncStatus.lastError}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonAlert
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !syncStatus.isSyncing,
                                onClick = onSyncNow,
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (syncStatus.isSyncing) "Synchro…" else "Synchroniser maintenant")
                            }
                            OutlinedButton(
                                enabled = !syncStatus.isSyncing,
                                onClick = {
                                    onDisconnectCloud()
                                    cloudPassword = ""
                                    Toast.makeText(context, "Déconnecté du Cloud", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert)
                            ) { Text("Déconnecter") }
                        }
                    } else {
                        OutlinedTextField(
                            value = apiBaseUrl,
                            onValueChange = { apiBaseUrl = it },
                            label = { Text("URL API (ex: https://api-atelierpro.crfdmali.org)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !isRegisterMode,
                                onClick = { isRegisterMode = false; authError = null },
                                label = { Text("Connexion") }
                            )
                            FilterChip(
                                selected = isRegisterMode,
                                onClick = { isRegisterMode = true; authError = null },
                                label = { Text("Créer un compte atelier") }
                            )
                        }
                        OutlinedTextField(
                            value = cloudEmail,
                            onValueChange = { cloudEmail = it },
                            label = { Text("Email du patron") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = cloudPassword,
                            onValueChange = { cloudPassword = it },
                            label = { Text("Mot de passe (8 car. min)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (authError != null) {
                            Text(authError!!, style = MaterialTheme.typography.bodySmall, color = CrimsonAlert)
                        }
                        Button(
                            enabled = !isAuthLoading && apiBaseUrl.isNotBlank() && cloudEmail.isNotBlank() && cloudPassword.length >= 8,
                            onClick = {
                                authError = null
                                isAuthLoading = true
                                scope.launch {
                                    val res = onAuthenticateCloud(
                                        apiBaseUrl.trim(),
                                        cloudEmail.trim(),
                                        cloudPassword,
                                        if (isRegisterMode) name.ifBlank { settings.workshopName } else null,
                                        if (isRegisterMode) phone.ifBlank { settings.workshopPhone } else null
                                    )
                                    isAuthLoading = false
                                    res.onSuccess {
                                        cloudPassword = ""
                                        Toast.makeText(context, "Connecté au Cloud & synchro lancée", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        authError = err.message ?: "Échec de connexion au serveur"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)
                        ) {
                            Text(
                                when {
                                    isAuthLoading -> "Connexion en cours…"
                                    isRegisterMode -> "Créer le compte & Synchroniser"
                                    else -> "Se connecter & Synchroniser"
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sauvegarde locale (.zip) & Export Excel (.csv)", fontWeight = FontWeight.Bold)
                    Text("Exporte la base SQLite et les photos en .zip, ou génère des tableaux CSV compatibles Excel.", style = MaterialTheme.typography.bodySmall, color = StoneMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { zipExportLauncher.launch("AtelierPro_Backup_${LocalDate.now()}.zip") },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)
                        ) { Text("Exporter .zip") }
                        OutlinedButton(
                            onClick = { zipImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Importer .zip") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { ordersCsvLauncher.launch("AtelierPro_Commandes_${LocalDate.now()}.csv") },
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) { Text("CSV Commandes") }
                        OutlinedButton(
                            onClick = { expensesCsvLauncher.launch("AtelierPro_Depenses_${LocalDate.now()}.csv") },
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) { Text("CSV Dépenses") }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, StoneBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Données d’atelier", fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = {
                            onLoadDemoData()
                            Toast.makeText(context, "Données d’exemple chargées", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Charger des données d’exemple") }

                    OutlinedButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert)
                    ) { Text("Réinitialiser l’atelier (tout effacer)") }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Effacer toutes les données ?") },
            text = { Text("Toutes les commandes, clients, modèles, stocks et dépenses seront supprimés définitivement. Exportez d’abord un .zip si nécessaire.") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetClean()
                        confirmReset = false
                        Toast.makeText(context, "Atelier réinitialisé", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) { Text("Confirmer") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annuler") } }
        )
    }
}
