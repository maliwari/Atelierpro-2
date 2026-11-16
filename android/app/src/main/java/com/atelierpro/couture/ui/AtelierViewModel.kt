package com.atelierpro.couture.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atelierpro.couture.data.*
import com.atelierpro.couture.license.LicenseEvaluation
import com.atelierpro.couture.license.LicenseManager
import com.atelierpro.couture.sync.SyncManager
import com.atelierpro.couture.sync.SyncStatusState
import com.atelierpro.couture.util.PinSecurityHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AtelierUiState(
    val models: List<GarmentModelEntity> = emptyList(),
    val clients: List<ClientEntity> = emptyList(),
    val orders: List<OrderEntity> = emptyList(),
    val inventory: List<HaberdasheryItemEntity> = emptyList(),
    val movements: List<StockMovementEntity> = emptyList(),
    val employees: List<EmployeeEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val settings: WorkshopSettingsEntity = WorkshopSettingsEntity(),
    val loaded: Boolean = false
)

class AtelierViewModel(application: Application) : AndroidViewModel(application) {
    private fun dao() = AtelierDatabase.getInstance(getApplication()).atelierDao()
    private val syncManager = SyncManager.getInstance(application)
    private val dbReloadTrigger = MutableStateFlow(0)
    private val _clockTampered = MutableStateFlow(false)

