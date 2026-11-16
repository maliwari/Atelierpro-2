package com.atelierpro.couture.util

import com.atelierpro.couture.data.ClientEntity
import com.atelierpro.couture.data.ExpenseEntity
import com.atelierpro.couture.data.GarmentModelEntity
import com.atelierpro.couture.data.OrderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun escapeCsvQuotesSemicolonsAndSanitizesNewlines() {
        assertEquals("Simple", CsvExporter.escapeCsv("Simple"))
        assertEquals("\"Tissu; brodé\"", CsvExporter.escapeCsv("Tissu; brodé"))
        assertEquals("\"Note \"\"VIP\"\"\"", CsvExporter.escapeCsv("Note \"VIP\""))
        assertEquals("Ligne 1 Ligne 2", CsvExporter.escapeCsv("Ligne 1\nLigne 2"))
    }

    @Test
    fun buildOrdersCsvIncludesBomHeaderAndCalculatedBalance() {
        val clients = mapOf("c1" to ClientEntity("c1", "Aminata Diallo", "+22376112233", "Bamako"))
        val models = mapOf("m1" to GarmentModelEntity("m1", "Robe Sirène", "Femme", defaultPrice = 38000, estimatedDays = 5, measurementKeys = emptyList(), recipe = emptyList()))
        val orders = listOf(
            OrderEntity("o1", "CMD-2026-001", "c1", "m1", measurements = emptyMap(), orderDate = "2026-11-10", deliveryDate = "2026-11-15", totalPrice = 38000L, advancePaid = 20000L, status = "Essayage")
        )

        val csv = CsvExporter.buildOrdersCsv(orders, clients, models, "FCFA")
        assertTrue(csv.startsWith("\uFEFFReference;"))
        assertTrue(csv.contains("CMD-2026-001;2026-11-10;2026-11-15;Aminata Diallo;+22376112233;Robe Sirène;Essayage;38000;20000;18000"))
    }

    @Test
    fun buildExpensesCsvFormatsRows() {
        val expenses = listOf(ExpenseEntity("e1", "Loyer atelier", 65000L, "2026-11-01", "Local; principal"))
        val csv = CsvExporter.buildExpensesCsv(expenses, "FCFA")
        assertTrue(csv.contains("2026-11-01;Loyer atelier;\"Local; principal\";65000"))
    }
}
