package com.atelierpro.couture.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.atelierpro.couture.data.*
import java.io.File
import java.io.FileOutputStream

/**
 * Native Android PdfDocument generator for:
 * 1) Detailed A4 Client Receipt (with Workshop Logo, Model, Measurements, Balance Due & automatic pagination)
 * 2) Detailed A4 Tailor Cut Sheet (Fiche de coupe)
 * 3) Safe WhatsApp Intent sharing via FileProvider
 */
object PdfReceiptGenerator {

    fun generateDetailedReceiptPdf(
        context: Context,
        order: OrderEntity,
        client: ClientEntity?,
        model: GarmentModelEntity?,
        employee: EmployeeEntity?,
        settings: WorkshopSettingsEntity
    ): File {
        val pdf = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas

        val indigoPaint = Paint().apply { color = Color.rgb(30, 58, 95) }
        val surfacePaint = Paint().apply { color = Color.rgb(245, 245, 244) }
        val borderPaint = Paint().apply {
            color = Color.rgb(214, 211, 209)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val titleWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 236, 244)
            textSize = 11f
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 25, 23)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(87, 83, 78)
            textSize = 10.5f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 25, 23)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // 1. Header Band + Workshop Logo
        canvas.drawRect(0f, 0f, 595f, 96f, indigoPaint)
        val logoRect = RectF(32f, 16f, 96f, 80f)
        canvas.drawRoundRect(logoRect, 8f, 8f, Paint().apply { color = Color.WHITE })

        val customLogo = if (settings.workshopLogoPath.isNotBlank() && File(settings.workshopLogoPath).exists()) {
            BitmapFactory.decodeFile(settings.workshopLogoPath)
        } else null

        if (customLogo != null) {
            canvas.drawBitmap(customLogo, null, RectF(36f, 20f, 92f, 76f), null)
            customLogo.recycle()
        } else {
            val monogramPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 58, 95)
                textSize = 24f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val initials = settings.workshopName.split(" ").filter { it.isNotBlank() }
                .take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "AP" }
            canvas.drawText(initials, 64f, 56f, monogramPaint)
        }

        canvas.drawText(settings.workshopName.take(36), 112f, 40f, titleWhite)
        canvas.drawText(settings.workshopAddress.take(54), 112f, 58f, subWhite)
        canvas.drawText("Tél. : ${settings.workshopPhone}", 112f, 74f, subWhite)

        // 2. Title & Reference
        var y = 128f
        canvas.drawText("REÇU DÉTAILLÉ DE COMMANDE", 32f, y, headingPaint)
        val refPaint = Paint(valuePaint).apply {
            color = Color.rgb(30, 58, 95)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Réf. ${order.reference}", 563f, y, refPaint)
        y += 10f
        canvas.drawLine(32f, y, 563f, y, borderPaint)

        // 3. Client & Model Info Cards
        y += 16f
        canvas.drawRoundRect(RectF(32f, y, 290f, y + 96f), 8f, 8f, surfacePaint)
        canvas.drawRoundRect(RectF(305f, y, 563f, y + 96f), 8f, 8f, surfacePaint)

        canvas.drawText("CLIENT", 44f, y + 18f, labelPaint)
        canvas.drawText((client?.fullName ?: "Client").take(30), 44f, y + 36f, valuePaint)
        canvas.drawText("Tél. : ${client?.phone ?: "—"}", 44f, y + 54f, labelPaint)
        canvas.drawText((client?.address ?: "").take(34), 44f, y + 72f, labelPaint)

        canvas.drawText("MODÈLE & SUIVI", 317f, y + 18f, labelPaint)
        canvas.drawText((model?.name ?: "Sur-mesure").take(28), 317f, y + 36f, valuePaint)
        canvas.drawText("${model?.category ?: "—"} • ${order.status}", 317f, y + 54f, labelPaint)
        canvas.drawText("Livraison : ${order.deliveryDate}", 317f, y + 72f, labelPaint)
        if (employee != null) {
            canvas.drawText("Tailleur : ${employee.fullName.take(24)}", 317f, y + 88f, labelPaint)
        }

        y += 116f
        if (order.fabricNotes.isNotBlank()) {
            canvas.drawText("Tissu : ${order.fabricNotes.take(70)}", 32f, y, labelPaint)
            y += 20f
        }

        // 4. Measurements Section with pagination support
        canvas.drawText("Mensurations enregistrées (cm)", 32f, y, headingPaint)
        y += 12f

        val entries = order.measurements.entries.filter { it.value > 0.0 }.toList()
        if (entries.isEmpty()) {
            canvas.drawText("Aucune mesure saisie.", 32f, y + 14f, labelPaint)
            y += 28f
        } else {
            var idx = 0
            while (idx < entries.size) {
                val remainingRowsOnPage = ((760f - y) / 24f).toInt().coerceAtLeast(1)
                val maxEntriesOnPage = remainingRowsOnPage * 2
                val slice = entries.subList(idx, (idx + maxEntriesOnPage).coerceAtMost(entries.size))
                val rowCount = (slice.size + 1) / 2
                val boxHeight = rowCount * 24f + 12f
                canvas.drawRoundRect(RectF(32f, y, 563f, y + boxHeight), 8f, 8f, borderPaint)

                slice.forEachIndexed { localIdx, entry ->
                    val col = localIdx % 2
                    val row = localIdx / 2
                    val xPos = if (col == 0) 46f else 312f
                    val yPos = y + 20f + row * 24f
                    canvas.drawText(entry.key.take(24), xPos, yPos, labelPaint)
                    val valRight = Paint(valuePaint).apply { textAlign = Paint.Align.RIGHT }
                    canvas.drawText("${entry.value} cm", xPos + 235f, yPos, valRight)
                }

                idx += slice.size
                y += boxHeight + 16f

                if (idx < entries.size) {
                    pdf.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                    page = pdf.startPage(pageInfo)
                    canvas = page.canvas
                    y = 48f
                    canvas.drawText("Mensurations (suite) — ${order.reference}", 32f, y, headingPaint)
                    y += 14f
                }
            }
        }

        // Ensure financial summary fits on current page
        if (y + 130f > 790f) {
            pdf.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            page = pdf.startPage(pageInfo)
            canvas = page.canvas
            y = 48f
        }

        // 5. Financial Summary Box
        canvas.drawRoundRect(RectF(32f, y, 563f, y + 106f), 10f, 10f, surfacePaint)
        canvas.drawText("DÉTAIL FINANCIER", 46f, y + 22f, labelPaint)

        val rightAmount = Paint(valuePaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Prix total convenu", 46f, y + 44f, labelPaint)
        canvas.drawText("${order.totalPrice} ${settings.currency}", 549f, y + 44f, rightAmount)

        canvas.drawText("Avance versée", 46f, y + 64f, labelPaint)
        val paidPaint = Paint(rightAmount).apply { color = Color.rgb(21, 128, 61) }
        canvas.drawText("${order.advancePaid} ${settings.currency}", 549f, y + 64f, paidPaint)

        canvas.drawLine(46f, y + 74f, 549f, y + 74f, borderPaint)
        val balance = (order.totalPrice - order.advancePaid).coerceAtLeast(0L)
        val balanceTitle = Paint(headingPaint).apply { textSize = 13.5f }
        val balanceVal = Paint(headingPaint).apply {
            textSize = 13.5f
            textAlign = Paint.Align.RIGHT
            color = if (balance > 0) Color.rgb(185, 28, 28) else Color.rgb(21, 128, 61)
        }
        canvas.drawText("SOLDE À PAYER", 46f, y + 95f, balanceTitle)
        canvas.drawText(
            if (balance > 0) "$balance ${settings.currency}" else "SOLDÉ (0)",
            549f,
            y + 95f,
            balanceVal
        )

        val footerPaint = Paint(labelPaint).apply {
            textAlign = Paint.Align.CENTER
            textSize = 9.5f
        }
        canvas.drawText(
            "Merci de présenter ce reçu lors du retrait de votre tenue — ${settings.workshopName}",
            297.5f,
            810f,
            footerPaint
        )

        pdf.finishPage(page)

        val pdfDir = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }
        val safeRef = order.reference.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(pdfDir, "Recu_$safeRef.pdf")
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    fun generateCutSheetPdf(
        context: Context,
        order: OrderEntity,
        client: ClientEntity?,
        model: GarmentModelEntity?,
        employee: EmployeeEntity?,
        inventory: List<HaberdasheryItemEntity>,
        settings: WorkshopSettingsEntity
    ): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val darkPaint = Paint().apply { color = Color.rgb(28, 25, 23) }
        val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 25, 23)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(68, 64, 60)
            textSize = 11f
        }

        canvas.drawRect(0f, 0f, 595f, 70f, darkPaint)
        canvas.drawText("FICHE DE COUPE — ${order.reference}", 32f, 42f, whiteBold)

        var y = 104f
        canvas.drawText("Modèle : ${model?.name ?: "Sur-mesure"}", 32f, y, heading)
        y += 20f
        canvas.drawText("Client : ${client?.fullName ?: "—"}  •  Tailleur : ${employee?.fullName ?: "Non assigné"}", 32f, y, body)
        y += 18f
        canvas.drawText("Livraison : ${order.deliveryDate}  •  Essayage : ${order.fittingDate ?: "—"}", 32f, y, body)
        y += 18f
        canvas.drawText("Tissu : ${order.fabricNotes.ifBlank { "Voir photo" }}", 32f, y, body)

        y += 30f
        canvas.drawText("Mensurations requises (cm)", 32f, y, heading)
        y += 20f
        order.measurements.entries.forEachIndexed { idx, entry ->
            val colX = if (idx % 2 == 0) 32f else 310f
            if (idx > 0 && idx % 2 == 0) y += 20f
            canvas.drawText("${entry.key} : ${entry.value} cm", colX, y, body)
        }

        y += 34f
        canvas.drawText("Mercerie nécessaire (Recette modèle)", 32f, y, heading)
        y += 20f
        if (model != null && model.recipe.isNotEmpty()) {
            model.recipe.forEach { rec ->
                val item = inventory.find { it.id == rec.itemId }
                canvas.drawText("[  ] ${item?.name ?: rec.itemId} — ${rec.quantity} ${item?.unit ?: ""}", 36f, y, body)
                y += 18f
            }
        } else {
            canvas.drawText("Aucune fourniture spécifique.", 36f, y, body)
        }

        if (order.notes.isNotBlank()) {
            y += 24f
            canvas.drawText("Notes atelier : ${order.notes.take(120)}", 32f, y, body)
        }

        pdf.finishPage(page)
        val pdfDir = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }
        val safeRef = order.reference.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(pdfDir, "Fiche_Coupe_$safeRef.pdf")
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    fun sharePdfViaWhatsApp(context: Context, pdfFile: File, message: String): Boolean {
        return runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val pm = context.packageManager
                val waPkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
                    runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
                }
                if (waPkg != null) setPackage(waPkg)
            }
            val chooser = Intent.createChooser(intent, "Partager le reçu PDF via WhatsApp").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun openWhatsAppMessage(context: Context, phone: String, message: String): Boolean {
        return runCatching {
            val cleanPhone = phone.replace(Regex("[^0-9]"), "")
            val uri = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