    val syncStatus: StateFlow<SyncStatusState> = syncManager.status

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingSyncCount: StateFlow<Int> = dbReloadTrigger.flatMapLatest {
        dao().observePendingSyncCount()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val licenseEvaluation: StateFlow<LicenseEvaluation?> = combine(
        dbReloadTrigger.flatMapLatest { dao().observeLicenseState() },
        _clockTampered
    ) { entity, tampered ->
        entity?.let { LicenseManager.evaluateEntity(it, tampered) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val usedCodes: StateFlow<List<UsedActivationCodeEntity>> = dbReloadTrigger.flatMapLatest {
        dao().observeUsedCodes()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshClockAndLicense()
        syncManager.startAutoSync()
    }

    fun refreshClockAndLicense() = viewModelScope.launch {
        val eval = LicenseManager.checkAndEvaluate(dao())
        _clockTampered.value = eval.isClockTampered
    }

    suspend fun activateLicenseCode(rawCode: String): Result<LicenseEvaluation> {
        val res = LicenseManager.activateCode(dao(), rawCode)
        res.onSuccess { eval ->
            _clockTampered.value = eval.isClockTampered
            syncManager.triggerAsyncSync(silent = true)
        }
        return res
    }

    fun notifyDatabaseReplaced() {
        dbReloadTrigger.value += 1
        refreshClockAndLicense()
    }

    fun triggerManualSync() = viewModelScope.launch {
        syncManager.syncNow(silent = false)
    }

    suspend fun authenticateCloud(
        baseUrl: String,
        email: String,
        password: String,
        registerName: String? = null,
        registerPhone: String? = null
    ): Result<String> = syncManager.authenticate(baseUrl, email, password, registerName, registerPhone)

    fun disconnectCloud() {
        syncManager.disconnectCloud()
    }

    fun getSyncApiBaseUrl(): String = syncManager.getPreferences().apiBaseUrl
    fun getConnectedEmail(): String? = syncManager.getPreferences().connectedEmail

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AtelierUiState> = dbReloadTrigger.flatMapLatest {
        val d = dao()
        combine(
            d.observeModels(),
            d.observeClients(),
            d.observeOrders(),
            d.observeInventory(),
            d.observeStockMovements(),
            d.observeEmployees(),
            d.observeExpenses(),
            d.observeSettings()
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            AtelierUiState(
                models = arr[0] as List<GarmentModelEntity>,
                clients = arr[1] as List<ClientEntity>,
                orders = arr[2] as List<OrderEntity>,
                inventory = arr[3] as List<HaberdasheryItemEntity>,
                movements = arr[4] as List<StockMovementEntity>,
                employees = arr[5] as List<EmployeeEntity>,
                expenses = arr[6] as List<ExpenseEntity>,
                settings = (arr[7] as WorkshopSettingsEntity?) ?: WorkshopSettingsEntity(),
                loaded = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AtelierUiState())

    fun completeOnboarding(
        workshopName: String,
        workshopPhone: String,
        workshopAddress: String,
        currency: String,
        pin: String,
        pinEnabled: Boolean,
        loadSampleData: Boolean
    ) = viewModelScope.launch {
        val salt = PinSecurityHelper.generateSalt()
        val hash = if (pinEnabled && pin.length == 4) PinSecurityHelper.hashPin(pin, salt) else ""
        dao().upsertSettings(
            WorkshopSettingsEntity(
                id = 1,
                workshopName = workshopName.trim().ifBlank { "Mon Atelier de Couture" },
                workshopPhone = workshopPhone.trim(),
                workshopAddress = workshopAddress.trim(),
                currency = currency,
                pinCode = "",
                pinHash = hash,
                pinSalt = if (hash.isNotBlank()) salt else "",
                pinEnabled = pinEnabled && hash.isNotBlank(),
                biometricEnabled = true,
                onboardingCompleted = true
            )
        )
        if (loadSampleData) {
            AtelierDatabase.seedSampleWorkshop(dao())
        }
    }

    fun updateSettings(
        workshopName: String,
        workshopPhone: String,
        workshopAddress: String,
        workshopLogoPath: String,
        currency: String,
        pinEnabled: Boolean,
        biometricEnabled: Boolean,
        newPin: String?
    ) = viewModelScope.launch {
        val cur = uiState.value.settings
        val (nextHash, nextSalt) = if (!newPin.isNullOrBlank() && newPin.length == 4) {
            val s = PinSecurityHelper.generateSalt()
            PinSecurityHelper.hashPin(newPin, s) to s
        } else {
            cur.pinHash to cur.pinSalt
        }
        dao().upsertSettings(
            cur.copy(
                workshopName = workshopName.trim().ifBlank { cur.workshopName },
                workshopPhone = workshopPhone.trim(),
                workshopAddress = workshopAddress.trim(),
                workshopLogoPath = workshopLogoPath,
                currency = currency,
                pinCode = "",
                pinHash = nextHash,
                pinSalt = nextSalt,
                pinEnabled = pinEnabled && nextHash.isNotBlank(),
                biometricEnabled = biometricEnabled,
                onboardingCompleted = true
            )
        )
    }

    fun loadDemoData() = viewModelScope.launch {
        AtelierDatabase.seedSampleWorkshop(dao())
    }

    fun resetAllDataClean() = viewModelScope.launch {
        dao().clearOrders()
        dao().clearClients()
        dao().clearModels()
        dao().clearInventory()
        dao().clearMovements()
        dao().clearEmployees()
        dao().clearExpenses()
    }

    // Orders (toute création/modification passe isSynced = 0 & updatedAt UTC ; suppression en Soft Delete)
    fun saveOrder(order: OrderEntity) = viewModelScope.launch {
        val existing = dao().findOrderByUuid(order.uuidLocal)
        val photoChanged = existing != null && existing.fabricPhotoPath != order.fabricPhotoPath
        dao().upsertOrder(
            order.copy(
                remoteUrl = if (photoChanged) null else order.remoteUrl,
                updatedAt = SyncTimestamp.nowIsoUtc(),
                isSynced = 0,
                deletedAt = null
            )
        )
        val client = uiState.value.clients.find { it.id == order.clientId }
        if (client != null && order.measurements.isNotEmpty()) {
            val merged = client.latestMeasurements.toMutableMap().apply { putAll(order.measurements) }
            dao().upsertClient(
                client.copy(
                    latestMeasurements = merged,
                    updatedAt = SyncTimestamp.nowIsoUtc(),
                    isSynced = 0
                )
            )
        }
    }

    fun deleteOrder(order: OrderEntity) = viewModelScope.launch {
        dao().softDeleteOrder(order.id, SyncTimestamp.nowIsoUtc())
    }

    fun updateOrderStatus(order: OrderEntity, nextStatus: OrderStatus) = viewModelScope.launch {
        val shouldDeduct = !order.stockDeducted && nextStatus != OrderStatus.EN_ATTENTE
        var deducted = order.stockDeducted
        val now = SyncTimestamp.nowIsoUtc()
        if (shouldDeduct) {
            val model = uiState.value.models.find { it.id == order.modelId }
            val today = LocalDate.now().toString()
            model?.recipe?.forEach { entry ->
                val item = uiState.value.inventory.find { it.id == entry.itemId }
                if (item != null) {
                    val newQty = (item.quantityInStock - entry.quantity).coerceAtLeast(0)
                    dao().upsertInventoryItem(item.copy(quantityInStock = newQty, updatedAt = now, isSynced = 0))
                    dao().insertStockMovement(
                        StockMovementEntity(
                            id = "mov-${System.currentTimeMillis()}-${item.id}",
                            itemId = item.id,
                            type = "sortie_auto",
                            quantity = entry.quantity,
                            date = today,
                            note = "Déduction coupe ${order.reference}",
                            orderId = order.id
                        )
                    )
                }
            }
            deducted = true
        }
        dao().upsertOrder(
            order.copy(
                status = nextStatus.label,
                stockDeducted = deducted,
                updatedAt = now,
                isSynced = 0
            )
        )
    }

    fun updateOrderPayment(order: OrderEntity, advancePaid: Long) = viewModelScope.launch {
        dao().upsertOrder(
            order.copy(
                advancePaid = advancePaid.coerceIn(0L, order.totalPrice),
                updatedAt = SyncTimestamp.nowIsoUtc(),
                isSynced = 0
            )
        )
    }

    // Models
    fun saveModel(model: GarmentModelEntity) = viewModelScope.launch {
        val existing = dao().findModelByUuid(model.uuidLocal)
        val photoChanged = existing != null && existing.photoPath != model.photoPath
        dao().upsertModel(
            model.copy(
                remoteUrl = if (photoChanged) null else model.remoteUrl,
                updatedAt = SyncTimestamp.nowIsoUtc(),
                isSynced = 0,
                deletedAt = null
            )
        )
    }

    fun deleteModel(model: GarmentModelEntity) = viewModelScope.launch {
        dao().softDeleteModel(model.id, SyncTimestamp.nowIsoUtc())
    }

    // Clients
    fun saveClient(client: ClientEntity) = viewModelScope.launch {
        dao().upsertClient(
            client.copy(
                updatedAt = SyncTimestamp.nowIsoUtc(),
                isSynced = 0,
                deletedAt = null
            )
        )
    }

    fun deleteClient(client: ClientEntity) = viewModelScope.launch {
        dao().softDeleteClient(client.id, SyncTimestamp.nowIsoUtc())
    }

    // Inventory
    fun saveInventoryItem(item: HaberdasheryItemEntity) = viewModelScope.launch {
        dao().upsertInventoryItem(
            item.copy(
                updatedAt = SyncTimestamp.nowIsoUtc(),
                isSynced = 0,
                deletedAt = null
            )
        )
    }

    fun deleteInventoryItem(item: HaberdasheryItemEntity) = viewModelScope.launch {
        dao().softDeleteInventoryItem(item.id, SyncTimestamp.nowIsoUtc())
    }

    fun adjustInventory(
        item: HaberdasheryItemEntity,
        delta: Int,
        note: String,
        recordAsExpense: Boolean = false
    ) = viewModelScope.launch {
        val today = LocalDate.now().toString()
        val now = SyncTimestamp.nowIsoUtc()
        val nextQty = (item.quantityInStock + delta).coerceAtLeast(0)
        dao().upsertInventoryItem(item.copy(quantityInStock = nextQty, updatedAt = now, isSynced = 0))
        dao().insertStockMovement(
            StockMovementEntity(
                id = "mov-${System.currentTimeMillis()}",
                itemId = item.id,
                type = if (delta >= 0) "entree" else "sortie_manuelle",
                quantity = kotlin.math.abs(delta),
                date = today,
                note = note
            )
        )
        if (recordAsExpense && delta > 0) {
            dao().upsertExpense(
                ExpenseEntity(
                    id = "exp-${System.currentTimeMillis()}",
                    category = "Achat Mercerie",
                    amount = delta.toLong() * item.unitPrice,
                    date = today,
                    description = "Achat $delta ${item.unit} — ${item.name}",
                    updatedAt = now,
                    isSynced = 0
                )
            )
        }
    }

    // Employees
    fun saveEmployee(emp: EmployeeEntity) = viewModelScope.launch {
        dao().upsertEmployee(
            emp.copy(
                updatedAt = SyncTimestamp.nowIsoUtc(),
                isSynced = 0,
                deletedAt = null
            )
        )
    }

    fun deleteEmployee(emp: EmployeeEntity) = viewModelScope.launch {
        dao().softDeleteEmployee(emp.id, SyncTimestamp.nowIsoUtc())
    }

    // Expenses
    fun saveExpense(exp: ExpenseEntity) = viewModelScope.launch {
        val existing = dao().findExpenseByUuid(exp.uuidLocal)
        val photoChanged = existing != null && existing.receiptPhotoPath != exp.receiptPhotoPath
        dao().upsertExpense(
            exp.copy(
                remoteUrl = if (photoChanged) null else exp.remoteUrl,
                updatedAt = SyncTimestamp.nowIsoUtc(),
                isSynced = 0,
                deletedAt = null
            )
        )
    }

    fun deleteExpense(exp: ExpenseEntity) = viewModelScope.launch {
        dao().softDeleteExpense(exp.id, SyncTimestamp.nowIsoUtc())
    }
}
