package com.atelierpro.couture.license

import com.atelierpro.couture.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LicenseManagerTest {

    @Test
    fun workshopCodeMatchesAtelPattern() {
        val code = LicenseManager.generateWorkshopCode()
        assertTrue(code.matches(Regex("^ATEL-[2-9A-Z]{4}$")))
    }

    @Test
    fun whatsappUrlContainsRequiredNumberAndAtelierId() {
        val url = LicenseManager.buildWhatsAppActivationUrl("ATEL-7K4M")
        assertEquals(
            "https://wa.me/22365972000?text=Bonjour%2C%20je%20souhaite%20activer%20AtelierPro.%20ID%20:%20ATEL-7K4M",
            url
        )
    }

    @Test
    fun activateValidCodeExtendsExpirationAndPreventsReplay() = runBlocking {
        val fakeDao = InMemoryLicenseDao()
        val now = Instant.parse("2026-11-16T08:00:00Z")
        val initial = LicenseManager.ensureInitialized(fakeDao, now)
        val sig = LicenseManager.computeSignature(initial.workshopCode, "M1", "2026-11")
        val activationCode = "M1-$sig"

        val res1 = LicenseManager.activateCode(fakeDao, activationCode, now)
        assertTrue(res1.isSuccess)
        val eval = res1.getOrThrow()
        assertEquals(LicenseStatus.ACTIVE, eval.status)
        assertEquals(0, eval.isSynced)
        assertEquals("2026-12-30", eval.expirationDate) // 14j essai + 30j M1

        // Rejeu du même code interdit
        val replay = LicenseManager.activateCode(fakeDao, activationCode, now)
        assertTrue(replay.isFailure)
    }

    @Test
    fun clockRollbackTriggersTamperLockWithoutOverwritingLastKnown() = runBlocking {
        val fakeDao = InMemoryLicenseDao()
        val t1 = Instant.parse("2026-11-16T12:00:00Z")
        LicenseManager.checkAndEvaluate(fakeDao, t1)

        // L'utilisateur recule l'horloge de 2 jours
        val rolledBack = Instant.parse("2026-11-14T12:00:00Z")
        val eval = LicenseManager.checkAndEvaluate(fakeDao, rolledBack)
        assertTrue(eval.isClockTampered)
        assertEquals(t1.toEpochMilli(), fakeDao.getLicenseState()!!.lastKnownEpochMs)
    }

    private class InMemoryLicenseDao : AtelierDao {
        private var license: LicenseStateEntity? = null
        private val usedCodes = mutableMapOf<String, UsedActivationCodeEntity>()

        override suspend fun getLicenseState(): LicenseStateEntity? = license
        override suspend fun upsertLicenseState(license: LicenseStateEntity) { this.license = license }
        override suspend fun findUsedCode(code: String): UsedActivationCodeEntity? = usedCodes[code]
        override suspend fun insertUsedCode(entry: UsedActivationCodeEntity) { usedCodes.putIfAbsent(entry.code, entry) }
        override fun observeLicenseState(): Flow<LicenseStateEntity?> = flowOf(license)
        override suspend fun markLicenseSynced() { license = license?.copy(isSynced = 1) }
        override fun observeUsedCodes(): Flow<List<UsedActivationCodeEntity>> = flowOf(usedCodes.values.toList())
        override suspend fun getUnsyncedUsedCodes(): List<UsedActivationCodeEntity> = usedCodes.values.filter { it.isSynced == 0 }
        override suspend fun markUsedCodesSynced(codes: List<String>) {}

        // Unused stubs for interface completeness
        override fun observeModels(): Flow<List<GarmentModelEntity>> = flowOf(emptyList())
        override suspend fun getAllModelsRaw(): List<GarmentModelEntity> = emptyList()
        override suspend fun getUnsyncedModels(): List<GarmentModelEntity> = emptyList()
        override suspend fun findModelByUuid(uuid: String): GarmentModelEntity? = null
        override suspend fun upsertModel(model: GarmentModelEntity) {}
        override suspend fun softDeleteModel(id: String, ts: String) {}
        override suspend fun markModelsSynced(uuids: List<String>) {}
        override suspend fun purgeDeletedSyncedModels() {}
        override fun observeClients(): Flow<List<ClientEntity>> = flowOf(emptyList())
        override suspend fun getAllClientsRaw(): List<ClientEntity> = emptyList()
        override suspend fun getUnsyncedClients(): List<ClientEntity> = emptyList()
        override suspend fun findClientByUuid(uuid: String): ClientEntity? = null
        override suspend fun upsertClient(client: ClientEntity) {}
        override suspend fun softDeleteClient(id: String, ts: String) {}
        override suspend fun markClientsSynced(uuids: List<String>) {}
        override suspend fun purgeDeletedSyncedClients() {}
        override fun observeOrders(): Flow<List<OrderEntity>> = flowOf(emptyList())
        override suspend fun getAllOrdersRaw(): List<OrderEntity> = emptyList()
        override suspend fun getUnsyncedOrders(): List<OrderEntity> = emptyList()
        override suspend fun findOrderByUuid(uuid: String): OrderEntity? = null
        override suspend fun upsertOrder(order: OrderEntity) {}
        override suspend fun softDeleteOrder(id: String, ts: String) {}
        override suspend fun markOrdersSynced(uuids: List<String>) {}
        override suspend fun purgeDeletedSyncedOrders() {}
        override fun observeInventory(): Flow<List<HaberdasheryItemEntity>> = flowOf(emptyList())
        override suspend fun getAllInventoryRaw(): List<HaberdasheryItemEntity> = emptyList()
        override suspend fun getUnsyncedInventory(): List<HaberdasheryItemEntity> = emptyList()
        override suspend fun findInventoryByUuid(uuid: String): HaberdasheryItemEntity? = null
        override suspend fun upsertInventoryItem(item: HaberdasheryItemEntity) {}
        override suspend fun softDeleteInventoryItem(id: String, ts: String) {}
        override suspend fun markInventorySynced(uuids: List<String>) {}
        override suspend fun purgeDeletedSyncedInventory() {}
        override fun observeStockMovements(): Flow<List<StockMovementEntity>> = flowOf(emptyList())
        override suspend fun insertStockMovement(movement: StockMovementEntity) {}
        override fun observeEmployees(): Flow<List<EmployeeEntity>> = flowOf(emptyList())
        override suspend fun getAllEmployeesRaw(): List<EmployeeEntity> = emptyList()
        override suspend fun getUnsyncedEmployees(): List<EmployeeEntity> = emptyList()
        override suspend fun findEmployeeByUuid(uuid: String): EmployeeEntity? = null
        override suspend fun upsertEmployee(employee: EmployeeEntity) {}
        override suspend fun softDeleteEmployee(id: String, ts: String) {}
        override suspend fun markEmployeesSynced(uuids: List<String>) {}
        override suspend fun purgeDeletedSyncedEmployees() {}
        override fun observeExpenses(): Flow<List<ExpenseEntity>> = flowOf(emptyList())
        override suspend fun getAllExpensesRaw(): List<ExpenseEntity> = emptyList()
        override suspend fun getUnsyncedExpenses(): List<ExpenseEntity> = emptyList()
        override suspend fun findExpenseByUuid(uuid: String): ExpenseEntity? = null
        override suspend fun upsertExpense(expense: ExpenseEntity) {}
        override suspend fun softDeleteExpense(id: String, ts: String) {}
        override suspend fun markExpensesSynced(uuids: List<String>) {}
        override suspend fun purgeDeletedSyncedExpenses() {}
        override fun observePendingSyncCount(): Flow<Int> = flowOf(0)
        override suspend fun hardDeleteClientByUuid(uuid: String) {}
        override suspend fun hardDeleteModelByUuid(uuid: String) {}
        override suspend fun hardDeleteInventoryByUuid(uuid: String) {}
        override suspend fun hardDeleteEmployeeByUuid(uuid: String) {}
        override suspend fun hardDeleteOrderByUuid(uuid: String) {}
        override suspend fun hardDeleteExpenseByUuid(uuid: String) {}
        override fun observeSettings(): Flow<WorkshopSettingsEntity?> = flowOf(null)
        override suspend fun upsertSettings(settings: WorkshopSettingsEntity) {}
        override suspend fun clearOrders() {}
        override suspend fun clearClients() {}
        override suspend fun clearModels() {}
        override suspend fun clearInventory() {}
        override suspend fun clearMovements() {}
        override suspend fun clearEmployees() {}
        override suspend fun clearExpenses() {}
    }
}
