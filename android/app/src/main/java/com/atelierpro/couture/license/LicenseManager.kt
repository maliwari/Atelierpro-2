package com.atelierpro.couture.license

import com.atelierpro.couture.data.AtelierDao
import com.atelierpro.couture.data.LicenseStateEntity
import com.atelierpro.couture.data.SyncTimestamp
import com.atelierpro.couture.data.UsedActivationCodeEntity
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class LicenseStatus(val label: String) {
    TRIAL("Essai gratuit"),
    ACTIVE("Actif"),
    EXPIRED("Expiré")
}

data class LicenseEvaluation(
    val workshopCode: String,
    val status: LicenseStatus,
    val expirationDate: String,
    val remainingDays: Long,
    val isClockTampered: Boolean,
    val lastKnownIso: String,
    val isSynced: Int
)

/**
 * Gestionnaire de licence hors-ligne & activation manuelle par WhatsApp.
 *
 * Format du code : {DUREE}-{SIGNATURE_6_CARACTERES}
 * - M1 : +30 jours
 * - A1 : +365 jours
 * Signature = HMAC-SHA256(ID_ATELIER + "-" + DUREE + "-" + MOIS_ANNEE_ACTUEL, SECRET_KEY)
 * tronquée aux 6 premiers caractères hexadécimaux en majuscules.
 */
object LicenseManager {

    const val WHATSAPP_ADMIN_PHONE = "22365972000"
    const val TRIAL_DAYS = 14L
    private const val CLOCK_ROLLBACK_TOLERANCE_MS = 60_000L // 1 minute de tolérance NTP
    private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val SECRET_KEY = "AtelierPro_M@li_2026!HMAC#Key_v1"

    private val YEAR_MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    private val CODE_REGEX = Regex("^(M1|M3|A1)-([A-Z0-9]{6})$")

    fun generateWorkshopCode(): String {
        val rng = SecureRandom()
        val suffix = buildString(4) {
            repeat(4) {
                append(CODE_ALPHABET[rng.nextInt(CODE_ALPHABET.length)])
            }
        }
        return "ATEL-$suffix"
    }

    fun buildWhatsAppActivationUrl(workshopCode: String): String {
        val message = "Bonjour, je souhaite activer AtelierPro. ID : $workshopCode"
        val encoded = URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        return "https://wa.me/$WHATSAPP_ADMIN_PHONE?text=$encoded"
    }

    fun currentYearMonth(now: Instant = Instant.now()): String =
        YEAR_MONTH_FORMAT.format(now.atZone(ZoneOffset.UTC))

    fun computeSignature(
        workshopCode: String,
        durationCode: String,
        yearMonth: String = currentYearMonth()
    ): String {
        val payload = "${workshopCode.trim().uppercase()}-${durationCode.trim().uppercase()}-$yearMonth"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02X".format(it) }
        return hex.take(6)
    }

    fun parseDurationDays(durationCode: String): Int? = when (durationCode.uppercase()) {
        "M1" -> 30
        "M3" -> 90
        "A1" -> 365
        else -> null
    }

    suspend fun ensureInitialized(dao: AtelierDao, now: Instant = Instant.now()): LicenseStateEntity {
        val existing = dao.getLicenseState()
        if (existing != null) return existing

        val today = LocalDate.now(ZoneOffset.UTC)
        val initial = LicenseStateEntity(
            id = 1,
            workshopCode = generateWorkshopCode(),
            planType = "trial",
            expirationDate = today.plusDays(TRIAL_DAYS).toString(),
            lastKnownEpochMs = now.toEpochMilli(),
            lastKnownIso = now.toString(),
            isSynced = 0,
            updatedAt = now.toString()
        )
        dao.upsertLicenseState(initial)
        return initial
    }

