package com.atelierpro.couture.util

import android.content.Context
import android.net.Uri
import com.atelierpro.couture.data.AtelierCalculations
import com.atelierpro.couture.data.ClientEntity
import com.atelierpro.couture.data.ExpenseEntity
import com.atelierpro.couture.data.GarmentModelEntity
import com.atelierpro.couture.data.OrderEntity

/**
 * Generates semicolon-delimited UTF-8 BOM CSV exports for Excel / LibreOffice accounting.
 */
object CsvExporter {

    fun escapeCsv(value: String): String {
        val cleaned = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        return if (cleaned.contains(';') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }

    fun buildOrdersCsv(
        orders: List<OrderEntity>,
        clientsById: Map<String, ClientEntity>,
        modelsById: Map<String, GarmentModelEntity>,
        currency: String
    ): String {
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("Reference;DateCommande;DateLivraison;Client;Telephone;Modele;Statut;PrixTotal($currency);Avance($currency);Solde($currency)")
        orders.forEach { ord ->
            val client = clientsById[ord.clientId]
            val model = modelsById[ord.modelId]
            val balance = AtelierCalculations.calculateOrderBalance(ord.totalPrice, ord.advancePaid)
            sb.appendLine(
                listOf(
                    ord.reference,
                    ord.orderDate,
                    ord.deliveryDate,
                    client?.fullName ?: "Client",
                    client?.phone ?: "",
                    model?.name ?: "Sur-mesure",
                    ord.status,
                    ord.totalPrice.toString(),
                    ord.advancePaid.toString(),
                    balance.toString()
                ).joinToString(";") { escapeCsv(it) }
            )
        }
        return sb.toString()
    }

    fun buildExpensesCsv(
        expenses: List<ExpenseEntity>,
        currency: String
    ): String {
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("Date;Categorie;Description;Montant($currency)")
        expenses.forEach { exp ->
            sb.appendLine(
                listOf(
                    exp.date,
                    exp.category,
                    exp.description,
                    exp.amount.toString()
                ).joinToString(";") { escapeCsv(it) }
            )
        }
        return sb.toString()
    }

    fun writeCsvToUri(context: Context, uri: Uri, content: String): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return false
            true
        }.getOrDefault(false)
    }
}
