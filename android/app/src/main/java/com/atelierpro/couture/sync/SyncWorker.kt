package com.atelierpro.couture.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker de secours exécuté automatiquement par Android dès que la connexion réseau revient,
 * en cas d'échec ou de timeout lors d'une synchronisation précédente.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val syncResult = SyncManager.getInstance(applicationContext).syncNow(silent = true)
        return if (syncResult.isSuccess) Result.success() else Result.retry()
    }
}