    /**
     * Vérifie la cohérence de l'horloge système à chaque ouverture/reprise de l'application.
     * Si la date du téléphone est antérieure à derniere_date_connue, bloque l'app sans écraser derniere_date_connue.
     */
    suspend fun checkAndEvaluate(dao: AtelierDao, now: Instant = Instant.now()): LicenseEvaluation {
        var entity = ensureInitialized(dao, now)
        val nowMs = now.toEpochMilli()
        val isTampered = nowMs + CLOCK_ROLLBACK_TOLERANCE_MS < entity.lastKnownEpochMs

        if (!isTampered && nowMs > entity.lastKnownEpochMs) {
            entity = entity.copy(
                lastKnownEpochMs = nowMs,
                lastKnownIso = now.toString()
            )
            dao.upsertLicenseState(entity)
        }

        return evaluateEntity(entity, isTampered, now)
    }

    fun evaluateEntity(
        entity: LicenseStateEntity,
        isClockTampered: Boolean,
        now: Instant = Instant.now()
    ): LicenseEvaluation {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        val expDate = runCatching { LocalDate.parse(entity.expirationDate) }.getOrElse { today.minusDays(1) }
        val remaining = ChronoUnit.DAYS.between(today, expDate)
        val status = when {
            today.isAfter(expDate) -> LicenseStatus.EXPIRED
            entity.planType == "trial" -> LicenseStatus.TRIAL
            else -> LicenseStatus.ACTIVE
        }
        return LicenseEvaluation(
            workshopCode = entity.workshopCode,
            status = status,
            expirationDate = entity.expirationDate,
            remainingDays = remaining.coerceAtLeast(0L),
            isClockTampered = isClockTampered,
            lastKnownIso = entity.lastKnownIso,
            isSynced = entity.isSynced
        )
    }

    /**
     * Vérifie hors-ligne un code {DUREE}-{SIG6}, contrôle la table codes_utilises,
     * prolonge date_expiration et passe is_synced = 0 pour synchronisation MySQL.
     */
    suspend fun activateCode(
        dao: AtelierDao,
        rawCode: String,
        now: Instant = Instant.now()
    ): Result<LicenseEvaluation> {
        val entity = ensureInitialized(dao, now)
        val nowMs = now.toEpochMilli()
        if (nowMs + CLOCK_ROLLBACK_TOLERANCE_MS < entity.lastKnownEpochMs) {
            return Result.failure(IllegalStateException("Horloge incorrecte. Réglez la date et l'heure du téléphone avant d'activer."))
        }

        val normalized = rawCode.trim().uppercase()
        val match = CODE_REGEX.matchEntire(normalized)
            ?: return Result.failure(IllegalArgumentException("Format invalide. Attendu : M1-XXXXXX, M3-XXXXXX ou A1-XXXXXX."))

        val durationCode = match.groupValues[1]
        val providedSig = match.groupValues[2]
        val daysToAdd = parseDurationDays(durationCode)
            ?: return Result.failure(IllegalArgumentException("Durée non reconnue ($durationCode)."))

        if (dao.findUsedCode(normalized) != null) {
            return Result.failure(IllegalStateException("Ce code d'activation a déjà été utilisé sur cet atelier."))
        }

        val expectedSig = computeSignature(entity.workshopCode, durationCode, currentYearMonth(now))
        val validSig = MessageDigest.isEqual(
            expectedSig.toByteArray(Charsets.UTF_8),
            providedSig.toByteArray(Charsets.UTF_8)
        )
        if (!validSig) {
            return Result.failure(IllegalArgumentException("Code d'activation invalide ou expiré pour ce mois."))
        }

        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        val currentExp = runCatching { LocalDate.parse(entity.expirationDate) }.getOrElse { today }
        val baseDate = if (currentExp.isAfter(today)) currentExp else today
        val newExpiration = baseDate.plusDays(daysToAdd.toLong()).toString()
        val timestamp = SyncTimestamp.nowIsoUtc()

        dao.insertUsedCode(
            UsedActivationCodeEntity(
                code = normalized,
                durationCode = durationCode,
                daysAdded = daysToAdd,
                usedAt = timestamp,
                isSynced = 0
            )
        )

        val updated = entity.copy(
            planType = "pro",
            expirationDate = newExpiration,
            lastKnownEpochMs = maxOf(entity.lastKnownEpochMs, nowMs),
            lastKnownIso = now.toString(),
            isSynced = 0,
            updatedAt = timestamp
        )
        dao.upsertLicenseState(updated)

        return Result.success(evaluateEntity(updated, isClockTampered = false, now = now))
    }
}
