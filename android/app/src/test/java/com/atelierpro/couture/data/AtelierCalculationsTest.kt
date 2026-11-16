package com.atelierpro.couture.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AtelierCalculationsTest {

    @Test
    fun calculateOrderBalance_neverNegative() {
        assertEquals(18000L, AtelierCalculations.calculateOrderBalance(38000L, 20000L))
        assertEquals(0L, AtelierCalculations.calculateOrderBalance(38000L, 45000L))
    }

    @Test
    fun calculateModelProfitability_usesRealOrdersWhenAvailable() {
        val inventory = mapOf(
            "merc-1" to HaberdasheryItemEntity("merc-1", "Fil", "bobine", 750L, 20, 5),
            "merc-2" to HaberdasheryItemEntity("merc-2", "Doublure", "mètre", 1500L, 10, 4)
        )
        val model = GarmentModelEntity(
            id = "mod-1",
            name = "Robe",
            category = "Femme",
            defaultPrice = 30000L,
            estimatedDays = 5,
            measurementKeys = listOf("Tour de poitrine"),
            recipe = listOf(RecipeEntry("merc-1", 2), RecipeEntry("merc-2", 3)) // 1500 + 4500 = 6000
        )
        val orders = listOf(
            OrderEntity("o1", "CMD-2026-001", "c1", "mod-1", measurements = emptyMap(), orderDate = "2026-11-01", deliveryDate = "2026-11-06", totalPrice = 40000L, advancePaid = 20000L),
            OrderEntity("o2", "CMD-2026-002", "c2", "mod-1", measurements = emptyMap(), orderDate = "2026-11-02", deliveryDate = "2026-11-07", totalPrice = 50000L, advancePaid = 50000L)
        )

        val result = AtelierCalculations.calculateModelProfitability(model, orders, inventory)
        assertEquals(2, result.orderCount)
        assertEquals(45000L, result.averageSellingPrice)
        assertEquals(6000L, result.haberdasheryCost)
        assertEquals(39000L, result.averageMargin)
        assertEquals(86, result.marginPercent)
    }

    @Test
    fun calculateEmployeeEarnings_supportsFixePieceAndCommission() {
        val orders = listOf(
            OrderEntity("o1", "CMD-2026-001", "c1", "m1", measurements = emptyMap(), assignedEmployeeId = "e2", orderDate = "2026-11-01", deliveryDate = "2026-11-06", totalPrice = 40000L, advancePaid = 10000L),
            OrderEntity("o2", "CMD-2026-002", "c2", "m1", measurements = emptyMap(), assignedEmployeeId = "e2", orderDate = "2026-11-02", deliveryDate = "2026-11-07", totalPrice = 60000L, advancePaid = 30000L),
            OrderEntity("o3", "CMD-2026-003", "c3", "m1", measurements = emptyMap(), assignedEmployeeId = "e3", orderDate = "2026-11-02", deliveryDate = "2026-11-07", totalPrice = 50000L, advancePaid = 50000L)
        )
        val empFixe = EmployeeEntity("e1", "Oumar", "+223", "Coupeur", "fixe", 120000L)
        val empPiece = EmployeeEntity("e2", "Bintou", "+223", "Tailleur", "piece", 8500L)
        val empComm = EmployeeEntity("e3", "Sekou", "+223", "Brodeur", "commission", 20L)

        assertEquals(120000L, AtelierCalculations.calculateEmployeeEarnings(empFixe, orders))
        assertEquals(17000L, AtelierCalculations.calculateEmployeeEarnings(empPiece, orders))
        assertEquals(10000L, AtelierCalculations.calculateEmployeeEarnings(empComm, orders))
    }

    @Test
    fun generateNextOrderReference_avoidsCollisionsAfterDeletions() {
        assertEquals("CMD-2026-001", AtelierCalculations.generateNextOrderReference(2026, emptyList()))
        val existing = listOf(
            OrderEntity("o1", "CMD-2026-001", "c1", "m1", measurements = emptyMap(), orderDate = "2026-11-01", deliveryDate = "2026-11-05", totalPrice = 10000L, advancePaid = 0L),
            OrderEntity("o4", "CMD-2026-004", "c1", "m1", measurements = emptyMap(), orderDate = "2026-11-01", deliveryDate = "2026-11-05", totalPrice = 10000L, advancePaid = 0L)
        )
        assertEquals("CMD-2026-005", AtelierCalculations.generateNextOrderReference(2026, existing))
    }
}
