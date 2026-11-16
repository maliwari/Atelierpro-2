package com.atelierpro.couture.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Compresses captured/selected fabric, model, or logo images to WebP (or JPEG) at 70% quality,
 * max dimension 1080px, stored in internal app filesDir/photos/.
 */
object ImageCompressor {
    fun saveCompressedImage(context: Context, sourceUri: Uri, prefix: String = "img"): String? {
        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (original == null) return null

        val scaled = scaleBitmap(original, 1080)
        val ext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "webp" else "jpg"
        val outFile = File(photosDir, "${prefix}_${System.currentTimeMillis()}.$ext")

        FileOutputStream(outFile).use { fos ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.JPEG
            }
            scaled.compress(format, 70, fos)
        }
        if (scaled != original) scaled.recycle()
        original.recycle()
        return outFile.absolutePath
    }

    fun saveCompressedBitmap(context: Context, bitmap: Bitmap, prefix: String = "cam"): String {
        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val scaled = scaleBitmap(bitmap, 1080)
        val ext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "webp" else "jpg"
        val outFile = File(photosDir, "${prefix}_${System.currentTimeMillis()}.$ext")
        FileOutputStream(outFile).use { fos ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.JPEG
            }
            scaled.compress(format, 70, fos)
        }
        if (scaled != bitmap) scaled.recycle()
        return outFile.absolutePath
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val maxSide = max(w, h)
        if (maxSide <= maxDim) return src
        val ratio = maxDim.toFloat() / maxSide.toFloat()
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
