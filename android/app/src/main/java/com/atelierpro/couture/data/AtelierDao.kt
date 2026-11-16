package com.atelierpro.couture.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AtelierDao {
    // Models (UI observes only non-deleted rows)
    @Query("SELECT * FROM garment_models WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeModels(): Flow<List<GarmentModelEntity>>

    @Query("SELECT * FROM garment_models")
    suspend fun getAllModelsRaw(): List<GarmentModelEntity>

    @Query("SELECT * FROM garment_models WHERE isSynced = 0")
    suspend fun getUnsyncedModels(): List<GarmentModelEntity>

    @Query("SELECT * FROM garment_models WHERE uuidLocal = :uuid LIMIT 1")
    suspend fun findModelByUuid(uuid: String): GarmentModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: GarmentModelEntity)

    @Query("UPDATE garment_models SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteModel(id: String, ts: String)

    @Query("UPDATE garment_models SET isSynced = 1 WHERE uuidLocal IN (:uuids)")
    suspend fun markModelsSynced(uuids: List<String>)

    @Query("DELETE FROM garment_models WHERE deletedAt IS NOT NULL AND isSynced = 1")
    suspend fun purgeDeletedSyncedModels()

    // Clients
    @Query("SELECT * FROM clients WHERE deletedAt IS NULL ORDER BY fullName ASC")
    fun observeClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients")
    suspend fun getAllClientsRaw(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE isSynced = 0")
    suspend fun getUnsyncedClients(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE uuidLocal = :uuid LIMIT 1")
    suspend fun findClientByUuid(uuid: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClient(client: ClientEntity)

    @Query("UPDATE clients SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteClient(id: String, ts: String)

    @Query("UPDATE clients SET isSynced = 1 WHERE uuidLocal IN (:uuids)")
    suspend fun markClientsSynced(uuids: List<String>)

    @Query("DELETE FROM clients WHERE deletedAt IS NOT NULL AND isSynced = 1")
    suspend fun purgeDeletedSyncedClients()

    // Orders
    @Query("SELECT * FROM orders WHERE deletedAt IS NULL ORDER BY deliveryDate ASC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders")
    suspend fun getAllOrdersRaw(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE isSynced = 0")
    suspend fun getUnsyncedOrders(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE uuidLocal = :uuid LIMIT 1")
    suspend fun findOrderByUuid(uuid: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(order: OrderEntity)

    @Query("UPDATE orders SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteOrder(id: String, ts: String)

    @Query("UPDATE orders SET isSynced = 1 WHERE uuidLocal IN (:uuids)")
    suspend fun markOrdersSynced(uuids: List<String>)

    @Query("DELETE FROM orders WHERE deletedAt IS NOT NULL AND isSynced = 1")
    suspend fun purgeDeletedSyncedOrders()

    // Haberdashery & Stock Movements
    @Query("SELECT * FROM haberdashery WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeInventory(): Flow<List<HaberdasheryItemEntity>>

    @Query("SELECT * FROM haberdashery")
    suspend fun getAllInventoryRaw(): List<HaberdasheryItemEntity>

    @Query("SELECT * FROM haberdashery WHERE isSynced = 0")
    suspend fun getUnsyncedInventory(): List<HaberdasheryItemEntity>

    @Query("SELECT * FROM haberdashery WHERE uuidLocal = :uuid LIMIT 1")
    suspend fun findInventoryByUuid(uuid: String): HaberdasheryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventoryItem(item: HaberdasheryItemEntity)

    @Query("UPDATE haberdashery SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteInventoryItem(id: String, ts: String)

    @Query("UPDATE haberdashery SET isSynced = 1 WHERE uuidLocal IN (:uuids)")
    suspend fun markInventorySynced(uuids: List<String>)

    @Query("DELETE FROM haberdashery WHERE deletedAt IS NOT NULL AND isSynced = 1")
    suspend fun purgeDeletedSyncedInventory()

    @Query("SELECT * FROM stock_movements ORDER BY date DESC")
    fun observeStockMovements(): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity)

    // Employees
    @Query("SELECT * FROM employees WHERE deletedAt IS NULL ORDER BY fullName ASC")
    fun observeEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees")
    suspend fun getAllEmployeesRaw(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE isSynced = 0")
    suspend fun getUnsyncedEmployees(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE uuidLocal = :uuid LIMIT 1")
    suspend fun findEmployeeByUuid(uuid: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmployee(employee: EmployeeEntity)

    @Query("UPDATE employees SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteEmployee(id: String, ts: String)

    @Query("UPDATE employees SET isSynced = 1 WHERE uuidLocal IN (:uuids)")
    suspend fun markEmployeesSynced(uuids: List<String>)

    @Query("DELETE FROM employees WHERE deletedAt IS NOT NULL AND isSynced = 1")
    suspend fun purgeDeletedSyncedEmployees()

    // Expenses
    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY date DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesRaw(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE uuidLocal = :uuid LIMIT 1")
    suspend fun findExpenseByUuid(uuid: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Query("UPDATE expenses SET deletedAt = :ts, updatedAt = :ts, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteExpense(id: String, ts: String)

    @Query("UPDATE expenses SET isSynced = 1 WHERE uuidLocal IN (:uuids)")
    suspend fun markExpensesSynced(uuids: List<String>)

    @Query("DELETE FROM expenses WHERE deletedAt IS NOT NULL AND isSynced = 1")
    suspend fun purgeDeletedSyncedExpenses()

    // Reactive pending sync count across all synchronized tables + license state
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM clients WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM garment_models WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM haberdashery WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM employees WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM orders WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM expenses WHERE isSynced = 0) +
            (SELECT COUNT(*) FROM license_state WHERE isSynced = 0)
        """
    )
    fun observePendingSyncCount(): Flow<Int>

    @Query("DELETE FROM clients WHERE uuidLocal = :uuid")
    suspend fun hardDeleteClientByUuid(uuid: String)

    @Query("DELETE FROM garment_models WHERE uuidLocal = :uuid")
    suspend fun hardDeleteModelByUuid(uuid: String)

    @Query("DELETE FROM haberdashery WHERE uuidLocal = :uuid")
    suspend fun hardDeleteInventoryByUuid(uuid: String)

    @Query("DELETE FROM employees WHERE uuidLocal = :uuid")
    suspend fun hardDeleteEmployeeByUuid(uuid: String)

    @Query("DELETE FROM orders WHERE uuidLocal = :uuid")
    suspend fun hardDeleteOrderByUuid(uuid: String)

    @Query("DELETE FROM expenses WHERE uuidLocal = :uuid")
    suspend fun hardDeleteExpenseByUuid(uuid: String)

    // Settings
    @Query("SELECT * FROM workshop_settings WHERE id = 1")
    fun observeSettings(): Flow<WorkshopSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: WorkshopSettingsEntity)

    // License & Used Activation Codes (codes_utilises)
    @Query("SELECT * FROM license_state WHERE id = 1 LIMIT 1")
    fun observeLicenseState(): Flow<LicenseStateEntity?>

    @Query("SELECT * FROM license_state WHERE id = 1 LIMIT 1")
    suspend fun getLicenseState(): LicenseStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLicenseState(license: LicenseStateEntity)

    @Query("UPDATE license_state SET isSynced = 1 WHERE id = 1")
    suspend fun markLicenseSynced()

    @Query("SELECT * FROM codes_utilises ORDER BY usedAt DESC")
    fun observeUsedCodes(): Flow<List<UsedActivationCodeEntity>>

    @Query("SELECT * FROM codes_utilises WHERE isSynced = 0")
    suspend fun getUnsyncedUsedCodes(): List<UsedActivationCodeEntity>

    @Query("SELECT * FROM codes_utilises WHERE code = :code LIMIT 1")
    suspend fun findUsedCode(code: String): UsedActivationCodeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUsedCode(entry: UsedActivationCodeEntity)

    @Query("UPDATE codes_utilises SET isSynced = 1 WHERE code IN (:codes)")
    suspend fun markUsedCodesSynced(codes: List<String>)

    // Clear all tables (for clean reset)
    @Query("DELETE FROM orders")
    suspend fun clearOrders()

    @Query("DELETE FROM clients")
    suspend fun clearClients()

    @Query("DELETE FROM garment_models")
    suspend fun clearModels()

    @Query("DELETE FROM haberdashery")
    suspend fun clearInventory()

    @Query("DELETE FROM stock_movements")
    suspend fun clearMovements()

    @Query("DELETE FROM employees")
    suspend fun clearEmployees()

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()
}
