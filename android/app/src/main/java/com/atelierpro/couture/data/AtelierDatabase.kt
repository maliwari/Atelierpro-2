package com.atelierpro.couture.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

@Database(
    entities = [
        GarmentModelEntity::class,
        ClientEntity::class,
        OrderEntity::class,
        HaberdasheryItemEntity::class,
        StockMovementEntity::class,
        EmployeeEntity::class,
        ExpenseEntity::class,
        WorkshopSettingsEntity::class,
        LicenseStateEntity::class,
        UsedActivationCodeEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(AtelierTypeConverters::class)
abstract class AtelierDatabase : RoomDatabase() {
    abstract fun atelierDao(): AtelierDao

    companion object {
        const val DB_FILE_NAME = "atelierpro_sqlite.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = SyncTimestamp.nowIsoUtc()
                val tables = listOf("garment_models", "clients", "orders", "haberdashery", "employees", "expenses")
                tables.forEach { tbl ->
                    db.execSQL("ALTER TABLE $tbl ADD COLUMN uuidLocal TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        "UPDATE $tbl SET uuidLocal = lower(" +
                            "hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                            "substr(hex(randomblob(2)), 2) || '-' || " +
                            "substr('89ab', (abs(random()) % 4) + 1, 1) || " +
                            "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))" +
                            ") WHERE uuidLocal = ''"
                    )
                    db.execSQL("ALTER TABLE $tbl ADD COLUMN updatedAt TEXT NOT NULL DEFAULT '$now'")
                    db.execSQL("ALTER TABLE $tbl ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $tbl ADD COLUMN deletedAt TEXT DEFAULT NULL")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${tbl}_uuidLocal ON $tbl(uuidLocal)")
                }
                db.execSQL("ALTER TABLE garment_models ADD COLUMN remoteUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE orders ADD COLUMN remoteUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE expenses ADD COLUMN remoteUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS license_state (
                        id INTEGER NOT NULL PRIMARY KEY,
                        workshopCode TEXT NOT NULL,
                        planType TEXT NOT NULL,
                        expirationDate TEXT NOT NULL,
                        lastKnownEpochMs INTEGER NOT NULL,
                        lastKnownIso TEXT NOT NULL,
                        isSynced INTEGER NOT NULL,
                        updatedAt TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS codes_utilises (
                        code TEXT NOT NULL PRIMARY KEY,
                        durationCode TEXT NOT NULL,
                        daysAdded INTEGER NOT NULL,
                        usedAt TEXT NOT NULL,
                        isSynced INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AtelierDatabase? = null

        fun getInstance(context: Context): AtelierDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AtelierDatabase::class.java,
                    DB_FILE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(InitCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun checkpointAndClose() {
            synchronized(this) {
                INSTANCE?.let { db ->
                    runCatching {
                        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                    }
                    db.close()
                }
                INSTANCE = null
            }
        }

        suspend fun seedSampleWorkshop(dao: AtelierDao) {
            val today = LocalDate.now()

            dao.upsertInventoryItem(HaberdasheryItemEntity("merc-1", "Bobine de fil polyester", "bobine", 750, 28, 8))
            dao.upsertInventoryItem(HaberdasheryItemEntity("merc-2", "Fil à broder doré", "bobine", 2200, 4, 6))
            dao.upsertInventoryItem(HaberdasheryItemEntity("merc-3", "Doublure satin", "mètre", 1500, 22, 6))
            dao.upsertInventoryItem(HaberdasheryItemEntity("merc-4", "Fermeture éclair 50cm", "pièce", 600, 15, 5))
            dao.upsertInventoryItem(HaberdasheryItemEntity("merc-5", "Thermocollant toile tailleur", "mètre", 1800, 14, 4))
            dao.upsertInventoryItem(HaberdasheryItemEntity("merc-6", "Boutons laiton doré (jeu de 6)", "paquet", 1200, 9, 4))

            dao.upsertModel(
                GarmentModelEntity(
                    id = "mod-1",
                    name = "Grand Boubou Brodé Royal",
                    category = "Cérémonie",
                    defaultPrice = 45000,
                    estimatedDays = 6,
                    measurementKeys = listOf("Tour de poitrine", "Carrure", "Longueur manche", "Longueur robe / boubou", "Tour de cou"),
                    recipe = listOf(RecipeEntry("merc-1", 2), RecipeEntry("merc-2", 2), RecipeEntry("merc-5", 1)),
                    notes = "Broderie col et poignets fil doré"
                )
            )
            dao.upsertModel(
                GarmentModelEntity(
                    id = "mod-2",
                    name = "Robe Sirène Satin & Dentelle",
                    category = "Femme",
                    defaultPrice = 38000,
                    estimatedDays = 5,
                    measurementKeys = listOf("Tour de poitrine", "Tour de taille", "Tour de hanches", "Carrure", "Longueur robe / boubou"),
                    recipe = listOf(RecipeEntry("merc-1", 2), RecipeEntry("merc-3", 3), RecipeEntry("merc-4", 1)),
                    notes = "Corsage doublé, fermeture invisible dos"
                )
            )
            dao.upsertModel(
                GarmentModelEntity(
                    id = "mod-3",
                    name = "Costume 3 Pièces Sur-Mesure",
                    category = "Homme",
                    defaultPrice = 65000,
                    estimatedDays = 8,
                    measurementKeys = listOf("Tour de poitrine", "Tour de taille", "Carrure", "Longueur manche", "Longueur pantalon"),
                    recipe = listOf(RecipeEntry("merc-1", 3), RecipeEntry("merc-3", 2), RecipeEntry("merc-5", 2)),
                    notes = "Entoilage complet, gilet 5 boutons"
                )
            )

            dao.upsertClient(
                ClientEntity(
                    id = "cli-1",
                    fullName = "Aminata Diallo",
                    phone = "+22376112233",
                    address = "Badalabougou, Bamako",
                    notes = "Essayage le matin",
                    latestMeasurements = mapOf(
                        "Tour de poitrine" to 96.0,
                        "Tour de taille" to 76.0,
                        "Tour de hanches" to 104.0,
                        "Carrure" to 39.0,
                        "Longueur robe / boubou" to 146.0
                    )
                )
            )
            dao.upsertClient(
                ClientEntity(
                    id = "cli-2",
                    fullName = "Moussa Traoré",
                    phone = "+22378445566",
                    address = "ACI 2000",
                    notes = "Client régulier",
                    latestMeasurements = mapOf(
                        "Tour de poitrine" to 106.0,
                        "Tour de taille" to 92.0,
                        "Carrure" to 47.0,
                        "Longueur manche" to 64.0,
                        "Longueur pantalon" to 105.0
                    )
                )
            )

            dao.upsertEmployee(EmployeeEntity("emp-1", "Oumar Sangaré", "+22376010203", "Coupeur", "fixe", 120000))
            dao.upsertEmployee(EmployeeEntity("emp-2", "Bintou Keïta", "+22379123456", "Tailleur", "piece", 8500))
            dao.upsertEmployee(EmployeeEntity("emp-3", "Sékou Doumbia", "+22365987654", "Brodeur", "commission", 20))

            dao.upsertOrder(
                OrderEntity(
                    id = "ord-1",
                    reference = "CMD-${today.year}-041",
                    clientId = "cli-1",
                    modelId = "mod-2",
                    fabricNotes = "Satin soie émeraude 4 yards",
                    measurements = mapOf(
                        "Tour de poitrine" to 96.0,
                        "Tour de taille" to 76.0,
                        "Tour de hanches" to 104.0,
                        "Carrure" to 39.0,
                        "Longueur robe / boubou" to 146.0
                    ),
                    status = OrderStatus.ESSAYAGE.label,
                    stockDeducted = true,
                    assignedEmployeeId = "emp-2",
                    orderDate = today.minusDays(4).toString(),
                    fittingDate = today.toString(),
                    deliveryDate = today.plusDays(1).toString(),
                    totalPrice = 38000,
                    advancePaid = 20000
                )
            )
            dao.upsertOrder(
                OrderEntity(
                    id = "ord-2",
                    reference = "CMD-${today.year}-042",
                    clientId = "cli-2",
                    modelId = "mod-3",
                    fabricNotes = "Lainage anthracite 3,20m",
                    measurements = mapOf(
                        "Tour de poitrine" to 106.0,
                        "Tour de taille" to 92.0,
                        "Carrure" to 47.0,
                        "Longueur manche" to 64.0,
                        "Longueur pantalon" to 105.0
                    ),
                    status = OrderStatus.COUTURE.label,
                    stockDeducted = true,
                    assignedEmployeeId = "emp-2",
                    orderDate = today.minusDays(9).toString(),
                    deliveryDate = today.minusDays(1).toString(),
                    totalPrice = 65000,
                    advancePaid = 35000
                )
            )

            dao.upsertExpense(ExpenseEntity("exp-1", "Loyer atelier", 65000, today.minusDays(10).toString(), "Loyer mensuel local"))
            dao.upsertExpense(ExpenseEntity("exp-2", "Électricité/Eau", 14500, today.minusDays(5).toString(), "Facture énergie machines"))
        }
    }

    private class InitCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.atelierDao()
                    dao.upsertSettings(WorkshopSettingsEntity())
                    com.atelierpro.couture.license.LicenseManager.ensureInitialized(dao)
                }
            }
        }
    }
}
