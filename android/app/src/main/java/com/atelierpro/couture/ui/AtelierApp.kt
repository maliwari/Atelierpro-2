package com.atelierpro.couture.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atelierpro.couture.data.OrderEntity
import com.atelierpro.couture.license.LicenseStatus
import com.atelierpro.couture.ui.components.SyncStatusIndicator
import com.atelierpro.couture.ui.screens.*
import com.atelierpro.couture.ui.theme.*

enum class AtelierTab(val label: String) {
    ACCUEIL("Accueil"),
    COMMANDES("Commandes"),
    CATALOGUE("Catalogue"),
    FINANCES("Finances")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtelierApp(
    viewModel: AtelierViewModel,
    widthSizeClass: WindowWidthSizeClass
) {
    val state by viewModel.uiState.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val licenseEval by viewModel.licenseEvaluation.collectAsState()
    val usedCodes by viewModel.usedCodes.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact

    var showSubscriptionScreen by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(AtelierTab.ACCUEIL) }
    var catalogSubTab by remember { mutableIntStateOf(0) }
    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    var financeUnlocked by remember { mutableStateOf(false) }
    var showOrderEditor by remember { mutableStateOf(false) }
    var editingOrder by remember { mutableStateOf<OrderEntity?>(null) }

    DisposableEffect(lifecycleOwner, state.settings.pinEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshClockAndLicense()
            }
            if (event == Lifecycle.Event.ON_STOP && state.settings.pinEnabled) {
                financeUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!state.loaded || licenseEval == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = CreamCanvas) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IndigoAtelier)
            }
        }
        return
    }

    val currentLicense = licenseEval!!
    if (currentLicense.isClockTampered) {
        ClockTamperLockScreen(
            license = currentLicense,
            onRecheckClock = { viewModel.refreshClockAndLicense() }
        )
        return
    }

    if (currentLicense.status == LicenseStatus.EXPIRED) {
        SubscriptionScreen(
            license = currentLicense,
            usedCodes = usedCodes,
            isBlocking = true,
            onActivateCode = { viewModel.activateLicenseCode(it) }
        )
        return
    }

    if (showSubscriptionScreen) {
        SubscriptionScreen(
            license = currentLicense,
            usedCodes = usedCodes,
            isBlocking = false,
            onClose = { showSubscriptionScreen = false },
            onActivateCode = { viewModel.activateLicenseCode(it) }
        )
        return
    }

    if (!state.settings.onboardingCompleted) {
        OnboardingScreen(
            onComplete = { name, phone, address, currency, pin, pinEnabled, loadSample ->
                viewModel.completeOnboarding(
                    workshopName = name,
                    workshopPhone = phone,
                    workshopAddress = address,
                    currency = currency,
                    pin = pin,
                    pinEnabled = pinEnabled,
                    loadSampleData = loadSample
                )
            }
        )
        return
    }

    Scaffold(
        containerColor = CreamCanvas,
        topBar = {
            if (!isTablet) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.settings.workshopName,
                                fontWeight = FontWeight.Bold,
                                color = StoneInk,
                                maxLines = 1
                            )
                        }
                    },
                    actions = {
                        SyncStatusIndicator(
                            syncStatus = syncStatus,
                            pendingCount = pendingSyncCount,
                            onSyncNow = {
                                if (syncStatus.isAuthenticated) {
                                    viewModel.triggerManualSync()
                                    Toast.makeText(context, "Synchronisation lancée…", Toast.LENGTH_SHORT).show()
                                } else {
                                    currentTab = AtelierTab.FINANCES
                                    Toast.makeText(context, "Connectez votre compte Cloud dans Réglages", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        FilledTonalButton(
                            onClick = {
                                editingOrder = null
                                showOrderEditor = true
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Commande")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        bottomBar = {
            if (!isTablet) {
                NavigationBar(containerColor = Color.White) {
                    AtelierTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            AtelierTab.ACCUEIL -> Icons.Default.CalendarToday
                            AtelierTab.COMMANDES -> Icons.Default.ListAlt
                            AtelierTab.CATALOGUE -> Icons.Default.MenuBook
                            AtelierTab.FINANCES -> Icons.Default.AccountBalanceWallet
                        }
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = {
                                if (tab == AtelierTab.CATALOGUE) catalogSubTab = 0
                                currentTab = tab
                            },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isTablet) {
                NavigationRail(
                    containerColor = Color.White,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    editingOrder = null
                                    showOrderEditor = true
                                },
                                containerColor = IndigoAtelier,
                                contentColor = Color.White
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Nouvelle commande")
                            }
                            SyncStatusIndicator(
                                syncStatus = syncStatus,
                                pendingCount = pendingSyncCount,
                                onSyncNow = {
                                    if (syncStatus.isAuthenticated) {
                                        viewModel.triggerManualSync()
                                        Toast.makeText(context, "Synchronisation lancée…", Toast.LENGTH_SHORT).show()
                                    } else {
                                        currentTab = AtelierTab.FINANCES
                                        Toast.makeText(context, "Connectez votre compte Cloud dans Réglages", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                ) {
                    AtelierTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            AtelierTab.ACCUEIL -> Icons.Default.CalendarToday
                            AtelierTab.COMMANDES -> Icons.Default.ListAlt
                            AtelierTab.CATALOGUE -> Icons.Default.MenuBook
                            AtelierTab.FINANCES -> Icons.Default.AccountBalanceWallet
                        }
                        NavigationRailItem(
                            selected = currentTab == tab,
                            onClick = {
                                if (tab == AtelierTab.CATALOGUE) catalogSubTab = 0
                                currentTab = tab
                            },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when (currentTab) {
                    AtelierTab.ACCUEIL -> HomeAgendaScreen(
                        state = state,
                        onOpenOrder = { orderId ->
                            selectedOrderId = orderId
                            currentTab = AtelierTab.COMMANDES
                        },
                        onNewOrder = {
                            editingOrder = null
                            showOrderEditor = true
                        },
                        onNavigateMercerie = {
                            catalogSubTab = 2
                            currentTab = AtelierTab.CATALOGUE
                        }
                    )

                    AtelierTab.COMMANDES -> OrdersScreen(
                        state = state,
                        isTablet = isTablet,
                        selectedOrderId = selectedOrderId,
                        onSelectOrder = { selectedOrderId = it },
                        onNewOrder = {
                            editingOrder = null
                            showOrderEditor = true
                        },
                        onEditOrder = { order ->
                            editingOrder = order
                            showOrderEditor = true
                        },
                        onDeleteOrder = { order ->
                            viewModel.deleteOrder(order)
                            Toast.makeText(context, "Commande ${order.reference} supprimée", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateStatus = { order, status ->
                            viewModel.updateOrderStatus(order, status)
                            Toast.makeText(context, "Statut : ${status.label}", Toast.LENGTH_SHORT).show()
                        },
                        onUpdatePayment = { order, amount ->
                            viewModel.updateOrderPayment(order, amount)
                            Toast.makeText(context, "Paiement mis à jour", Toast.LENGTH_SHORT).show()
                        }
                    )

                    AtelierTab.CATALOGUE -> CatalogClientsInventoryScreen(
                        state = state,
                        initialSubTab = catalogSubTab,
                        onSaveModel = { viewModel.saveModel(it) },
                        onDeleteModel = { viewModel.deleteModel(it) },
                        onSaveClient = { viewModel.saveClient(it) },
                        onDeleteClient = { viewModel.deleteClient(it) },
                        onSaveInventoryItem = { viewModel.saveInventoryItem(it) },
                        onAdjustStock = { item, delta, note, asExpense ->
                            viewModel.adjustInventory(item, delta, note, asExpense)
                        }
                    )

                    AtelierTab.FINANCES -> {
                        if (state.settings.pinEnabled && !financeUnlocked) {
                            PinLockScreen(
                                settings = state.settings,
                                onUnlock = { financeUnlocked = true }
                            )
                        } else {
                            FinancesHrBackupScreen(
                                state = state,
                                licenseEvaluation = currentLicense,
                                onOpenSubscription = { showSubscriptionScreen = true },
                                syncStatus = syncStatus,
                                pendingSyncCount = pendingSyncCount,
                                defaultApiUrl = viewModel.getSyncApiBaseUrl(),
                                connectedEmail = viewModel.getConnectedEmail(),
                                onSyncNow = { viewModel.triggerManualSync() },
                                onAuthenticateCloud = { url, email, pass, regName, regPhone ->
                                    viewModel.authenticateCloud(url, email, pass, regName, regPhone)
                                },
                                onDisconnectCloud = { viewModel.disconnectCloud() },
                                onLock = { financeUnlocked = false },
                                onSaveExpense = { viewModel.saveExpense(it) },
                                onDeleteExpense = { viewModel.deleteExpense(it) },
                                onSaveEmployee = { viewModel.saveEmployee(it) },
                                onDeleteEmployee = { viewModel.deleteEmployee(it) },
                                onUpdateSettings = { name, phone, addr, logo, cur, pinOn, bioOn, newPin ->
                                    viewModel.updateSettings(name, phone, addr, logo, cur, pinOn, bioOn, newPin)
                                },
                                onDatabaseChanged = { viewModel.notifyDatabaseReplaced() },
                                onLoadDemoData = { viewModel.loadDemoData() },
                                onResetClean = { viewModel.resetAllDataClean() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOrderEditor) {
        OrderEditorDialog(
            state = state,
            initialOrder = editingOrder,
            onDismiss = {
                showOrderEditor = false
                editingOrder = null
            },
            onQuickAddClient = { viewModel.saveClient(it) },
            onSaveOrder = { order ->
                viewModel.saveOrder(order)
                selectedOrderId = order.id
                currentTab = AtelierTab.COMMANDES
                showOrderEditor = false
                editingOrder = null
                Toast.makeText(context, "Commande ${order.reference} enregistrée", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
