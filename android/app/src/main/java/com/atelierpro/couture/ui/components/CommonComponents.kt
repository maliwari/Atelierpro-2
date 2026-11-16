package com.atelierpro.couture.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.atelierpro.couture.ui.theme.*
import com.atelierpro.couture.util.ImageCompressor
import java.io.File

@Composable
fun NumericKeypadDialog(
    title: String,
    unit: String,
    initialValue: Double,
    allowDecimal: Boolean = true,
    step: Double = 1.0,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var raw by remember {
        mutableStateOf(
            if (initialValue == 0.0) "0"
            else if (initialValue % 1.0 == 0.0) initialValue.toLong().toString()
            else initialValue.toString()
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Display + Quick -/+
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5F4))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val cur = raw.toDoubleOrNull() ?: 0.0
                            val next = (cur - step).coerceAtLeast(0.0)
                            raw = if (!allowDecimal || next % 1.0 == 0.0) next.toLong().toString() else String.format("%.1f", next).replace(',', '.')
                        },
                        modifier = Modifier.size(46.dp)
                    ) { Icon(Icons.Default.Remove, contentDescription = "Diminuer") }

                    Text(
                        text = if (unit.isNotBlank()) "$raw $unit" else raw,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = StoneInk
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val cur = raw.toDoubleOrNull() ?: 0.0
                            val next = cur + step
                            raw = if (!allowDecimal || next % 1.0 == 0.0) next.toLong().toString() else String.format("%.1f", next).replace(',', '.')
                        },
                        modifier = Modifier.size(46.dp)
                    ) { Icon(Icons.Default.Add, contentDescription = "Augmenter") }
                }

                // 3x4 keypad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(if (allowDecimal) "." else "C", "0", "⌫")
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            OutlinedButton(
                                onClick = {
                                    when (key) {
                                        "C" -> raw = "0"
                                        "⌫" -> raw = if (raw.length <= 1) "0" else raw.dropLast(1)
                                        "." -> if (allowDecimal && !raw.contains(".")) raw += "."
                                        else -> {
                                            if (raw == "0") raw = key
                                            else if (raw.length < 10) raw += key
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Annuler") }
                    Button(
                        onClick = {
                            onConfirm(raw.toDoubleOrNull() ?: 0.0)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoAtelier)
                    ) { Text("Valider") }
                }
            }
        }
    }
}

@Composable
fun PhotoPickerCard(
    label: String,
    photoPath: String,
    onPhotoChanged: (String) -> Unit
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val saved = ImageCompressor.saveCompressedImage(context, uri, "cam")
            if (saved != null) onPhotoChanged(saved)
        }
    }

    val launchCamera = {
        val tmpFile = File(context.cacheDir, "cam_tmp_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Permission caméra refusée. Utilisez la galerie.", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val saved = ImageCompressor.saveCompressedImage(context, uri, "gal")
            if (saved != null) onPhotoChanged(saved)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = StoneInk)
        if (photoPath.isNotBlank() && File(photoPath).exists()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF5F5F4))
            ) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("Changer")
                    }
                    FilledTonalIconButton(onClick = { onPhotoChanged("") }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = CrimsonAlert)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (hasPerm) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Caméra")
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galerie")
                }
            }
        }
        Text(
            "Compression automatique WebP / JPEG 70 % pour préserver la mémoire.",
            style = MaterialTheme.typography.labelSmall,
            color = StoneMuted
        )
    }
}

@Composable
fun PdfPreviewDialog(
    pdfFile: File,
    title: String,
    onDismiss: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val pages = remember(pdfFile.absolutePath) {
        val bitmaps = mutableListOf<Bitmap>()
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                }
            }
        }
        bitmaps
    }

    DisposableEffect(pages) {
        onDispose { pages.forEach { it.recycle() } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }
                HorizontalDivider(color = StoneBorder)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pages.forEach { bmp ->
                        Card(border = BorderStroke(1.dp, StoneBorder)) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = title,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                HorizontalDivider(color = StoneBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Fermer")
                    }
                    Button(
                        onClick = onShareWhatsApp,
                        modifier = Modifier.weight(1.4f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Partager WhatsApp")
                    }
                }
            }
        }
    }
}
