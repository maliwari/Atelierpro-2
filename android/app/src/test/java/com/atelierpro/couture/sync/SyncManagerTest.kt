package com.atelierpro.couture.sync

import com.atelierpro.couture.data.*
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class SyncManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: InMemorySyncDao
    private lateinit var prefs: InMemorySyncPreferences
    private var retryScheduled = false
    private var online = true

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dao = InMemorySyncDao()
        prefs = InMemorySyncPreferences(
            apiBaseUrl = server.url("/").toString().trimEnd('/'),
            authToken = "jwt.test.token",
            deviceUuid = "dev-uuid-001"
        )
        retryScheduled = false
        online = true
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun buildManager(autoTriggerAfterAuth: Boolean = false): SyncManager = SyncManager(
        prefs = prefs,
        daoProvider = { dao },
        isNetworkOnlineFn = { online },
        scheduleRetryFn = { retryScheduled = true },
        deviceNameProvider = { "Samsung Galaxy A15" },
        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build(),
        autoTriggerAfterAuth = autoTriggerAfterAuth
    )

    @Test
    fun syncNowRunsFullOfflineFirstCycleCheckDeviceUploadPushConfirmPurgePullAndMerge() = runBlocking {
        val tempPhoto = File.createTempFile("tissu_test_", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
            deleteOnExit()
        }

        dao.upsertLicenseState(
            LicenseStateEntity(
                id = 1,
                workshopCode = "ATEL-7K4M",
                planType = "trial",
                expirationDate = "2026-11-30",
                lastKnownEpochMs = 1_763_200_000_000L,
                lastKnownIso = "2026-11-16T00:00:00Z",
                isSynced = 0,
                updatedAt = "2026-11-16 00:00:00"
            )
        )
        dao.upsertClient(
            ClientEntity(
                id = "cli-1",
                fullName = "Aminata Traore",
                phone = "+22376001122",
                uuidLocal = "uuid-cli-1",
                updatedAt = "2026-11-16 08:00:00",
                isSynced = 0
            )
        )
        dao.upsertModel(
            GarmentModelEntity(
                id = "mod-1",
                name = "Bazin Brode",
                category = "Femme",
                defaultPrice = 45000,
                uuidLocal = "uuid-mod-1",
                updatedAt = "2026-11-16 08:00:00",
                isSynced = 1
            )
        )
        dao.upsertOrder(
            OrderEntity(
                id = "ord-1",
                reference = "CMD-001",
                clientId = "cli-1",
                modelId = "mod-1",
                fabricPhotoPath = tempPhoto.absolutePath,
                remoteUrl = null,
                orderDate = "2026-11-16",
                deliveryDate = "2026-11-22",
                totalPrice = 45000,
                advancePaid = 20000,
                uuidLocal = "uuid-ord-1",
                updatedAt = "2026-11-16 08:05:00",
                isSynced = 0
            )
        )
        dao.upsertExpense(
            ExpenseEntity(
                id = "exp-del",
                category = "Transport",
                amount = 2000,
                date = "2026-11-15",
                uuidLocal = "uuid-exp-del",
                updatedAt = "2026-11-16 08:10:00",
                isSynced = 0,
                deletedAt = "2026-11-16 08:10:00"
            )
        )

        val requestPaths = mutableListOf<String>()
        var capturedPushBody = ""

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                requestPaths.add(path)
                return when {
                    path == "/api/check_device" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            {
                              "success": true,
                              "authorized": true,
                              "plan_abonnement": "pro",
                              "statut": "actif",
                              "date_expiration": "2027-02-15 00:00:00",
                              "devices_used": 1,
                              "max_appareils": 2
                            }
                            """.trimIndent()
                        )
                    path == "/api/upload/image" -> MockResponse()
                        .setResponseCode(201)
                        .setBody("""{"success":true,"url":"https://api-atelierpro.crfdmali.org/uploads/1/tissu.jpg"}""")
                    path == "/api/sync/push" -> {
                        capturedPushBody = request.body.readUtf8()
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(
                                """
                                {
                                  "success": true,
                                  "synced_uuids": {
                                    "clients": ["uuid-cli-1"],
                                    "mercerie": [],
                                    "modeles": [],
                                    "employes": [],
                                    "commandes": ["uuid-ord-1"],
                                    "depenses": ["uuid-exp-del"]
                                  },
                                  "licence_synced": true,
                                  "synced_codes": []
                                }
                                """.trimIndent()
                            )
                    }
                    path.startsWith("/api/sync/pull") -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            {
                              "success": true,
                              "server_time": "2026-11-16 09:00:00",
                              "data": {
                                "clients": [
                                  {
                                    "uuid_local": "uuid-cli-remote",
                                    "nom": "Moussa Diallo",
                                    "telephone": "+22366112233",
                                    "adresse": "Hamdallaye",
                                    "notes": "Client tablette",
                                    "updated_at": "2026-11-16 08:55:00",
                                    "deleted_at": null
                                  }
                                ],
                                "mercerie": [],
                                "modeles": [],
                                "modele_mercerie": [],
                                "employes": [],
                                "commandes": [],
                                "depenses": []
                              }
                            }
                            """.trimIndent()
                        )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val manager = buildManager()
        val result = manager.syncNow(silent = false)

        assertTrue(result.isSuccess)
        assertEquals(4, requestPaths.size)
        assertEquals("/api/check_device", requestPaths[0])
        assertEquals("/api/upload/image", requestPaths[1])
        assertEquals("/api/sync/push", requestPaths[2])
        assertTrue(requestPaths[3].startsWith("/api/sync/pull"))

        // L'URL distante uploadée a bien été injectée dans le JSON envoyé à /api/sync/push
        val pushJson = JsonParser.parseString(capturedPushBody).asJsonObject
        val pushedOrder = pushJson.getAsJsonArray("commandes").get(0).asJsonObject
        assertEquals(
            "https://api-atelierpro.crfdmali.org/uploads/1/tissu.jpg",
            pushedOrder.get("photo_tissu_url").asString
        )

        // Confirmation locale (isSynced = 1) et purge physique du soft-delete
        assertEquals(1, dao.findClientByUuid("uuid-cli-1")?.isSynced)
        assertEquals(1, dao.findOrderByUuid("uuid-ord-1")?.isSynced)
        assertNull(dao.findExpenseByUuid("uuid-exp-del"))

        // Licence prolongée par /api/check_device + marquée synchronisée
        val lic = dao.getLicenseState()!!
        assertEquals("2027-02-15", lic.expirationDate)
        assertEquals("pro", lic.planType)
        assertEquals(1, lic.isSynced)

        // Pull + Merge d'un client distant
        val remoteClient = dao.findClientByUuid("uuid-cli-remote")
        assertNotNull(remoteClient)
        assertEquals("Moussa Diallo", remoteClient?.fullName)
        assertEquals(1, remoteClient?.isSynced)

        assertEquals("2026-11-16 09:00:00", prefs.lastSyncTimestamp)
        assertFalse(retryScheduled)
        assertNull(manager.status.value.lastError)
    }

    @Test
    fun syncNowAbortsWithoutRetryWhenDeviceQuotaExceeded403() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(403)
                    .setBody(
                        """
                        {
                          "success": false,
                          "authorized": false,
                          "reason": "device_quota_exceeded",
                          "message": "Quota d’appareils atteint (2/2)."
                        }
                        """.trimIndent()
                    )
            }
        }

        val manager = buildManager()
        val result = manager.syncNow(silent = true)

        assertTrue(result.isFailure)
        assertFalse(retryScheduled)
        assertEquals(1, server.requestCount)
        assertEquals("Quota d’appareils atteint (2/2).", manager.status.value.lastError)
    }

    @Test
    fun syncNowClearsSessionOnUnauthorized401() = runBlocking {
        dao.upsertClient(
            ClientEntity(
                id = "cli-1",
                fullName = "Fatoumata Keita",
                phone = "+22370000001",
                uuidLocal = "uuid-cli-1",
                updatedAt = "2026-11-16 08:00:00",
                isSynced = 0
            )
        )

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/check_device" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"success":true,"authorized":true,"statut":"actif"}""")
                    "/api/sync/push" -> MockResponse()
                        .setResponseCode(401)
                        .setBody("""{"success":false,"error":"Token expiré"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val manager = buildManager()
        val result = manager.syncNow(silent = false)

        assertTrue(result.isFailure)
        assertNull(prefs.authToken)
        assertFalse(manager.status.value.isAuthenticated)
        assertFalse(retryScheduled)
    }

    @Test
    fun syncNowQueuesRetryWhenOffline() = runBlocking {
        online = false
        val manager = buildManager()

        val result = manager.syncNow(silent = false)

        assertTrue(result.isFailure)
        assertTrue(retryScheduled)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun authenticateStoresBearerTokenAndEmail() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"token":"jwt.prod.999"}""")
        )
        prefs.authToken = null

        val manager = buildManager(autoTriggerAfterAuth = false)
        val res = manager.authenticate(
            baseUrl = server.url("/").toString().trimEnd('/'),
            email = "patron@crfdmali.org",
            password = "MotDePasseSuperSur"
        )

        assertTrue(res.isSuccess)
        assertEquals("jwt.prod.999", res.getOrNull())
        assertEquals("jwt.prod.999", prefs.authToken)
        assertEquals("patron@crfdmali.org", prefs.connectedEmail)
        assertTrue(manager.status.value.isAuthenticated)
    }

    private class InMemorySyncPreferences(
        override var apiBaseUrl: String,
        override var authToken: String?,
        override val deviceUuid: String,
        override var lastSyncTimestamp: String = SyncPreferences.DEFAULT_LAST_SYNC,
        override var connectedEmail: String? = null
    ) : SyncPreferencesStore {
        override fun isAuthenticated(): Boolean = !authToken.isNullOrBlank()
        override fun clearSession() {
            authToken = null
            connectedEmail = null
            lastSyncTimestamp = SyncPreferences.DEFAULT_LAST_SYNC
        }
    }

    private class InMemorySyncDao : AtelierDao {
        private val clients = mutableMapOf<String, ClientEntity>()
        private val models = mutableMapOf<String, GarmentModelEntity>()
        private val inventory = mutableMapOf<String, HaberdasheryItemEntity>()
        private val employees = mutableMapOf<String, EmployeeEntity>()
        private val orders = mutableMapOf<String, OrderEntity>()
        private val expenses = mutableMapOf<String, ExpenseEntity>()
        private val usedCodes = mutableMapOf<String, UsedActivationCodeEntity>()
        private var licenseState: LicenseStateEntity? = null

        override fun observeModels(): Flow<List<GarmentModelEntity>> = flowOf(models.values.toList())
        override suspend fun getAllModelsRaw(): List<GarmentModelEntity> = models.values.toList()
        override suspend fun getUnsyncedModels(): List<GarmentModelEntity> = models.values.filter { it.isSynced == 0 }
        override suspend fun findModelByUuid(uuid: String): GarmentModelEntity? = models.values.firstOrNull { it.uuidLocal == uuid }
        override suspend fun upsertModel(model: GarmentModelEntity) { models[model.id] = model }
        override suspend fun softDeleteModel(id: String, ts: String) {
            models[id]?.let { models[id] = it.copy(deletedAt = ts, updatedAt = ts, isSynced = 0) }
        }
        override suspend fun markModelsSynced(uuids: List<String>) {
            models.replaceAll { _, v -> if (v.uuidLocal in uuids) v.copy(isSynced = 1) else v }
        }
        override suspend fun purgeDeletedSyncedModels() {
            models.entries.removeIf { it.value.deletedAt != null && it.value.isSynced == 1 }
        }

        override fun observeClients(): Flow<List<ClientEntity>> = flowOf(clients.values.toList())
        override suspend fun getAllClientsRaw(): List<ClientEntity> = clients.values.toList()
        override suspend fun getUnsyncedClients(): List<ClientEntity> = clients.values.filter { it.isSynced == 0 }
        override suspend fun findClientByUuid(uuid: String): ClientEntity? = clients.values.firstOrNull { it.uuidLocal == uuid }
        override suspend fun upsertClient(client: ClientEntity) { clients[client.id] = client }
        override suspend fun softDeleteClient(id: String, ts: String) {
            clients[id]?.let { clients[id] = it.copy(deletedAt = ts, updatedAt = ts, isSynced = 0) }
        }
        override suspend fun markClientsSynced(uuids: List<String>) {
            clients.replaceAll { _, v -> if (v.uuidLocal in uuids) v.copy(isSynced = 1) else v }
        }
        override suspend fun purgeDeletedSyncedClients() {
            clients.entries.removeIf { it.value.deletedAt != null && it.value.isSynced == 1 }
        }

        override fun observeOrders(): Flow<List<OrderEntity>> = flowOf(orders.values.toList())
        override suspend fun getAllOrdersRaw(): List<OrderEntity> = orders.values.toList()
        override suspend fun getUnsyncedOrders(): List<OrderEntity> = orders.values.filter { it.isSynced == 0 }
        override suspend fun findOrderByUuid(uuid: String): OrderEntity? = orders.values.firstOrNull { it.uuidLocal == uuid }
        override suspend fun upsertOrder(order: OrderEntity) { orders[order.id] = order }
        override suspend fun softDeleteOrder(id: String, ts: String) {
            orders[id]?.let { orders[id] = it.copy(deletedAt = ts, updatedAt = ts, isSynced = 0) }
        }
        override suspend fun markOrdersSynced(uuids: List<String>) {
            orders.replaceAll { _, v -> if (v.uuidLocal in uuids) v.copy(isSynced = 1) else v }
        }
        override suspend fun purgeDeletedSyncedOrders() {
            orders.entries.removeIf { it.value.deletedAt != null && it.value.isSynced == 1 }
        }

        override fun observeInventory(): Flow<List<HaberdasheryItemEntity>> = flowOf(inventory.values.toList())
        override suspend fun getAllInventoryRaw(): List<HaberdasheryItemEntity> = inventory.values.toList()
        override suspend fun getUnsyncedInventory(): List<HaberdasheryItemEntity> = inventory.values.filter { it.isSynced == 0 }
        override suspend fun findInventoryByUuid(uuid: String): HaberdasheryItemEntity? = inventory.values.firstOrNull { it.uuidLocal == uuid }
        override suspend fun upsertInventoryItem(item: HaberdasheryItemEntity) { inventory[item.id] = item }
        override suspend fun softDeleteInventoryItem(id: String, ts: String) {
            inventory[id]?.let { inventory[id] = it.copy(deletedAt = ts, updatedAt = ts, isSynced = 0) }
        }
        override suspend fun markInventorySynced(uuids: List<String>) {
            inventory.replaceAll { _, v -> if (v.uuidLocal in uuids) v.copy(isSynced = 1) else v }
        }
        override suspend fun purgeDeletedSyncedInventory() {
            inventory.entries.removeIf { it.value.deletedAt != null && it.value.isSynced == 1 }
        }

        override fun observeStockMovements(): Flow<List<StockMovementEntity>> = flowOf(emptyList())
        override suspend fun insertStockMovement(movement: StockMovementEntity) {}

        override fun observeEmployees(): Flow<List<EmployeeEntity>> = flowOf(employees.values.toList())
        override suspend fun getAllEmployeesRaw(): List<EmployeeEntity> = employees.values.toList()
        override suspend fun getUnsyncedEmployees(): List<EmployeeEntity> = employees.values.filter { it.isSynced == 0 }
        override suspend fun findEmployeeByUuid(uuid: String): EmployeeEntity? = employees.values.firstOrNull { it.uuidLocal == uuid }
        override suspend fun upsertEmployee(employee: EmployeeEntity) { employees[employee.id] = employee }
        override suspend fun softDeleteEmployee(id: String, ts: String) {
            employees[id]?.let { employees[id] = it.copy(deletedAt = ts, updatedAt = ts, isSynced = 0) }
        }
        override suspend fun markEmployeesSynced(uuids: List<String>) {
            employees.replaceAll { _, v -> if (v.uuidLocal in uuids) v.copy(isSynced = 1) else v }
        }
        override suspend fun purgeDeletedSyncedEmployees() {
            employees.entries.removeIf { it.value.deletedAt != null && it.value.isSynced == 1 }
        }

        override fun observeExpenses(): Flow<List<ExpenseEntity>> = flowOf(expenses.values.toList())
        override suspend fun getAllExpensesRaw(): List<ExpenseEntity> = expenses.values.toList()
        override suspend fun getUnsyncedExpenses(): List<ExpenseEntity> = expenses.values.filter { it.isSynced == 0 }
        override suspend fun findExpenseByUuid(uuid: String): ExpenseEntity? = expenses.values.firstOrNull { it.uuidLocal == uuid }
        override suspend fun upsertExpense(expense: ExpenseEntity) { expenses[expense.id] = expense }
        override suspend fun softDeleteExpense(id: String, ts: String) {
            expenses[id]?.let { expenses[id] = it.copy(deletedAt = ts, updatedAt = ts, isSynced = 0) }
        }
        override suspend fun markExpensesSynced(uuids: List<String>) {
            expenses.replaceAll { _, v -> if (v.uuidLocal in uuids) v.copy(isSynced = 1) else v }
        }
        override suspend fun purgeDeletedSyncedExpenses() {
            expenses.entries.removeIf { it.value.deletedAt != null && it.value.isSynced == 1 }
        }

        override fun observePendingSyncCount(): Flow<Int> = flowOf(0)
        override suspend fun hardDeleteClientByUuid(uuid: String) { clients.entries.removeIf { it.value.uuidLocal == uuid } }
        override suspend fun hardDeleteModelByUuid(uuid: String) { models.entries.removeIf { it.value.uuidLocal == uuid } }
        override suspend fun hardDeleteInventoryByUuid(uuid: String) { inventory.entries.removeIf { it.value.uuidLocal == uuid } }
        override suspend fun hardDeleteEmployeeByUuid(uuid: String) { employees.entries.removeIf { it.value.uuidLocal == uuid } }
        override suspend fun hardDeleteOrderByUuid(uuid: String) { orders.entries.removeIf { it.value.uuidLocal == uuid } }
        override suspend fun hardDeleteExpenseByUuid(uuid: String) { expenses.entries.removeIf { it.value.uuidLocal == uuid } }

        override fun observeSettings(): Flow<WorkshopSettingsEntity?> = flowOf(null)
        override suspend fun upsertSettings(settings: WorkshopSettingsEntity) {}

        override fun observeLicenseState(): Flow<LicenseStateEntity?> = flowOf(licenseState)
        override suspend fun getLicenseState(): LicenseStateEntity? = licenseState
        override suspend fun upsertLicenseState(license: LicenseStateEntity) { licenseState = license }
        override suspend fun markLicenseSynced() { licenseState = licenseState?.copy(isSynced = 1) }

        override fun observeUsedCodes(): Flow<List<UsedActivationCodeEntity>> = flowOf(usedCodes.values.toList())
        override suspend fun getUnsyncedUsedCodes(): List<UsedActivationCodeEntity> = usedCodes.values.filter { it.isSynced == 0 }
        override suspend fun findUsedCode(code: String): UsedActivationCodeEntity? = usedCodes[code]
        override suspend fun insertUsedCode(entry: UsedActivationCodeEntity) { usedCodes.putIfAbsent(entry.code, entry) }
        override suspend fun markUsedCodesSynced(codes: List<String>) {
            codes.forEach { c -> usedCodes[c]?.let { usedCodes[c] = it.copy(isSynced = 1) } }
        }

        override suspend fun clearOrders() = orders.clear()
        override suspend fun clearClients() = clients.clear()
        override suspend fun clearModels() = models.clear()
        override suspend fun clearInventory() = inventory.clear()
        override suspend fun clearMovements() {}
        override suspend fun clearEmployees() = employees.clear()
        override suspend fun clearExpenses() = expenses.clear()
    }
}
