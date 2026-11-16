package com.atelierpro.couture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atelierpro.couture.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtelierDatabaseTest {
    private lateinit var db: AtelierDatabase
    private lateinit var dao: AtelierDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AtelierDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.atelierDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun fullWorkshopCrudAndSeedSampleWorksOffline() = runBlocking {
        AtelierDatabase.seedSampleWorkshop(dao)

        val models = dao.observeModels().first()
        val clients = dao.observeClients().first()
        val orders = dao.observeOrders().first()
        val inventory = dao.observeInventory().first()
        val employees = dao.observeEmployees().first()
        val expenses = dao.observeExpenses().first()

        assertEquals(3, models.size)
        assertEquals(2, clients.size)
        assertEquals(2, orders.size)
        assertEquals(6, inventory.size)
        assertEquals(3, employees.size)
        assertEquals(2, expenses.size)

        val customOrder = OrderEntity(
            id = "ord-test",
            reference = "CMD-2026-099",
            clientId = clients.first().id,
            modelId = models.first().id,
            measurements = mapOf("Tour de poitrine" to 101.5),
            orderDate = "2026-11-15",
            deliveryDate = "2026-11-20",
            totalPrice = 45000L,
            advancePaid = 15000L
        )
        dao.upsertOrder(customOrder)
        val reloaded = dao.observeOrders().first().find { it.id == "ord-test" }
        assertNotNull(reloaded)
        assertEquals(101.5, reloaded!!.measurements["Tour de poitrine"] ?: 0.0, 0.001)
    }
}
