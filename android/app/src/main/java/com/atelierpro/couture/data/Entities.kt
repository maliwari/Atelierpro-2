package com.atelierpro.couture.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object SyncTimestamp {
    private val ISO_UTC = DateTimeFormatter.ISO_INSTANT

    fun nowIsoUtc(): String = ISO_UTC.format(Instant.now())

    fun newUuid(): String = UUID.randomUUID().toString()
}

data class RecipeEntry(
    val itemId: String,
    val quantity: Int
)

enum class OrderStatus(val label: String) {
    EN_ATTENTE("En attente"),
    COUPE("Coupe"),
    COUTURE("Couture"),
    ESSAYAGE("Essayage"),
    PRET("Prêt"),
    LIVRE("Livré")
}

object AtelierConstants {
    val GARMENT_CATEGORIES = listOf("Homme", "Femme", "Enfant", "Cérémonie")
    val HABERDASHERY_UNITS = listOf("pièce", "bobine", "mètre", "paquet")
    val EMPLOYEE_ROLES = listOf("Tailleur", "Coupeur", "Brodeur", "Apprenti")
    val EXPENSE_CATEGORIES = listOf(
        "Loyer atelier",
        "Électricité/Eau",
        "Achat Mercerie",
        "Salaires",
        "Entretien machines",
        "Divers"
    )
    val CURRENCIES = listOf("FCFA", "€", "MAD")
    val STANDARD_MEASUREMENTS = listOf(
        "Tour de poitrine",
        "Tour de taille",
        "Tour de hanches",
        "Carrure",
        "Longueur manche",
        "Tour de bras",
        "Longueur robe / boubou",
        "Longueur veste / haut",
        "Longueur pantalon",
        "Tour de cuisse",
        "Tour de cou",
        "Bas pantalon"
    )
}

@Entity(
    tableName = "garment_models",
    indices = [Index(value = ["uuidLocal"], unique = true)]
)
data class GarmentModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val photoPath: String = "",
    val remoteUrl: String? = null,
    val defaultPrice: Long,
    val estimatedDays: Int,
    val measurementKeys: List<String>,
    val recipe: List<RecipeEntry>,
    val notes: String = "",
    val uuidLocal: String = SyncTimestamp.newUuid(),
    val updatedAt: String = SyncTimestamp.nowIsoUtc(),
    val isSynced: Int = 0,
    val deletedAt: String? = null
)

@Entity(
    tableName = "clients",
    indices = [Index(value = ["uuidLocal"], unique = true)]
)
data class ClientEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phone: String,
    val address: String,
    val notes: String = "",
    val latestMeasurements: Map<String, Double> = emptyMap(),
    val uuidLocal: String = SyncTimestamp.newUuid(),
    val updatedAt: String = SyncTimestamp.nowIsoUtc(),
    val isSynced: Int = 0,
    val deletedAt: String? = null
)

@Entity(
    tableName = "orders",
    indices = [Index(value = ["uuidLocal"], unique = true)]
)
data class OrderEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val clientId: String,
    val modelId: String,
    val fabricPhotoPath: String = "",
    val remoteUrl: String? = null,
    val fabricNotes: String = "",
    val measurements: Map<String, Double>,
    val status: String = OrderStatus.EN_ATTENTE.label,
    val stockDeducted: Boolean = false,
    val assignedEmployeeId: String? = null,
    val orderDate: String,
    val fittingDate: String? = null,
    val deliveryDate: String,
    val totalPrice: Long,
    val advancePaid: Long,
    val notes: String = "",
    val uuidLocal: String = SyncTimestamp.newUuid(),
    val updatedAt: String = SyncTimestamp.nowIsoUtc(),
    val isSynced: Int = 0,
    val deletedAt: String? = null
)

@Entity(
    tableName = "haberdashery",
    indices = [Index(value = ["uuidLocal"], unique = true)]
)
data class HaberdasheryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val unitPrice: Long,
    val quantityInStock: Int,
    val criticalThreshold: Int,
    val uuidLocal: String = SyncTimestamp.newUuid(),
    val updatedAt: String = SyncTimestamp.nowIsoUtc(),
    val isSynced: Int = 0,
    val deletedAt: String? = null
)

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val type: String, // entree, sortie_auto, sortie_manuelle
    val quantity: Int,
    val date: String,
    val note: String,
    val orderId: String? = null
)

@Entity(
    tableName = "employees",
    indices = [Index(value = ["uuidLocal"], unique = true)]
)
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phone: String,
    val role: String,
    val payMode: String, // fixe, piece, commission
    val payRate: Long,
    val active: Boolean = true,
    val uuidLocal: String = SyncTimestamp.newUuid(),
    val updatedAt: String = SyncTimestamp.nowIsoUtc(),
    val isSynced: Int = 0,
    val deletedAt: String? = null
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["uuidLocal"], unique = true)]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val category: String,
    val amount: Long,
    val date: String,
    val description: String,
    val receiptPhotoPath: String = "",
    val remoteUrl: String? = null,
    val uuidLocal: String = SyncTimestamp.newUuid(),
    val updatedAt: String = SyncTimestamp.nowIsoUtc(),
    val isSynced: Int = 0,
    val deletedAt: String? = null
)

@Entity(tableName = "workshop_settings")
data class WorkshopSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val workshopName: String = "Mon Atelier de Couture",
    val workshopPhone: String = "",
    val workshopAddress: String = "",
    val workshopLogoPath: String = "",
    val currency: String = "FCFA",
    val pinCode: String = "", // Legacy fallback only when pinHash is empty
    val pinHash: String = "",
    val pinSalt: String = "",
    val pinEnabled: Boolean = true,
    val biometricEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false
)

@Entity(tableName = "license_state")
data class LicenseStateEntity(
    @PrimaryKey val id: Int = 1,
    val workshopCode: String,
    val planType: String = "trial", // trial, pro
    val expirationDate: String, // YYYY-MM-DD
    val lastKnownEpochMs: Long,
    val lastKnownIso: String,
    val isSynced: Int = 0,
    val updatedAt: String = SyncTimestamp.nowIsoUtc()
)

@Entity(tableName = "codes_utilises")
data class UsedActivationCodeEntity(
    @PrimaryKey val code: String,
    val durationCode: String, // M1 (30j), A1 (365j)
    val daysAdded: Int,
    val usedAt: String,
    val isSynced: Int = 0
)

class AtelierTypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        gson.fromJson(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()

    @TypeConverter
    fun fromRecipeList(value: List<RecipeEntry>): String = gson.toJson(value)

    @TypeConverter
    fun toRecipeList(value: String): List<RecipeEntry> =
        gson.fromJson(value, object : TypeToken<List<RecipeEntry>>() {}.type) ?: emptyList()

    @TypeConverter
    fun fromMeasurementsMap(value: Map<String, Double>): String = gson.toJson(value)

    @TypeConverter
    fun toMeasurementsMap(value: String): Map<String, Double> =
        gson.fromJson(value, object : TypeToken<Map<String, Double>>() {}.type) ?: emptyMap()
}
