package com.atelierpro.couture.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.atelierpro.couture.data.*
import com.atelierpro.couture.license.LicenseManager
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class SyncStatusState(
    val isSyncing: Boolean = false,
    val isOnline: Boolean = false,
    val isAuthenticated: Boolean = false,
    val lastSyncTime: String? = null,
    val lastError: String? = null
)

/**
 * Service de Synchronisation Automatique Offline-First (Room SQLite <-> API PHP/MySQL).
 *
 * Cycle exécuté :
 * 1) Upload prioritaire des images locales non encore envoyées (POST /api/upload/image)
 * 2) PUSH des lignes locales où is_synced == 0 (POST /api/sync/push)
 * 3) CONFIRMATION locale : passage à is_synced = 1 et purge physique des deletedAt != null
 * 4) PULL incrémental (GET /api/sync/pull?last_sync=...) et MERGE (UPSERT isSynced = 1)
 */
class SyncManager internal constructor(
    private val prefs: SyncPreferencesStore,
    private val daoProvider: () -> AtelierDao,
    private val isNetworkOnlineFn: () -> Boolean,
    private val scheduleRetryFn: () -> Unit,
    private val registerNetworkCallbackFn: ((() -> Unit) -> Unit)? = null,
    private val deviceNameProvider: () -> String = { buildAndroidDeviceName() },
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val autoTriggerAfterAuth: Boolean = true
) {

    private constructor(appContext: Context) : this(
        prefs = SyncPreferences(appContext),
        daoProvider = { AtelierDatabase.getInstance(appContext).atelierDao() },
        isNetworkOnlineFn = { checkAndroidNetworkOnline(appContext) },
        scheduleRetryFn = { enqueueAndroidRetryWorker(appContext) },
        registerNetworkCallbackFn = null,
        deviceNameProvider = { buildAndroidDeviceName() },
        httpClient = defaultHttpClient(),
        autoTriggerAfterAuth = true
    ) {
        this.androidAppContext = appContext.applicationContext
    }

    private var androidAppContext: Context? = null
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Verrou atomique empêchant deux synchronisations simultanées
    private val syncLock = AtomicBoolean(false)
    private var networkCallbackRegistered = false

    private val _status = MutableStateFlow(
        SyncStatusState(
            isSyncing = false,
            isOnline = isNetworkOnlineFn(),
            isAuthenticated = prefs.isAuthenticated(),
            lastSyncTime = prefs.lastSyncTimestamp.takeIf { it != SyncPreferences.DEFAULT_LAST_SYNC }
        )
    )
    val status: StateFlow<SyncStatusState> = _status.asStateFlow()

    private fun dao(): AtelierDao = daoProvider()

    private fun checkNetworkOnline(): Boolean = isNetworkOnlineFn()

    private fun enqueueRetryWorker() {
        if (prefs.isAuthenticated()) {
            scheduleRetryFn()
        }
    }

    /**
     * Démarre l'écoute réseau (ConnectivityManager) et lance une synchro au démarrage si connecté.
     */
    fun startAutoSync() {
        registerConnectivityListener()
        if (checkNetworkOnline() && prefs.isAuthenticated()) {
            triggerAsyncSync(silent = true)
        }
    }

    fun triggerAsyncSync(silent: Boolean = false) {
        scope.launch {
            syncNow(silent = silent)
        }
    }

    /**
     * Exécute le cycle complet : Upload Images -> PUSH -> Confirmation -> PULL -> Merge.
     * En cas d'erreur réseau/timeout, ne bloque jamais l'UI et planifie un retry via WorkManager.
     */
    suspend fun syncNow(silent: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        val token = prefs.authToken
        if (token.isNullOrBlank()) {
            _status.value = _status.value.copy(isAuthenticated = false)
            return@withContext Result.success(Unit)
        }

        if (!checkNetworkOnline()) {
            _status.value = _status.value.copy(isOnline = false)
            enqueueRetryWorker()
            return@withContext Result.failure(IllegalStateException("Appareil hors-ligne"))
        }

        // Verrou anti-concurrence
        if (!syncLock.compareAndSet(false, true)) {
            return@withContext Result.success(Unit)
        }

        _status.value = _status.value.copy(
            isSyncing = true,
            isOnline = true,
            isAuthenticated = true,
            lastError = null
        )

        return@withContext try {
            // 0. Vérification du quota & statut appareil (POST /api/check_device)
            checkDeviceRegistration(token)

            // 1. Upload prioritaire des images locales (remoteUrl == null)
            uploadPendingImages(token)

            // 2 & 3. PUSH batch JSON + confirmation locale (isSynced = 1 + purge soft deletes)
            pushUnsyncedData(token)

            // 4. PULL incrémental + MERGE dans Room SQLite
            pullAndMerge(token)

            _status.value = _status.value.copy(
                isSyncing = false,
                lastSyncTime = prefs.lastSyncTimestamp,
                lastError = null
            )
            Result.success(Unit)
        } catch (e: DeviceAuthorizationException) {
            _status.value = _status.value.copy(
                isSyncing = false,
                isAuthenticated = prefs.isAuthenticated(),
                lastError = e.message
            )
            Result.failure(e)
        } catch (e: Exception) {
            if (!silent) {
                _status.value = _status.value.copy(lastError = e.message ?: "Erreur de synchronisation")
            }
            _status.value = _status.value.copy(
                isSyncing = false,
                isAuthenticated = prefs.isAuthenticated()
            )
            enqueueRetryWorker()
            Result.failure(e)
        } finally {
            syncLock.set(false)
        }
    }

    private class DeviceAuthorizationException(message: String) : IllegalStateException(message)

    // -------------------------------------------------------------------------
    // ÉTAPE 0 : VÉRIFICATION APPAREIL & STATUT LICENCE (/api/check_device)
    // -------------------------------------------------------------------------
    private suspend fun checkDeviceRegistration(token: String) {
        val db = dao()
        val licenseState = LicenseManager.ensureInitialized(db)
        val deviceName = deviceNameProvider().ifBlank { "Appareil Android" }

        val payload = JsonObject().apply {
            addProperty("device_uuid", prefs.deviceUuid)
            addProperty("device_name", deviceName)
            addProperty("platform", "android")
            addProperty("code_atelier", licenseState.workshopCode)
        }

        val request = Request.Builder()
            .url("${prefs.apiBaseUrl}/api/check_device")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (resp.code == 401) {
                prefs.clearSession()
                throw SecurityException("Session expirée (401)")
            }

            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            if (resp.code == 403) {
                val msg = json?.get("message")?.asString
                    ?: "Appareil bloqué ou quota maximum atteint."
                throw DeviceAuthorizationException(msg)
            }
            if (!resp.isSuccessful) {
                throw IllegalStateException("Vérification appareil HTTP ${resp.code}")
            }

            // Mettre à jour la date d'expiration locale si le backoffice l'a prolongée
            val serverExpRaw = json?.get("date_expiration")?.takeIf { !it.isJsonNull }?.asString?.take(10)
            val serverPlan = json?.get("plan_abonnement")?.takeIf { !it.isJsonNull }?.asString ?: "free"
            if (!serverExpRaw.isNullOrBlank() && serverExpRaw > licenseState.expirationDate) {
                db.upsertLicenseState(
                    licenseState.copy(
                        planType = if (serverPlan == "free") licenseState.planType else "pro",
                        expirationDate = serverExpRaw,
                        isSynced = 1,
                        updatedAt = SyncTimestamp.nowIsoUtc()
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // ÉTAPE 1 : UPLOAD PRIORITAIRE DES IMAGES (multipart/form-data)
    // -------------------------------------------------------------------------
    private suspend fun uploadPendingImages(token: String) {
        val db = dao()

        // Modèles avec photo locale non encore uploadée
        db.getUnsyncedModels()
            .filter { it.deletedAt == null && it.photoPath.isNotBlank() && it.remoteUrl.isNullOrBlank() }
            .forEach { model ->
                val file = File(model.photoPath)
                if (file.exists()) {
                    val url = uploadSingleImage(token, file)
                    db.upsertModel(model.copy(remoteUrl = url, updatedAt = SyncTimestamp.nowIsoUtc(), isSynced = 0))
                }
            }

        // Commandes avec photo de tissu locale non encore uploadée
        db.getUnsyncedOrders()
            .filter { it.deletedAt == null && it.fabricPhotoPath.isNotBlank() && it.remoteUrl.isNullOrBlank() }
            .forEach { order ->
                val file = File(order.fabricPhotoPath)
                if (file.exists()) {
                    val url = uploadSingleImage(token, file)
                    db.upsertOrder(order.copy(remoteUrl = url, updatedAt = SyncTimestamp.nowIsoUtc(), isSynced = 0))
                }
            }

        // Dépenses avec justificatif photo non encore uploadé
        db.getUnsyncedExpenses()
            .filter { it.deletedAt == null && it.receiptPhotoPath.isNotBlank() && it.remoteUrl.isNullOrBlank() }
            .forEach { expense ->
                val file = File(expense.receiptPhotoPath)
                if (file.exists()) {
                    val url = uploadSingleImage(token, file)
                    db.upsertExpense(expense.copy(remoteUrl = url, updatedAt = SyncTimestamp.nowIsoUtc(), isSynced = 0))
                }
            }
    }

    private fun uploadSingleImage(token: String, file: File): String {
        val mimeType = when (file.extension.lowercase()) {
            "webp" -> "image/webp"
            "png" -> "image/png"
            else -> "image/jpeg"
        }.toMediaType()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody(mimeType))
            .build()

        val request = Request.Builder()
            .url("${prefs.apiBaseUrl}/api/upload/image")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (resp.code == 401) throw SecurityException("Session expirée (401)")
            if (!resp.isSuccessful) throw IllegalStateException("Upload image HTTP ${resp.code}")

            val json = JsonParser.parseString(raw).asJsonObject
            return json.get("url")?.asString
                ?: throw IllegalStateException("URL absente dans la réponse upload")
        }
    }

    // -------------------------------------------------------------------------
    // ÉTAPES 2 & 3 : PUSH JSON BATCH + CONFIRMATION & PURGE SOFT DELETES
    // -------------------------------------------------------------------------
    private suspend fun pushUnsyncedData(token: String) {
        val db = dao()

        val unsyncedClients = db.getUnsyncedClients()
        val unsyncedMercerie = db.getUnsyncedInventory()
        val unsyncedModels = db.getUnsyncedModels()
        val unsyncedEmployees = db.getUnsyncedEmployees()
        val unsyncedOrders = db.getUnsyncedOrders()
        val unsyncedExpenses = db.getUnsyncedExpenses()
        val licenseState = db.getLicenseState()?.takeIf { it.isSynced == 0 }
        val unsyncedCodes = db.getUnsyncedUsedCodes()

        val nothingToPush = unsyncedClients.isEmpty() &&
            unsyncedMercerie.isEmpty() &&
            unsyncedModels.isEmpty() &&
            unsyncedEmployees.isEmpty() &&
            unsyncedOrders.isEmpty() &&
            unsyncedExpenses.isEmpty() &&
            licenseState == null &&
            unsyncedCodes.isEmpty()

        if (nothingToPush) return

        // Maps locales id -> uuidLocal pour lier proprement les clés étrangères MySQL
        val allClientsById = db.getAllClientsRaw().associateBy { it.id }
        val allModelsById = db.getAllModelsRaw().associateBy { it.id }
        val allInventoryById = db.getAllInventoryRaw().associateBy { it.id }
        val allEmployeesById = db.getAllEmployeesRaw().associateBy { it.id }

        val root = JsonObject().apply {
            add("clients", JsonArray().apply {
                unsyncedClients.forEach { c ->
                    add(JsonObject().apply {
                        addProperty("uuid_local", c.uuidLocal)
                        addProperty("nom", c.fullName)
                        addProperty("telephone", c.phone)
                        addProperty("adresse", c.address)
                        addProperty("notes", c.notes)
                        addProperty("updated_at", c.updatedAt)
                        if (c.deletedAt != null) addProperty("deleted_at", c.deletedAt) else add("deleted_at", null)
                    })
                }
            })

            add("mercerie", JsonArray().apply {
                unsyncedMercerie.forEach { m ->
                    add(JsonObject().apply {
                        addProperty("uuid_local", m.uuidLocal)
                        addProperty("nom", m.name)
                        addProperty("unite", m.unit)
                        addProperty("prix_unitaire", m.unitPrice)
                        addProperty("stock_actuel", m.quantityInStock)
                        addProperty("seuil_alerte", m.criticalThreshold)
                        addProperty("updated_at", m.updatedAt)
                        if (m.deletedAt != null) addProperty("deleted_at", m.deletedAt) else add("deleted_at", null)
                    })
                }
            })

            add("modeles", JsonArray().apply {
                unsyncedModels.forEach { mod ->
                    add(JsonObject().apply {
                        addProperty("uuid_local", mod.uuidLocal)
                        addProperty("nom", mod.name)
                        addProperty("categorie", mod.category)
                        addProperty("photo_url", mod.remoteUrl ?: "")
                        addProperty("prix_base", mod.defaultPrice)
                        addProperty("jours_confection", mod.estimatedDays)
                        add("mesures_requises", gson.toJsonTree(mod.measurementKeys))
                        add("recette", JsonArray().apply {
                            mod.recipe.forEach { rec ->
                                val itemUuid = allInventoryById[rec.itemId]?.uuidLocal ?: rec.itemId
                                add(JsonObject().apply {
                                    addProperty("mercerie_uuid", itemUuid)
                                    addProperty("quantite_necessaire", rec.quantity)
                                })
                            }
                        })
                        addProperty("updated_at", mod.updatedAt)
                        if (mod.deletedAt != null) addProperty("deleted_at", mod.deletedAt) else add("deleted_at", null)
                    })
                }
            })

            add("employes", JsonArray().apply {
                unsyncedEmployees.forEach { e ->
                    add(JsonObject().apply {
                        addProperty("uuid_local", e.uuidLocal)
                        addProperty("nom", e.fullName)
                        addProperty("telephone", e.phone)
                        addProperty("poste", e.role)
                        addProperty("type_remuneration", e.payMode)
                        addProperty("montant_remuneration", e.payRate)
                        addProperty("actif", e.active)
                        addProperty("updated_at", e.updatedAt)
                        if (e.deletedAt != null) addProperty("deleted_at", e.deletedAt) else add("deleted_at", null)
                    })
                }
            })

            add("commandes", JsonArray().apply {
                unsyncedOrders.forEach { o ->
                    val clientUuid = allClientsById[o.clientId]?.uuidLocal ?: o.clientId
                    val modelUuid = allModelsById[o.modelId]?.uuidLocal ?: o.modelId
                    val empUuid = o.assignedEmployeeId?.let { eid -> allEmployeesById[eid]?.uuidLocal ?: eid }
                    add(JsonObject().apply {
                        addProperty("uuid_local", o.uuidLocal)
                        addProperty("client_uuid", clientUuid)
                        addProperty("modele_uuid", modelUuid)
                        if (empUuid != null) addProperty("employe_uuid", empUuid) else add("employe_uuid", null)
                        addProperty("photo_tissu_url", o.remoteUrl ?: "")
                        add("mesures_client", gson.toJsonTree(o.measurements))
                        addProperty("date_commande", o.orderDate)
                        if (o.fittingDate != null) addProperty("date_essayage", o.fittingDate) else add("date_essayage", null)
                        addProperty("date_livraison_prevue", o.deliveryDate)
                        addProperty("prix_total", o.totalPrice)
                        addProperty("avance_payee", o.advancePaid)
                        addProperty("statut", o.status)
                        addProperty("notes_commande", o.notes)
                        addProperty("updated_at", o.updatedAt)
                        if (o.deletedAt != null) addProperty("deleted_at", o.deletedAt) else add("deleted_at", null)
                    })
                }
            })

            add("depenses", JsonArray().apply {
                unsyncedExpenses.forEach { d ->
                    add(JsonObject().apply {
                        addProperty("uuid_local", d.uuidLocal)
                        addProperty("categorie", d.category)
                        addProperty("montant", d.amount)
                        addProperty("date_depense", d.date)
                        addProperty("description", d.description)
                        addProperty("justificatif_url", d.remoteUrl ?: "")
                        addProperty("updated_at", d.updatedAt)
                        if (d.deletedAt != null) addProperty("deleted_at", d.deletedAt) else add("deleted_at", null)
                    })
                }
            })

            if (licenseState != null) {
                add("licence", JsonObject().apply {
                    addProperty("workshop_code", licenseState.workshopCode)
                    addProperty("plan_abonnement", if (licenseState.planType == "pro") "pro" else "free")
                    addProperty("date_expiration", licenseState.expirationDate)
                    addProperty("updated_at", licenseState.updatedAt)
                })
            }

            if (unsyncedCodes.isNotEmpty()) {
                val wCode = licenseState?.workshopCode ?: db.getLicenseState()?.workshopCode ?: ""
                add("codes_utilises", JsonArray().apply {
                    unsyncedCodes.forEach { entry ->
                        add(JsonObject().apply {
                            addProperty("workshop_code", wCode)
                            addProperty("code", entry.code)
                            addProperty("duration_code", entry.durationCode)
                            addProperty("days_added", entry.daysAdded)
                            addProperty("used_at", entry.usedAt)
                        })
                    }
                })
            }
        }

        val request = Request.Builder()
            .url("${prefs.apiBaseUrl}/api/sync/push")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(root).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (resp.code == 401) {
                prefs.clearSession()
                throw SecurityException("Session expirée (401)")
            }
            if (!resp.isSuccessful) throw IllegalStateException("Push HTTP ${resp.code}")

            val json = JsonParser.parseString(raw).asJsonObject
            val syncedObj = json.getAsJsonObject("synced_uuids") ?: return@use

            fun extractUuids(key: String): List<String> =
                syncedObj.getAsJsonArray(key)?.map { it.asString } ?: emptyList()

            extractUuids("clients").takeIf { it.isNotEmpty() }?.let {
                db.markClientsSynced(it)
                db.purgeDeletedSyncedClients()
            }
            extractUuids("mercerie").takeIf { it.isNotEmpty() }?.let {
                db.markInventorySynced(it)
                db.purgeDeletedSyncedInventory()
            }
            extractUuids("modeles").takeIf { it.isNotEmpty() }?.let {
                db.markModelsSynced(it)
                db.purgeDeletedSyncedModels()
            }
            extractUuids("employes").takeIf { it.isNotEmpty() }?.let {
                db.markEmployeesSynced(it)
                db.purgeDeletedSyncedEmployees()
            }
            extractUuids("commandes").takeIf { it.isNotEmpty() }?.let {
                db.markOrdersSynced(it)
                db.purgeDeletedSyncedOrders()
            }
            extractUuids("depenses").takeIf { it.isNotEmpty() }?.let {
                db.markExpensesSynced(it)
                db.purgeDeletedSyncedExpenses()
            }

            if (json.get("licence_synced")?.asBoolean == true) {
                db.markLicenseSynced()
            }
            json.getAsJsonArray("synced_codes")?.map { it.asString }?.takeIf { it.isNotEmpty() }?.let {
                db.markUsedCodesSynced(it)
            }
        }
    }

    // -------------------------------------------------------------------------
    // ÉTAPE 4 : PULL INCRÉMENTAL & MERGE DANS SQLITE (isSynced = 1)
    // -------------------------------------------------------------------------
    private suspend fun pullAndMerge(token: String) {
        val encodedTs = URLEncoder.encode(prefs.lastSyncTimestamp, "UTF-8")
        val request = Request.Builder()
            .url("${prefs.apiBaseUrl}/api/sync/pull?last_sync=$encodedTs")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (resp.code == 401) {
                prefs.clearSession()
                throw SecurityException("Session expirée (401)")
            }
            if (!resp.isSuccessful) throw IllegalStateException("Pull HTTP ${resp.code}")

            val root = JsonParser.parseString(raw).asJsonObject
            val serverTime = root.get("server_time")?.asString
            val data = root.getAsJsonObject("data") ?: return@use
            val db = dao()

            // Regrouper les recettes par modele_uuid
            val recipesByModelUuid = mutableMapOf<String, MutableList<RecipeEntry>>()
            data.getAsJsonArray("modele_mercerie")?.forEach { el ->
                val obj = el.asJsonObject
                val mUuid = obj.get("modele_uuid").asString
                val itemUuid = obj.get("mercerie_uuid").asString
                val qty = obj.get("quantite_necessaire").asInt
                val localItemId = db.findInventoryByUuid(itemUuid)?.id ?: itemUuid
                recipesByModelUuid.getOrPut(mUuid) { mutableListOf() }.add(RecipeEntry(localItemId, qty))
            }

            // 1. Clients
            data.getAsJsonArray("clients")?.forEach { el ->
                val o = el.asJsonObject
                val uuid = o.get("uuid_local").asString
                val deletedAt = o.get("deleted_at")?.takeIf { !it.isJsonNull }?.asString
                if (deletedAt != null) {
                    db.hardDeleteClientByUuid(uuid)
                } else {
                    val existing = db.findClientByUuid(uuid)
                    db.upsertClient(
                        ClientEntity(
                            id = existing?.id ?: uuid,
                            fullName = o.get("nom").asString,
                            phone = o.get("telephone")?.asString ?: "",
                            address = o.get("adresse")?.asString ?: "",
                            notes = o.get("notes")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            latestMeasurements = existing?.latestMeasurements ?: emptyMap(),
                            uuidLocal = uuid,
                            updatedAt = o.get("updated_at").asString,
                            isSynced = 1,
                            deletedAt = null
                        )
                    )
                }
            }

            // 2. Mercerie
            data.getAsJsonArray("mercerie")?.forEach { el ->
                val o = el.asJsonObject
                val uuid = o.get("uuid_local").asString
                val deletedAt = o.get("deleted_at")?.takeIf { !it.isJsonNull }?.asString
                if (deletedAt != null) {
                    db.hardDeleteInventoryByUuid(uuid)
                } else {
                    val existing = db.findInventoryByUuid(uuid)
                    db.upsertInventoryItem(
                        HaberdasheryItemEntity(
                            id = existing?.id ?: uuid,
                            name = o.get("nom").asString,
                            unit = o.get("unite").asString,
                            unitPrice = o.get("prix_unitaire").asDouble.toLong(),
                            quantityInStock = o.get("stock_actuel").asInt,
                            criticalThreshold = o.get("seuil_alerte").asInt,
                            uuidLocal = uuid,
                            updatedAt = o.get("updated_at").asString,
                            isSynced = 1,
                            deletedAt = null
                        )
                    )
                }
            }

            // 3. Modèles
            data.getAsJsonArray("modeles")?.forEach { el ->
                val o = el.asJsonObject
                val uuid = o.get("uuid_local").asString
                val deletedAt = o.get("deleted_at")?.takeIf { !it.isJsonNull }?.asString
                if (deletedAt != null) {
                    db.hardDeleteModelByUuid(uuid)
                } else {
                    val existing = db.findModelByUuid(uuid)
                    val measList = o.getAsJsonArray("mesures_requises")?.map { it.asString } ?: emptyList()
                    val mergedRecipe = recipesByModelUuid[uuid] ?: existing?.recipe ?: emptyList()
                    db.upsertModel(
                        GarmentModelEntity(
                            id = existing?.id ?: uuid,
                            name = o.get("nom").asString,
                            category = o.get("categorie").asString,
                            photoPath = existing?.photoPath ?: "",
                            remoteUrl = o.get("photo_url")?.asString?.takeIf { it.isNotBlank() },
                            defaultPrice = o.get("prix_base").asDouble.toLong(),
                            estimatedDays = o.get("jours_confection").asInt,
                            measurementKeys = measList,
                            recipe = mergedRecipe,
                            notes = existing?.notes ?: "",
                            uuidLocal = uuid,
                            updatedAt = o.get("updated_at").asString,
                            isSynced = 1,
                            deletedAt = null
                        )
                    )
                }
            }

            // 4. Employés
            data.getAsJsonArray("employes")?.forEach { el ->
                val o = el.asJsonObject
                val uuid = o.get("uuid_local").asString
                val deletedAt = o.get("deleted_at")?.takeIf { !it.isJsonNull }?.asString
                if (deletedAt != null) {
                    db.hardDeleteEmployeeByUuid(uuid)
                } else {
                    val existing = db.findEmployeeByUuid(uuid)
                    db.upsertEmployee(
                        EmployeeEntity(
                            id = existing?.id ?: uuid,
                            fullName = o.get("nom").asString,
                            phone = o.get("telephone").asString,
                            role = o.get("poste").asString,
                            payMode = o.get("type_remuneration").asString,
                            payRate = o.get("montant_remuneration").asDouble.toLong(),
                            active = o.get("actif").asInt == 1,
                            uuidLocal = uuid,
                            updatedAt = o.get("updated_at").asString,
                            isSynced = 1,
                            deletedAt = null
                        )
                    )
                }
            }

            // 5. Commandes
            data.getAsJsonArray("commandes")?.forEach { el ->
                val o = el.asJsonObject
                val uuid = o.get("uuid_local").asString
                val deletedAt = o.get("deleted_at")?.takeIf { !it.isJsonNull }?.asString
                if (deletedAt != null) {
                    db.hardDeleteOrderByUuid(uuid)
                } else {
                    val existing = db.findOrderByUuid(uuid)
                    val clientId = db.findClientByUuid(o.get("client_uuid").asString)?.id ?: o.get("client_uuid").asString
                    val modelId = db.findModelByUuid(o.get("modele_uuid").asString)?.id ?: o.get("modele_uuid").asString
                    val empUuid = o.get("employe_uuid")?.takeIf { !it.isJsonNull }?.asString
                    val empId = empUuid?.let { db.findEmployeeByUuid(it)?.id ?: it }
                    val measMap = mutableMapOf<String, Double>()
                    o.getAsJsonObject("mesures_client")?.entrySet()?.forEach { (k, v) ->
                        measMap[k] = v.asDouble
                    }

                    db.upsertOrder(
                        OrderEntity(
                            id = existing?.id ?: uuid,
                            reference = existing?.reference ?: "CMD-${uuid.take(6).uppercase()}",
                            clientId = clientId,
                            modelId = modelId,
                            fabricPhotoPath = existing?.fabricPhotoPath ?: "",
                            remoteUrl = o.get("photo_tissu_url")?.asString?.takeIf { it.isNotBlank() },
                            fabricNotes = existing?.fabricNotes ?: "",
                            measurements = measMap,
                            status = o.get("statut").asString,
                            stockDeducted = existing?.stockDeducted ?: (o.get("statut").asString != OrderStatus.EN_ATTENTE.label),
                            assignedEmployeeId = empId,
                            orderDate = o.get("date_commande").asString,
                            fittingDate = o.get("date_essayage")?.takeIf { !it.isJsonNull }?.asString,
                            deliveryDate = o.get("date_livraison_prevue").asString,
                            totalPrice = o.get("prix_total").asDouble.toLong(),
                            advancePaid = o.get("avance_payee").asDouble.toLong(),
                            notes = o.get("notes_commande")?.takeIf { !it.isJsonNull }?.asString ?: "",
                            uuidLocal = uuid,
                            updatedAt = o.get("updated_at").asString,
                            isSynced = 1,
                            deletedAt = null
                        )
                    )
                }
            }

            // 6. Dépenses
            data.getAsJsonArray("depenses")?.forEach { el ->
                val o = el.asJsonObject
                val uuid = o.get("uuid_local").asString
                val deletedAt = o.get("deleted_at")?.takeIf { !it.isJsonNull }?.asString
                if (deletedAt != null) {
                    db.hardDeleteExpenseByUuid(uuid)
                } else {
                    val existing = db.findExpenseByUuid(uuid)
                    db.upsertExpense(
                        ExpenseEntity(
                            id = existing?.id ?: uuid,
                            category = o.get("categorie").asString,
                            amount = o.get("montant").asDouble.toLong(),
                            date = o.get("date_depense").asString,
                            description = o.get("description")?.asString ?: "",
                            receiptPhotoPath = existing?.receiptPhotoPath ?: "",
                            remoteUrl = o.get("justificatif_url")?.asString?.takeIf { it.isNotBlank() },
                            uuidLocal = uuid,
                            updatedAt = o.get("updated_at").asString,
                            isSynced = 1,
                            deletedAt = null
                        )
                    )
                }
            }

            // 7. Licence & Codes utilisés (mise à jour depuis le serveur si expiration plus récente)
            data.getAsJsonObject("licence")?.let { lic ->
                val serverExpRaw = lic.get("date_expiration")?.takeIf { !it.isJsonNull }?.asString?.take(10)
                val serverPlan = lic.get("plan_abonnement")?.takeIf { !it.isJsonNull }?.asString ?: "free"
                val localLic = db.getLicenseState()
                if (localLic != null && !serverExpRaw.isNullOrBlank() && serverExpRaw > localLic.expirationDate) {
                    db.upsertLicenseState(
                        localLic.copy(
                            planType = if (serverPlan == "free") localLic.planType else "pro",
                            expirationDate = serverExpRaw,
                            isSynced = 1,
                            updatedAt = SyncTimestamp.nowIsoUtc()
                        )
                    )
                }
            }
            data.getAsJsonArray("codes_utilises")?.forEach { el ->
                val obj = el.asJsonObject
                val code = obj.get("code")?.asString ?: return@forEach
                db.insertUsedCode(
                    UsedActivationCodeEntity(
                        code = code,
                        durationCode = obj.get("duration_code")?.asString ?: "M1",
                        daysAdded = obj.get("days_added")?.asInt ?: 30,
                        usedAt = obj.get("used_at")?.asString ?: SyncTimestamp.nowIsoUtc(),
                        isSynced = 1
                    )
                )
            }

            if (!serverTime.isNullOrBlank()) {
                prefs.lastSyncTimestamp = serverTime
            }
        }
    }

    // -------------------------------------------------------------------------
    // AUTHENTIFICATION SAAS (LOGIN / REGISTER)
    // -------------------------------------------------------------------------
    suspend fun authenticate(
        baseUrl: String,
        email: String,
        password: String,
        registerName: String? = null,
        registerPhone: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            prefs.apiBaseUrl = baseUrl
            val endpoint = if (registerName != null) "/api/auth/register" else "/api/auth/login"
            val bodyJson = JsonObject().apply {
                addProperty("email", email.trim())
                addProperty("password", password)
                if (registerName != null) {
                    addProperty("nom_atelier", registerName.trim())
                    addProperty("telephone", registerPhone?.trim() ?: "")
                }
            }
            val req = Request.Builder()
                .url("${prefs.apiBaseUrl}$endpoint")
                .post(gson.toJson(bodyJson).toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: ""
                val json = JsonParser.parseString(raw).asJsonObject
                if (!resp.isSuccessful) {
                    throw IllegalStateException(json.get("error")?.asString ?: "Erreur authentification (${resp.code})")
                }
                val token = json.get("token")?.asString ?: throw IllegalStateException("Token manquant")
                prefs.authToken = token
                prefs.connectedEmail = email.trim()
                _status.value = _status.value.copy(isAuthenticated = true, lastError = null)
                token
            }
        }.onSuccess {
            if (autoTriggerAfterAuth) {
                triggerAsyncSync(silent = true)
            }
        }
    }

    fun disconnectCloud() {
        prefs.clearSession()
        _status.value = _status.value.copy(isAuthenticated = false, lastSyncTime = null, lastError = null)
    }

    fun getPreferences(): SyncPreferencesStore = prefs

    // -------------------------------------------------------------------------
    // ÉCOUTE RÉSEAU (ConnectivityManager) & WORKMANAGER RETRY
    // -------------------------------------------------------------------------
    private fun registerConnectivityListener() {
        if (networkCallbackRegistered) return
        val customReg = registerNetworkCallbackFn
        if (customReg != null) {
            customReg {
                if (prefs.isAuthenticated()) {
                    triggerAsyncSync(silent = true)
                }
            }
            networkCallbackRegistered = true
            return
        }
        val ctx = androidAppContext ?: return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !_status.value.isOnline
                _status.value = _status.value.copy(isOnline = true)
                // Déclenche automatiquement dès le passage Hors-ligne -> En ligne
                if (wasOffline && prefs.isAuthenticated()) {
                    triggerAsyncSync(silent = true)
                }
            }

            override fun onLost(network: Network) {
                _status.value = _status.value.copy(isOnline = checkNetworkOnline())
            }
        })
        networkCallbackRegistered = true
    }

    companion object {
        @Volatile
        private var INSTANCE: SyncManager? = null

        fun getInstance(context: Context): SyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

        private fun buildAndroidDeviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Appareil Android" }

        private fun checkAndroidNetworkOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        private fun enqueueAndroidRetryWorker(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val work = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "atelierpro_auto_sync",
                ExistingWorkPolicy.KEEP,
                work
            )
        }
    }
}
